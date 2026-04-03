package com.smartscraper.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "export_history",
        indexes = {
                @Index(name = "idx_export_history_user_id", columnList = "user_id"),
                @Index(name = "idx_export_history_exported_at", columnList = "exported_at")
        }
)
public class ExportHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "export_type", nullable = false, length = 4)
    private String exportType; // CSV or PDF

    // Requirement expects: News, Ecommerce, Research, Normal
    @Column(name = "module", nullable = false, length = 20)
    private String module;

    @Column(name = "keyword", nullable = false, length = 1000)
    private String keyword;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "exported_at", nullable = false, updatable = false)
    private LocalDateTime exportedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    public ExportHistory() {
    }

    public ExportHistory(Long userId, String module, String exportType, String keyword, String fileName) {
        this.userId = userId;
        this.module = module;
        this.exportType = exportType;
        this.keyword = keyword;
        this.fileName = fileName;
    }

    @PrePersist
    protected void onCreate() {
        if (exportedAt == null) {
            exportedAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getExportType() {
        return exportType;
    }

    public void setExportType(String exportType) {
        this.exportType = exportType;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public LocalDateTime getExportedAt() {
        return exportedAt;
    }

    public void setExportedAt(LocalDateTime exportedAt) {
        this.exportedAt = exportedAt;
    }
}

