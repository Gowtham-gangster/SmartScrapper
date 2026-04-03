package com.smartscraper.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "scheduled_jobs",
        indexes = {
                @Index(name = "idx_scheduled_jobs_module_type", columnList = "module_type"),
                @Index(name = "idx_scheduled_jobs_status", columnList = "status"),
                @Index(name = "idx_scheduled_jobs_started_at", columnList = "started_at"),
        }
)
public class ScheduledJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "module_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ModuleType moduleType;

    @Column(name = "query_text", nullable = false, length = 1000)
    private String queryText;

    @Column(name = "search_history_id")
    private Long searchHistoryId;

    @Column(name = "status", nullable = false, length = 20)
    private String status; // RUNNING, SUCCESS, FAILED, SKIPPED

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "result_count", nullable = false)
    private Integer resultCount = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ScheduledJob() {
    }

    public ScheduledJob(ModuleType moduleType, String queryText, String status) {
        this.moduleType = moduleType;
        this.queryText = queryText;
        this.status = status;
        this.startedAt = LocalDateTime.now();
        this.createdAt = this.startedAt;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
        if (resultCount == null) {
            resultCount = 0;
        }
    }

    public Long getId() {
        return id;
    }

    public ModuleType getModuleType() {
        return moduleType;
    }

    public void setModuleType(ModuleType moduleType) {
        this.moduleType = moduleType;
    }

    public String getQueryText() {
        return queryText;
    }

    public void setQueryText(String queryText) {
        this.queryText = queryText;
    }

    public Long getSearchHistoryId() {
        return searchHistoryId;
    }

    public void setSearchHistoryId(Long searchHistoryId) {
        this.searchHistoryId = searchHistoryId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Integer getResultCount() {
        return resultCount;
    }

    public void setResultCount(Integer resultCount) {
        this.resultCount = resultCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

