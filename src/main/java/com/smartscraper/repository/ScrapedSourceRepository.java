package com.smartscraper.repository;

import com.smartscraper.entity.ScrapedSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScrapedSourceRepository extends JpaRepository<ScrapedSource, Long> {
    
    /**
     * Find a scraped source by URL.
     * 
     * @param sourceUrl The source URL
     * @return Optional containing the source if found
     */
    Optional<ScrapedSource> findBySourceUrl(String sourceUrl);
    
    /**
     * Find all sources by domain name.
     * 
     * @param domainName The domain name
     * @return List of sources from the domain
     */
    List<ScrapedSource> findByDomainName(String domainName);
    
    /**
     * Find sources by domain name containing a string (case-insensitive).
     * 
     * @param domainName The domain name pattern
     * @return List of matching sources
     */
    List<ScrapedSource> findByDomainNameContainingIgnoreCase(String domainName);
    
    /**
     * Check if a source URL already exists.
     * 
     * @param sourceUrl The source URL
     * @return true if exists, false otherwise
     */
    boolean existsBySourceUrl(String sourceUrl);
    
    /**
     * Count sources by domain name.
     * 
     * @param domainName The domain name
     * @return Count of sources from the domain
     */
    long countByDomainName(String domainName);
    
    /**
     * Find all distinct domain names.
     * 
     * @return List of distinct domain names
     */
    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT s.domainName FROM ScrapedSource s ORDER BY s.domainName")
    List<String> findDistinctDomainNames();
}
