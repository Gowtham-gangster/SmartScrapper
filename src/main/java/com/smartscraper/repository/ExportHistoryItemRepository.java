package com.smartscraper.repository;

import com.smartscraper.entity.ExportHistoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExportHistoryItemRepository extends JpaRepository<ExportHistoryItem, Long> {
}

