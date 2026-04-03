package com.smartscraper.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "export_history_items",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"export_history_id", "scraped_data_id"})
        },
        indexes = {
                @Index(name = "idx_export_history_items_export_history_id", columnList = "export_history_id"),
                @Index(name = "idx_export_history_items_scraped_data_id", columnList = "scraped_data_id")
        }
)
public class ExportHistoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "export_history_id", nullable = false)
    private Long exportHistoryId;

    // Export items are tied to the persisted research content blocks (scraped_data)
    @Column(name = "scraped_data_id", nullable = false)
    private Long scrapedDataId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "export_history_id", insertable = false, updatable = false)
    private ExportHistory exportHistory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scraped_data_id", insertable = false, updatable = false)
    private ScrapedContent scrapedContent;

    public ExportHistoryItem() {
    }

    public ExportHistoryItem(Long exportHistoryId, Long scrapedDataId) {
        this.exportHistoryId = exportHistoryId;
        this.scrapedDataId = scrapedDataId;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getExportHistoryId() {
        return exportHistoryId;
    }

    public void setExportHistoryId(Long exportHistoryId) {
        this.exportHistoryId = exportHistoryId;
    }

    public Long getScrapedDataId() {
        return scrapedDataId;
    }

    public void setScrapedDataId(Long scrapedDataId) {
        this.scrapedDataId = scrapedDataId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

