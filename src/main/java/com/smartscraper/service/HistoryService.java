package com.smartscraper.service;

import com.smartscraper.entity.ModuleType;
import com.smartscraper.entity.ScrapedContent;
import com.smartscraper.entity.SearchHistory;
import com.smartscraper.repository.ScrapedContentRepository;
import com.smartscraper.repository.SearchHistoryRepository;
import com.smartscraper.repository.SavedContentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for managing search history operations.
 * Handles history retrieval, filtering, statistics, and deletion.
 */
@Service
public class HistoryService {

    private static final Logger logger = LoggerFactory.getLogger(HistoryService.class);

    private final SearchHistoryRepository searchHistoryRepository;
    private final ScrapedContentRepository scrapedContentRepository;
    private final SavedContentRepository savedContentRepository;

    public HistoryService(SearchHistoryRepository searchHistoryRepository,
                          ScrapedContentRepository scrapedContentRepository,
                          SavedContentRepository savedContentRepository) {
        this.searchHistoryRepository = searchHistoryRepository;
        this.scrapedContentRepository = scrapedContentRepository;
        this.savedContentRepository = savedContentRepository;
    }

    /**
     * Get paginated search history for a user.
     */
    @Transactional(readOnly = true)
    public Page<SearchHistory> getSearchHistory(Long userId, Pageable pageable) {
        return searchHistoryRepository.findByUserIdOrderBySearchedAtDesc(userId, pageable);
    }

    /**
     * Get paginated search history filtered by module type.
     */
    @Transactional(readOnly = true)
    public Page<SearchHistory> getSearchHistoryByModule(Long userId, ModuleType moduleType, Pageable pageable) {
        return searchHistoryRepository.findByUserIdAndModuleTypeOrderBySearchedAtDesc(userId, moduleType, pageable);
    }

    /**
     * Get search history statistics by module type.
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getModuleStatistics(Long userId) {
        Map<String, Long> stats = new HashMap<>();
        stats.put("RESEARCH", searchHistoryRepository.countByUserIdAndModuleType(userId, ModuleType.RESEARCH));
        stats.put("NEWS", searchHistoryRepository.countByUserIdAndModuleType(userId, ModuleType.NEWS));
        stats.put("ECOMMERCE", searchHistoryRepository.countByUserIdAndModuleType(userId, ModuleType.ECOMMERCE));
        stats.put("SEARCH", searchHistoryRepository.countByUserIdAndModuleType(userId, ModuleType.SEARCH));
        stats.put("TOTAL", searchHistoryRepository.countByUserId(userId));
        
        logger.debug("Module statistics for user {}: {}", userId, stats);
        return stats;
    }

    /**
     * Get search history by ID.
     */
    @Transactional(readOnly = true)
    public SearchHistory getSearchHistoryById(Long historyId) {
        return searchHistoryRepository.findById(historyId).orElse(null);
    }

    /**
     * Delete search history entry.
     * Also deletes associated saved content to avoid FK violations.
     */
    @Transactional
    public boolean deleteSearchHistory(Long userId, Long historyId) {
        SearchHistory history = searchHistoryRepository.findById(historyId).orElse(null);
        
        if (history == null) {
            logger.warn("Search history {} not found", historyId);
            return false;
        }
        
        if (!history.getUserId().equals(userId)) {
            logger.warn("User {} attempted to delete history {} owned by user {}", 
                    userId, historyId, history.getUserId());
            return false;
        }
        
        try {
            // Delete saved content that points to scraped content under this search
            List<ScrapedContent> scrapedContents = scrapedContentRepository.findBySearchId(historyId);
            List<Long> contentIds = scrapedContents.stream()
                    .map(ScrapedContent::getId)
                    .collect(Collectors.toList());
            
            if (!contentIds.isEmpty()) {
                savedContentRepository.deleteByContentIdIn(contentIds);
                logger.debug("Deleted saved content for {} content IDs", contentIds.size());
            }
            
            searchHistoryRepository.delete(history);
            logger.info("User {} successfully deleted search history {}", userId, historyId);
            return true;
            
        } catch (Exception e) {
            logger.error("Error deleting history {} for user {}: {}", historyId, userId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get total history count for a user.
     */
    public long getHistoryCount(Long userId) {
        return searchHistoryRepository.countByUserId(userId);
    }
}
