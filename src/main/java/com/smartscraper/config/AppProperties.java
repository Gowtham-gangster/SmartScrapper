package com.smartscraper.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Scraping scraping = new Scraping();
    private final Relevance relevance = new Relevance();
    private final Pagination pagination = new Pagination();

    public Scraping getScraping() {
        return scraping;
    }

    public Relevance getRelevance() {
        return relevance;
    }

    public Pagination getPagination() {
        return pagination;
    }

    public static class Scraping {
        private int timeoutMs = 10000;
        private int maxUrlsPerDomain = 2;
        private int targetUrlCount = 20;
        private String userAgent = "SmartScraper/1.0 (Educational Research Tool)";

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public int getMaxUrlsPerDomain() {
            return maxUrlsPerDomain;
        }

        public void setMaxUrlsPerDomain(int maxUrlsPerDomain) {
            this.maxUrlsPerDomain = maxUrlsPerDomain;
        }

        public int getTargetUrlCount() {
            return targetUrlCount;
        }

        public void setTargetUrlCount(int targetUrlCount) {
            this.targetUrlCount = targetUrlCount;
        }

        public String getUserAgent() {
            return userAgent;
        }

        public void setUserAgent(String userAgent) {
            this.userAgent = userAgent;
        }
    }

    public static class Relevance {
        private double minKeywordFrequency = 0.3;
        private int minContentLength = 100;
        private double minOverallScore = 0.4;

        public double getMinKeywordFrequency() {
            return minKeywordFrequency;
        }

        public void setMinKeywordFrequency(double minKeywordFrequency) {
            this.minKeywordFrequency = minKeywordFrequency;
        }

        public int getMinContentLength() {
            return minContentLength;
        }

        public void setMinContentLength(int minContentLength) {
            this.minContentLength = minContentLength;
        }

        public double getMinOverallScore() {
            return minOverallScore;
        }

        public void setMinOverallScore(double minOverallScore) {
            this.minOverallScore = minOverallScore;
        }
    }

    public static class Pagination {
        private int defaultPageSize = 20;
        private int maxPageSize = 100;

        public int getDefaultPageSize() {
            return defaultPageSize;
        }

        public void setDefaultPageSize(int defaultPageSize) {
            this.defaultPageSize = defaultPageSize;
        }

        public int getMaxPageSize() {
            return maxPageSize;
        }

        public void setMaxPageSize(int maxPageSize) {
            this.maxPageSize = maxPageSize;
        }
    }
}
