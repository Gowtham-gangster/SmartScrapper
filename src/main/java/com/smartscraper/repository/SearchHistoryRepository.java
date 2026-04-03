package com.smartscraper.repository;

import com.smartscraper.entity.ModuleType;
import com.smartscraper.entity.SearchHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {
    
    /**
     * Find all search history for a specific user.
     * 
     * @param userId The user ID
     * @return List of search history entries
     */
    List<SearchHistory> findByUserId(Long userId);
    
    /**
     * Find all search history for a user, ordered by most recent first.
     * 
     * @param userId The user ID
     * @return List of search history ordered by searchedAt descending
     */
    List<SearchHistory> findByUserIdOrderBySearchedAtDesc(Long userId);
    
    /**
     * Find search history for a user by module type.
     * 
     * @param userId The user ID
     * @param moduleType The module type
     * @return List of search history for the specified module
     */
    List<SearchHistory> findByUserIdAndModuleType(Long userId, ModuleType moduleType);
    
    /**
     * Find search history for a user within a date range.
     * 
     * @param userId The user ID
     * @param startDate Start date
     * @param endDate End date
     * @return List of search history within the date range
     */
    List<SearchHistory> findByUserIdAndSearchedAtBetween(Long userId, LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Count total searches for a user.
     * 
     * @param userId The user ID
     * @return Count of search history entries
     */
    long countByUserId(Long userId);
    
    /**
     * Count searches for a user by module type.
     * 
     * @param userId The user ID
     * @param moduleType The module type
     * @return Count of searches for the module
     */
    long countByUserIdAndModuleType(Long userId, ModuleType moduleType);
    
    /**
     * Delete all search history for a user.
     * 
     * @param userId The user ID
     */
    void deleteByUserId(Long userId);
    
    /**
     * Find recent search history for a user (limit results).
     * 
     * @param userId The user ID
     * @return List of recent search history (top 10)
     */
    List<SearchHistory> findTop10ByUserIdOrderBySearchedAtDesc(Long userId);

    /**
     * Find top N search history records for a user and module type, ordered by date descending.
     * 
     * @param userId User ID
     * @param moduleType Module type
     * @return List of search history records
     */
    List<SearchHistory> findTop10ByUserIdAndModuleTypeOrderBySearchedAtDesc(Long userId, ModuleType moduleType);

    /**
     * Find search history for a user by module type, ordered by most recent first.
     * 
     * @param userId The user ID
     * @param moduleType The module type
     * @return List of search history ordered by searchedAt descending
     */
    List<SearchHistory> findByUserIdAndModuleTypeOrderBySearchedAtDesc(Long userId, ModuleType moduleType);

    Page<SearchHistory> findByUserIdOrderBySearchedAtDesc(Long userId, Pageable pageable);

    Page<SearchHistory> findByUserIdAndModuleTypeOrderBySearchedAtDesc(Long userId, ModuleType moduleType, Pageable pageable);
}
