package com.smartscraper.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Utility class for URL operations.
 */
public class UrlUtils {

    private static final Logger logger = LoggerFactory.getLogger(UrlUtils.class);

    /**
     * Extract domain name from a URL.
     * 
     * Examples:
     * - https://www.example.com/path → example.com
     * - http://subdomain.example.com:8080/path → subdomain.example.com
     * - https://example.co.uk/page → example.co.uk
     * 
     * @param url The URL to extract domain from
     * @return Domain name without protocol and port, or null if invalid
     */
    public static String extractDomain(String url) {
        if (url == null || url.trim().isEmpty()) {
            logger.warn("Cannot extract domain from null or empty URL");
            return null;
        }

        try {
            // Add protocol if missing
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }

            URI uri = new URI(url);
            String host = uri.getHost();

            if (host == null) {
                logger.warn("Could not extract host from URL: {}", url);
                return null;
            }

            // Remove www. prefix if present
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }

            return host.toLowerCase();

        } catch (URISyntaxException e) {
            logger.error("Invalid URL syntax: {} - {}", url, e.getMessage());
            return null;
        } catch (Exception e) {
            logger.error("Error extracting domain from URL: {} - {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * Validate if a string is a valid URL.
     * 
     * @param url The URL to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }

        try {
            // Add protocol if missing
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }

            URI uri = new URI(url);
            return uri.getHost() != null;

        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * Normalize a URL by ensuring it has a protocol.
     * 
     * @param url The URL to normalize
     * @return Normalized URL with protocol
     */
    public static String normalizeUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return url;
        }

        url = url.trim();

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "https://" + url;
        }

        return url;
    }

    /**
     * Extract the base URL (protocol + domain).
     * 
     * Example: https://www.example.com/path/page → https://example.com
     * 
     * @param url The URL
     * @return Base URL or null if invalid
     */
    public static String extractBaseUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }

        try {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }

            URI uri = new URI(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();

            if (scheme == null || host == null) {
                return null;
            }

            // Remove www. prefix
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }

            return scheme + "://" + host.toLowerCase();

        } catch (URISyntaxException e) {
            logger.error("Invalid URL syntax: {}", url);
            return null;
        }
    }
}
