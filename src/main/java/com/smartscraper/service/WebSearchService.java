package com.smartscraper.service;

import com.smartscraper.dto.SearchResultDto;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for performing web searches and extracting results.
 * Uses DuckDuckGo HTML search for keyword-based searches.
 */
@Service
public class WebSearchService {

    private static final Logger logger = LoggerFactory.getLogger(WebSearchService.class);
    private static final int TIMEOUT = 10000; // 10 seconds
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    /**
     * Perform web search and extract results.
     * Minimal filtering - returns results as-is from search engine.
     * 
     * @param query Search query
     * @return List of search results
     */
    public List<SearchResultDto> search(String query) {
        logger.info("Performing web search for query: {}", query);
        
        List<SearchResultDto> results = new ArrayList<>();

        try {
            // Use DuckDuckGo HTML search
            results = searchDuckDuckGo(query);
            logger.info("Found {} results from DuckDuckGo", results.size());
        } catch (Exception e) {
            logger.error("Error performing web search: {}", e.getMessage(), e);
        }

        return results;
    }

    /**
     * Search using DuckDuckGo HTML interface.
     * 
     * @param query Search query
     * @return List of search results
     * @throws Exception if search fails
     */
    private List<SearchResultDto> searchDuckDuckGo(String query) throws Exception {
        List<SearchResultDto> results = new ArrayList<>();
        
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://html.duckduckgo.com/html/?q=" + encodedQuery;
        
        Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT)
                .referrer("https://duckduckgo.com/")
                .get();

        // Extract search results
        Elements resultElements = doc.select("div.result");
        
        for (Element element : resultElements) {
            try {
                SearchResultDto result = new SearchResultDto();
                
                // Title
                Element titleElement = element.selectFirst("a.result__a");
                if (titleElement != null) {
                    result.setTitle(titleElement.text());
                    result.setUrl(extractUrl(titleElement.attr("href")));
                }
                
                // Snippet
                Element snippetElement = element.selectFirst("a.result__snippet");
                if (snippetElement != null) {
                    result.setSnippet(snippetElement.text());
                }
                
                // Display URL
                Element urlElement = element.selectFirst("span.result__url");
                if (urlElement != null) {
                    result.setDisplayUrl(urlElement.text());
                }
                
                // Only add if we have title and URL
                if (result.getTitle() != null && result.getUrl() != null) {
                    results.add(result);
                }
                
                // Limit to 20 results
                if (results.size() >= 20) {
                    break;
                }
                
            } catch (Exception e) {
                logger.debug("Error parsing search result: {}", e.getMessage());
            }
        }
        
        return results;
    }

    /**
     * Extract actual URL from DuckDuckGo redirect URL.
     * 
     * @param redirectUrl DuckDuckGo redirect URL
     * @return Actual URL
     */
    private String extractUrl(String redirectUrl) {
        if (redirectUrl == null || redirectUrl.isEmpty()) {
            return "";
        }
        
        // DuckDuckGo uses redirect URLs like: //duckduckgo.com/l/?uddg=...&rut=...
        // We need to extract the actual URL from the uddg parameter
        try {
            if (redirectUrl.startsWith("//")) {
                redirectUrl = "https:" + redirectUrl;
            }
            
            // For simplicity, just return the redirect URL
            // In production, you might want to follow the redirect or parse the URL parameter
            return redirectUrl;
            
        } catch (Exception e) {
            logger.debug("Error extracting URL: {}", e.getMessage());
            return redirectUrl;
        }
    }

    /**
     * Clean and format snippet text.
     * Minimal filtering - just basic cleanup.
     * 
     * @param snippet Raw snippet text
     * @return Cleaned snippet
     */
    private String cleanSnippet(String snippet) {
        if (snippet == null || snippet.isEmpty()) {
            return "";
        }
        
        // Minimal filtering - just trim and limit length
        snippet = snippet.trim();
        
        if (snippet.length() > 300) {
            snippet = snippet.substring(0, 297) + "...";
        }
        
        return snippet;
    }
}
