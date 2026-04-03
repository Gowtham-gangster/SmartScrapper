package com.smartscraper.service;

/**
 * Common scraper interface.
 * Implementations should interpret {@code query} as they need for the scraper type.
 */
public interface ScraperService {
    ScrapeResult scrape(String query);
}

