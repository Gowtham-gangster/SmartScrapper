package com.smartscraper.util;

import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RobotsTxtChecker {

    private static final Logger logger = LoggerFactory.getLogger(RobotsTxtChecker.class);
    private static final int TIMEOUT_MS = 5000;
    private static final String USER_AGENT = "SmartScraper";
    
    // Cache robots.txt rules per domain
    private final Map<String, RobotsRules> cache = new ConcurrentHashMap<>();

    /**
     * Check if scraping is allowed for a given URL.
     * 
     * @param urlString URL to check
     * @return true if allowed, false otherwise
     */
    public boolean isAllowed(String urlString) {
        try {
            URL url = new URL(urlString);
            String domain = url.getProtocol() + "://" + url.getHost();
            String path = url.getPath();

            // Get or fetch robots.txt rules
            RobotsRules rules = cache.computeIfAbsent(domain, this::fetchRobotsTxt);

            // Check if path is allowed
            return rules.isAllowed(path);

        } catch (Exception e) {
            logger.debug("Error checking robots.txt for {}: {}", urlString, e.getMessage());
            // If we can't check, assume it's allowed (fail open)
            return true;
        }
    }

    /**
     * Fetch and parse robots.txt for a domain.
     * 
     * @param domain Domain to fetch robots.txt from
     * @return Parsed robots rules
     */
    private RobotsRules fetchRobotsTxt(String domain) {
        try {
            String robotsUrl = domain + "/robots.txt";
            logger.debug("Fetching robots.txt from: {}", robotsUrl);

            String content = Jsoup.connect(robotsUrl)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .ignoreContentType(true)
                    .execute()
                    .body();

            return parseRobotsTxt(content);

        } catch (Exception e) {
            logger.debug("Could not fetch robots.txt for {}: {}", domain, e.getMessage());
            // If robots.txt doesn't exist or can't be fetched, allow all
            return new RobotsRules(true);
        }
    }

    /**
     * Parse robots.txt content.
     * 
     * @param content robots.txt content
     * @return Parsed rules
     */
    private RobotsRules parseRobotsTxt(String content) {
        RobotsRules rules = new RobotsRules(false);
        boolean relevantUserAgent = false;

        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();

            // Skip comments and empty lines
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            // Check for User-agent directive
            if (line.toLowerCase().startsWith("user-agent:")) {
                String agent = line.substring(11).trim();
                relevantUserAgent = agent.equals("*") || 
                                   agent.equalsIgnoreCase(USER_AGENT) ||
                                   agent.equalsIgnoreCase("SmartScraper");
            }

            // Parse Disallow directives for relevant user agents
            if (relevantUserAgent && line.toLowerCase().startsWith("disallow:")) {
                String path = line.substring(9).trim();
                if (!path.isEmpty()) {
                    rules.addDisallowedPath(path);
                }
            }

            // Parse Allow directives (overrides Disallow)
            if (relevantUserAgent && line.toLowerCase().startsWith("allow:")) {
                String path = line.substring(6).trim();
                if (!path.isEmpty()) {
                    rules.addAllowedPath(path);
                }
            }

            // Parse Crawl-delay
            if (relevantUserAgent && line.toLowerCase().startsWith("crawl-delay:")) {
                try {
                    String delay = line.substring(12).trim();
                    rules.setCrawlDelay(Integer.parseInt(delay));
                } catch (NumberFormatException e) {
                    logger.debug("Invalid crawl-delay value: {}", line);
                }
            }
        }

        return rules;
    }

    /**
     * Get recommended crawl delay for a domain.
     * 
     * @param urlString URL
     * @return Crawl delay in seconds, or 0 if none specified
     */
    public int getCrawlDelay(String urlString) {
        try {
            URL url = new URL(urlString);
            String domain = url.getProtocol() + "://" + url.getHost();
            RobotsRules rules = cache.get(domain);
            return rules != null ? rules.getCrawlDelay() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Clear the robots.txt cache.
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * Internal class to store robots.txt rules.
     */
    private static class RobotsRules {
        private final boolean allowAll;
        private final Map<String, Boolean> pathRules = new HashMap<>();
        private int crawlDelay = 0;

        public RobotsRules(boolean allowAll) {
            this.allowAll = allowAll;
        }

        public void addDisallowedPath(String path) {
            pathRules.put(path, false);
        }

        public void addAllowedPath(String path) {
            pathRules.put(path, true);
        }

        public void setCrawlDelay(int seconds) {
            this.crawlDelay = seconds;
        }

        public int getCrawlDelay() {
            return crawlDelay;
        }

        public boolean isAllowed(String path) {
            if (allowAll) {
                return true;
            }

            // Check exact matches first
            if (pathRules.containsKey(path)) {
                return pathRules.get(path);
            }

            // Check prefix matches (longest match wins)
            String longestMatch = "";
            Boolean allowed = true;

            for (Map.Entry<String, Boolean> entry : pathRules.entrySet()) {
                String rulePath = entry.getKey();
                if (path.startsWith(rulePath) && rulePath.length() > longestMatch.length()) {
                    longestMatch = rulePath;
                    allowed = entry.getValue();
                }
            }

            return allowed;
        }
    }
}
