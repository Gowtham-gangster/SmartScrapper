package com.smartscraper.service;

import com.smartscraper.dto.SavedContentDto;
import com.smartscraper.entity.SavedContent;
import com.smartscraper.entity.ScrapedContent;
import com.smartscraper.repository.SavedContentRepository;
import com.smartscraper.repository.ScrapedContentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service for managing saved content operations.
 */
@Service
public class SavedContentService {

    private static final Logger logger = LoggerFactory.getLogger(SavedContentService.class);

    private final SavedContentRepository savedContentRepository;
    private final ScrapedContentRepository scrapedContentRepository;

    public SavedContentService(SavedContentRepository savedContentRepository,
                               ScrapedContentRepository scrapedContentRepository) {
        this.savedContentRepository = savedContentRepository;
        this.scrapedContentRepository = scrapedContentRepository;
    }

    /**
     * Get paginated saved content for a user with full details.
     */
    @Transactional(readOnly = true)
    public Page<SavedContent> getSavedContentWithDetails(Long userId, Pageable pageable) {
        // Fetch lightweight SavedContent records to get stable IDs
        Page<SavedContent> savedPage = savedContentRepository.findByUserIdOrderBySavedAtDesc(userId, pageable);
        
        List<Long> savedIds = savedPage.getContent().stream()
                .map(SavedContent::getId)
                .collect(Collectors.toList());
        
        if (savedIds.isEmpty()) {
            return savedPage;
        }
        
        // Fetch full details with content
        List<SavedContent> fetchedDetails = savedContentRepository.findByUserIdAndIdInWithContent(userId, savedIds);
        
        Map<Long, SavedContent> byId = fetchedDetails.stream()
                .collect(Collectors.toMap(SavedContent::getId, sc -> sc));
        
        // Replace lightweight objects with full details
        savedPage.getContent().forEach(sc -> {
            SavedContent full = byId.get(sc.getId());
            if (full != null) {
                sc.setScrapedContent(full.getScrapedContent());
            }
        });
        
        return savedPage;
    }

    /**
     * Get saved content IDs for a user.
     */
    public Set<Long> getSavedContentIds(Long userId) {
        List<SavedContent> userSavedContent = savedContentRepository.findByUserId(userId);
        return userSavedContent.stream()
                .map(SavedContent::getContentId)
                .collect(Collectors.toSet());
    }

    /**
     * Save content for a user.
     */
    @Transactional
    public SavedContent saveContent(Long userId, Long contentId) {
        // Check if already saved
        if (savedContentRepository.existsByUserIdAndContentId(userId, contentId)) {
            logger.info("Content {} already saved by user {}", contentId, userId);
            return null;
        }
        
        // Verify content exists
        if (!scrapedContentRepository.existsById(contentId)) {
            logger.warn("Content {} does not exist", contentId);
            throw new IllegalArgumentException("Content not found");
        }
        
        SavedContent savedContent = new SavedContent(userId, contentId);
        SavedContent saved = savedContentRepository.save(savedContent);
        
        logger.info("User {} successfully saved content {}", userId, contentId);
        return saved;
    }

    /**
     * Check if content is saved by user.
     */
    public boolean isContentSaved(Long userId, Long contentId) {
        return savedContentRepository.existsByUserIdAndContentId(userId, contentId);
    }

    /**
     * Unsave content for a user.
     */
    @Transactional
    public boolean unsaveContent(Long userId, Long savedId) {
        SavedContent savedContent = savedContentRepository.findById(savedId).orElse(null);
        
        if (savedContent == null) {
            logger.warn("Saved content {} not found", savedId);
            return false;
        }
        
        if (!savedContent.getUserId().equals(userId)) {
            logger.warn("User {} attempted to delete saved content {} owned by user {}",
                    userId, savedId, savedContent.getUserId());
            return false;
        }
        
        savedContentRepository.delete(savedContent);
        logger.info("User {} successfully deleted saved content {}", userId, savedId);
        return true;
    }

    /**
     * Delete multiple saved content items.
     */
    @Transactional
    public int deleteSavedContent(Long userId, List<Long> savedIds) {
        int deletedCount = 0;
        
        for (Long savedId : savedIds) {
            if (unsaveContent(userId, savedId)) {
                deletedCount++;
            }
        }
        
        logger.info("User {} deleted {} saved items", userId, deletedCount);
        return deletedCount;
    }

    /**
     * Delete all saved content for a user.
     */
    @Transactional
    public long deleteAllSavedContent(Long userId) {
        long count = savedContentRepository.countByUserId(userId);
        
        if (count == 0) {
            return 0;
        }
        
        savedContentRepository.deleteByUserId(userId);
        logger.info("User {} successfully deleted all {} saved items", userId, count);
        return count;
    }

    /**
     * Get count of saved content for a user.
     */
    public long getSavedContentCount(Long userId) {
        return savedContentRepository.countByUserId(userId);
    }

    /**
     * Delete saved content by content IDs.
     */
    @Transactional
    public void deleteSavedContentByContentIds(List<Long> contentIds) {
        if (!contentIds.isEmpty()) {
            savedContentRepository.deleteByContentIdIn(contentIds);
            logger.info("Deleted saved content for {} content IDs", contentIds.size());
        }
    }

    /**
     * Get saved content as DTOs with pagination.
     * Includes full content details and proper ordering.
     */
    @Transactional(readOnly = true)
    public List<SavedContentDto> getSavedContentDtos(Long userId, Pageable pageable, Page<SavedContent> savedPage) {
        List<Long> savedIds = savedPage.getContent().stream()
                .map(SavedContent::getId)
                .collect(Collectors.toList());
        
        if (savedIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Fetch full details with content
        List<SavedContent> details = savedContentRepository.findByUserIdAndIdInWithContent(userId, savedIds);
        Map<Long, SavedContent> byId = details.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(SavedContent::getId, Function.identity(), (a, b) -> a));
        
        // Map to DTOs in the correct order
        List<SavedContentDto> items = new ArrayList<>();
        for (Long id : savedIds) {
            SavedContent saved = byId.get(id);
            if (saved == null) continue;
            
            SavedContentDto dto = mapToDto(saved);
            items.add(dto);
        }
        
        return items;
    }

    /**
     * Map SavedContent entity to DTO.
     */
    private SavedContentDto mapToDto(SavedContent saved) {
        SavedContentDto dto = new SavedContentDto();
        dto.setId(saved.getId());
        dto.setContentId(saved.getContentId());
        dto.setSavedAt(saved.getSavedAt());
        
        ScrapedContent content = saved.getScrapedContent();
        if (content != null) {
            dto.setRelevanceScore(content.getRelevanceScore());
            dto.setContentText(content.getContentText());
            
            if (content.getScrapedSource() != null) {
                dto.setDomainName(content.getScrapedSource().getDomainName());
                dto.setSourceUrl(content.getScrapedSource().getSourceUrl());
            }
        }
        
        return dto;
    }

    /**
     * Get saved content list with full details (for web pages).
     * Returns ordered list matching the page order.
     */
    @Transactional(readOnly = true)
    public List<SavedContent> getSavedContentListWithDetails(Long userId, Page<SavedContent> savedPage) {
        List<Long> savedIds = savedPage.getContent().stream()
                .map(SavedContent::getId)
                .collect(Collectors.toList());
        
        if (savedIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Fetch full details
        List<SavedContent> fetchedDetails = savedContentRepository.findByUserIdAndIdInWithContent(userId, savedIds);
        Map<Long, SavedContent> byId = fetchedDetails.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(SavedContent::getId, Function.identity(), (a, b) -> a));
        
        // Reorder to match page order
        return savedIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
