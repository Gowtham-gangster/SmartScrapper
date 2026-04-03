package com.smartscraper.repository;

import com.smartscraper.entity.ExportHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExportHistoryRepository extends JpaRepository<ExportHistory, Long> {

    Page<ExportHistory> findByUserIdOrderByExportedAtDesc(Long userId, Pageable pageable);
}

