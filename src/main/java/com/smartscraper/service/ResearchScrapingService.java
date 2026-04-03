package com.smartscraper.service;

import com.smartscraper.dto.ContentBlock;
import com.smartscraper.entity.ScrapedContent;
import com.smartscraper.entity.ScrapedSource;
import com.smartscraper.repository.ScrapedContentRepository;
import com.smartscraper.repository.ScrapedSourceRepository;
import com.smartscraper.service.UrlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service for performing research-focused web scraping.
 * Scrapes academic and informational content related to research topics.
 */
@Service
public class ResearchScrapingService {

    private static final Logger logger = LoggerFactory.getLogger(ResearchScrapingService.class);

    // Quality thresholds for filtering content
    private static final double MIN_KEYWORD_FREQUENCY_SCORE = 0.3;  // Minimum keyword relevance
    private static final int MIN_CONTENT_LENGTH = 100;               // Minimum paragraph length
    private static final double MIN_OVERALL_SCORE = 0.4;             // Minimum combined score

    private final ScrapedSourceRepository scrapedSourceRepository;
    private final ScrapedContentRepository scrapedContentRepository;
    private final TopicDiscoveryService topicDiscoveryService;
    private final JsoupScraperService jsoupScraperService;
    private final PuppeteerScraperService puppeteerScraperService;
    private final Set<String> dynamicDomains;
    
    // Trusted academic and educational sources for enhanced scoring
    private static final Set<String> TRUSTED_SOURCES = new HashSet<>(Arrays.asList(
            "scholar.google.com", "geeksforgeeks.org", "w3schools.com", "udemy.com",
            "coursera.org", "ieee.org", "springer.com", "elsevier.com", "researchgate.net",
            "arxiv.org", "acm.org", "sciencedirect.com", "jstor.org", "pubmed.ncbi.nlm.nih.gov",
            "nature.com", "science.org", "wiley.com", "tandfonline.com", "mit.edu",
            "stanford.edu", "harvard.edu", "wikipedia.org", "stackoverflow.com", "github.com", "medium.com"
    ));

    @Autowired
    public ResearchScrapingService(
            ScrapedSourceRepository scrapedSourceRepository,
            ScrapedContentRepository scrapedContentRepository,
            TopicDiscoveryService topicDiscoveryService,
            JsoupScraperService jsoupScraperService,
            PuppeteerScraperService puppeteerScraperService,
            @Value("${app.puppeteer.dynamic-domains:}") String dynamicDomainsCsv) {
        this.scrapedSourceRepository = scrapedSourceRepository;
        this.scrapedContentRepository = scrapedContentRepository;
        this.topicDiscoveryService = topicDiscoveryService;
        this.jsoupScraperService = jsoupScraperService;
        this.puppeteerScraperService = puppeteerScraperService;
        this.dynamicDomains = parseDomains(dynamicDomainsCsv);
    }

    /**
     * Perform research scraping for a given topic.
     * 
     * @param searchId Search history ID
     * @param topicName Research topic
     */
    @Transactional
    public void performResearchScraping(Long searchId, String topicName) {
        logger.info("Starting research scraping for topic: '{}' (Search ID: {})", topicName, searchId);

        try {
            // Get research URLs for the topic
            List<String> researchUrls = getResearchUrls(topicName);
            logger.info("Found {} research URLs to scrape", researchUrls.size());

            int successCount = 0;
            int failCount = 0;
            int totalSaved = 0;
            int totalDiscarded = 0;

            // Scrape each URL
            for (String url : researchUrls) {
                try {
                    ContentFilterStats stats = scrapeResearchUrl(searchId, url, topicName);
                    successCount++;
                    totalSaved += stats.savedCount;
                    totalDiscarded += stats.discardedCount;
                    logger.debug("Successfully scraped: {} (Saved: {}, Discarded: {})", 
                            url, stats.savedCount, stats.discardedCount);
                } catch (Exception e) {
                    failCount++;
                    logger.warn("Failed to scrape URL {}: {}", url, e.getMessage());
                }
            }

            logger.info("Research scraping completed. URLs: Success: {}, Failed: {}. Content: Saved: {}, Discarded: {}", 
                    successCount, failCount, totalSaved, totalDiscarded);

        } catch (Exception e) {
            logger.error("Error during research scraping: {}", e.getMessage(), e);
            throw new RuntimeException("Research scraping failed", e);
        }
    }

    /**
     * Statistics for content filtering.
     */
    private static class ContentFilterStats {
        int savedCount;
        int discardedCount;

        ContentFilterStats(int savedCount, int discardedCount) {
            this.savedCount = savedCount;
            this.discardedCount = discardedCount;
        }
    }

    /**
     * Get research URLs for a topic.
     * Uses TopicDiscoveryService to find URLs from multiple diverse domains.
     * 
     * @param topicName Research topic
     * @return List of URLs to scrape
     */
    private List<String> getResearchUrls(String topicName) {
        logger.info("Discovering research URLs for topic: '{}'", topicName);
        
        // Use TopicDiscoveryService to find diverse URLs
        List<String> urls = topicDiscoveryService.discoverUrls(topicName);
        
        // Log discovery statistics
        Map<String, Object> stats = topicDiscoveryService.getDiscoveryStats(urls);
        logger.info("URL discovery stats: Total URLs: {}, Unique domains: {}, Avg per domain: {}", 
                stats.get("totalUrls"), 
                stats.get("uniqueDomains"),
                String.format("%.2f", stats.get("averageUrlsPerDomain")));
        
        return urls;
    }

    /**
     * Scrape content from a research URL.
     * Uses JsoupScraperService to extract structured content blocks.
     * Filters out low-quality content before saving.
     * 
     * @param searchId Search history ID
     * @param url URL to scrape
     * @param topicName Research topic for relevance scoring
     * @return Statistics about saved and discarded content
     */
    private ContentFilterStats scrapeResearchUrl(Long searchId, String url, String topicName) throws Exception {
        logger.debug("Scraping research URL: {}", url);

        // Extract domain and save source
        String domain = UrlUtils.extractDomain(url);
        ScrapedSource source = getOrCreateSource(domain, url);

        // Extract content blocks using the appropriate engine (static vs dynamic).
        List<ContentBlock> contentBlocks = scrapeContent(url);
        
        if (contentBlocks == null || contentBlocks.isEmpty()) {
            logger.warn("No content blocks extracted from URL: {}", url);
            return new ContentFilterStats(0, 0);
        }

        logger.info("Extracted {} content blocks from {}", contentBlocks.size(), url);

        int savedCount = 0;
        int discardedCount = 0;

        // Save each content block as a separate ScrapedContent record
        for (ContentBlock block : contentBlocks) {
            String paragraphText = block.getParagraph();
            
            // Filter 1: Check minimum content length
            if (paragraphText.length() < MIN_CONTENT_LENGTH) {
                logger.debug("Discarded block at position {} - too short ({} chars)", 
                        block.getPosition(), paragraphText.length());
                discardedCount++;
                continue;
            }

            // Calculate individual scores for filtering
            double keywordScore = calculateKeywordFrequencyScore(paragraphText, topicName);
            
            // Filter 2: Check minimum keyword frequency
            if (keywordScore < MIN_KEYWORD_FREQUENCY_SCORE) {
                logger.debug("Discarded block at position {} - low keyword frequency (score: {})", 
                        block.getPosition(), String.format("%.3f", keywordScore));
                discardedCount++;
                continue;
            }

            // Calculate comprehensive relevance score for this block
            double relevanceScore = calculateContentBlockScore(block, topicName);

            // Filter 3: Check minimum overall score
            if (relevanceScore < MIN_OVERALL_SCORE) {
                logger.debug("Discarded block at position {} - low overall score ({})", 
                        block.getPosition(), String.format("%.3f", relevanceScore));
                discardedCount++;
                continue;
            }

            // Content passed all filters - save it
            String contentText = block.getCombinedText();
            
            // Calculate enhanced relevance score inline
            int enhancedScore = calculateEnhancedScore(source, contentText, topicName);
            
            ScrapedContent scrapedContent = new ScrapedContent(
                    searchId,
                    source.getId(),
                    contentText,
                    relevanceScore
            );
            scrapedContent.setRelevanceScoreInt(enhancedScore);
            scrapedContentRepository.save(scrapedContent);
            savedCount++;

            logger.debug("Saved content block (position {}) with relevance score: {} (enhanced: {})", 
                    block.getPosition(), String.format("%.3f", relevanceScore), enhancedScore);
        }

        logger.info("Content filtering results for {}: Saved: {}, Discarded: {}, Total: {}", 
                url, savedCount, discardedCount, contentBlocks.size());
        
        return new ContentFilterStats(savedCount, discardedCount);
    }

    private List<ContentBlock> scrapeContent(String url) throws Exception {
        String domain = UrlUtils.extractDomain(url);
        if (domain != null && dynamicDomains.contains(domain)) {
            // PuppeteerScraperService returns a ScrapeResult payload of List<ContentBlock>
            @SuppressWarnings("unchecked")
            List<ContentBlock> blocks = (List<ContentBlock>) puppeteerScraperService.scrape(url).payload();
            return blocks;
        }
        return jsoupScraperService.scrapeContent(url);
    }

    private static Set<String> parseDomains(String csv) {
        if (csv == null || csv.isBlank()) {
            return new HashSet<>();
        }
        Set<String> domains = new HashSet<>();
        Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .forEach(domains::add);
        return domains;
    }

    /**
     * Calculate comprehensive relevance score for a content block.
     * Considers multiple factors: keyword frequency, paragraph length, and heading relevance.
     * 
     * @param block Content block to score
     * @param topicName Research topic
     * @return Relevance score (0.0 to 1.0)
     */
    private double calculateContentBlockScore(ContentBlock block, String topicName) {
        String paragraph = block.getParagraph();
        String heading = block.getHeading();
        
        // Factor 1: Topic keyword frequency in paragraph (40% weight)
        double keywordScore = calculateKeywordFrequencyScore(paragraph, topicName);
        
        // Factor 2: Paragraph length score (30% weight)
        double lengthScore = calculateParagraphLengthScore(paragraph);
        
        // Factor 3: Heading relevance score (30% weight)
        double headingScore = calculateHeadingRelevanceScore(heading, topicName);
        
        // Weighted combination
        double finalScore = (keywordScore * 0.40) + (lengthScore * 0.30) + (headingScore * 0.30);
        
        logger.debug("Content block score breakdown - Keyword: {}, Length: {}, Heading: {}, Final: {}", 
                String.format("%.3f", keywordScore),
                String.format("%.3f", lengthScore),
                String.format("%.3f", headingScore),
                String.format("%.3f", finalScore));
        
        return Math.min(finalScore, 1.0);
    }

    /**
     * Calculate keyword frequency score based on topic keywords in paragraph.
     * Higher score for more keyword matches and better distribution.
     * 
     * @param paragraph Paragraph text
     * @param topicName Topic keywords
     * @return Score (0.0 to 1.0)
     */
    private double calculateKeywordFrequencyScore(String paragraph, String topicName) {
        if (paragraph == null || paragraph.isEmpty() || topicName == null || topicName.isEmpty()) {
            return 0.0;
        }

        String paragraphLower = paragraph.toLowerCase();
        String topicLower = topicName.toLowerCase();

        // Split topic into keywords
        String[] keywords = topicLower.split("\\s+");
        
        int totalMatches = 0;
        int keywordsFound = 0;

        // Count occurrences of each keyword
        for (String keyword : keywords) {
            if (keyword.length() > 2) { // Ignore very short words (a, an, the, etc.)
                int count = countOccurrences(paragraphLower, keyword);
                if (count > 0) {
                    totalMatches += count;
                    keywordsFound++;
                }
            }
        }

        // Calculate keyword density (matches per 100 words)
        int wordCount = paragraph.split("\\s+").length;
        double density = (totalMatches * 100.0) / Math.max(wordCount, 1);
        
        // Calculate keyword coverage (percentage of keywords found)
        double coverage = (double) keywordsFound / Math.max(keywords.length, 1);
        
        // Combine density and coverage
        // Density score: normalize to 0-1 (assume 5 matches per 100 words is excellent)
        double densityScore = Math.min(density / 5.0, 1.0);
        
        // Final score: 70% density, 30% coverage
        double score = (densityScore * 0.7) + (coverage * 0.3);
        
        return Math.min(score, 1.0);
    }

    /**
     * Calculate paragraph length score.
     * Optimal length is 200-500 characters. Too short or too long gets lower scores.
     * 
     * @param paragraph Paragraph text
     * @return Score (0.0 to 1.0)
     */
    private double calculateParagraphLengthScore(String paragraph) {
        if (paragraph == null || paragraph.isEmpty()) {
            return 0.0;
        }

        int length = paragraph.length();
        
        // Optimal range: 200-500 characters
        int optimalMin = 200;
        int optimalMax = 500;
        
        if (length >= optimalMin && length <= optimalMax) {
            // Perfect length
            return 1.0;
        } else if (length < optimalMin) {
            // Too short - scale from 0.3 to 1.0
            // Below 50 chars: 0.3
            // 50-200 chars: scale linearly to 1.0
            if (length < 50) {
                return 0.3;
            }
            return 0.3 + (0.7 * (length - 50.0) / (optimalMin - 50.0));
        } else {
            // Too long - scale from 1.0 down to 0.5
            // 500-1000 chars: scale linearly to 0.5
            // Above 1000 chars: 0.5
            if (length > 1000) {
                return 0.5;
            }
            return 1.0 - (0.5 * (length - optimalMax) / (1000.0 - optimalMax));
        }
    }

    /**
     * Calculate heading relevance score.
     * Higher score if heading contains topic keywords.
     * 
     * @param heading Heading text (can be null)
     * @param topicName Topic keywords
     * @return Score (0.0 to 1.0)
     */
    private double calculateHeadingRelevanceScore(String heading, String topicName) {
        // No heading = neutral score
        if (heading == null || heading.isEmpty()) {
            return 0.5;
        }

        if (topicName == null || topicName.isEmpty()) {
            return 0.5;
        }

        String headingLower = heading.toLowerCase();
        String topicLower = topicName.toLowerCase();

        // Split topic into keywords
        String[] keywords = topicLower.split("\\s+");
        
        int keywordsFound = 0;
        int totalKeywords = 0;

        // Check if heading contains each keyword
        for (String keyword : keywords) {
            if (keyword.length() > 2) { // Ignore very short words
                totalKeywords++;
                if (headingLower.contains(keyword)) {
                    keywordsFound++;
                }
            }
        }

        if (totalKeywords == 0) {
            return 0.5;
        }

        // Calculate coverage
        double coverage = (double) keywordsFound / totalKeywords;
        
        // Scale: 0 keywords = 0.3, all keywords = 1.0
        double score = 0.3 + (coverage * 0.7);
        
        return Math.min(score, 1.0);
    }

    /**
     * Calculate relevance score based on topic keyword matching.
     * Legacy method - kept for backward compatibility.
     * 
     * @param content Scraped content text
     * @param topicName Research topic
     * @return Relevance score (0.0 to 1.0)
     */
    private double calculateRelevanceScore(String content, String topicName) {
        if (content == null || content.isEmpty() || topicName == null || topicName.isEmpty()) {
            return 0.0;
        }

        String contentLower = content.toLowerCase();
        String topicLower = topicName.toLowerCase();

        // Split topic into keywords
        String[] keywords = topicLower.split("\\s+");

        // Count keyword occurrences
        int totalMatches = 0;
        for (String keyword : keywords) {
            if (keyword.length() > 2) { // Ignore very short words
                int count = countOccurrences(contentLower, keyword);
                totalMatches += count;
            }
        }

        // Calculate score based on keyword density
        // Normalize by content length (per 1000 characters)
        double density = (totalMatches * 1000.0) / Math.max(content.length(), 1);
        
        // Cap score at 1.0
        return Math.min(density / 10.0, 1.0);
    }

    /**
     * Count occurrences of a substring in a string.
     * 
     * @param text Text to search in
     * @param substring Substring to search for
     * @return Number of occurrences
     */
    private int countOccurrences(String text, String substring) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }

    /**
     * Get existing source or create new one.
     * 
     * @param domain Domain name
     * @param url Full URL
     * @return ScrapedSource entity
     */
    private ScrapedSource getOrCreateSource(String domain, String url) {
        return scrapedSourceRepository.findBySourceUrl(url)
                .orElseGet(() -> {
                    ScrapedSource newSource = new ScrapedSource(domain, url);
                    // Try to extract metadata from the URL/content
                    extractAndSetMetadata(newSource, url);
                    return scrapedSourceRepository.save(newSource);
                });
    }

    /**
     * Extract and set metadata for a source.
     * This is a basic implementation that can be enhanced with actual web scraping.
     * 
     * @param source The source to populate
     * @param url The URL to extract from
     */
    private void extractAndSetMetadata(ScrapedSource source, String url) {
        try {
            // Try to scrape the page to get title and other metadata
            List<ContentBlock> blocks = scrapeContent(url);
            if (blocks != null && !blocks.isEmpty()) {
                // Use the first heading as title if available
                for (ContentBlock block : blocks) {
                    if (block.getHeading() != null && !block.getHeading().isEmpty()) {
                        source.setTitle(block.getHeading());
                        break;
                    }
                }
                
                // Try to extract year from URL or content
                Integer year = extractYearFromUrl(url);
                if (year != null) {
                    source.setYear(year);
                }
                
                // Use first paragraph as abstract if no abstract found
                if (blocks.size() > 0 && blocks.get(0).getParagraph() != null) {
                    String firstParagraph = blocks.get(0).getParagraph();
                    if (firstParagraph.length() > 200) {
                        source.setPaperAbstract(firstParagraph.substring(0, 200) + "...");
                    } else {
                        source.setPaperAbstract(firstParagraph);
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Could not extract metadata from {}: {}", url, e.getMessage());
        }
    }

    /**
     * Extract year from URL or content.
     * Looks for 4-digit years between 1990 and current year + 1.
     * 
     * @param url The URL to extract from
     * @return Year if found, null otherwise
     */
    private Integer extractYearFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        
        // Look for 4-digit year in URL
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(19|20)\\d{2}");
        java.util.regex.Matcher matcher = pattern.matcher(url);
        
        int currentYear = java.time.Year.now().getValue();
        while (matcher.find()) {
            try {
                int year = Integer.parseInt(matcher.group());
                // Validate year is reasonable (between 1990 and current year + 1)
                if (year >= 1990 && year <= currentYear + 1) {
                    return year;
                }
            } catch (NumberFormatException e) {
                // Continue searching
            }
        }
        
        return null;
    }
    
    /**
     * Calculate enhanced relevance score based on multiple criteria.
     * Scoring: Title match +40, Abstract match +20, Recent (>2020) +10, Trusted source +10
     */
    private int calculateEnhancedScore(ScrapedSource source, String contentText, String keyword) {
        int score = 0;
        if (source == null || keyword == null || keyword.isEmpty()) {
            return score;
        }

        String keywordLower = keyword.toLowerCase();
        String[] keywords = keywordLower.split("\\s+");

        // Keyword in title (+40 points)
        if (source.getTitle() != null && !source.getTitle().isEmpty()) {
            String titleLower = source.getTitle().toLowerCase();
            for (String kw : keywords) {
                if (kw.length() > 2 && titleLower.contains(kw)) {
                    score += 40;
                    break;
                }
            }
        }

        // Keyword in abstract (+20 points)
        if (source.getPaperAbstract() != null && !source.getPaperAbstract().isEmpty()) {
            String abstractLower = source.getPaperAbstract().toLowerCase();
            for (String kw : keywords) {
                if (kw.length() > 2 && abstractLower.contains(kw)) {
                    score += 20;
                    break;
                }
            }
        }

        // Recent paper after 2020 (+10 points)
        if (source.getYear() != null && source.getYear() > 2020) {
            score += 10;
        }

        // Trusted source (+10 points)
        if (source.getDomainName() != null && isTrustedSource(source.getDomainName())) {
            score += 10;
        }

        return score;
    }
    
    /**
     * Check if domain is from a trusted source.
     */
    private boolean isTrustedSource(String domain) {
        if (domain == null || domain.isEmpty()) {
            return false;
        }
        String domainLower = domain.toLowerCase();
        if (TRUSTED_SOURCES.contains(domainLower)) {
            return true;
        }
        for (String trustedSource : TRUSTED_SOURCES) {
            if (domainLower.endsWith(trustedSource) || domainLower.contains(trustedSource)) {
                return true;
            }
        }
        return false;
    }
}
