package com.smartscraper.service;

import com.smartscraper.dto.ContentBlock;
import com.smartscraper.exception.ScrapingException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.smartscraper.service.ScrapeResult;
import com.smartscraper.service.ScraperService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for extracting structured content from web pages using Jsoup.
 * Extracts paragraphs with their associated headings while filtering out
 * navigation, footers, ads, and other non-content elements.
 */
@Service
public class JsoupScraperService implements ScraperService {

    private static final Logger logger = LoggerFactory.getLogger(JsoupScraperService.class);
    private static final int TIMEOUT_MS = 10000;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private static final int MIN_PARAGRAPH_LENGTH = 50;

    /**
     * Scrape content from a URL and return individual paragraphs with headings.
     * 
     * @param url URL to scrape
     * @return List of content blocks (heading + paragraph pairs)
     * @throws Exception if scraping fails
     */
    public List<ContentBlock> scrapeContent(String url) throws Exception {
        logger.debug("Scraping content from URL: {}", url);

        // Fetch the page
        Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get();

        // Remove unwanted elements
        removeUnwantedElements(doc);

        // Extract content blocks
        List<ContentBlock> contentBlocks = extractContentBlocks(doc);

        logger.info("Extracted {} content blocks from {}", contentBlocks.size(), url);
        return contentBlocks;
    }

    /**
     * ScraperService interface entrypoint.
     * Treats {@code query} as a URL to scrape.
     */
    @Override
    public ScrapeResult scrape(String query) {
        try {
            return new ScrapeResult(scrapeContent(query));
        } catch (Exception e) {
            throw new ScrapingException("Jsoup scrape failed for: " + query, e);
        }
    }

    /**
     * Scrape content from HTML string.
     * 
     * @param html HTML content
     * @return List of content blocks
     */
    public List<ContentBlock> scrapeContentFromHtml(String html) {
        logger.debug("Scraping content from HTML string");

        Document doc = Jsoup.parse(html);

        // Remove unwanted elements
        removeUnwantedElements(doc);

        // Extract content blocks
        List<ContentBlock> contentBlocks = extractContentBlocks(doc);

        logger.info("Extracted {} content blocks from HTML", contentBlocks.size());
        return contentBlocks;
    }

    /**
     * Remove unwanted elements from the document.
     * Removes navigation, footers, ads, comments, scripts, and other non-content elements.
     * 
     * @param doc Jsoup document
     */
    private void removeUnwantedElements(Document doc) {
        // Remove scripts and styles
        doc.select("script, style, noscript").remove();

        // Remove navigation elements
        doc.select("nav, [role=navigation], .nav, .navbar, .navigation, #nav, #navbar, #navigation").remove();

        // Remove headers (site headers, not content headings)
        doc.select("header, [role=banner], .header, .site-header, #header").remove();

        // Remove footers
        doc.select("footer, [role=contentinfo], .footer, .site-footer, #footer").remove();

        // Remove sidebars
        doc.select("aside, [role=complementary], .sidebar, .side-bar, #sidebar").remove();

        // Remove ads and promotional content
        doc.select(".ad, .ads, .advertisement, .promo, .promotion, .sponsored").remove();
        doc.select("[class*=ad-], [class*=ads-], [id*=ad-], [id*=ads-]").remove();
        doc.select("[class*=advert], [id*=advert]").remove();

        // Remove social media widgets
        doc.select(".social, .share, .sharing, .social-share, .social-media").remove();

        // Remove comments sections
        doc.select(".comments, .comment-section, #comments, [class*=comment]").remove();

        // Remove forms (usually newsletter signups, search forms)
        doc.select("form").remove();

        // Remove iframes (usually ads or embeds)
        doc.select("iframe").remove();

        // Remove hidden elements
        doc.select("[style*=display:none], [style*=display: none]").remove();
        doc.select("[hidden]").remove();

        // Remove breadcrumbs
        doc.select(".breadcrumb, .breadcrumbs, [role=navigation]").remove();

        // Remove menus
        doc.select(".menu, .main-menu, #menu").remove();

        logger.debug("Removed unwanted elements from document");
    }

    /**
     * Extract content blocks (paragraphs with associated headings) from the document.
     * 
     * @param doc Jsoup document
     * @return List of content blocks
     */
    private List<ContentBlock> extractContentBlocks(Document doc) {
        List<ContentBlock> contentBlocks = new ArrayList<>();

        // Try to find main content area first
        Element mainContent = findMainContent(doc);
        if (mainContent == null) {
            mainContent = doc.body();
        }

        // Extract paragraphs and headings
        String currentHeading = null;
        int position = 0;

        // Get all content elements (headings and paragraphs) in order
        Elements contentElements = mainContent.select("h1, h2, h3, h4, p");

        for (Element element : contentElements) {
            String tagName = element.tagName();

            if (tagName.matches("h[1-4]")) {
                // Update current heading context
                currentHeading = element.text().trim();
                logger.debug("Found heading: {}", currentHeading);

            } else if (tagName.equals("p")) {
                // Extract paragraph
                String paragraphText = element.text().trim();

                // Filter out short paragraphs (likely not content)
                if (paragraphText.length() >= MIN_PARAGRAPH_LENGTH) {
                    ContentBlock block = new ContentBlock(currentHeading, paragraphText, position++);
                    contentBlocks.add(block);
                    logger.debug("Added content block at position {}: {} chars", position - 1, paragraphText.length());
                }
            }
        }

        return contentBlocks;
    }

    /**
     * Find the main content area of the page.
     * Looks for common content container elements.
     * 
     * @param doc Jsoup document
     * @return Main content element or null if not found
     */
    private Element findMainContent(Document doc) {
        // Try common content selectors in order of specificity
        String[] contentSelectors = {
                "article",
                "main",
                "[role=main]",
                ".main-content",
                ".content",
                "#content",
                ".article-content",
                ".article-body",
                ".post-content",
                ".entry-content",
                ".page-content"
        };

        for (String selector : contentSelectors) {
            Elements elements = doc.select(selector);
            if (!elements.isEmpty()) {
                logger.debug("Found main content using selector: {}", selector);
                return elements.first();
            }
        }

        logger.debug("Main content area not found, using body");
        return null;
    }

    /**
     * Extract only paragraphs (without headings) from a URL.
     * 
     * @param url URL to scrape
     * @return List of paragraph texts
     * @throws Exception if scraping fails
     */
    public List<String> extractParagraphs(String url) throws Exception {
        List<ContentBlock> blocks = scrapeContent(url);
        List<String> paragraphs = new ArrayList<>();

        for (ContentBlock block : blocks) {
            paragraphs.add(block.getParagraph());
        }

        return paragraphs;
    }

    /**
     * Extract content blocks and combine them into a single text.
     * 
     * @param url URL to scrape
     * @return Combined text content
     * @throws Exception if scraping fails
     */
    public String extractCombinedText(String url) throws Exception {
        List<ContentBlock> blocks = scrapeContent(url);
        StringBuilder combined = new StringBuilder();

        for (ContentBlock block : blocks) {
            if (block.getHeading() != null && !block.getHeading().isEmpty()) {
                combined.append(block.getHeading()).append("\n\n");
            }
            combined.append(block.getParagraph()).append("\n\n");
        }

        return combined.toString().trim();
    }

    /**
     * Filter content blocks by minimum paragraph length.
     * 
     * @param blocks List of content blocks
     * @param minLength Minimum paragraph length
     * @return Filtered list
     */
    public List<ContentBlock> filterByLength(List<ContentBlock> blocks, int minLength) {
        List<ContentBlock> filtered = new ArrayList<>();

        for (ContentBlock block : blocks) {
            if (block.getParagraph().length() >= minLength) {
                filtered.add(block);
            }
        }

        logger.debug("Filtered {} blocks to {} blocks (min length: {})", 
                blocks.size(), filtered.size(), minLength);
        return filtered;
    }

    /**
     * Filter content blocks by keyword relevance.
     * 
     * @param blocks List of content blocks
     * @param keywords Keywords to match
     * @return Filtered list
     */
    public List<ContentBlock> filterByKeywords(List<ContentBlock> blocks, String... keywords) {
        List<ContentBlock> filtered = new ArrayList<>();

        for (ContentBlock block : blocks) {
            String combinedText = block.getCombinedText().toLowerCase();
            
            for (String keyword : keywords) {
                if (combinedText.contains(keyword.toLowerCase())) {
                    filtered.add(block);
                    break;
                }
            }
        }

        logger.debug("Filtered {} blocks to {} blocks by keywords", blocks.size(), filtered.size());
        return filtered;
    }

    /**
     * Get content statistics.
     * 
     * @param blocks List of content blocks
     * @return Statistics map
     */
    public ContentStats getContentStats(List<ContentBlock> blocks) {
        ContentStats stats = new ContentStats();
        stats.totalBlocks = blocks.size();
        stats.blocksWithHeadings = 0;
        stats.totalCharacters = 0;
        stats.averageParagraphLength = 0;

        for (ContentBlock block : blocks) {
            if (block.getHeading() != null && !block.getHeading().isEmpty()) {
                stats.blocksWithHeadings++;
            }
            stats.totalCharacters += block.getParagraph().length();
        }

        if (stats.totalBlocks > 0) {
            stats.averageParagraphLength = stats.totalCharacters / stats.totalBlocks;
        }

        return stats;
    }

    /**
     * Content statistics class.
     */
    public static class ContentStats {
        public int totalBlocks;
        public int blocksWithHeadings;
        public int totalCharacters;
        public int averageParagraphLength;

        @Override
        public String toString() {
            return "ContentStats{" +
                    "totalBlocks=" + totalBlocks +
                    ", blocksWithHeadings=" + blocksWithHeadings +
                    ", totalCharacters=" + totalCharacters +
                    ", averageParagraphLength=" + averageParagraphLength +
                    '}';
        }
    }
}
