package com.smartscraper.service;

import com.smartscraper.entity.ModuleType;
import com.smartscraper.service.EcommerceScraperService;
import com.smartscraper.service.NewsScraperService;
import com.smartscraper.service.ResearchScraperService;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Factory that selects the appropriate scraper implementation based on module type.
 */
@Component
public class ScraperFactory {

    private final Map<ModuleType, ScraperService> scrapers = new EnumMap<>(ModuleType.class);

    public ScraperFactory(ResearchScraperService researchScraperService,
                           NewsScraperService newsScraperService,
                           EcommerceScraperService ecommerceScraperService) {
        scrapers.put(ModuleType.RESEARCH, researchScraperService);
        scrapers.put(ModuleType.NEWS, newsScraperService);
        scrapers.put(ModuleType.ECOMMERCE, ecommerceScraperService);
    }

    public ScraperService getScraper(ModuleType moduleType) {
        ScraperService service = scrapers.get(moduleType);
        if (service == null) {
            throw new IllegalArgumentException("No scraper registered for module type: " + moduleType);
        }
        return service;
    }
}

