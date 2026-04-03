package com.smartscraper.service;

import com.smartscraper.entity.ScrapedContent;
import com.smartscraper.entity.ScrapedSource;
import com.smartscraper.exception.ScrapingException;
import com.smartscraper.repository.ScrapedContentRepository;
import com.smartscraper.repository.ScrapedSourceRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Multi-platform research scraper service.
 * Scrapes educational content from 13 learning platforms.
 * Expects query format: {@code <searchHistoryId>|<topicName>}
 */
@Service
public class ResearchScraperService implements ScraperService {

    private static final Logger logger = LoggerFactory.getLogger(ResearchScraperService.class);
    private static final int TIMEOUT_MS = 10000;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    private final ScrapedSourceRepository scrapedSourceRepository;
    private final ScrapedContentRepository scrapedContentRepository;
    private final CourseScrapingService courseScrapingService;

    public ResearchScraperService(ScrapedSourceRepository scrapedSourceRepository,
                                   ScrapedContentRepository scrapedContentRepository,
                                   CourseScrapingService courseScrapingService) {
        this.scrapedSourceRepository = scrapedSourceRepository;
        this.scrapedContentRepository = scrapedContentRepository;
        this.courseScrapingService = courseScrapingService;
    }

    @Override
    public ScrapeResult scrape(String query) {
        try {
            ParsedQuery parsed = ParsedQuery.parse(query);
            logger.info("Multi-platform research scraping started (searchHistoryId={}, topic='{}')",
                    parsed.searchHistoryId, parsed.topicName);
            
            // Scrape from all platforms
            scrapeFromAllPlatforms(parsed.searchHistoryId, parsed.topicName);
            
            // Also scrape courses
            courseScrapingService.scrapeCourses(parsed.searchHistoryId, parsed.topicName);
            
            return new ScrapeResult(null);
        } catch (Exception e) {
            throw new ScrapingException("Multi-platform research scraping failed", e);
        }
    }

    @Transactional
    public void scrapeFromAllPlatforms(Long searchHistoryId, String keyword) {
        logger.info("Starting multi-platform scraping for keyword: '{}'", keyword);
        
        List<PlatformResult> allResults = new ArrayList<>();

        // Educational Platforms
        allResults.addAll(scrapeWikipedia(keyword));
        allResults.addAll(scrapeBritannica(keyword));
        allResults.addAll(scrapeStanfordEncyclopedia(keyword));
        allResults.addAll(scrapeKhanAcademy(keyword));
        allResults.addAll(scrapeMITOpenCourseWare(keyword));
        
        // Programming & Tech Learning
        allResults.addAll(scrapeGeeksforGeeks(keyword));
        allResults.addAll(scrapeTutorialsPoint(keyword));
        allResults.addAll(scrapeJavatpoint(keyword));
        allResults.addAll(scrapeW3Schools(keyword));
        allResults.addAll(scrapeProgramiz(keyword));
        allResults.addAll(scrapeFreeCodeCamp(keyword));
        allResults.addAll(scrapeMDNDocs(keyword));
        
        // Online Course Platforms
        allResults.addAll(scrapeCoursera(keyword));
        allResults.addAll(scrapeEdX(keyword));
        allResults.addAll(scrapeUdemy(keyword));
        allResults.addAll(scrapeUdacity(keyword));
        allResults.addAll(scrapeSimplilearn(keyword));
        allResults.addAll(scrapeGreatLearning(keyword));
        allResults.addAll(scrapeScaler(keyword));
        allResults.addAll(scrapeFutureLearn(keyword));
        allResults.addAll(scrapePluralSight(keyword));
        
        // Indian Educational Institutions
        allResults.addAll(scrapeNPTEL(keyword));
        allResults.addAll(scrapeSWAYAM(keyword));
        allResults.addAll(scrapeIITCourses(keyword));
        allResults.addAll(scrapeIIScCourses(keyword));
        allResults.addAll(scrapeAICTE(keyword));
        allResults.addAll(scrapeIGNOU(keyword));
        
        // Academic & Research Platforms
        allResults.addAll(scrapeGoogleScholar(keyword));
        allResults.addAll(scrapeSemanticScholar(keyword));
        allResults.addAll(scrapeArXiv(keyword));
        allResults.addAll(scrapeResearchGate(keyword));
        
        // Academic Publishers
        allResults.addAll(scrapeSpringer(keyword));
        allResults.addAll(scrapeIEEE(keyword));
        allResults.addAll(scrapeScienceDirect(keyword));
        allResults.addAll(scrapeACM(keyword));
        allResults.addAll(scrapeWiley(keyword));
        allResults.addAll(scrapeTaylorFrancis(keyword));

        logger.info("Scraped {} results from all platforms (before filtering)", allResults.size());

        // VALIDATION AND FILTERING
        List<PlatformResult> validResults = new ArrayList<>();
        
        for (PlatformResult result : allResults) {
            // 1. STRICT KEYWORD FILTERING - Check if keyword exists in title, introduction, or headings
            if (!containsKeywordInTitleOrIntroduction(result.title, result.introduction, keyword)) {
                logger.debug("Discarded result from {} - keyword '{}' not found in title or introduction", 
                            result.platform, keyword);
                continue;
            }
            
            // 2. Check if title is not empty
            if (result.title == null || result.title.trim().isEmpty()) {
                logger.debug("Discarded result from {} - empty title", result.platform);
                continue;
            }
            
            // 3. Check if introduction is not empty and meaningful
            if (result.introduction == null || 
                result.introduction.trim().isEmpty() || 
                result.introduction.equals("No introduction available") || 
                result.introduction.length() < 20) {
                logger.debug("Discarded result from {} - no meaningful introduction", result.platform);
                continue;
            }
            
            // 4. Check if at least 2 key points exist
            int keyPointCount = countKeyPoints(result.keyPoints);
            if (keyPointCount < 2) {
                logger.debug("Discarded result from {} - insufficient key points (found: {})", 
                            result.platform, keyPointCount);
                continue;
            }
            
            // 5. Validate source link
            if (!isValidSourceLink(result.sourceLink)) {
                logger.debug("Discarded result from {} - invalid source link", result.platform);
                continue;
            }
            
            // 5. Check if content is relevant to keyword
            if (!isContentRelevant(result.title, result.introduction, keyword)) {
                logger.debug("Discarded result from {} - not relevant to keyword", result.platform);
                continue;
            }
            
            validResults.add(result);
        }

        logger.info("After validation: {} valid results (filtered out {} irrelevant)", 
                    validResults.size(), allResults.size() - validResults.size());

        // Calculate relevance scores for valid results (content-based only)
        for (PlatformResult result : validResults) {
            int score = calculateRelevanceScore(result, keyword);
            result.relevanceScore = score;
        }

        // Sort by relevance score descending
        validResults.sort((r1, r2) -> Integer.compare(r2.relevanceScore, r1.relevanceScore));

        logger.info("Total results after scoring: {} (no merging - each result from its own source)", validResults.size());

        // Save ALL results to database (no merging, no limit)
        int totalSaved = 0;
        int totalFailed = 0;

        for (PlatformResult result : validResults) {
            try {
                saveResult(searchHistoryId, result, keyword);
                totalSaved++;
                logger.debug("Saved result from {} with score: {}", result.platform, result.relevanceScore);
            } catch (Exception e) {
                logger.warn("Failed to save result from {}: {}", result.platform, e.getMessage());
                totalFailed++;
            }
        }

        logger.info("Multi-platform scraping completed. Saved: {}, Failed: {}", totalSaved, totalFailed);
    }



    // ==================== PLATFORM SCRAPERS WITH SPECIFIC EXTRACTION RULES ====================

    private List<PlatformResult> scrapeGeeksforGeeks(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String url = "https://www.geeksforgeeks.org/" + keyword.toLowerCase().replace(" ", "-") + "/";
            logger.debug("Scraping GeeksforGeeks: {}", url);
            
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();
            
            String title = doc.select("h1.entry-title, h1").first() != null 
                    ? doc.select("h1.entry-title, h1").first().text() : keyword;
            
            // Extract first 2 paragraphs containing the keyword
            String intro = extractFirstParagraphsWithKeyword(doc, "article p, .entry-content p, p", keyword, 2);
            
            // Extract bullet points for Applications, Features, Advantages
            String keyPoints = extractSpecificSections(doc, new String[]{"Applications", "Features", "Advantages", "Characteristics"});
            
            if (!intro.equals("No introduction available") && countKeyPoints(keyPoints) >= 2 && isValidSourceLink(url)) {
                results.add(new PlatformResult("GeeksforGeeks", title, intro, keyPoints, url));
                logger.info("Successfully scraped GeeksforGeeks with validated URL");
            } else if (!isValidSourceLink(url)) {
                logger.debug("Skipped GeeksforGeeks result - invalid URL: {}", url);
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape GeeksforGeeks: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeSimplilearn(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String searchUrl = "https://www.simplilearn.com/tutorials/" + keyword.toLowerCase().replace(" ", "-");
            logger.debug("Scraping Simplilearn: {}", searchUrl);
            
            Document doc = Jsoup.connect(searchUrl)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();
            
            String title = doc.select("h1").first() != null ? doc.select("h1").first().text() : keyword;
            String intro = extractParagraph(doc, "article p, .content p, p", keyword);
            String keyPoints = extractList(doc, "ul li, ol li");
            
            if (!intro.equals("No introduction available") && isValidSourceLink(searchUrl)) {
                results.add(new PlatformResult("Simplilearn", title, intro, keyPoints, searchUrl));
                logger.info("Successfully scraped Simplilearn with validated URL");
            } else if (!isValidSourceLink(searchUrl)) {
                logger.debug("Skipped Simplilearn result - invalid URL: {}", searchUrl);
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape Simplilearn: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeW3Schools(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String url = "https://www.w3schools.com/" + keyword.toLowerCase().replace(" ", "_") + "/default.asp";
            logger.debug("Scraping W3Schools: {}", url);
            
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();
            
            String title = doc.select("h1").first() != null ? doc.select("h1").first().text() : keyword;
            String intro = extractParagraph(doc, ".w3-panel p, p", keyword);
            String keyPoints = extractList(doc, "ul li, ol li");
            
            if (!intro.equals("No introduction available") && isValidSourceLink(url)) {
                results.add(new PlatformResult("W3Schools", title, intro, keyPoints, url));
                logger.info("Successfully scraped W3Schools with validated URL");
            } else if (!isValidSourceLink(url)) {
                logger.debug("Skipped W3Schools result - invalid URL: {}", url);
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape W3Schools: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeUnacademy(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String searchUrl = "https://unacademy.com/goal/upsc-civil-services-examination-ias-preparation/TMOVD/course/" 
                    + keyword.toLowerCase().replace(" ", "-");
            logger.debug("Scraping Unacademy: {}", searchUrl);
            
            Document doc = Jsoup.connect(searchUrl)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();
            
            String title = doc.select("h1, .course-title").first() != null 
                    ? doc.select("h1, .course-title").first().text() : keyword;
            String intro = extractParagraph(doc, ".description p, p", keyword);
            String keyPoints = extractList(doc, "ul li, ol li");
            
            results.add(new PlatformResult("Unacademy", title, intro, keyPoints, searchUrl));
            logger.info("Successfully scraped Unacademy");
        } catch (Exception e) {
            logger.warn("Failed to scrape Unacademy: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeNVIDIA(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String searchUrl = "https://www.nvidia.com/en-us/deep-learning-ai/education/";
            logger.debug("Scraping NVIDIA: {}", searchUrl);
            
            Document doc = Jsoup.connect(searchUrl)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();
            
            String title = "NVIDIA Deep Learning - " + keyword;
            String intro = extractParagraph(doc, "article p, .content p, p", keyword);
            String keyPoints = extractList(doc, "ul li, ol li");
            
            results.add(new PlatformResult("NVIDIA", title, intro, keyPoints, searchUrl));
            logger.info("Successfully scraped NVIDIA");
        } catch (Exception e) {
            logger.warn("Failed to scrape NVIDIA: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeGreatLearning(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String url = "https://www.mygreatlearning.com/blog/" + keyword.toLowerCase().replace(" ", "-") + "/";
            logger.debug("Scraping Great Learning: {}", url);
            
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();
            
            String title = doc.select("h1").first() != null ? doc.select("h1").first().text() : keyword;
            String intro = extractParagraph(doc, "article p, .entry-content p, p", keyword);
            String keyPoints = extractList(doc, "ul li, ol li");
            
            if (!intro.equals("No introduction available") && isValidSourceLink(url)) {
                results.add(new PlatformResult("Great Learning", title, intro, keyPoints, url));
                logger.info("Successfully scraped Great Learning with validated URL");
            } else if (!isValidSourceLink(url)) {
                logger.debug("Skipped Great Learning result - invalid URL: {}", url);
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape Great Learning: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeTutorialsPoint(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String url = "https://www.tutorialspoint.com/" + keyword.toLowerCase().replace(" ", "_") + "/index.htm";
            logger.debug("Scraping TutorialsPoint: {}", url);
            
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();
            
            String title = doc.select("h1").first() != null ? doc.select("h1").first().text() : keyword;
            String intro = extractParagraph(doc, "article p, p", keyword);
            String keyPoints = extractList(doc, "ul li, ol li");
            
            if (!intro.equals("No introduction available") && isValidSourceLink(url)) {
                results.add(new PlatformResult("TutorialsPoint", title, intro, keyPoints, url));
                logger.info("Successfully scraped TutorialsPoint with validated URL");
            } else if (!isValidSourceLink(url)) {
                logger.debug("Skipped TutorialsPoint result - invalid URL: {}", url);
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape TutorialsPoint: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeNPTEL(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String searchUrl = "https://nptel.ac.in/courses/" + keyword.toLowerCase().replace(" ", "-");
            logger.debug("Scraping NPTEL: {}", searchUrl);
            
            Document doc = Jsoup.connect(searchUrl)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();
            
            String title = doc.select("h1, .course-title").first() != null 
                    ? doc.select("h1, .course-title").first().text() : "NPTEL - " + keyword;
            String intro = extractParagraph(doc, ".description p, p", keyword);
            String keyPoints = extractList(doc, "ul li, ol li");
            
            if (!intro.equals("No introduction available") && isValidSourceLink(searchUrl)) {
                results.add(new PlatformResult("NPTEL", title, intro, keyPoints, searchUrl));
                logger.info("Successfully scraped NPTEL with validated URL");
            } else if (!isValidSourceLink(searchUrl)) {
                logger.debug("Skipped NPTEL result - invalid URL: {}", searchUrl);
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape NPTEL: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeSWAYAM(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String searchUrl = "https://swayam.gov.in/explorer?searchText=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            logger.debug("Scraping SWAYAM: {}", searchUrl);
            
            Document doc = Jsoup.connect(searchUrl)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();
            
            String title = doc.select("h1, .course-name").first() != null 
                    ? doc.select("h1, .course-name").first().text() : "SWAYAM - " + keyword;
            String intro = extractParagraph(doc, ".course-description p, p", keyword);
            String keyPoints = extractList(doc, "ul li, ol li");
            
            if (!intro.equals("No introduction available") && isValidSourceLink(searchUrl)) {
                results.add(new PlatformResult("SWAYAM", title, intro, keyPoints, searchUrl));
                logger.info("Successfully scraped SWAYAM with validated URL");
            } else if (!isValidSourceLink(searchUrl)) {
                logger.debug("Skipped SWAYAM result - invalid URL: {}", searchUrl);
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape SWAYAM: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeCoursera(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String searchUrl = "https://www.coursera.org/search?query=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            logger.debug("Scraping Coursera: {}", searchUrl);
            
            Document doc = Jsoup.connect(searchUrl)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();
            
            String title = doc.select("h2, .course-title").first() != null 
                    ? doc.select("h2, .course-title").first().text() : "Coursera - " + keyword;
            String intro = extractParagraph(doc, ".description p, p", keyword);
            String keyPoints = extractList(doc, "ul li, ol li");
            
            if (!intro.equals("No introduction available") && isValidSourceLink(searchUrl)) {
                results.add(new PlatformResult("Coursera", title, intro, keyPoints, searchUrl));
                logger.info("Successfully scraped Coursera with validated URL");
            } else if (!isValidSourceLink(searchUrl)) {
                logger.debug("Skipped Coursera result - invalid URL: {}", searchUrl);
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape Coursera: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeEdX(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String searchUrl = "https://www.edx.org/search?q=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            logger.debug("Scraping edX: {}", searchUrl);
            
            Document doc = Jsoup.connect(searchUrl)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();
            
            String title = doc.select("h2, .course-title").first() != null 
                    ? doc.select("h2, .course-title").first().text() : "edX - " + keyword;
            String intro = extractParagraph(doc, ".description p, p", keyword);
            String keyPoints = extractList(doc, "ul li, ol li");
            
            if (!intro.equals("No introduction available") && isValidSourceLink(searchUrl)) {
                results.add(new PlatformResult("edX", title, intro, keyPoints, searchUrl));
                logger.info("Successfully scraped edX with validated URL");
            } else if (!isValidSourceLink(searchUrl)) {
                logger.debug("Skipped edX result - invalid URL: {}", searchUrl);
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape edX: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeJavatpoint(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String url = "https://www.javatpoint.com/" + keyword.toLowerCase().replace(" ", "-");
            logger.debug("Scraping Javatpoint: {}", url);
            
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();
            
            String title = doc.select("h1").first() != null ? doc.select("h1").first().text() : keyword;
            String intro = extractParagraph(doc, "article p, p", keyword);
            String keyPoints = extractList(doc, "ul li, ol li");
            
            if (!intro.equals("No introduction available") && isValidSourceLink(url)) {
                results.add(new PlatformResult("Javatpoint", title, intro, keyPoints, url));
                logger.info("Successfully scraped Javatpoint with validated URL");
            } else if (!isValidSourceLink(url)) {
                logger.debug("Skipped Javatpoint result - invalid URL: {}", url);
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape Javatpoint: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeWikipedia(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String url = "https://en.wikipedia.org/wiki/" + keyword.replace(" ", "_");
            logger.debug("Scraping Wikipedia: {}", url);
            
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();
            
            String title = doc.select("h1#firstHeading").first() != null 
                    ? doc.select("h1#firstHeading").first().text() : keyword;
            
            // Get first paragraph from Wikipedia
            String intro = "";
            Elements paragraphs = doc.select("#mw-content-text .mw-parser-output > p");
            for (Element p : paragraphs) {
                String text = p.text();
                if (!text.isEmpty() && text.length() > 50) {
                    intro = cleanIntroduction(text, keyword);
                    break;
                }
            }
            
            if (intro.isEmpty()) {
                intro = "No introduction available";
            }
            
            String keyPoints = extractList(doc, "#mw-content-text ul li");
            
            if (!intro.equals("No introduction available") && isValidSourceLink(url)) {
                results.add(new PlatformResult("Wikipedia", title, intro, keyPoints, url));
                logger.info("Successfully scraped Wikipedia with validated URL");
            } else if (!isValidSourceLink(url)) {
                logger.debug("Skipped Wikipedia result - invalid URL: {}", url);
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape Wikipedia: {}", e.getMessage());
        }
        return results;
    }

    // ==================== HELPER METHODS ====================

    private String extractParagraph(Document doc, String selector, String keyword) {
        Elements elements = doc.select(selector);
        for (Element element : elements) {
            String text = element.text();
            if (!text.isEmpty() && text.length() > 50) {
                // Clean and format the introduction
                return cleanIntroduction(text, keyword);
            }
        }
        return "No introduction available";
    }

    /**
     * Extract first N paragraphs that contain the keyword.
     * Used for platforms like GeeksforGeeks that need multiple paragraphs.
     */
    private String extractFirstParagraphsWithKeyword(Document doc, String selector, String keyword, int count) {
        Elements elements = doc.select(selector);
        StringBuilder result = new StringBuilder();
        int found = 0;
        String keywordLower = keyword.toLowerCase();
        
        for (Element element : elements) {
            String text = element.text();
            if (!text.isEmpty() && text.length() > 50 && text.toLowerCase().contains(keywordLower)) {
                result.append(text).append(" ");
                found++;
                if (found >= count) {
                    break;
                }
            }
        }
        
        if (result.length() == 0) {
            return "No introduction available";
        }
        
        return cleanIntroduction(result.toString(), keyword);
    }

    /**
     * Extract content from specific sections (e.g., "Features", "Applications", "Advantages").
     * Looks for headings with these keywords and extracts bullet points below them.
     */
    private String extractSpecificSections(Document doc, String[] sectionNames) {
        List<String> keyPoints = new ArrayList<>();
        
        // Look for headings containing section names
        for (String sectionName : sectionNames) {
            Elements headings = doc.select("h2, h3, h4, h5, strong, b");
            for (Element heading : headings) {
                String headingText = heading.text().toLowerCase();
                if (headingText.contains(sectionName.toLowerCase())) {
                    // Found a relevant section, extract bullet points after it
                    Element parent = heading.parent();
                    if (parent != null) {
                        Elements lists = parent.select("ul li, ol li");
                        for (Element li : lists) {
                            String point = cleanKeyPoint(li.text());
                            if (!point.isEmpty() && point.length() > 10 && point.length() < 150) {
                                keyPoints.add(point);
                                if (keyPoints.size() >= 5) {
                                    break;
                                }
                            }
                        }
                    }
                    
                    // Also check next siblings
                    Element nextSibling = heading.nextElementSibling();
                    while (nextSibling != null && keyPoints.size() < 5) {
                        if (nextSibling.tagName().equals("ul") || nextSibling.tagName().equals("ol")) {
                            Elements items = nextSibling.select("li");
                            for (Element li : items) {
                                String point = cleanKeyPoint(li.text());
                                if (!point.isEmpty() && point.length() > 10 && point.length() < 150) {
                                    keyPoints.add(point);
                                    if (keyPoints.size() >= 5) {
                                        break;
                                    }
                                }
                            }
                        }
                        if (nextSibling.tagName().matches("h[1-6]")) {
                            break; // Stop at next heading
                        }
                        nextSibling = nextSibling.nextElementSibling();
                    }
                    
                    if (keyPoints.size() >= 4) {
                        break; // We have enough points
                    }
                }
            }
            if (keyPoints.size() >= 4) {
                break;
            }
        }
        
        // If no specific sections found, extract general bullet points
        if (keyPoints.isEmpty()) {
            Elements allLists = doc.select("ul li, ol li");
            for (Element li : allLists) {
                String text = li.text();
                if (containsKeyTerms(text)) {
                    String point = cleanKeyPoint(text);
                    if (!point.isEmpty() && point.length() > 10 && point.length() < 150) {
                        keyPoints.add(point);
                        if (keyPoints.size() >= 5) {
                            break;
                        }
                    }
                }
            }
        }
        
        return formatKeyPoints(keyPoints);
    }

    /**
     * Extract definition section from document.
     * Looks for first paragraph or section labeled as "Definition".
     */
    private String extractDefinition(Document doc, String keyword) {
        // First try to find explicit definition section
        Elements headings = doc.select("h2, h3, h4, strong, b");
        for (Element heading : headings) {
            String headingText = heading.text().toLowerCase();
            if (headingText.contains("definition") || headingText.contains("what is")) {
                Element nextSibling = heading.nextElementSibling();
                if (nextSibling != null && nextSibling.tagName().equals("p")) {
                    return cleanIntroduction(nextSibling.text(), keyword);
                }
            }
        }
        
        // Otherwise, extract first paragraph with keyword
        return extractFirstParagraphsWithKeyword(doc, "p", keyword, 1);
    }

    /**
     * Extract abstract from research papers.
     * Looks for abstract section or meta description.
     */
    private String extractAbstract(Document doc, String keyword) {
        // Try to find abstract section
        Elements abstracts = doc.select(".abstract, #abstract, [class*='abstract']");
        if (!abstracts.isEmpty()) {
            String abstractText = abstracts.first().text();
            if (!abstractText.isEmpty()) {
                return cleanIntroduction(abstractText, keyword);
            }
        }
        
        // Try meta description
        Elements metaDesc = doc.select("meta[name=description]");
        if (!metaDesc.isEmpty()) {
            String desc = metaDesc.attr("content");
            if (!desc.isEmpty()) {
                return cleanIntroduction(desc, keyword);
            }
        }
        
        // Fallback to first paragraph
        return extractFirstParagraphsWithKeyword(doc, "p", keyword, 1);
    }

    /**
     * Clean and format introduction text according to rules:
     * - Extract first paragraph
     * - Limit to 4 lines or 400 characters
     * - Clean HTML tags
     * - Remove references like [1], [2]
     * - Remove special characters
     */
    /**
     * Extract topic-specific introduction that contains the keyword.
     * Removes navigation, ads, announcements, and limits to 3-4 lines.
     */
    private String cleanIntroduction(String rawText, String keyword) {
        if (rawText == null || rawText.isEmpty()) {
            return "No introduction available";
        }

        // Step 1: Remove HTML tags
        String cleaned = rawText.replaceAll("<[^>]+>", "");

        // Step 2: Remove navigation, ads, and announcements
        cleaned = removeNavigationAndAds(cleaned);

        // Step 3: Remove references
        cleaned = cleaned.replaceAll("\\[\\d+\\]", "");
        cleaned = cleaned.replaceAll("\\[citation needed\\]", "");
        cleaned = cleaned.replaceAll("\\[edit\\]", "");
        cleaned = cleaned.replaceAll("\\[\\w+\\]", "");

        // Step 4: Remove special characters
        cleaned = cleaned.replaceAll("[^a-zA-Z0-9\\s.,;:!?()\\-'\"]+", " ");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        // Step 5: Extract first paragraph that contains the keyword
        String keywordLower = keyword.toLowerCase();
        String[] paragraphs = cleaned.split("\\. ");
        StringBuilder relevantParagraph = new StringBuilder();
        
        for (String sentence : paragraphs) {
            if (sentence.toLowerCase().contains(keywordLower)) {
                relevantParagraph.append(sentence).append(". ");
                if (relevantParagraph.length() >= 200) {
                    break;
                }
            }
        }

        // If no keyword-containing paragraph found, take first paragraph
        if (relevantParagraph.length() == 0) {
            for (String sentence : paragraphs) {
                if (relevantParagraph.length() + sentence.length() < 300) {
                    relevantParagraph.append(sentence).append(". ");
                } else {
                    break;
                }
            }
        }

        String result = relevantParagraph.toString().trim();

        // Step 6: Limit to 3-4 lines (approximately 80 chars per line = 240-320 chars)
        if (result.length() > 320) {
            result = result.substring(0, 317) + "...";
        }

        // Final check
        if (result.isEmpty() || result.length() < 20) {
            return "No introduction available";
        }

        return result;
    }

    /**
     * Remove navigation text, ads, announcements, and menus.
     */
    private String removeNavigationAndAds(String text) {
        // Common navigation and ad patterns
        String[] patterns = {
            "(?i)sign up.*?free",
            "(?i)subscribe.*?newsletter",
            "(?i)click here.*?more",
            "(?i)advertisement",
            "(?i)sponsored",
            "(?i)related articles",
            "(?i)you may also like",
            "(?i)recommended for you",
            "(?i)trending now",
            "(?i)popular posts",
            "(?i)follow us",
            "(?i)share this",
            "(?i)cookie policy",
            "(?i)privacy policy",
            "(?i)terms of service",
            "(?i)menu",
            "(?i)navigation",
            "(?i)breadcrumb"
        };

        for (String pattern : patterns) {
            text = text.replaceAll(pattern + ".*?\\.", "");
        }

        return text;
    }

    /**
     * STRICT KEYWORD FILTERING
     * Check if keyword exists in title OR introduction.
     * This is a mandatory check - if keyword is not found in either, the result is discarded.
     * 
     * @param title The page title
     * @param introduction The introduction/definition text
     * @param keyword The search keyword
     * @return true if keyword found in title OR introduction, false otherwise
     */
    private boolean containsKeywordInTitleOrIntroduction(String title, String introduction, String keyword) {
        if ((title == null || title.trim().isEmpty()) && 
            (introduction == null || introduction.trim().isEmpty())) {
            return false;
        }

        String keywordLower = keyword.toLowerCase();
        String[] keywords = keywordLower.split("\\s+");

        // Check title for keyword
        if (title != null && !title.trim().isEmpty()) {
            String titleLower = title.toLowerCase();
            for (String kw : keywords) {
                if (kw.length() > 2 && titleLower.contains(kw)) {
                    logger.debug("Keyword '{}' found in title: {}", kw, title);
                    return true;
                }
            }
        }

        // Check introduction for keyword
        if (introduction != null && !introduction.trim().isEmpty() && 
            !introduction.equals("No introduction available")) {
            String introLower = introduction.toLowerCase();
            for (String kw : keywords) {
                if (kw.length() > 2 && introLower.contains(kw)) {
                    logger.debug("Keyword '{}' found in introduction", kw);
                    return true;
                }
            }
        }

        // Keyword not found in either title or introduction
        logger.debug("Keyword '{}' NOT found in title or introduction", keyword);
        return false;
    }

    /**
     * Validate if content is relevant to the search keyword.
     * Returns true if keyword found in title or content.
     */
    private boolean isContentRelevant(String title, String content, String keyword) {
        if (title == null && content == null) {
            return false;
        }

        String keywordLower = keyword.toLowerCase();
        String[] keywords = keywordLower.split("\\s+");

        // Check title
        if (title != null) {
            String titleLower = title.toLowerCase();
            for (String kw : keywords) {
                if (kw.length() > 2 && titleLower.contains(kw)) {
                    return true;
                }
            }
        }

        // Check content
        if (content != null) {
            String contentLower = content.toLowerCase();
            int keywordCount = 0;
            for (String kw : keywords) {
                if (kw.length() > 2 && contentLower.contains(kw)) {
                    keywordCount++;
                }
            }
            // At least half of the keywords should be present
            return keywordCount >= Math.max(1, keywords.length / 2);
        }

        return false;
    }

    /**
     * Validate and normalize source URL.
     * Rules:
     * - URL must not be empty
     * - URL must start with http or https
     * - If URL is relative, convert to absolute URL
     * - Validate URL format
     * 
     * @param link The URL to validate
     * @param baseUrl The base URL for converting relative URLs (optional)
     * @return true if URL is valid, false otherwise
     */
    private boolean isValidSourceLink(String link) {
        if (link == null || link.trim().isEmpty()) {
            logger.debug("URL validation failed: URL is null or empty");
            return false;
        }
        
        String trimmedLink = link.trim();
        
        // Check if URL starts with http or https
        if (!trimmedLink.startsWith("http://") && !trimmedLink.startsWith("https://")) {
            logger.debug("URL validation failed: URL does not start with http/https: {}", trimmedLink);
            return false;
        }
        
        // Additional validation: check for valid URL format
        try {
            java.net.URL url = new java.net.URL(trimmedLink);
            // Check if host is not empty
            if (url.getHost() == null || url.getHost().isEmpty()) {
                logger.debug("URL validation failed: Invalid host in URL: {}", trimmedLink);
                return false;
            }
            logger.debug("URL validation passed: {}", trimmedLink);
            return true;
        } catch (java.net.MalformedURLException e) {
            logger.debug("URL validation failed: Malformed URL: {} - {}", trimmedLink, e.getMessage());
            return false;
        }
    }

    /**
     * Convert relative URL to absolute URL.
     * If URL is already absolute, return as is.
     * If URL is relative, prepend the base URL.
     * 
     * @param url The URL to convert (may be relative or absolute)
     * @param baseUrl The base URL to use for relative URLs
     * @return Absolute URL
     */
    private String toAbsoluteUrl(String url, String baseUrl) {
        if (url == null || url.trim().isEmpty()) {
            return baseUrl;
        }
        
        String trimmedUrl = url.trim();
        
        // If already absolute, return as is
        if (trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://")) {
            return trimmedUrl;
        }
        
        // Handle relative URLs
        if (baseUrl == null || baseUrl.isEmpty()) {
            return trimmedUrl;
        }
        
        try {
            java.net.URL base = new java.net.URL(baseUrl);
            java.net.URL absolute = new java.net.URL(base, trimmedUrl);
            return absolute.toString();
        } catch (java.net.MalformedURLException e) {
            logger.warn("Failed to convert relative URL '{}' with base '{}': {}", 
                       trimmedUrl, baseUrl, e.getMessage());
            return trimmedUrl;
        }
    }

    /**
     * Validate URL by attempting to check if it's accessible.
     * This is an optional check that can be used for critical validation.
     * Note: This method makes an HTTP request, so use sparingly.
     * 
     * @param url The URL to validate
     * @return true if URL is accessible, false otherwise
     */
    private boolean isUrlAccessible(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        
        try {
            java.net.URL urlObj = new java.net.URL(url);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) urlObj.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000); // 5 seconds timeout
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            
            // Accept 2xx and 3xx status codes
            boolean accessible = (responseCode >= 200 && responseCode < 400);
            if (!accessible) {
                logger.debug("URL not accessible: {} - Response code: {}", url, responseCode);
            }
            return accessible;
        } catch (Exception e) {
            logger.debug("URL accessibility check failed for {}: {}", url, e.getMessage());
            return false;
        }
    }

    /**
     * Count the number of key points in the key points string.
     * Key points are separated by bullet points (•) or newlines.
     * Returns 0 if key points are null, empty, or contain "No key points available".
     */
    private int countKeyPoints(String keyPoints) {
        if (keyPoints == null || keyPoints.trim().isEmpty()) {
            return 0;
        }
        
        // Check for placeholder text
        if (keyPoints.equals("No key points available") || 
            keyPoints.equals("No introduction available")) {
            return 0;
        }
        
        // Count bullet points (•)
        int bulletCount = 0;
        for (char c : keyPoints.toCharArray()) {
            if (c == '•') {
                bulletCount++;
            }
        }
        
        // If bullet points found, return that count
        if (bulletCount > 0) {
            return bulletCount;
        }
        
        // Otherwise, count non-empty lines
        String[] lines = keyPoints.split("\n");
        int lineCount = 0;
        for (String line : lines) {
            if (line.trim().length() > 10) { // At least 10 characters to be meaningful
                lineCount++;
            }
        }
        
        return lineCount;
    }

    /**
     * Count keyword occurrences in text.
     */
    private int countKeywordOccurrences(String text, String keyword) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        String textLower = text.toLowerCase();
        String keywordLower = keyword.toLowerCase();
        String[] keywords = keywordLower.split("\\s+");
        
        int count = 0;
        for (String kw : keywords) {
            if (kw.length() > 2) {
                int index = 0;
                while ((index = textLower.indexOf(kw, index)) != -1) {
                    count++;
                    index += kw.length();
                }
            }
        }
        return count;
    }

    /**
     * Extract only bullet points (<li>) or lines containing key sections.
     * Limit to 5 key points.
     */
    private String extractList(Document doc, String selector) {
        List<String> keyPoints = new ArrayList<>();
        
        // Extract <li> elements (bullet points)
        Elements listItems = doc.select("li");
        for (Element item : listItems) {
            String text = item.text().trim();
            if (isRelevantKeyPoint(text, item)) {
                String cleanedPoint = cleanKeyPoint(text);
                if (!cleanedPoint.isEmpty() && cleanedPoint.length() > 10 && cleanedPoint.length() < 150) {
                    keyPoints.add(cleanedPoint);
                    if (keyPoints.size() >= 5) {
                        break;
                    }
                }
            }
        }
        
        // If not enough points from <li>, extract from paragraphs containing key terms
        if (keyPoints.size() < 5) {
            Elements paragraphs = doc.select("p");
            for (Element p : paragraphs) {
                String text = p.text().trim();
                if (containsKeyTerms(text)) {
                    String cleanedPoint = cleanKeyPoint(text);
                    if (!cleanedPoint.isEmpty() && cleanedPoint.length() > 10 && cleanedPoint.length() < 150) {
                        keyPoints.add(cleanedPoint);
                        if (keyPoints.size() >= 5) {
                            break;
                        }
                    }
                }
            }
        }
        
        // Format and return
        return formatKeyPoints(keyPoints);
    }

    /**
     * Check if text contains key terms like features, characteristics, objectives, etc.
     */
    private boolean containsKeyTerms(String text) {
        String textLower = text.toLowerCase();
        String[] keyTerms = {
            "feature", "characteristic", "objective", "application", 
            "advantage", "benefit", "purpose", "use", "property"
        };
        
        for (String term : keyTerms) {
            if (textLower.contains(term)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the text or its parent section contains relevant keywords
     */
    private boolean isRelevantKeyPoint(String text, Element item) {
        String textLower = text.toLowerCase();
        
        // Direct match in the text
        if (containsKeySection(textLower)) {
            return true;
        }
        
        // Check parent headings
        Element parent = item.parent();
        while (parent != null) {
            // Check previous siblings for headings
            Element prevSibling = parent.previousElementSibling();
            if (prevSibling != null && prevSibling.tagName().matches("h[1-6]")) {
                String headingText = prevSibling.text().toLowerCase();
                if (containsKeySection(headingText)) {
                    return true;
                }
            }
            
            // Check if parent has a heading
            Elements headings = parent.select("h1, h2, h3, h4, h5, h6");
            for (Element heading : headings) {
                String headingText = heading.text().toLowerCase();
                if (containsKeySection(headingText)) {
                    return true;
                }
            }
            
            parent = parent.parent();
            if (parent != null && parent.tagName().equals("body")) {
                break;
            }
        }
        
        // If text is substantial and looks like a feature/objective
        return text.length() > 20 && text.length() < 200;
    }

    /**
     * Check if text contains key section keywords
     */
    private boolean containsKeySection(String text) {
        return text.contains("objective") || 
               text.contains("feature") || 
               text.contains("characteristic") || 
               text.contains("application") || 
               text.contains("advantage") ||
               text.contains("benefit") ||
               text.contains("key point") ||
               text.contains("highlights") ||
               text.contains("why") ||
               text.contains("uses") ||
               text.contains("purpose");
    }

    /**
     * Clean individual key point:
     * - Remove references [1], [2]
     * - Remove special characters
     * - Remove extra whitespace
     * - Limit length
     */
    private String cleanKeyPoint(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // Remove references [1], [2], etc.
        String cleaned = text.replaceAll("\\[\\d+\\]", "");
        cleaned = cleaned.replaceAll("\\[citation needed\\]", "");
        cleaned = cleaned.replaceAll("\\[edit\\]", "");
        
        // Remove special characters (keep letters, numbers, basic punctuation)
        cleaned = cleaned.replaceAll("[^a-zA-Z0-9\\s.,;:!?()\\-'\"]+", " ");
        
        // Remove extra whitespace
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        
        // Limit to 150 characters per point
        if (cleaned.length() > 150) {
            cleaned = cleaned.substring(0, 147) + "...";
        }
        
        return cleaned;
    }

    /**
     * Format key points as 4-6 bullet points
     */
    /**
     * Format key points as bullet list. Limit to 5 points.
     */
    private String formatKeyPoints(List<String> points) {
        if (points.isEmpty()) {
            return "No key points available";
        }
        
        // Limit to 5 key points
        int targetCount = Math.min(5, points.size());
        List<String> selectedPoints = new ArrayList<>();
        
        // Prioritize diverse points (avoid duplicates)
        Set<String> usedWords = new HashSet<>();
        
        for (String point : points) {
            if (selectedPoints.size() >= targetCount) {
                break;
            }
            
            // Check if this point is unique enough
            String[] words = point.toLowerCase().split("\\s+");
            boolean isDuplicate = false;
            
            for (String word : words) {
                if (word.length() > 5 && usedWords.contains(word)) {
                    isDuplicate = true;
                    break;
                }
            }
            
            if (!isDuplicate) {
                selectedPoints.add(point);
                // Add significant words to used set
                for (String word : words) {
                    if (word.length() > 5) {
                        usedWords.add(word);
                    }
                }
            }
        }
        
        // Format as bullet points
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < selectedPoints.size(); i++) {
            result.append("• ").append(selectedPoints.get(i));
            if (i < selectedPoints.size() - 1) {
                result.append("\n");
            }
        }
        
        return result.toString();
    }

    // ==================== NEW PLATFORM SCRAPERS ====================

    private List<PlatformResult> scrapeBritannica(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String searchUrl = "https://www.britannica.com/search?query=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(searchUrl).userAgent(USER_AGENT).timeout(TIMEOUT_MS).get();
            
            Elements articles = doc.select(".search-results article, .card");
            if (!articles.isEmpty()) {
                Element firstArticle = articles.first();
                String title = firstArticle.select("h2, h3, .title").text();
                String intro = cleanIntroduction(firstArticle.select("p, .description").text(), keyword);
                String link = firstArticle.select("a").attr("abs:href");
                
                // Use searchUrl as fallback if link is empty
                String finalUrl = link.isEmpty() ? searchUrl : link;
                
                // Validate URL before adding result
                if (!title.isEmpty() && !intro.equals("No introduction available") && isValidSourceLink(finalUrl)) {
                    results.add(new PlatformResult("Britannica", title, intro, "No key points available", finalUrl));
                    logger.debug("Added Britannica result with validated URL: {}", finalUrl);
                } else if (!isValidSourceLink(finalUrl)) {
                    logger.debug("Skipped Britannica result - invalid URL: {}", finalUrl);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape Britannica: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeStanfordEncyclopedia(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String searchUrl = "https://plato.stanford.edu/search/searcher.py?query=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(searchUrl).userAgent(USER_AGENT).timeout(TIMEOUT_MS).get();
            
            Elements entries = doc.select(".result_item, .entry");
            if (!entries.isEmpty()) {
                Element firstEntry = entries.first();
                String title = firstEntry.select("h3, .title").text();
                String intro = cleanIntroduction(firstEntry.select("p").text(), keyword);
                String link = firstEntry.select("a").attr("abs:href");
                
                String finalUrl = link.isEmpty() ? searchUrl : link;
                
                if (!title.isEmpty() && !intro.equals("No introduction available") && isValidSourceLink(finalUrl)) {
                    results.add(new PlatformResult("Stanford Encyclopedia", title, intro, "No key points available", finalUrl));
                    logger.debug("Added Stanford Encyclopedia result with validated URL: {}", finalUrl);
                } else if (!isValidSourceLink(finalUrl)) {
                    logger.debug("Skipped Stanford Encyclopedia result - invalid URL: {}", finalUrl);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape Stanford Encyclopedia: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeProgramiz(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String url = "https://www.programiz.com/" + keyword.toLowerCase().replace(" ", "-");
            Document doc = Jsoup.connect(url).userAgent(USER_AGENT).timeout(TIMEOUT_MS).get();
            
            String title = doc.select("h1").first() != null ? doc.select("h1").first().text() : keyword;
            String intro = extractParagraph(doc, "article p, .content p, p", keyword);
            String keyPoints = extractList(doc, "ul li, ol li");
            
            if (!intro.equals("No introduction available") && isValidSourceLink(url)) {
                results.add(new PlatformResult("Programiz", title, intro, keyPoints, url));
                logger.debug("Added Programiz result with validated URL: {}", url);
            } else if (!isValidSourceLink(url)) {
                logger.debug("Skipped Programiz result - invalid URL: {}", url);
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape Programiz: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeMDNDocs(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String searchUrl = "https://developer.mozilla.org/en-US/search?q=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(searchUrl).userAgent(USER_AGENT).timeout(TIMEOUT_MS).get();
            
            Elements articles = doc.select(".search-result, article");
            if (!articles.isEmpty()) {
                Element firstArticle = articles.first();
                String title = firstArticle.select("h3, h2, .title").text();
                String intro = cleanIntroduction(firstArticle.select("p, .summary").text(), keyword);
                String link = firstArticle.select("a").attr("abs:href");
                
                String finalUrl = link.isEmpty() ? searchUrl : link;
                
                if (!title.isEmpty() && !intro.equals("No introduction available") && isValidSourceLink(finalUrl)) {
                    results.add(new PlatformResult("MDN Docs", title, intro, "No key points available", finalUrl));
                    logger.debug("Added MDN Docs result with validated URL: {}", finalUrl);
                } else if (!isValidSourceLink(finalUrl)) {
                    logger.debug("Skipped MDN Docs result - invalid URL: {}", finalUrl);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape MDN Docs: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeScaler(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String searchUrl = "https://www.scaler.com/topics/" + keyword.toLowerCase().replace(" ", "-") + "/";
            Document doc = Jsoup.connect(searchUrl).userAgent(USER_AGENT).timeout(TIMEOUT_MS).get();
            
            String title = doc.select("h1").first() != null ? doc.select("h1").first().text() : keyword;
            String intro = extractParagraph(doc, "article p, .content p, p", keyword);
            String keyPoints = extractList(doc, "ul li, ol li");
            
            if (!intro.equals("No introduction available") && isValidSourceLink(searchUrl)) {
                results.add(new PlatformResult("Scaler", title, intro, keyPoints, searchUrl));
                logger.debug("Added Scaler result with validated URL: {}", searchUrl);
            } else if (!isValidSourceLink(searchUrl)) {
                logger.debug("Skipped Scaler result - invalid URL: {}", searchUrl);
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape Scaler: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeFutureLearn(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String searchUrl = "https://www.futurelearn.com/search?q=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(searchUrl).userAgent(USER_AGENT).timeout(TIMEOUT_MS).get();
            
            Elements courses = doc.select(".course-card, article");
            if (!courses.isEmpty()) {
                Element firstCourse = courses.first();
                String title = firstCourse.select("h3, h2, .title").text();
                String intro = cleanIntroduction(firstCourse.select("p, .description").text(), keyword);
                String link = firstCourse.select("a").attr("abs:href");
                
                String finalUrl = link.isEmpty() ? searchUrl : link;
                
                if (!title.isEmpty() && !intro.equals("No introduction available") && isValidSourceLink(finalUrl)) {
                    results.add(new PlatformResult("FutureLearn", title, intro, "No key points available", finalUrl));
                    logger.debug("Added FutureLearn result with validated URL: {}", finalUrl);
                } else if (!isValidSourceLink(finalUrl)) {
                    logger.debug("Skipped FutureLearn result - invalid URL: {}", finalUrl);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape FutureLearn: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeIITCourses(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String searchUrl = "https://www.iitbombayx.in/courses?search=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(searchUrl).userAgent(USER_AGENT).timeout(TIMEOUT_MS).get();
            
            Elements courses = doc.select(".course, article");
            if (!courses.isEmpty()) {
                Element firstCourse = courses.first();
                String title = firstCourse.select("h3, h2, .title").text();
                String intro = cleanIntroduction(firstCourse.select("p, .description").text(), keyword);
                String link = firstCourse.select("a").attr("abs:href");
                
                String finalUrl = link.isEmpty() ? searchUrl : link;
                
                if (!title.isEmpty() && !intro.equals("No introduction available") && isValidSourceLink(finalUrl)) {
                    results.add(new PlatformResult("IIT Courses", title, intro, "No key points available", finalUrl));
                    logger.debug("Added IIT Courses result with validated URL: {}", finalUrl);
                } else if (!isValidSourceLink(finalUrl)) {
                    logger.debug("Skipped IIT Courses result - invalid URL: {}", finalUrl);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape IIT Courses: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeIIScCourses(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String searchUrl = "https://www.iisc.ac.in/search/?q=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(searchUrl).userAgent(USER_AGENT).timeout(TIMEOUT_MS).get();
            
            Elements items = doc.select(".search-result, article");
            if (!items.isEmpty()) {
                Element firstItem = items.first();
                String title = firstItem.select("h3, h2, .title").text();
                String intro = cleanIntroduction(firstItem.select("p").text(), keyword);
                String link = firstItem.select("a").attr("abs:href");
                
                String finalUrl = link.isEmpty() ? searchUrl : link;
                
                if (!title.isEmpty() && !intro.equals("No introduction available") && isValidSourceLink(finalUrl)) {
                    results.add(new PlatformResult("IISc Courses", title, intro, "No key points available", finalUrl));
                    logger.debug("Added IISc Courses result with validated URL: {}", finalUrl);
                } else if (!isValidSourceLink(finalUrl)) {
                    logger.debug("Skipped IISc Courses result - invalid URL: {}", finalUrl);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape IISc Courses: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeAICTE(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String searchUrl = "https://www.aicte-india.org/?s=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(searchUrl).userAgent(USER_AGENT).timeout(TIMEOUT_MS).get();
            
            Elements items = doc.select(".search-result, article");
            if (!items.isEmpty()) {
                Element firstItem = items.first();
                String title = firstItem.select("h3, h2, .title").text();
                String intro = cleanIntroduction(firstItem.select("p").text(), keyword);
                String link = firstItem.select("a").attr("abs:href");
                
                String finalUrl = link.isEmpty() ? searchUrl : link;
                
                if (!title.isEmpty() && !intro.equals("No introduction available") && isValidSourceLink(finalUrl)) {
                    results.add(new PlatformResult("AICTE", title, intro, "No key points available", finalUrl));
                    logger.debug("Added AICTE result with validated URL: {}", finalUrl);
                } else if (!isValidSourceLink(finalUrl)) {
                    logger.debug("Skipped AICTE result - invalid URL: {}", finalUrl);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape AICTE: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeIGNOU(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String searchUrl = "https://www.ignou.ac.in/ignou/studentzone/search?q=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(searchUrl).userAgent(USER_AGENT).timeout(TIMEOUT_MS).get();
            
            Elements items = doc.select(".search-result, article");
            if (!items.isEmpty()) {
                Element firstItem = items.first();
                String title = firstItem.select("h3, h2, .title").text();
                String intro = cleanIntroduction(firstItem.select("p").text(), keyword);
                String link = firstItem.select("a").attr("abs:href");
                
                String finalUrl = link.isEmpty() ? searchUrl : link;
                
                if (!title.isEmpty() && !intro.equals("No introduction available") && isValidSourceLink(finalUrl)) {
                    results.add(new PlatformResult("IGNOU", title, intro, "No key points available", finalUrl));
                    logger.debug("Added IGNOU result with validated URL: {}", finalUrl);
                } else if (!isValidSourceLink(finalUrl)) {
                    logger.debug("Skipped IGNOU result - invalid URL: {}", finalUrl);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape IGNOU: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeGoogleScholar(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String searchUrl = "https://scholar.google.com/scholar?q=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(searchUrl).userAgent(USER_AGENT).timeout(TIMEOUT_MS).get();
            
            Elements papers = doc.select(".gs_ri");
            if (!papers.isEmpty()) {
                Element firstPaper = papers.first();
                String title = firstPaper.select("h3, .gs_rt").text();
                String intro = cleanIntroduction(firstPaper.select(".gs_rs").text(), keyword);
                String link = firstPaper.select("h3 a, .gs_rt a").attr("abs:href");
                
                String finalUrl = link.isEmpty() ? searchUrl : link;
                
                if (!title.isEmpty() && !intro.equals("No introduction available") && isValidSourceLink(finalUrl)) {
                    results.add(new PlatformResult("Google Scholar", title, intro, "No key points available", finalUrl));
                    logger.debug("Added Google Scholar result with validated URL: {}", finalUrl);
                } else if (!isValidSourceLink(finalUrl)) {
                    logger.debug("Skipped Google Scholar result - invalid URL: {}", finalUrl);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape Google Scholar: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeSemanticScholar(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String searchUrl = "https://www.semanticscholar.org/search?q=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(searchUrl).userAgent(USER_AGENT).timeout(TIMEOUT_MS).get();
            
            Elements papers = doc.select(".search-result, .cl-paper-row");
            if (!papers.isEmpty()) {
                Element firstPaper = papers.first();
                String title = firstPaper.select("h3, .cl-paper-title").text();
                String intro = cleanIntroduction(firstPaper.select("p, .cl-paper-abstract").text(), keyword);
                String link = firstPaper.select("a").attr("abs:href");
                
                String finalUrl = link.isEmpty() ? searchUrl : link;
                
                if (!title.isEmpty() && !intro.equals("No introduction available") && isValidSourceLink(finalUrl)) {
                    results.add(new PlatformResult("Semantic Scholar", title, intro, "No key points available", finalUrl));
                    logger.debug("Added Semantic Scholar result with validated URL: {}", finalUrl);
                } else if (!isValidSourceLink(finalUrl)) {
                    logger.debug("Skipped Semantic Scholar result - invalid URL: {}", finalUrl);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape Semantic Scholar: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeArXiv(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String searchUrl = "https://arxiv.org/search/?query=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(searchUrl).userAgent(USER_AGENT).timeout(TIMEOUT_MS).get();
            
            Elements papers = doc.select(".arxiv-result");
            if (!papers.isEmpty()) {
                Element firstPaper = papers.first();
                String title = firstPaper.select(".title").text();
                String intro = cleanIntroduction(firstPaper.select(".abstract-short, p").text(), keyword);
                String link = firstPaper.select("a").attr("abs:href");
                
                String finalUrl = link.isEmpty() ? searchUrl : link;
                
                if (!title.isEmpty() && !intro.equals("No introduction available") && isValidSourceLink(finalUrl)) {
                    results.add(new PlatformResult("arXiv", title, intro, "No key points available", finalUrl));
                    logger.debug("Added arXiv result with validated URL: {}", finalUrl);
                } else if (!isValidSourceLink(finalUrl)) {
                    logger.debug("Skipped arXiv result - invalid URL: {}", finalUrl);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape arXiv: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeResearchGate(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String searchUrl = "https://www.researchgate.net/search?q=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(searchUrl).userAgent(USER_AGENT).timeout(TIMEOUT_MS).get();
            
            Elements papers = doc.select(".nova-legacy-e-text, .search-box__result-item");
            if (!papers.isEmpty()) {
                Element firstPaper = papers.first();
                String title = firstPaper.select("h3, .nova-legacy-e-text--size-l").text();
                String intro = cleanIntroduction(firstPaper.select("p, .nova-legacy-e-text--size-m").text(), keyword);
                String link = firstPaper.select("a").attr("abs:href");
                
                String finalUrl = link.isEmpty() ? searchUrl : link;
                
                if (!title.isEmpty() && !intro.equals("No introduction available") && isValidSourceLink(finalUrl)) {
                    results.add(new PlatformResult("ResearchGate", title, intro, "No key points available", finalUrl));
                    logger.debug("Added ResearchGate result with validated URL: {}", finalUrl);
                } else if (!isValidSourceLink(finalUrl)) {
                    logger.debug("Skipped ResearchGate result - invalid URL: {}", finalUrl);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape ResearchGate: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeSpringer(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String searchUrl = "https://link.springer.com/search?query=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(searchUrl).userAgent(USER_AGENT).timeout(TIMEOUT_MS).get();
            
            Elements papers = doc.select(".result-item, article");
            if (!papers.isEmpty()) {
                Element firstPaper = papers.first();
                String title = firstPaper.select("h3, .title").text();
                String intro = cleanIntroduction(firstPaper.select("p, .snippet").text(), keyword);
                String link = firstPaper.select("a").attr("abs:href");
                
                String finalUrl = link.isEmpty() ? searchUrl : link;
                
                if (!title.isEmpty() && !intro.equals("No introduction available") && isValidSourceLink(finalUrl)) {
                    results.add(new PlatformResult("Springer", title, intro, "No key points available", finalUrl));
                    logger.debug("Added Springer result with validated URL: {}", finalUrl);
                } else if (!isValidSourceLink(finalUrl)) {
                    logger.debug("Skipped Springer result - invalid URL: {}", finalUrl);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape Springer: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeIEEE(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String searchUrl = "https://ieeexplore.ieee.org/search/searchresult.jsp?queryText=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(searchUrl).userAgent(USER_AGENT).timeout(TIMEOUT_MS).get();
            
            Elements papers = doc.select(".List-results-items, .result-item");
            if (!papers.isEmpty()) {
                Element firstPaper = papers.first();
                String title = firstPaper.select("h3, .result-item-title").text();
                String intro = cleanIntroduction(firstPaper.select("p, .description").text(), keyword);
                String link = firstPaper.select("a").attr("abs:href");
                
                String finalUrl = link.isEmpty() ? searchUrl : link;
                
                if (!title.isEmpty() && !intro.equals("No introduction available") && isValidSourceLink(finalUrl)) {
                    results.add(new PlatformResult("IEEE", title, intro, "No key points available", finalUrl));
                    logger.debug("Added IEEE result with validated URL: {}", finalUrl);
                } else if (!isValidSourceLink(finalUrl)) {
                    logger.debug("Skipped IEEE result - invalid URL: {}", finalUrl);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape IEEE: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeScienceDirect(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String searchUrl = "https://www.sciencedirect.com/search?qs=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(searchUrl).userAgent(USER_AGENT).timeout(TIMEOUT_MS).get();
            
            Elements papers = doc.select(".result-item, article");
            if (!papers.isEmpty()) {
                Element firstPaper = papers.first();
                String title = firstPaper.select("h3, .result-list-title").text();
                String intro = cleanIntroduction(firstPaper.select("p, .abstract").text(), keyword);
                String link = firstPaper.select("a").attr("abs:href");
                
                String finalUrl = link.isEmpty() ? searchUrl : link;
                
                if (!title.isEmpty() && !intro.equals("No introduction available") && isValidSourceLink(finalUrl)) {
                    results.add(new PlatformResult("ScienceDirect", title, intro, "No key points available", finalUrl));
                    logger.debug("Added ScienceDirect result with validated URL: {}", finalUrl);
                } else if (!isValidSourceLink(finalUrl)) {
                    logger.debug("Skipped ScienceDirect result - invalid URL: {}", finalUrl);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape ScienceDirect: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeACM(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String searchUrl = "https://dl.acm.org/action/doSearch?AllField=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(searchUrl).userAgent(USER_AGENT).timeout(TIMEOUT_MS).get();
            
            Elements papers = doc.select(".issue-item, .search__item");
            if (!papers.isEmpty()) {
                Element firstPaper = papers.first();
                String title = firstPaper.select("h5, .issue-item__title").text();
                String intro = cleanIntroduction(firstPaper.select("p, .issue-item__abstract").text(), keyword);
                String link = firstPaper.select("a").attr("abs:href");
                
                String finalUrl = link.isEmpty() ? searchUrl : link;
                
                if (!title.isEmpty() && !intro.equals("No introduction available") && isValidSourceLink(finalUrl)) {
                    results.add(new PlatformResult("ACM", title, intro, "No key points available", finalUrl));
                    logger.debug("Added ACM result with validated URL: {}", finalUrl);
                } else if (!isValidSourceLink(finalUrl)) {
                    logger.debug("Skipped ACM result - invalid URL: {}", finalUrl);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape ACM: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeWiley(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String searchUrl = "https://onlinelibrary.wiley.com/action/doSearch?AllField=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(searchUrl).userAgent(USER_AGENT).timeout(TIMEOUT_MS).get();
            
            Elements papers = doc.select(".item__body, .search-result");
            if (!papers.isEmpty()) {
                Element firstPaper = papers.first();
                String title = firstPaper.select("h3, .item__title").text();
                String intro = cleanIntroduction(firstPaper.select("p, .item__abstract").text(), keyword);
                String link = firstPaper.select("a").attr("abs:href");
                
                String finalUrl = link.isEmpty() ? searchUrl : link;
                
                if (!title.isEmpty() && !intro.equals("No introduction available") && isValidSourceLink(finalUrl)) {
                    results.add(new PlatformResult("Wiley", title, intro, "No key points available", finalUrl));
                    logger.debug("Added Wiley result with validated URL: {}", finalUrl);
                } else if (!isValidSourceLink(finalUrl)) {
                    logger.debug("Skipped Wiley result - invalid URL: {}", finalUrl);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape Wiley: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeTaylorFrancis(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String searchUrl = "https://www.tandfonline.com/action/doSearch?AllField=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(searchUrl).userAgent(USER_AGENT).timeout(TIMEOUT_MS).get();
            
            Elements papers = doc.select(".art_title, .search-result");
            if (!papers.isEmpty()) {
                Element firstPaper = papers.first();
                String title = firstPaper.select("h3, .art_title a").text();
                String intro = cleanIntroduction(firstPaper.select("p, .abstract").text(), keyword);
                String link = firstPaper.select("a").attr("abs:href");
                
                String finalUrl = link.isEmpty() ? searchUrl : link;
                
                if (!title.isEmpty() && !intro.equals("No introduction available") && isValidSourceLink(finalUrl)) {
                    results.add(new PlatformResult("Taylor & Francis", title, intro, "No key points available", finalUrl));
                    logger.debug("Added Taylor & Francis result with validated URL: {}", finalUrl);
                } else if (!isValidSourceLink(finalUrl)) {
                    logger.debug("Skipped Taylor & Francis result - invalid URL: {}", finalUrl);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape Taylor & Francis: {}", e.getMessage());
        }
        return results;
    }

    private void saveResult(Long searchHistoryId, PlatformResult result, String keyword) {
        // Create or get ScrapedSource
        ScrapedSource source = scrapedSourceRepository.findBySourceUrl(result.sourceLink)
                .orElseGet(() -> {
                    ScrapedSource newSource = new ScrapedSource(result.platform, result.sourceLink);
                    newSource.setTitle(result.title);
                    newSource.setPaperAbstract(result.introduction);
                    return scrapedSourceRepository.save(newSource);
                });

        // Create content text combining all information
        String contentText = String.format(
                "Platform: %s\n\nTitle: %s\n\nIntroduction:\n%s\n\nKey Points:\n%s\n\nSource: %s",
                result.platform,
                result.title,
                result.introduction,
                result.keyPoints,
                result.sourceLink
        );

        // Calculate relevance score
        int relevanceScore = calculateRelevanceScore(result, keyword);

        // Save ScrapedContent
        ScrapedContent content = new ScrapedContent(searchHistoryId, source.getId(), contentText);
        content.setRelevanceScore((double) relevanceScore / 100.0);
        content.setRelevanceScoreInt(relevanceScore);
        scrapedContentRepository.save(content);

        logger.debug("Saved result from {} with score: {}", result.platform, relevanceScore);
    }

    // ==================== ADDITIONAL PLATFORM SCRAPERS ====================

    private List<PlatformResult> scrapeKhanAcademy(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String searchUrl = "https://www.khanacademy.org/search?page_search_query=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(searchUrl).timeout(TIMEOUT_MS).get();
            
            Elements articles = doc.select("article, .search-result");
            if (!articles.isEmpty()) {
                Element firstArticle = articles.first();
                String title = firstArticle.select("h2, h3, .title").text();
                String intro = cleanIntroduction(firstArticle.select("p, .description").text(), keyword);
                String link = firstArticle.select("a").attr("abs:href");
                
                String finalUrl = link.isEmpty() ? searchUrl : link;
                
                if (!title.isEmpty() && isValidSourceLink(finalUrl)) {
                    PlatformResult result = new PlatformResult("Khan Academy", title, intro, 
                        "No key points available", finalUrl);
                    results.add(result);
                    logger.debug("Added Khan Academy result with validated URL: {}", finalUrl);
                } else if (!isValidSourceLink(finalUrl)) {
                    logger.debug("Skipped Khan Academy result - invalid URL: {}", finalUrl);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape Khan Academy: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeMITOpenCourseWare(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String searchUrl = "https://ocw.mit.edu/search/?q=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(searchUrl).timeout(TIMEOUT_MS).get();
            
            Elements courses = doc.select(".course-item, article");
            if (!courses.isEmpty()) {
                Element firstCourse = courses.first();
                String title = firstCourse.select("h3, h2, .title").text();
                String intro = cleanIntroduction(firstCourse.select("p, .description").text(), keyword);
                String link = firstCourse.select("a").attr("abs:href");
                
                String finalUrl = link.isEmpty() ? searchUrl : link;
                
                if (!title.isEmpty() && isValidSourceLink(finalUrl)) {
                    PlatformResult result = new PlatformResult("MIT OpenCourseWare", title, intro,
                        "No key points available", finalUrl);
                    results.add(result);
                    logger.debug("Added MIT OpenCourseWare result with validated URL: {}", finalUrl);
                } else if (!isValidSourceLink(finalUrl)) {
                    logger.debug("Skipped MIT OpenCourseWare result - invalid URL: {}", finalUrl);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape MIT OpenCourseWare: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeUdemy(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String searchUrl = "https://www.udemy.com/courses/search/?q=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(searchUrl).timeout(TIMEOUT_MS).get();
            
            Elements courses = doc.select(".course-card, article");
            if (!courses.isEmpty()) {
                Element firstCourse = courses.first();
                String title = firstCourse.select("h3, .course-title").text();
                String intro = cleanIntroduction(firstCourse.select("p, .course-headline").text(), keyword);
                String link = firstCourse.select("a").attr("abs:href");
                
                String finalUrl = link.isEmpty() ? searchUrl : link;
                
                if (!title.isEmpty() && isValidSourceLink(finalUrl)) {
                    PlatformResult result = new PlatformResult("Udemy", title, intro,
                        "No key points available", finalUrl);
                    results.add(result);
                    logger.debug("Added Udemy result with validated URL: {}", finalUrl);
                } else if (!isValidSourceLink(finalUrl)) {
                    logger.debug("Skipped Udemy result - invalid URL: {}", finalUrl);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape Udemy: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeUdacity(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String searchUrl = "https://www.udacity.com/courses/all?search=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(searchUrl).timeout(TIMEOUT_MS).get();
            
            Elements courses = doc.select(".course-card, article");
            if (!courses.isEmpty()) {
                Element firstCourse = courses.first();
                String title = firstCourse.select("h3, h2, .title").text();
                String intro = cleanIntroduction(firstCourse.select("p, .description").text(), keyword);
                String link = firstCourse.select("a").attr("abs:href");
                
                String finalUrl = link.isEmpty() ? searchUrl : link;
                
                if (!title.isEmpty() && isValidSourceLink(finalUrl)) {
                    PlatformResult result = new PlatformResult("Udacity", title, intro,
                        "No key points available", finalUrl);
                    results.add(result);
                    logger.debug("Added Udacity result with validated URL: {}", finalUrl);
                } else if (!isValidSourceLink(finalUrl)) {
                    logger.debug("Skipped Udacity result - invalid URL: {}", finalUrl);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape Udacity: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeFreeCodeCamp(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String searchUrl = "https://www.freecodecamp.org/news/search/?query=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(searchUrl).timeout(TIMEOUT_MS).get();
            
            Elements articles = doc.select("article, .post-card");
            if (!articles.isEmpty()) {
                Element firstArticle = articles.first();
                String title = firstArticle.select("h2, h3, .post-card-title").text();
                String intro = cleanIntroduction(firstArticle.select("p, .post-card-excerpt").text(), keyword);
                String link = firstArticle.select("a").attr("abs:href");
                
                String finalUrl = link.isEmpty() ? searchUrl : link;
                
                if (!title.isEmpty() && isValidSourceLink(finalUrl)) {
                    PlatformResult result = new PlatformResult("FreeCodeCamp", title, intro,
                        "No key points available", finalUrl);
                    results.add(result);
                    logger.debug("Added FreeCodeCamp result with validated URL: {}", finalUrl);
                } else if (!isValidSourceLink(finalUrl)) {
                    logger.debug("Skipped FreeCodeCamp result - invalid URL: {}", finalUrl);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape FreeCodeCamp: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeStackOverflow(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String searchUrl = "https://stackoverflow.com/search?q=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(searchUrl).timeout(TIMEOUT_MS).get();
            
            Elements questions = doc.select(".question-summary, .search-result");
            if (!questions.isEmpty()) {
                Element firstQuestion = questions.first();
                String title = firstQuestion.select("h3, .result-link a").text();
                String intro = cleanIntroduction(firstQuestion.select(".excerpt, .summary").text(), keyword);
                String link = firstQuestion.select("a.result-link, h3 a").attr("abs:href");
                
                if (!title.isEmpty()) {
                    PlatformResult result = new PlatformResult("Stack Overflow", title, intro,
                        "No key points available", link.isEmpty() ? searchUrl : link);
                    results.add(result);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape Stack Overflow: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeMedium(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String searchUrl = "https://medium.com/search?q=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(searchUrl).timeout(TIMEOUT_MS).get();
            
            Elements articles = doc.select("article, .postArticle");
            if (!articles.isEmpty()) {
                Element firstArticle = articles.first();
                String title = firstArticle.select("h2, h3").text();
                String intro = cleanIntroduction(firstArticle.select("p, .graf").text(), keyword);
                String link = firstArticle.select("a").attr("abs:href");
                
                if (!title.isEmpty()) {
                    PlatformResult result = new PlatformResult("Medium", title, intro,
                        "No key points available", link.isEmpty() ? searchUrl : link);
                    results.add(result);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape Medium: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeTowardsDataScience(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String searchUrl = "https://towardsdatascience.com/search?q=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(searchUrl).timeout(TIMEOUT_MS).get();
            
            Elements articles = doc.select("article, .postArticle");
            if (!articles.isEmpty()) {
                Element firstArticle = articles.first();
                String title = firstArticle.select("h2, h3").text();
                String intro = cleanIntroduction(firstArticle.select("p, .graf").text(), keyword);
                String link = firstArticle.select("a").attr("abs:href");
                
                if (!title.isEmpty()) {
                    PlatformResult result = new PlatformResult("Towards Data Science", title, intro,
                        "No key points available", link.isEmpty() ? searchUrl : link);
                    results.add(result);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape Towards Data Science: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeScholarpedia(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String searchUrl = "http://www.scholarpedia.org/article/" + URLEncoder.encode(keyword.replace(" ", "_"), StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(searchUrl).timeout(TIMEOUT_MS).get();
            
            String title = doc.select("h1.firstHeading").text();
            String intro = cleanIntroduction(doc.select("#mw-content-text p").first() != null ? 
                doc.select("#mw-content-text p").first().text() : "", keyword);
            
            if (!title.isEmpty()) {
                PlatformResult result = new PlatformResult("Scholarpedia", title, intro,
                    "No key points available", searchUrl);
                results.add(result);
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape Scholarpedia: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapeCodecademy(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String searchUrl = "https://www.codecademy.com/search?query=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(searchUrl).timeout(TIMEOUT_MS).get();
            
            Elements courses = doc.select(".course-card, article");
            if (!courses.isEmpty()) {
                Element firstCourse = courses.first();
                String title = firstCourse.select("h3, h2, .title").text();
                String intro = cleanIntroduction(firstCourse.select("p, .description").text(), keyword);
                String link = firstCourse.select("a").attr("abs:href");
                
                if (!title.isEmpty()) {
                    PlatformResult result = new PlatformResult("Codecademy", title, intro,
                        "No key points available", link.isEmpty() ? searchUrl : link);
                    results.add(result);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape Codecademy: {}", e.getMessage());
        }
        return results;
    }

    private List<PlatformResult> scrapePluralSight(String keyword) {
        List<PlatformResult> results = new ArrayList<>();
        try {
            String searchUrl = "https://www.pluralsight.com/search?q=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(searchUrl).timeout(TIMEOUT_MS).get();
            
            Elements courses = doc.select(".search-result, article");
            if (!courses.isEmpty()) {
                Element firstCourse = courses.first();
                String title = firstCourse.select("h3, h2, .title").text();
                String intro = cleanIntroduction(firstCourse.select("p, .description").text(), keyword);
                String link = firstCourse.select("a").attr("abs:href");
                
                String finalUrl = link.isEmpty() ? searchUrl : link;
                
                if (!title.isEmpty() && isValidSourceLink(finalUrl)) {
                    PlatformResult result = new PlatformResult("PluralSight", title, intro,
                        "No key points available", finalUrl);
                    results.add(result);
                    logger.debug("Added PluralSight result with validated URL: {}", finalUrl);
                } else if (!isValidSourceLink(finalUrl)) {
                    logger.debug("Skipped PluralSight result - invalid URL: {}", finalUrl);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to scrape PluralSight: {}", e.getMessage());
        }
        return results;
    }

    /**
     * Calculate relevance score based on content quality only.
     * Platform reputation is NOT considered.
     * 
     * Scoring rules:
     * - Keyword present in title: +30 points
     * - Keyword present in introduction: +25 points
     * - Keyword frequency in content: up to +20 points (1 point per occurrence, max 20)
     * - Content length > 300 words: +10 points
     * - At least 3 key points: +10 points
     * - Keyword present in title (treated as heading): +5 points (already counted in title check)
     * 
     * Maximum score: 100 points
     */
    private int calculateRelevanceScore(PlatformResult result, String keyword) {
        int score = 0;
        String keywordLower = keyword.toLowerCase();
        String[] keywords = keywordLower.split("\\s+");

        // 1. Keyword in title (+30 points)
        boolean keywordInTitle = false;
        if (result.title != null && !result.title.isEmpty()) {
            String titleLower = result.title.toLowerCase();
            for (String kw : keywords) {
                if (kw.length() > 2 && titleLower.contains(kw)) {
                    score += 30;
                    keywordInTitle = true;
                    logger.debug("Score +30 (keyword in title): {}", result.platform);
                    break;
                }
            }
        }

        // 2. Keyword in introduction (+25 points)
        if (result.introduction != null && !result.introduction.isEmpty()) {
            String introLower = result.introduction.toLowerCase();
            for (String kw : keywords) {
                if (kw.length() > 2 && introLower.contains(kw)) {
                    score += 25;
                    logger.debug("Score +25 (keyword in introduction): {}", result.platform);
                    break;
                }
            }
        }

        // 3. Keyword frequency in content (up to +20 points)
        String combinedText = (result.title != null ? result.title : "") + " " + 
                              (result.introduction != null ? result.introduction : "") + " " +
                              (result.keyPoints != null ? result.keyPoints : "");
        int occurrences = countKeywordOccurrences(combinedText, keyword);
        int frequencyScore = Math.min(20, occurrences); // 1 point per occurrence, max 20
        score += frequencyScore;
        logger.debug("Score +{} (keyword frequency: {} occurrences): {}", frequencyScore, occurrences, result.platform);

        // 4. Content length > 300 words (+10 points)
        int wordCount = countWords(result.introduction, result.keyPoints);
        if (wordCount > 300) {
            score += 10;
            logger.debug("Score +10 (content length: {} words): {}", wordCount, result.platform);
        }

        // 5. At least 3 key points (+10 points)
        int keyPointCount = countKeyPoints(result.keyPoints);
        if (keyPointCount >= 3) {
            score += 10;
            logger.debug("Score +10 (key points: {}): {}", keyPointCount, result.platform);
        }

        // 6. Keyword in headings (+5 points)
        // Since we treat title as h1 heading, this is already counted in title check
        // We add +5 bonus if keyword is in title (heading)
        if (keywordInTitle) {
            score += 5;
            logger.debug("Score +5 (keyword in heading/title): {}", result.platform);
        }

        logger.info("Total content-based relevance score for {}: {}", result.platform, score);
        return score;
    }

    /**
     * Count total words in introduction and key points.
     */
    private int countWords(String introduction, String keyPoints) {
        int count = 0;
        
        if (introduction != null && !introduction.isEmpty()) {
            count += introduction.split("\\s+").length;
        }
        
        if (keyPoints != null && !keyPoints.isEmpty() && 
            !keyPoints.equals("No key points available")) {
            count += keyPoints.split("\\s+").length;
        }
        
        return count;
    }

    // ==================== INNER CLASSES ====================

    private static class PlatformResult {
        String platform;
        String title;
        String introduction;
        String keyPoints;
        String sourceLink;
        int relevanceScore;

        PlatformResult(String platform, String title, String introduction, String keyPoints, String sourceLink) {
            this.platform = platform;
            this.title = title;
            this.introduction = introduction;
            this.keyPoints = keyPoints;
            this.sourceLink = sourceLink;
            this.relevanceScore = 0;
        }
    }

    private static class ParsedQuery {
        private final Long searchHistoryId;
        private final String topicName;

        private ParsedQuery(Long searchHistoryId, String topicName) {
            this.searchHistoryId = searchHistoryId;
            this.topicName = topicName;
        }

        private static ParsedQuery parse(String query) {
            if (query == null || query.isBlank()) {
                throw new IllegalArgumentException("Query is empty");
            }
            String[] parts = query.split("\\|", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Query must be formatted as <searchHistoryId>|<topicName>");
            }
            Long searchHistoryId = Long.parseLong(parts[0]);
            String topicName = parts[1];
            return new ParsedQuery(searchHistoryId, topicName);
        }
    }
}

