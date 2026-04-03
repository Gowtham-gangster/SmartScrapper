package com.smartscraper.service;

import com.smartscraper.dto.ProductItem;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;

/**
 * Improved service for scraping product information from e-commerce sites.
 * Uses multiple strategies and better error handling.
 */
@Service
public class ImprovedProductScraperService {

    private static final Logger logger = LoggerFactory.getLogger(ImprovedProductScraperService.class);
    private static final int TIMEOUT = 15000; // 15 seconds
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int MAX_RETRIES = 2;

    /**
     * Search for products across multiple e-commerce sites with retry logic.
     * 
     * @param query Search query
     * @return List of product items sorted by price
     */
    public List<ProductItem> searchProducts(String query) {
        logger.info("Searching products for query: '{}'", query);
        
        List<ProductItem> allProducts = new CopyOnWriteArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(3);
        List<Future<?>> futures = new ArrayList<>();

        // Search sites in parallel
        futures.add(executor.submit(() -> {
            try {
                List<ProductItem> products = searchWithRetry(() -> searchEbay(query), "eBay");
                if (!products.isEmpty()) {
                    allProducts.addAll(products);
                    logger.info("eBay: Found {} products", products.size());
                }
            } catch (Exception e) {
                logger.warn("eBay search failed: {}", e.getMessage());
            }
        }));

        futures.add(executor.submit(() -> {
            try {
                List<ProductItem> products = searchWithRetry(() -> searchWalmart(query), "Walmart");
                if (!products.isEmpty()) {
                    allProducts.addAll(products);
                    logger.info("Walmart: Found {} products", products.size());
                }
            } catch (Exception e) {
                logger.warn("Walmart search failed: {}", e.getMessage());
            }
        }));

        futures.add(executor.submit(() -> {
            try {
                List<ProductItem> products = searchWithRetry(() -> searchTarget(query), "Target");
                if (!products.isEmpty()) {
                    allProducts.addAll(products);
                    logger.info("Target: Found {} products", products.size());
                }
            } catch (Exception e) {
                logger.warn("Target search failed: {}", e.getMessage());
            }
        }));

        // Wait for all searches to complete (max 30 seconds)
        for (Future<?> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                logger.warn("Search task failed or timed out: {}", e.getMessage());
            }
        }

        executor.shutdown();

        // Sort by price
        allProducts.sort(Comparator.comparing(
            ProductItem::getPrice,
            Comparator.nullsLast(Comparator.naturalOrder())
        ));

        logger.info("Total products found: {}", allProducts.size());
        return new ArrayList<>(allProducts);
    }

    /**
     * Search with retry logic.
     */
    private List<ProductItem> searchWithRetry(Callable<List<ProductItem>> searchTask, String siteName) throws Exception {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                logger.debug("Attempting {} search (attempt {}/{})", siteName, attempt, MAX_RETRIES);
                return searchTask.call();
            } catch (Exception e) {
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    Thread.sleep(1000 * attempt); // Exponential backoff
                }
            }
        }
        
        throw lastException;
    }

    /**
     * Search products on eBay - More reliable than Amazon.
     */
    private List<ProductItem> searchEbay(String query) throws Exception {
        List<ProductItem> products = new ArrayList<>();
        
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://www.ebay.com/sch/i.html?_nkw=" + encodedQuery + "&_sop=15"; // Sort by price
        
        logger.debug("Fetching eBay URL: {}", url);
        
        Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT)
                .referrer("https://www.google.com")
                .get();

        // eBay product selectors - Updated for current structure
        Elements productElements = doc.select("li.s-item");
        logger.debug("Found {} product elements on eBay", productElements.size());
        
        for (Element element : productElements) {
            try {
                // Skip sponsored/ad items
                if (element.select("span.SECONDARY_INFO").text().toLowerCase().contains("sponsored")) {
                    continue;
                }
                
                ProductItem product = new ProductItem();
                
                // Product name
                Element titleElement = element.selectFirst("div.s-item__title span");
                if (titleElement == null) {
                    titleElement = element.selectFirst("h3.s-item__title");
                }
                if (titleElement != null && !titleElement.text().equalsIgnoreCase("Shop on eBay")) {
                    product.setName(titleElement.text());
                } else {
                    continue; // Skip if no valid title
                }
                
                // Price
                Element priceElement = element.selectFirst("span.s-item__price");
                if (priceElement != null) {
                    String priceText = priceElement.text();
                    BigDecimal price = extractPrice(priceText);
                    if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
                        product.setPrice(price);
                        product.setCurrency("$");
                    } else {
                        continue; // Skip if no valid price
                    }
                }
                
                // URL
                Element linkElement = element.selectFirst("a.s-item__link");
                if (linkElement != null) {
                    product.setUrl(linkElement.attr("href"));
                }
                
                // Source
                product.setSource("eBay");
                
                // Only add if we have name and price
                if (product.getName() != null && product.getPrice() != null) {
                    products.add(product);
                    logger.debug("Added eBay product: {} - ${}", product.getName(), product.getPrice());
                }
                
                // Limit to 15 products per site
                if (products.size() >= 15) {
                    break;
                }
                
            } catch (Exception e) {
                logger.debug("Error parsing eBay product: {}", e.getMessage());
            }
        }
        
        return products;
    }

    /**
     * Search products on Walmart.
     */
    private List<ProductItem> searchWalmart(String query) throws Exception {
        List<ProductItem> products = new ArrayList<>();
        
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://www.walmart.com/search?q=" + encodedQuery;
        
        logger.debug("Fetching Walmart URL: {}", url);
        
        Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT)
                .referrer("https://www.google.com")
                .get();

        // Walmart uses dynamic loading, so we'll get what we can from initial HTML
        Elements productElements = doc.select("[data-item-id]");
        logger.debug("Found {} product elements on Walmart", productElements.size());
        
        for (Element element : productElements) {
            try {
                ProductItem product = new ProductItem();
                
                // Product name
                Element titleElement = element.selectFirst("span[data-automation-id='product-title']");
                if (titleElement != null) {
                    product.setName(titleElement.text());
                }
                
                // Price
                Element priceElement = element.selectFirst("div[data-automation-id='product-price'] span");
                if (priceElement != null) {
                    String priceText = priceElement.text();
                    BigDecimal price = extractPrice(priceText);
                    if (price != null) {
                        product.setPrice(price);
                        product.setCurrency("$");
                    }
                }
                
                // URL
                Element linkElement = element.selectFirst("a");
                if (linkElement != null) {
                    String href = linkElement.attr("href");
                    if (!href.startsWith("http")) {
                        href = "https://www.walmart.com" + href;
                    }
                    product.setUrl(href);
                }
                
                // Source
                product.setSource("Walmart");
                
                // Only add if we have name and price
                if (product.getName() != null && product.getPrice() != null) {
                    products.add(product);
                    logger.debug("Added Walmart product: {} - ${}", product.getName(), product.getPrice());
                }
                
                // Limit to 15 products per site
                if (products.size() >= 15) {
                    break;
                }
                
            } catch (Exception e) {
                logger.debug("Error parsing Walmart product: {}", e.getMessage());
            }
        }
        
        return products;
    }

    /**
     * Search products on Target.
     */
    private List<ProductItem> searchTarget(String query) throws Exception {
        List<ProductItem> products = new ArrayList<>();
        
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://www.target.com/s?searchTerm=" + encodedQuery;
        
        logger.debug("Fetching Target URL: {}", url);
        
        Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT)
                .referrer("https://www.google.com")
                .get();

        // Target also uses dynamic loading
        Elements productElements = doc.select("[data-test='product-grid'] > div");
        logger.debug("Found {} product elements on Target", productElements.size());
        
        for (Element element : productElements) {
            try {
                ProductItem product = new ProductItem();
                
                // Product name
                Element titleElement = element.selectFirst("a[data-test='product-title']");
                if (titleElement != null) {
                    product.setName(titleElement.text());
                    product.setUrl("https://www.target.com" + titleElement.attr("href"));
                }
                
                // Price
                Element priceElement = element.selectFirst("span[data-test='current-price']");
                if (priceElement != null) {
                    String priceText = priceElement.text();
                    BigDecimal price = extractPrice(priceText);
                    if (price != null) {
                        product.setPrice(price);
                        product.setCurrency("$");
                    }
                }
                
                // Source
                product.setSource("Target");
                
                // Only add if we have name and price
                if (product.getName() != null && product.getPrice() != null) {
                    products.add(product);
                    logger.debug("Added Target product: {} - ${}", product.getName(), product.getPrice());
                }
                
                // Limit to 15 products per site
                if (products.size() >= 15) {
                    break;
                }
                
            } catch (Exception e) {
                logger.debug("Error parsing Target product: {}", e.getMessage());
            }
        }
        
        return products;
    }

    /**
     * Extract numeric price from price text.
     */
    private BigDecimal extractPrice(String priceText) {
        if (priceText == null || priceText.isEmpty()) {
            return null;
        }

        try {
            // Remove currency symbols, commas, and extra text
            String cleanPrice = priceText
                    .replaceAll("[^0-9.]", "")
                    .trim();
            
            if (cleanPrice.isEmpty()) {
                return null;
            }
            
            // Handle multiple decimal points (take first occurrence)
            int firstDot = cleanPrice.indexOf('.');
            if (firstDot != -1) {
                int secondDot = cleanPrice.indexOf('.', firstDot + 1);
                if (secondDot != -1) {
                    cleanPrice = cleanPrice.substring(0, secondDot);
                }
            }
            
            BigDecimal price = new BigDecimal(cleanPrice);
            
            // Sanity check: price should be between $0.01 and $100,000
            if (price.compareTo(new BigDecimal("0.01")) >= 0 && 
                price.compareTo(new BigDecimal("100000")) <= 0) {
                return price;
            }
            
            return null;
        } catch (Exception e) {
            logger.debug("Failed to parse price: {}", priceText);
            return null;
        }
    }

    /**
     * Get list of supported e-commerce sites.
     */
    public List<String> getSupportedSites() {
        List<String> sites = new ArrayList<>();
        sites.add("eBay");
        sites.add("Walmart");
        sites.add("Target");
        return sites;
    }
}
