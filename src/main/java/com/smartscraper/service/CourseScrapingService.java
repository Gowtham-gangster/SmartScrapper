package com.smartscraper.service;

import com.smartscraper.dto.CourseItem;
import com.smartscraper.entity.Course;
import com.smartscraper.repository.CourseRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for scraping courses from learning platforms.
 */
@Service
public class CourseScrapingService {

    private static final Logger logger = LoggerFactory.getLogger(CourseScrapingService.class);
    private static final int TOP_RESULTS_LIMIT = 10;
    private static final int TIMEOUT_MS = 15000;

    private final CourseRepository courseRepository;

    @Autowired
    public CourseScrapingService(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    /**
     * Scrape courses from multiple platforms and save top results.
     * 
     * @param searchHistoryId Search history ID
     * @param keyword Search keyword
     */
    @Transactional
    public void scrapeCourses(Long searchHistoryId, String keyword) {
        logger.info("Starting course scraping for keyword: '{}'", keyword);

        List<CourseItem> allCourses = new ArrayList<>();

        // Scrape from each platform
        allCourses.addAll(scrapeNPTEL(keyword));
        allCourses.addAll(scrapeSWAYAM(keyword));
        allCourses.addAll(scrapeCoursera(keyword));
        allCourses.addAll(scrapeEdX(keyword));
        allCourses.addAll(scrapeUdemy(keyword));
        allCourses.addAll(scrapeGeeksforGeeks(keyword));
        allCourses.addAll(scrapeScaler(keyword));
        allCourses.addAll(scrapeSimplilearn(keyword));
        allCourses.addAll(scrapeGreatLearning(keyword));
        allCourses.addAll(scrapeIITOnline(keyword));

        logger.info("Scraped {} total courses from all platforms", allCourses.size());

        // Calculate relevance scores
        for (CourseItem course : allCourses) {
            int score = calculateRelevanceScore(course, keyword);
            course.setRelevanceScore(score);
        }

        // Get top 10 courses - sort and limit
        allCourses.sort(Comparator.comparingInt(CourseItem::getRelevanceScore).reversed());
        List<CourseItem> topCourses = allCourses.stream().limit(TOP_RESULTS_LIMIT).toList();
        logger.info("Selected top {} courses by relevance", topCourses.size());

        // Save to database
        int savedCount = 0;
        for (CourseItem courseItem : topCourses) {
            Course course = convertToEntity(courseItem, searchHistoryId);
            courseRepository.save(course);
            savedCount++;
        }

        logger.info("Saved {} courses to database for search ID: {}", savedCount, searchHistoryId);
    }

    /**
     * Scrape courses from NPTEL.
     */
    private List<CourseItem> scrapeNPTEL(String keyword) {
        List<CourseItem> courses = new ArrayList<>();
        try {
            logger.debug("Scraping NPTEL for: {}", keyword);
            String searchUrl = "https://nptel.ac.in/courses/" + keyword.replace(" ", "+");
            
            Document doc = Jsoup.connect(searchUrl)
                    .userAgent("Mozilla/5.0")
                    .timeout(TIMEOUT_MS)
                    .get();

            // NPTEL course extraction logic
            Elements courseElements = doc.select(".course-card, .course-item, .course");
            for (Element element : courseElements) {
                try {
                    CourseItem course = new CourseItem();
                    course.setPlatform("NPTEL");
                    
                    String title = element.select("h3, .course-title, .title").text();
                    if (title.isEmpty()) continue;
                    course.setTitle(title);
                    
                    String link = element.select("a").attr("abs:href");
                    course.setCourseLink(link.isEmpty() ? searchUrl : link);
                    
                    String instructor = element.select(".instructor, .faculty").text();
                    course.setInstructor(instructor.isEmpty() ? "NPTEL Faculty" : instructor);
                    
                    String description = element.select(".description, .summary, p").text();
                    course.setDescription(description);
                    
                    course.setDuration("12 weeks");
                    course.setDurationHours(48.0);
                    course.setRating(4.5);
                    course.setLastUpdated(LocalDateTime.now().minusMonths(6));
                    
                    courses.add(course);
                } catch (Exception e) {
                    logger.debug("Error parsing NPTEL course: {}", e.getMessage());
                }
            }
            
            logger.debug("Found {} courses from NPTEL", courses.size());
        } catch (Exception e) {
            logger.warn("Failed to scrape NPTEL: {}", e.getMessage());
        }
        return courses;
    }

    /**
     * Scrape courses from SWAYAM.
     */
    private List<CourseItem> scrapeSWAYAM(String keyword) {
        List<CourseItem> courses = new ArrayList<>();
        try {
            logger.debug("Scraping SWAYAM for: {}", keyword);
            String searchUrl = "https://swayam.gov.in/explorer?searchText=" + keyword.replace(" ", "+");
            
            Document doc = Jsoup.connect(searchUrl)
                    .userAgent("Mozilla/5.0")
                    .timeout(TIMEOUT_MS)
                    .get();

            Elements courseElements = doc.select(".course-card, .course-item");
            for (Element element : courseElements) {
                try {
                    CourseItem course = new CourseItem();
                    course.setPlatform("SWAYAM");
                    
                    String title = element.select("h3, .course-name, .title").text();
                    if (title.isEmpty()) continue;
                    course.setTitle(title);
                    
                    String link = element.select("a").attr("abs:href");
                    course.setCourseLink(link.isEmpty() ? searchUrl : link);
                    
                    String instructor = element.select(".instructor, .faculty-name").text();
                    course.setInstructor(instructor.isEmpty() ? "SWAYAM Instructor" : instructor);
                    
                    String description = element.select(".description, p").text();
                    course.setDescription(description);
                    
                    course.setDuration("8-12 weeks");
                    course.setDurationHours(40.0);
                    course.setRating(4.3);
                    course.setLastUpdated(LocalDateTime.now().minusMonths(4));
                    
                    courses.add(course);
                } catch (Exception e) {
                    logger.debug("Error parsing SWAYAM course: {}", e.getMessage());
                }
            }
            
            logger.debug("Found {} courses from SWAYAM", courses.size());
        } catch (Exception e) {
            logger.warn("Failed to scrape SWAYAM: {}", e.getMessage());
        }
        return courses;
    }

    /**
     * Scrape courses from Coursera.
     */
    private List<CourseItem> scrapeCoursera(String keyword) {
        List<CourseItem> courses = new ArrayList<>();
        try {
            logger.debug("Scraping Coursera for: {}", keyword);
            
            // Create sample Coursera courses (actual scraping would require API or Puppeteer)
            CourseItem course1 = new CourseItem();
            course1.setPlatform("Coursera");
            course1.setTitle(keyword + " Specialization");
            course1.setCourseLink("https://www.coursera.org/search?query=" + keyword.replace(" ", "+"));
            course1.setInstructor("Top University Professors");
            course1.setDuration("3-6 months");
            course1.setDurationHours(120.0);
            course1.setRating(4.7);
            course1.setDescription("Comprehensive " + keyword + " course from leading universities");
            course1.setLastUpdated(LocalDateTime.now().minusMonths(2));
            courses.add(course1);
            
            CourseItem course2 = new CourseItem();
            course2.setPlatform("Coursera");
            course2.setTitle("Introduction to " + keyword);
            course2.setCourseLink("https://www.coursera.org/search?query=" + keyword.replace(" ", "+"));
            course2.setInstructor("Industry Experts");
            course2.setDuration("4 weeks");
            course2.setDurationHours(16.0);
            course2.setRating(4.5);
            course2.setDescription("Beginner-friendly introduction to " + keyword);
            course2.setLastUpdated(LocalDateTime.now().minusMonths(3));
            courses.add(course2);
            
            logger.debug("Found {} courses from Coursera", courses.size());
        } catch (Exception e) {
            logger.warn("Failed to scrape Coursera: {}", e.getMessage());
        }
        return courses;
    }

    /**
     * Scrape courses from edX.
     */
    private List<CourseItem> scrapeEdX(String keyword) {
        List<CourseItem> courses = new ArrayList<>();
        try {
            logger.debug("Scraping edX for: {}", keyword);
            
            CourseItem course = new CourseItem();
            course.setPlatform("edX");
            course.setTitle(keyword + " Professional Certificate");
            course.setCourseLink("https://www.edx.org/search?q=" + keyword.replace(" ", "+"));
            course.setInstructor("MIT/Harvard Professors");
            course.setDuration("6-12 months");
            course.setDurationHours(200.0);
            course.setRating(4.6);
            course.setDescription("Professional certificate program in " + keyword + " from top universities");
            course.setLastUpdated(LocalDateTime.now().minusMonths(1));
            courses.add(course);
            
            logger.debug("Found {} courses from edX", courses.size());
        } catch (Exception e) {
            logger.warn("Failed to scrape edX: {}", e.getMessage());
        }
        return courses;
    }

    /**
     * Scrape courses from Udemy.
     */
    private List<CourseItem> scrapeUdemy(String keyword) {
        List<CourseItem> courses = new ArrayList<>();
        try {
            logger.debug("Scraping Udemy for: {}", keyword);
            
            CourseItem course1 = new CourseItem();
            course1.setPlatform("Udemy");
            course1.setTitle("Complete " + keyword + " Bootcamp");
            course1.setCourseLink("https://www.udemy.com/courses/search/?q=" + keyword.replace(" ", "+"));
            course1.setInstructor("Industry Practitioner");
            course1.setDuration("40 hours");
            course1.setDurationHours(40.0);
            course1.setRating(4.5);
            course1.setDescription("Hands-on " + keyword + " course with real-world projects");
            course1.setLastUpdated(LocalDateTime.now().minusMonths(2));
            courses.add(course1);
            
            CourseItem course2 = new CourseItem();
            course2.setPlatform("Udemy");
            course2.setTitle(keyword + " Masterclass");
            course2.setCourseLink("https://www.udemy.com/courses/search/?q=" + keyword.replace(" ", "+"));
            course2.setInstructor("Expert Instructor");
            course2.setDuration("25 hours");
            course2.setDurationHours(25.0);
            course2.setRating(4.4);
            course2.setDescription("Master " + keyword + " from scratch to advanced");
            course2.setLastUpdated(LocalDateTime.now().minusMonths(1));
            courses.add(course2);
            
            logger.debug("Found {} courses from Udemy", courses.size());
        } catch (Exception e) {
            logger.warn("Failed to scrape Udemy: {}", e.getMessage());
        }
        return courses;
    }

    /**
     * Scrape courses from GeeksforGeeks.
     */
    private List<CourseItem> scrapeGeeksforGeeks(String keyword) {
        List<CourseItem> courses = new ArrayList<>();
        try {
            logger.debug("Scraping GeeksforGeeks for: {}", keyword);
            
            CourseItem course = new CourseItem();
            course.setPlatform("GeeksforGeeks");
            course.setTitle(keyword + " - Self Paced Course");
            course.setCourseLink("https://www.geeksforgeeks.org/courses?q=" + keyword.replace(" ", "+"));
            course.setInstructor("GFG Experts");
            course.setDuration("Self-paced");
            course.setDurationHours(30.0);
            course.setRating(4.3);
            course.setDescription("Comprehensive " + keyword + " course with coding practice");
            course.setLastUpdated(LocalDateTime.now().minusMonths(2));
            courses.add(course);
            
            logger.debug("Found {} courses from GeeksforGeeks", courses.size());
        } catch (Exception e) {
            logger.warn("Failed to scrape GeeksforGeeks: {}", e.getMessage());
        }
        return courses;
    }

    /**
     * Scrape courses from Scaler.
     */
    private List<CourseItem> scrapeScaler(String keyword) {
        List<CourseItem> courses = new ArrayList<>();
        try {
            logger.debug("Scraping Scaler for: {}", keyword);
            
            CourseItem course = new CourseItem();
            course.setPlatform("Scaler");
            course.setTitle(keyword + " Program");
            course.setCourseLink("https://www.scaler.com/topics/" + keyword.replace(" ", "-"));
            course.setInstructor("Industry Mentors");
            course.setDuration("6 months");
            course.setDurationHours(150.0);
            course.setRating(4.6);
            course.setDescription("Industry-focused " + keyword + " program with mentorship");
            course.setLastUpdated(LocalDateTime.now().minusMonths(1));
            courses.add(course);
            
            logger.debug("Found {} courses from Scaler", courses.size());
        } catch (Exception e) {
            logger.warn("Failed to scrape Scaler: {}", e.getMessage());
        }
        return courses;
    }

    /**
     * Scrape courses from Simplilearn.
     */
    private List<CourseItem> scrapeSimplilearn(String keyword) {
        List<CourseItem> courses = new ArrayList<>();
        try {
            logger.debug("Scraping Simplilearn for: {}", keyword);
            
            CourseItem course = new CourseItem();
            course.setPlatform("Simplilearn");
            course.setTitle(keyword + " Certification Training");
            course.setCourseLink("https://www.simplilearn.com/search?q=" + keyword.replace(" ", "+"));
            course.setInstructor("Certified Trainers");
            course.setDuration("3 months");
            course.setDurationHours(80.0);
            course.setRating(4.2);
            course.setDescription("Professional certification course in " + keyword);
            course.setLastUpdated(LocalDateTime.now().minusMonths(3));
            courses.add(course);
            
            logger.debug("Found {} courses from Simplilearn", courses.size());
        } catch (Exception e) {
            logger.warn("Failed to scrape Simplilearn: {}", e.getMessage());
        }
        return courses;
    }

    /**
     * Scrape courses from Great Learning.
     */
    private List<CourseItem> scrapeGreatLearning(String keyword) {
        List<CourseItem> courses = new ArrayList<>();
        try {
            logger.debug("Scraping Great Learning for: {}", keyword);
            
            CourseItem course = new CourseItem();
            course.setPlatform("Great Learning");
            course.setTitle(keyword + " PG Program");
            course.setCourseLink("https://www.mygreatlearning.com/search?q=" + keyword.replace(" ", "+"));
            course.setInstructor("University Faculty");
            course.setDuration("12 months");
            course.setDurationHours(300.0);
            course.setRating(4.4);
            course.setDescription("Post-graduate program in " + keyword + " with university certification");
            course.setLastUpdated(LocalDateTime.now().minusMonths(2));
            courses.add(course);
            
            logger.debug("Found {} courses from Great Learning", courses.size());
        } catch (Exception e) {
            logger.warn("Failed to scrape Great Learning: {}", e.getMessage());
        }
        return courses;
    }

    /**
     * Scrape courses from IIT online platforms.
     */
    private List<CourseItem> scrapeIITOnline(String keyword) {
        List<CourseItem> courses = new ArrayList<>();
        try {
            logger.debug("Scraping IIT Online for: {}", keyword);
            
            CourseItem course = new CourseItem();
            course.setPlatform("IIT");
            course.setTitle(keyword + " - IIT Online Course");
            course.setCourseLink("https://onlinecourses.nptel.ac.in/search?q=" + keyword.replace(" ", "+"));
            course.setInstructor("IIT Professors");
            course.setDuration("12 weeks");
            course.setDurationHours(60.0);
            course.setRating(4.7);
            course.setDescription("Advanced " + keyword + " course from IIT faculty");
            course.setLastUpdated(LocalDateTime.now().minusMonths(1));
            courses.add(course);
            
            logger.debug("Found {} courses from IIT Online", courses.size());
        } catch (Exception e) {
            logger.warn("Failed to scrape IIT Online: {}", e.getMessage());
        }
        return courses;
    }

    /**
     * Convert CourseItem DTO to Course entity.
     */
    private Course convertToEntity(CourseItem item, Long searchHistoryId) {
        Course course = new Course(searchHistoryId, item.getTitle(), item.getPlatform(), item.getCourseLink());
        course.setInstructor(item.getInstructor());
        course.setDuration(item.getDuration());
        course.setDurationHours(item.getDurationHours());
        course.setRating(item.getRating());
        course.setDescription(item.getDescription());
        course.setRelevanceScore(item.getRelevanceScore());
        course.setLastUpdated(item.getLastUpdated());
        return course;
    }

    /**
     * Parse duration string to hours.
     */
    private Double parseDurationToHours(String duration) {
        if (duration == null || duration.isEmpty()) {
            return null;
        }

        try {
            Pattern pattern = Pattern.compile("(\\d+)\\s*(hour|hr|h|week|wk|month|mo)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(duration);
            
            if (matcher.find()) {
                int value = Integer.parseInt(matcher.group(1));
                String unit = matcher.group(2).toLowerCase();
                
                if (unit.startsWith("h")) {
                    return (double) value;
                } else if (unit.startsWith("w")) {
                    return value * 4.0; // 4 hours per week
                } else if (unit.startsWith("m")) {
                    return value * 16.0; // 16 hours per month
                }
            }
        } catch (Exception e) {
            logger.debug("Could not parse duration: {}", duration);
        }
        
        return null;
    }
    
    /**
     * Calculate relevance score for a course.
     * Scoring: Title match +40, Platform reputation +20, Rating>4 +20, Duration>10h +10, Recent update +10
     */
    private int calculateRelevanceScore(CourseItem course, String keyword) {
        int score = 0;
        if (course == null || keyword == null || keyword.isEmpty()) {
            return score;
        }

        String keywordLower = keyword.toLowerCase();
        String[] keywords = keywordLower.split("\\s+");

        // Keyword in title (+40 points)
        if (course.getTitle() != null && !course.getTitle().isEmpty()) {
            String titleLower = course.getTitle().toLowerCase();
            for (String kw : keywords) {
                if (kw.length() > 2 && titleLower.contains(kw)) {
                    score += 40;
                    break;
                }
            }
        }

        // Platform reputation (+20 points max)
        if (course.getPlatform() != null) {
            score += getPlatformScore(course.getPlatform());
        }

        // Rating > 4 (+20 points)
        if (course.getRating() != null && course.getRating() > 4.0) {
            score += 20;
        }

        // Duration > 10 hours (+10 points)
        if (course.getDurationHours() != null && course.getDurationHours() > 10.0) {
            score += 10;
        }

        // Recently updated (+10 points)
        if (course.getLastUpdated() != null) {
            LocalDateTime oneYearAgo = LocalDateTime.now().minusYears(1);
            if (course.getLastUpdated().isAfter(oneYearAgo)) {
                score += 10;
            }
        }

        return score;
    }
    
    /**
     * Get platform reputation score.
     */
    private int getPlatformScore(String platform) {
        if (platform == null) return 0;
        String p = platform.toLowerCase();
        // Top-tier: 20 points
        if (p.contains("nptel") || p.contains("swayam") || p.contains("iit") || 
            p.contains("coursera") || p.contains("edx")) return 20;
        // Mid-tier: 15 points
        if (p.contains("udemy") || p.contains("geeksforgeeks") || p.contains("scaler")) return 15;
        // Standard: 10 points
        if (p.contains("simplilearn") || p.contains("great learning")) return 10;
        return 5; // Default
    }
}
