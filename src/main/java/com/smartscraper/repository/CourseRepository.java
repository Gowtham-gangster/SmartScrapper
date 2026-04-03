package com.smartscraper.repository;

import com.smartscraper.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Course entity.
 */
@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    /**
     * Find all courses for a specific search, ordered by relevance score descending.
     * 
     * @param searchHistoryId Search history ID
     * @return List of courses ordered by relevance
     */
    List<Course> findBySearchHistoryIdOrderByRelevanceScoreDesc(Long searchHistoryId);

    /**
     * Find top N courses for a specific search, ordered by relevance score descending.
     * 
     * @param searchHistoryId Search history ID
     * @param limit Maximum number of results
     * @return List of top courses
     */
    @Query("SELECT c FROM Course c WHERE c.searchHistoryId = :searchHistoryId ORDER BY c.relevanceScore DESC LIMIT :limit")
    List<Course> findTopBySearchHistoryIdOrderByRelevanceScore(@Param("searchHistoryId") Long searchHistoryId, @Param("limit") int limit);

    /**
     * Find courses by platform.
     * 
     * @param platform Platform name
     * @return List of courses
     */
    List<Course> findByPlatform(String platform);

    /**
     * Find courses with rating above threshold.
     * 
     * @param rating Minimum rating
     * @return List of courses
     */
    List<Course> findByRatingGreaterThanEqual(Double rating);

    /**
     * Count courses for a specific search.
     * 
     * @param searchHistoryId Search history ID
     * @return Count of courses
     */
    long countBySearchHistoryId(Long searchHistoryId);

    /**
     * Delete all courses for a specific search.
     * 
     * @param searchHistoryId Search history ID
     */
    void deleteBySearchHistoryId(Long searchHistoryId);
}
