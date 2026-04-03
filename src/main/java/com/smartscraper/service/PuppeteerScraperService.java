package com.smartscraper.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartscraper.dto.ContentBlock;
import com.smartscraper.exception.ScrapingException;
import com.smartscraper.service.ScrapeResult;
import com.smartscraper.service.ScraperService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Puppeteer-based renderer (dynamic pages) via an external API endpoint.
 * Expects the query parameter to be the target URL.
 */
@Service
public class PuppeteerScraperService implements ScraperService {

    private static final Logger logger = LoggerFactory.getLogger(PuppeteerScraperService.class);

    private final JsoupScraperService jsoupScraperService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // Example default: http://localhost:3001/render?url=
    private final String renderBaseUrl;

    public PuppeteerScraperService(
            JsoupScraperService jsoupScraperService,
            @Value("${app.puppeteer.render-url:http://localhost:3001/render?url=}") String renderBaseUrl) {
        this.jsoupScraperService = jsoupScraperService;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.renderBaseUrl = renderBaseUrl;
    }

    @Override
    public ScrapeResult scrape(String query) {
        if (query == null || query.trim().isEmpty()) {
            throw new ScrapingException("Puppeteer scrape query is empty");
        }

        try {
            String encodedTargetUrl = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String renderUrl = renderBaseUrl + encodedTargetUrl;

            logger.debug("Rendering dynamic page via Puppeteer API: {}", renderUrl);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(renderUrl))
                    .GET()
                    .header("Accept", "application/json, text/html;q=0.9, */*;q=0.8")
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ScrapingException("Puppeteer render failed with status: " + response.statusCode());
            }

            String body = response.body();
            String html = extractHtml(body);

            List<ContentBlock> blocks = jsoupScraperService.scrapeContentFromHtml(html);
            return new ScrapeResult(blocks);

        } catch (ScrapingException e) {
            throw e;
        } catch (Exception e) {
            throw new ScrapingException("Puppeteer scrape failed for: " + query, e);
        }
    }

    private String extractHtml(String body) throws Exception {
        if (body == null) {
            throw new ScrapingException("Puppeteer response body is null");
        }

        String trimmed = body.trim();
        if (trimmed.startsWith("{")) {
            // Expected JSON: {"html":"..."}
            JsonNode node = objectMapper.readTree(trimmed);
            JsonNode htmlNode = node.get("html");
            if (htmlNode != null && !htmlNode.asText().isEmpty()) {
                return htmlNode.asText();
            }
        }

        // If it's not JSON, assume it's raw HTML.
        return body;
    }
}

