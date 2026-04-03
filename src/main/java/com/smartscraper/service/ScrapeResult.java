package com.smartscraper.service;

/**
 * Wrapper for returning scraper output in a uniform way.
 * Payload is module/scraper specific (e.g., List<News>, List<Product>, List<ContentBlock>).
 */
public record ScrapeResult(Object payload) {
}

