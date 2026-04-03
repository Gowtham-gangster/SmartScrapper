package com.smartscraper.service;

import com.smartscraper.dto.ProductItem;
import com.smartscraper.entity.Product;
import com.smartscraper.exception.ScrapingException;
import com.smartscraper.repository.ProductRepository;
import com.smartscraper.service.ScrapeResult;
import com.smartscraper.service.ScraperService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Module-level scraper for E-commerce.
 * Expects query format: {@code <searchHistoryId>|<searchQuery>}
 */
@Service
public class EcommerceScraperService implements ScraperService {

    private static final Logger logger = LoggerFactory.getLogger(EcommerceScraperService.class);

    private final ImprovedProductScraperService productScraperService;
    private final ProductRepository productRepository;

    public EcommerceScraperService(ImprovedProductScraperService productScraperService,
                                    ProductRepository productRepository) {
        this.productScraperService = productScraperService;
        this.productRepository = productRepository;
    }

    @Override
    public ScrapeResult scrape(String query) {
        try {
            ParsedQuery parsed = ParsedQuery.parse(query);
            Long searchHistoryId = parsed.searchHistoryId;
            String searchQuery = parsed.q;

            logger.info("E-commerce scraping started (searchHistoryId={}, query='{}')", searchHistoryId, searchQuery);

            List<ProductItem> productItems = productScraperService.searchProducts(searchQuery);

            List<Product> entities = new ArrayList<>();
            for (ProductItem item : productItems) {
                Product entity = new Product(
                        searchHistoryId,
                        item.getName(),
                        item.getPrice(),
                        item.getCurrency(),
                        item.getSource(),
                        item.getUrl(),
                        item.getImageUrl()
                );
                entities.add(entity);
            }

            List<Product> saved = new ArrayList<>();
            for (Product savedEntity : productRepository.saveAll(entities)) {
                saved.add(savedEntity);
            }

            logger.info("E-commerce scraping completed (searchHistoryId={}, saved={})", searchHistoryId, saved.size());
            return new ScrapeResult(saved);
        } catch (Exception e) {
            throw new ScrapingException("E-commerce scraping failed", e);
        }
    }

    private static class ParsedQuery {
        private final Long searchHistoryId;
        private final String q;

        private ParsedQuery(Long searchHistoryId, String q) {
            this.searchHistoryId = searchHistoryId;
            this.q = q;
        }

        private static ParsedQuery parse(String query) {
            if (query == null || query.isBlank()) {
                throw new IllegalArgumentException("Query is empty");
            }
            String[] parts = query.split("\\|", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Query must be formatted as <searchHistoryId>|<query>");
            }
            Long searchHistoryId = Long.parseLong(parts[0]);
            String q = parts[1];
            return new ParsedQuery(searchHistoryId, q);
        }
    }
}

