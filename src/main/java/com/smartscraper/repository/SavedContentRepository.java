package com.smartscraper.repository;

import com.smartscraper.entity.SavedContent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SavedContentRepository extends JpaRepository<SavedContent, Long> {

    /**
     * Find all saved content for a specific user.
     * 
     * @param userId User ID
     * @return List of saved content
     */
    List<SavedContent> findByUserId(Long userId);

    /**
     * Find all saved content for a user, ordered by saved date descending.
     * 
     * @param userId User ID
     * @return List of saved content ordered by most recent first
     */
    List<SavedContent> findByUserIdOrderBySavedAtDesc(Long userId);

    /**
     * Find a page of saved content for a user.
     */
    Page<SavedContent> findByUserIdOrderBySavedAtDesc(Long userId, Pageable pageable);

    /**
     * Find all users who saved a specific content.
     * 
     * @param contentId Scraped content ID
     * @return List of saved content records
     */
    List<SavedContent> findByContentId(Long contentId);

    /**
     * Find a specific saved content record by user and content.
     * 
     * @param userId User ID
     * @param contentId Scraped content ID
     * @return Optional containing saved content if found
     */
    Optional<SavedContent> findByUserIdAndContentId(Long userId, Long contentId);

    /**
     * Check if a user has saved a specific content.
     * 
     * @param userId User ID
     * @param contentId Scraped content ID
     * @return true if saved, false otherwise
     */
    boolean existsByUserIdAndContentId(Long userId, Long contentId);

    /**
     * Count saved content for a specific user.
     * 
     * @param userId User ID
     * @return Count of saved content
     */
    long countByUserId(Long userId);

    /**
     * Count how many users saved a specific content.
     * 
     * @param contentId Scraped content ID
     * @return Count of users who saved this content
     */
    long countByContentId(Long contentId);

    /**
     * Find saved content for a user after a specific date.
     * 
     * @param userId User ID
     * @param date Date threshold
     * @return List of saved content
     */
    List<SavedContent> findByUserIdAndSavedAtAfter(Long userId, LocalDateTime date);

    /**
     * Delete a specific saved content record.
     * 
     * @param userId User ID
     * @param contentId Scraped content ID
     */
    void deleteByUserIdAndContentId(Long userId, Long contentId);

    /**
     * Delete all saved content for a user.
     * 
     * @param userId User ID
     */
    void deleteByUserId(Long userId);

    /**
     * Delete all saved records for a specific content.
     * 
     * @param contentId Scraped content ID
     */
    void deleteByContentId(Long contentId);

    void deleteByContentIdIn(List<Long> contentIds);

    /**
     * Get saved content with full details using JOIN.
     * 
     * @param userId User ID
     * @return List of saved content with scraped content details
     */
    @Query("SELECT sc FROM SavedContent sc " +
           "JOIN FETCH sc.scrapedContent " +
           "WHERE sc.userId = :userId " +
           "ORDER BY sc.savedAt DESC")
    List<SavedContent> findByUserIdWithContent(@Param("userId") Long userId);

    /**
     * Fetch saved content details for a specific user + set of IDs.
     * Used to avoid JOIN FETCH + pagination issues.
     */
    @Query("SELECT DISTINCT sc FROM SavedContent sc " +
            "JOIN FETCH sc.scrapedContent scContent " +
            "JOIN FETCH scContent.scrapedSource " +
            "WHERE sc.userId = :userId " +
            "AND sc.id IN :ids")
    List<SavedContent> findByUserIdAndIdInWithContent(@Param("userId") Long userId, @Param("ids") List<Long> ids);

    /**
     * Get recently saved content (last N days).
     * 
     * @param userId User ID
     * @param days Number of days to look back
     * @return List of recently saved content
     */
    @Query("SELECT sc FROM SavedContent sc " +
           "WHERE sc.userId = :userId " +
           "AND sc.savedAt >= :cutoffDate " +
           "ORDER BY sc.savedAt DESC")
    List<SavedContent> findRecentlySaved(@Param("userId") Long userId, @Param("cutoffDate") LocalDateTime cutoffDate);
}
