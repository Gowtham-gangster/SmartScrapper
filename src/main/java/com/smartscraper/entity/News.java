package com.smartscraper.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "news",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"search_history_id", "link"})
        },
        indexes = {
                @Index(name = "idx_news_search_history_id", columnList = "search_history_id"),
                @Index(name = "idx_news_published_date", columnList = "published_date")
        }
)
public class News {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "search_history_id", nullable = false)
    private Long searchHistoryId;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Lob
    @Column(name = "summary", columnDefinition = "TEXT", nullable = false)
    private String summary;

    @Column(name = "link", nullable = false, length = 2048)
    private String link;

    @Column(name = "source", nullable = false, length = 255)
    private String source;

    @Column(name = "published_date")
    private LocalDateTime publishedDate;

    @Lob
    @Column(name = "first_paragraph", columnDefinition = "TEXT")
    private String firstParagraph;

    @Column(name = "language", length = 50)
    private String language;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "relevance_score")
    private Integer relevanceScore;

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Relations (optional; used for joins/ownership queries)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "search_history_id", insertable = false, updatable = false)
    private SearchHistory searchHistory;

    public News() {
    }

    public News(Long searchHistoryId, String title, String summary, String link, String source, LocalDateTime publishedDate) {
        this.searchHistoryId = searchHistoryId;
        this.title = title;
        this.summary = summary;
        this.link = link;
        this.source = source;
        this.publishedDate = publishedDate;
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

    public Long getSearchHistoryId() {
        return searchHistoryId;
    }

    public void setSearchHistoryId(Long searchHistoryId) {
        this.searchHistoryId = searchHistoryId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LocalDateTime getPublishedDate() {
        return publishedDate;
    }

    public void setPublishedDate(LocalDateTime publishedDate) {
        this.publishedDate = publishedDate;
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

    public String getFirstParagraph() {
        return firstParagraph;
    }

    public void setFirstParagraph(String firstParagraph) {
        this.firstParagraph = firstParagraph;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getRelevanceScore() {
        return relevanceScore;
    }

    public void setRelevanceScore(Integer relevanceScore) {
        this.relevanceScore = relevanceScore;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}

