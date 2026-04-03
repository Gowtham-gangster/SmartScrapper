package com.smartscraper.service;

import com.smartscraper.dto.*;
import com.smartscraper.entity.*;
import com.smartscraper.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Service for retrieving and processing search results.
 * Handles result retrieval, DTO mapping, sorting, and filtering.
 */
@Service
public class ResultsService {

    private static final Logger logger = LoggerFactory.getLogger(ResultsService.class);

    private final SearchHistoryRepository searchHistoryRepository;
    private final NewsRepository newsRepository;
    private final ProductRepository productRepository;
    private final ScrapedContentRepository scrapedContentRepository;
    private final CourseRepository courseRepository;
    private final WebSearchService webSearchService;

    public ResultsService(SearchHistoryRepository searchHistoryRepository,
                          NewsRepository newsRepository,
                          ProductRepository productRepository,
                          ScrapedContentRepository scrapedContentRepository,
                          CourseRepository courseRepository,
                          WebSearchService webSearchService) {
        this.searchHistoryRepository = searchHistoryRepository;
        this.newsRepository = newsRepository;
        this.productRepository = productRepository;
        this.scrapedContentRepository = scrapedContentRepository;
        this.courseRepository = courseRepository;
        this.webSearchService = webSearchService;
    }

    /**
     * Get search history by ID.
     */
    @Transactional(readOnly = true)
    public SearchHistory getSearchHistory(Long searchId) {
        return searchHistoryRepository.findById(searchId).orElse(null);
    }

    /**
     * Get results for a search based on module type.
     */
    @Transactional(readOnly = true)
    public ApiResultsResponseDto getResults(Long searchId, Long userId) {
        SearchHistory history = searchHistoryRepository.findById(searchId).orElse(null);
        
        if (history == null) {
            logger.warn("SearchHistory {} not found", searchId);
            return null;
        }
        
        if (!history.getUserId().equals(userId)) {
            logger.warn("User {} attempted to access search {} owned by user {}", 
                    userId, searchId, history.getUserId());
            return null;
        }
        
        ModuleType moduleType = history.getModuleType();
        logger.info("Retrieving results for user={} searchId={} module={}", userId, searchId, moduleType);
        
        ApiResultsResponseDto response = new ApiResultsResponseDto();
        response.setModule(moduleType.name());
        
        switch (moduleType) {
            case NEWS -> response.setNews(getNewsResults(searchId));
            case ECOMMERCE -> response.setProducts(getProductResults(searchId));
            case RESEARCH -> {
                response.setResearch(getResearchResults(searchId));
                response.setCourses(getCourseResults(searchId));
            }
            case SEARCH -> response.setSearchResults(getSearchResults(history.getQueryText()));
            default -> {
                logger.warn("Unsupported module type: {}", moduleType);
                return null;
            }
        }
        
        return response;
    }

    /**
     * Get news results for a search.
     */
    @Transactional(readOnly = true)
    public List<NewsDto> getNewsResults(Long searchId) {
        List<News> items = newsRepository.findBySearchHistoryIdOrderByPublishedDateDesc(searchId);
        return mapToNewsDtos(items);
    }

    /**
     * Get product results for a search.
     */
    @Transactional(readOnly = true)
    public List<ProductDto> getProductResults(Long searchId) {
        List<Product> items = productRepository.findBySearchHistoryIdOrderByPriceAsc(searchId);
        return mapToProductDtos(items);
    }

    /**
     * Get research results for a search, sorted by relevance score.
     */
    @Transactional(readOnly = true)
    public List<ResearchContentDto> getResearchResults(Long searchId) {
        List<ScrapedContent> items = scrapedContentRepository.findBySearchIdOrderByRelevanceScoreIntDesc(searchId);
        List<ResearchContentDto> dtos = mapToResearchDtos(items);
        
        // Sort by enhanced score (descending)
        dtos.sort(Comparator.comparingInt(ResearchContentDto::getRelevanceScoreInt).reversed());
        
        logger.debug("Retrieved {} research results for search {}", dtos.size(), searchId);
        return dtos;
    }

    /**
     * Get course results for a search.
     */
    @Transactional(readOnly = true)
    public List<CourseDto> getCourseResults(Long searchId) {
        List<Course> courses = courseRepository.findTopBySearchHistoryIdOrderByRelevanceScore(searchId, 10);
        return mapToCourseDtos(courses);
    }

    /**
     * Get web search results.
     */
    public List<SearchResultDto> getSearchResults(String query) {
        return webSearchService.search(query);
    }

    /**
     * Map News entities to DTOs.
     */
    private List<NewsDto> mapToNewsDtos(List<News> items) {
        if (items == null) return Collections.emptyList();
        
        return items.stream().map(n -> {
            NewsDto dto = new NewsDto();
            dto.setId(n.getId());
            dto.setSearchHistoryId(n.getSearchHistoryId());
            dto.setTitle(n.getTitle());
            dto.setSource(n.getSource());
            dto.setPublishedDate(n.getPublishedDate());
            dto.setLink(n.getLink());
            dto.setSummary(n.getSummary());
            return dto;
        }).toList();
    }

    /**
     * Map Product entities to DTOs.
     */
    private List<ProductDto> mapToProductDtos(List<Product> items) {
        if (items == null) return Collections.emptyList();
        
        return items.stream().map(p -> {
            ProductDto dto = new ProductDto();
            dto.setId(p.getId());
            dto.setSearchHistoryId(p.getSearchHistoryId());
            dto.setProductName(p.getName());
            dto.setPrice(p.getPrice());
            dto.setCurrency(p.getCurrency());
            dto.setSource(p.getSource());
            dto.setUrl(p.getUrl());
            dto.setImageUrl(p.getImageUrl());
            dto.setRating(p.getRating());
            dto.setReviews(p.getReviews());
            dto.setCreatedAt(p.getCreatedAt());
            return dto;
        }).toList();
    }

    /**
     * Map ScrapedContent entities to Research DTOs.
     */
    private List<ResearchContentDto> mapToResearchDtos(List<ScrapedContent> items) {
        if (items == null) return Collections.emptyList();
        
        return items.stream().map(c -> {
            ResearchContentDto dto = new ResearchContentDto();
            dto.setId(c.getId());
            dto.setSearchId(c.getSearchId());
            dto.setContentText(c.getContentText());
            dto.setRelevanceScore(c.getRelevanceScore());
            dto.setRelevanceScoreInt(c.getRelevanceScoreInt() != null ? c.getRelevanceScoreInt() : 0);
            dto.setCreatedAt(c.getCreatedAt());
            dto.setSourceId(c.getSourceId());
            
            if (c.getScrapedSource() != null) {
                dto.setDomainName(c.getScrapedSource().getDomainName());
                dto.setSourceUrl(c.getScrapedSource().getSourceUrl());
                dto.setTitle(c.getScrapedSource().getTitle());
                dto.setYear(c.getScrapedSource().getYear());
                dto.setPaperAbstract(c.getScrapedSource().getPaperAbstract());
            }
            
            return dto;
        }).toList();
    }

    /**
     * Map Course entities to DTOs.
     */
    private List<CourseDto> mapToCourseDtos(List<Course> items) {
        if (items == null) return Collections.emptyList();
        
        return items.stream().map(c -> {
            CourseDto dto = new CourseDto();
            dto.setId(c.getId());
            dto.setSearchHistoryId(c.getSearchHistoryId());
            dto.setTitle(c.getTitle());
            dto.setPlatform(c.getPlatform());
            dto.setInstructor(c.getInstructor());
            dto.setDuration(c.getDuration());
            dto.setDurationHours(c.getDurationHours());
            dto.setRating(c.getRating());
            dto.setCourseLink(c.getCourseLink());
            dto.setDescription(c.getDescription());
            dto.setRelevanceScore(c.getRelevanceScore());
            dto.setLastUpdated(c.getLastUpdated());
            dto.setCreatedAt(c.getCreatedAt());
            return dto;
        }).toList();
    }
}
