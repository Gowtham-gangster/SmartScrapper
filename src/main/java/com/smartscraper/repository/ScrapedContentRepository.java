package com.smartscraper.repository;

import com.smartscraper.entity.ScrapedContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ScrapedContentRepository extends JpaRepository<ScrapedContent, Long> {

    /**
     * Find all scraped content for a specific search.
     * 
     * @param searchId Search history ID
     * @return List of scraped content
     */
    List<ScrapedContent> findBySearchId(Long searchId);

    /**
     * Find all scraped content for a specific search, ordered by relevance score descending.
     * 
     * @param searchId Search history ID
     * @return List of scraped content ordered by relevance
     */
    List<ScrapedContent> findBySearchIdOrderByRelevanceScoreDesc(Long searchId);

    /**
     * Find all scraped content for a specific search, ordered by enhanced relevance score descending.
     * 
     * @param searchId Search history ID
     * @return List of scraped content ordered by enhanced relevance score
     */
    List<ScrapedContent> findBySearchIdOrderByRelevanceScoreIntDesc(Long searchId);

    /**
     * Find all scraped content from a specific source.
     * 
     * @param sourceId Scraped source ID
     * @return List of scraped content
     */
    List<ScrapedContent> findBySourceId(Long sourceId);

    /**
     * Find all scraped content for a search from a specific source.
     * 
     * @param searchId Search history ID
     * @param sourceId Scraped source ID
     * @return List of scraped content
     */
    List<ScrapedContent> findBySearchIdAndSourceId(Long searchId, Long sourceId);

    /**
     * Count scraped content for a specific search.
     * 
     * @param searchId Search history ID
     * @return Count of scraped content
     */
    long countBySearchId(Long searchId);

    /**
     * Count scraped content from a specific source.
     * 
     * @param sourceId Scraped source ID
     * @return Count of scraped content
     */
    long countBySourceId(Long sourceId);

    /**
     * Find scraped content created after a specific date.
     * 
     * @param date Date threshold
     * @return List of scraped content
     */
    List<ScrapedContent> findByCreatedAtAfter(LocalDateTime date);

    /**
     * Find scraped content with relevance score above threshold.
     * 
     * @param threshold Minimum relevance score
     * @return List of scraped content
     */
    List<ScrapedContent> findByRelevanceScoreGreaterThanEqual(Double threshold);

    /**
     * Find top N scraped content for a search by relevance score.
     * 
     * @param searchId Search history ID
     * @param limit Maximum number of results
     * @return List of top scraped content
     */
    @Query("SELECT sc FROM ScrapedContent sc WHERE sc.searchId = :searchId ORDER BY sc.relevanceScore DESC LIMIT :limit")
    List<ScrapedContent> findTopBySearchIdOrderByRelevanceScore(@Param("searchId") Long searchId, @Param("limit") int limit);

    /**
     * Delete all scraped content for a specific search.
     * 
     * @param searchId Search history ID
     */
    void deleteBySearchId(Long searchId);

    /**
     * Delete all scraped content from a specific source.
     * 
     * @param sourceId Scraped source ID
     */
    void deleteBySourceId(Long sourceId);

    /**
     * Ownership check: ensure the scraped content belongs to the current user's search.
     */
    boolean existsByIdAndSearchHistory_UserId(Long id, Long userId);

    /**
     * Load only scraped content IDs that belong to the current user.
     */
    List<ScrapedContent> findByIdInAndSearchHistory_UserId(List<Long> ids, Long userId);

    /**
     * Find the latest scraped content for a given user.
     * Uses SearchHistory.userId ownership to avoid cross-user data leaks.
     */
    @Query("SELECT sc FROM ScrapedContent sc JOIN sc.searchHistory sh WHERE sh.userId = :userId ORDER BY sc.createdAt DESC")
    List<ScrapedContent> findLatestByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId, Pageable pageable);
}
