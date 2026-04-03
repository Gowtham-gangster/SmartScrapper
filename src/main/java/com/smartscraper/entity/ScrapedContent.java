package com.smartscraper.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "scraped_data",
        indexes = {
                @Index(name = "idx_scraped_data_search_id", columnList = "search_id"),
                @Index(name = "idx_scraped_data_source_id", columnList = "source_id"),
                @Index(name = "idx_scraped_data_relevance_score", columnList = "relevance_score"),
                @Index(name = "idx_scraped_data_created_at", columnList = "created_at")
        }
)
public class ScrapedContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "search_id", nullable = false)
    private Long searchId;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Lob
    @Column(name = "content_text", columnDefinition = "TEXT", nullable = false)
    private String contentText;

    @Column(name = "relevance_score")
    private Double relevanceScore;

    @Column(name = "relevance_score_int")
    private Integer relevanceScoreInt; // Enhanced scoring (0-100)

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Many-to-one relationship with SearchHistory
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "search_id", insertable = false, updatable = false)
    private SearchHistory searchHistory;

    // Many-to-one relationship with ScrapedSource
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", insertable = false, updatable = false)
    private ScrapedSource scrapedSource;

    // One-to-many relationship with SavedContent
    @OneToMany(mappedBy = "scrapedContent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SavedContent> savedByUsers = new ArrayList<>();

    // Constructors
    public ScrapedContent() {
    }

    public ScrapedContent(Long searchId, Long sourceId, String contentText) {
        this.searchId = searchId;
        this.sourceId = sourceId;
        this.contentText = contentText;
        this.createdAt = LocalDateTime.now();
    }

    public ScrapedContent(Long searchId, Long sourceId, String contentText, Double relevanceScore) {
        this.searchId = searchId;
        this.sourceId = sourceId;
        this.contentText = contentText;
        this.relevanceScore = relevanceScore;
        this.createdAt = LocalDateTime.now();
    }

    // Lifecycle callback to set createdAt before persisting
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSearchId() {
        return searchId;
    }

    public void setSearchId(Long searchId) {
        this.searchId = searchId;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public String getContentText() {
        return contentText;
    }

    public void setContentText(String contentText) {
        this.contentText = contentText;
    }

    public Double getRelevanceScore() {
        return relevanceScore;
    }

    public void setRelevanceScore(Double relevanceScore) {
        this.relevanceScore = relevanceScore;
    }

    public Integer getRelevanceScoreInt() {
        return relevanceScoreInt;
    }

    public void setRelevanceScoreInt(Integer relevanceScoreInt) {
        this.relevanceScoreInt = relevanceScoreInt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public SearchHistory getSearchHistory() {
        return searchHistory;
    }

    public void setSearchHistory(SearchHistory searchHistory) {
        this.searchHistory = searchHistory;
    }

    public ScrapedSource getScrapedSource() {
        return scrapedSource;
    }

    public void setScrapedSource(ScrapedSource scrapedSource) {
        this.scrapedSource = scrapedSource;
    }

    public List<SavedContent> getSavedByUsers() {
        return savedByUsers;
    }

    public void setSavedByUsers(List<SavedContent> savedByUsers) {
        this.savedByUsers = savedByUsers;
    }

    // Helper method to add saved content
    public void addSavedContent(SavedContent savedContent) {
        savedByUsers.add(savedContent);
        savedContent.setScrapedContent(this);
    }

    // Helper method to remove saved content
    public void removeSavedContent(SavedContent savedContent) {
        savedByUsers.remove(savedContent);
        savedContent.setScrapedContent(null);
    }

    @Override
    public String toString() {
        return "ScrapedContent{" +
                "id=" + id +
                ", searchId=" + searchId +
                ", sourceId=" + sourceId +
                ", contentText='" + (contentText != null ? contentText.substring(0, Math.min(50, contentText.length())) + "..." : "null") + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScrapedContent that = (ScrapedContent) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
