package com.smartscraper.repository;

import com.smartscraper.entity.ScrapedData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScrapedDataRepository extends JpaRepository<ScrapedData, Long> {
    
    /**
     * Find all scraped data for a specific user.
     * 
     * @param userId The user ID
     * @return List of scraped data belonging to the user
     */
    List<ScrapedData> findByUserId(Long userId);
    
    /**
     * Find all scraped data for a specific user, ordered by scraped date descending.
     * 
     * @param userId The user ID
     * @return List of scraped data ordered by most recent first
     */
    List<ScrapedData> findByUserIdOrderByScrapedAtDesc(Long userId);
    
    /**
     * Count total scraped records for a user.
     * 
     * @param userId The user ID
     * @return Count of scraped records
     */
    long countByUserId(Long userId);

    /**
     * Ownership-aware fetch for bulk export.
     */
    List<ScrapedData> findByIdInAndUser_Id(List<Long> ids, Long userId);
}
