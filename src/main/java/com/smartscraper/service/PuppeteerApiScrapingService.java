package com.smartscraper.service;

import com.smartscraper.dto.puppeteer.*;
import com.smartscraper.entity.News;
import com.smartscraper.entity.Product;
import com.smartscraper.entity.ScrapedSource;
import com.smartscraper.exception.ScrapingException;
import com.smartscraper.repository.NewsRepository;
import com.smartscraper.repository.ProductRepository;
import com.smartscraper.repository.ScrapedSourceRepository;
import com.smartscraper.service.UrlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Service
public class PuppeteerApiScrapingService {

    private static final Logger logger = LoggerFactory.getLogger(PuppeteerApiScrapingService.class);

    private final NewsRepository newsRepository;
    private final ProductRepository productRepository;
    private final ScrapedSourceRepository scrapedSourceRepository;

    private final String baseUrl;
    private final RestTemplate restTemplate;

    public PuppeteerApiScrapingService(
            NewsRepository newsRepository,
            ProductRepository productRepository,
            ScrapedSourceRepository scrapedSourceRepository,
            @Value("${app.puppeteer.api-base-url:http://localhost:3001}") String baseUrl,
            @Value("${app.puppeteer.api-timeout-ms:30000}") long requestTimeoutMs) {
        this.newsRepository = newsRepository;
        this.productRepository = productRepository;
        this.scrapedSourceRepository = scrapedSourceRepository;
        this.baseUrl = baseUrl;

        // Create a RestTemplate with explicit timeouts (connect + read).
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) requestTimeoutMs);
        factory.setReadTimeout((int) requestTimeoutMs);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Scrape and persist News results from Puppeteer microservice.
     */
    public List<News> scrapeAndPersistNews(Long searchHistoryId, String query, int page, int maxPages) {
        validate(page, maxPages);
        if (searchHistoryId == null) throw new IllegalArgumentException("searchHistoryId is required");

        // Note: ownership checks belong in the controller; this service only requires the history id.
        // If searchHistoryId is invalid, callers will typically fail when persisting and/or when joining later.
        Long effectiveSearchHistoryId = searchHistoryId;
        String trimmedQuery = requireNonBlank(query, "query");

        String endpoint = baseUrl + "/scrape/news";
        String url = endpoint + "?query=" + encode(trimmedQuery) + "&page=" + page + "&maxPages=" + maxPages;

        PuppeteerNewsScrapeResponse response = call(url, PuppeteerNewsScrapeResponse.class);
        List<PuppeteerNewsItemDto> items = safeItems(response.getItems());

        // Map to entities
        List<News> entities = new ArrayList<>();
        for (PuppeteerNewsItemDto item : items) {
            LocalDateTime publishedDate = parseToLocalDateTime(item.getDate());
            String title = defaultNonBlank(item.getTitle(), "Untitled");
            String summary = defaultNonBlank(item.getSummary(), "N/A");
            String source = defaultNonBlank(item.getSource(), "Puppeteer");
            entities.add(new News(
                    effectiveSearchHistoryId,
                    title,
                    summary,
                    defaultNonBlank(item.getLink(), "about:blank"),
                    source,
                    publishedDate
            ));
        }

        if (entities.isEmpty()) return entities;

        // Best-effort de-dupe by link
        Map<String, News> uniqueByLink = new LinkedHashMap<>();
        for (News n : entities) {
            String link = n.getLink();
            if (link == null) continue;
            uniqueByLink.putIfAbsent(link, n);
        }

        try {
            return newsRepository.saveAll(uniqueByLink.values());
        } catch (DataIntegrityViolationException e) {
            logger.warn("News persistence had integrity violations (dupe links likely). {}", e.getMessage());
            // Save individually to salvage what we can
            List<News> saved = new ArrayList<>();
            for (News n : uniqueByLink.values()) {
                try {
                    saved.add(newsRepository.save(n));
                } catch (Exception inner) {
                    logger.debug("Skipping news item due to save error: {}", inner.getMessage());
                }
            }
            return saved;
        }
    }

    /**
     * Scrape and persist Products results from Puppeteer microservice.
     */
    public List<Product> scrapeAndPersistProducts(Long searchHistoryId, String query, int page, int maxPages) {
        validate(page, maxPages);

        Long effectiveSearchHistoryId = searchHistoryId;
        String trimmedQuery = requireNonBlank(query, "query");

        String endpoint = baseUrl + "/scrape/products";
        String url = endpoint + "?query=" + encode(trimmedQuery) + "&page=" + page + "&maxPages=" + maxPages;

        PuppeteerProductsScrapeResponse response = call(url, PuppeteerProductsScrapeResponse.class);
        List<PuppeteerProductItemDto> items = safeItems(response.getItems());

        List<Product> entities = new ArrayList<>();
        for (PuppeteerProductItemDto item : items) {
            BigDecimal price = parsePrice(item.getPrice());
            String currency = extractCurrencySymbol(item.getPrice());
            entities.add(new Product(
                    effectiveSearchHistoryId,
                    emptyToNull(item.getProductName()),
                    price,
                    currency,
                    "Puppeteer",
                    emptyToNull(item.getLink()),
                    null
            ));
            // Set rating/reviews (nullable)
            Product p = entities.get(entities.size() - 1);
            p.setRating(emptyToNull(item.getRating()));
            p.setReviews(emptyToNull(item.getReviews()));
        }

        if (entities.isEmpty()) return entities;

        // De-dupe by url
        Map<String, Product> uniqueByUrl = new LinkedHashMap<>();
        for (Product p : entities) {
            if (p.getUrl() == null) continue;
            uniqueByUrl.putIfAbsent(p.getUrl(), p);
        }

        return productRepository.saveAll(uniqueByUrl.values());
    }

    /**
     * Scrape and persist Research paper results from Puppeteer microservice.
     * Note: persists into existing `ScrapedSource` table (domain_name/source_url + title/authors/year/abstract).
     */
    public List<ScrapedSource> scrapeAndPersistResearch(String query) {
        String trimmedQuery = requireNonBlank(query, "query");

        String endpoint = baseUrl + "/scrape/research";
        String url = endpoint + "?query=" + encode(trimmedQuery);

        PuppeteerResearchScrapeResponse response = call(url, PuppeteerResearchScrapeResponse.class);
        List<PuppeteerResearchItemDto> items = safeItems(response.getItems());

        List<ScrapedSource> entities = new ArrayList<>();
        for (PuppeteerResearchItemDto item : items) {
            String link = emptyToNull(item.getLink());
            String domain = UrlUtils.extractDomain(link);
            if (domain == null) continue;

            ScrapedSource source = new ScrapedSource(domain, link);
            source.setTitle(emptyToNull(item.getTitle()));
            source.setAuthors(emptyToNull(item.getAuthors()));
            source.setYear(parseYear(item.getYear()));
            source.setPaperAbstract(emptyToNull(item.getAbstract()));
            entities.add(source);
        }

        if (entities.isEmpty()) return entities;
        return scrapedSourceRepository.saveAll(entities);
    }

    private <T> T call(String url, Class<T> responseType) {
        logger.info("Calling Puppeteer API: {}", url);
        try {
            return restTemplate.getForObject(url, responseType);
        } catch (HttpStatusCodeException ex) {
            logger.error("Puppeteer API returned {}. Body: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new ScrapingException("Puppeteer API error: HTTP " + ex.getStatusCode().value(), ex);
        } catch (ResourceAccessException ex) {
            logger.error("Puppeteer API timed out/unreachable: {}", ex.getMessage());
            throw new ScrapingException("Puppeteer API timed out/unreachable", ex);
        } catch (Exception ex) {
            logger.error("Puppeteer API call failed: {}", ex.getMessage(), ex);
            throw new ScrapingException("Puppeteer API call failed", ex);
        }
    }

    private void validate(int page, int maxPages) {
        if (page < 1) throw new IllegalArgumentException("page must be >= 1");
        if (maxPages < 1) throw new IllegalArgumentException("maxPages must be >= 1");
    }

    private String requireNonBlank(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private <E> List<E> safeItems(List<E> items) {
        return items == null ? Collections.emptyList() : items;
    }

    private String emptyToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private LocalDateTime parseToLocalDateTime(String date) {
        if (date == null || date.isBlank()) return null;
        String t = date.trim();

        // ISO instant
        try {
            Instant instant = Instant.parse(t);
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        } catch (DateTimeParseException ignored) {
        }

        // ISO offset
        try {
            OffsetDateTime odt = OffsetDateTime.parse(t);
            return odt.toLocalDateTime();
        } catch (DateTimeParseException ignored) {
        }

        // Date only
        try {
            LocalDate d = LocalDate.parse(t, DateTimeFormatter.ISO_LOCAL_DATE);
            return d.atStartOfDay();
        } catch (DateTimeParseException ignored) {
        }

        // Fallback yyyy-MM-dd
        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            LocalDate d = LocalDate.parse(t, fmt);
            return d.atStartOfDay();
        } catch (DateTimeParseException ignored) {
        }

        return null;
    }

    private Integer parseYear(String year) {
        if (year == null || year.isBlank()) return null;
        try {
            // Allow "2026", "2026-..." etc
            String digits = year.replaceAll("[^0-9]", "");
            if (digits.length() < 4) return null;
            return Integer.parseInt(digits.substring(0, 4));
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal parsePrice(String priceText) {
        String t = emptyToNull(priceText);
        if (t == null) return null;
        String normalized = t.replaceAll(",", "").trim();
        // Extract first number-ish token
        String[] parts = normalized.replaceAll("[^0-9.]", " ").trim().split("\\s+");
        if (parts.length == 0) return null;
        String match = parts[0];
        if (match.isBlank()) return null;
        try {
            return new BigDecimal(match);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractCurrencySymbol(String priceText) {
        if (priceText == null) return null;
        if (priceText.contains("$")) return "$";
        if (priceText.contains("€")) return "EUR";
        if (priceText.contains("£")) return "GBP";
        return null;
    }

    private String defaultNonBlank(String value, String defaultValue) {
        String v = emptyToNull(value);
        return v == null ? defaultValue : v;
    }


}

