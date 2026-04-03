package com.smartscraper.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "search_history",
        indexes = {
                @Index(name = "idx_search_history_user_id", columnList = "user_id"),
                @Index(name = "idx_search_history_module_type", columnList = "module_type"),
                @Index(name = "idx_search_history_searched_at", columnList = "created_at")
        }
)
public class SearchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "module_type", nullable = false, length = 20)
    private ModuleType moduleType;

    @Column(name = "query_text", nullable = false, length = 1000)
    private String queryText;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime searchedAt;

    // Many-to-one relationship with User
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    // One-to-many relationship with ScrapedContent
    @OneToMany(mappedBy = "searchHistory", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ScrapedContent> scrapedContentList = new ArrayList<>();

    // Constructors
    public SearchHistory() {
    }

    public SearchHistory(Long userId, ModuleType moduleType, String queryText) {
        this.userId = userId;
        this.moduleType = moduleType;
        this.queryText = queryText;
        this.searchedAt = LocalDateTime.now();
    }

    // Lifecycle callback to set searchedAt before persisting
    @PrePersist
    protected void onCreate() {
        if (searchedAt == null) {
            searchedAt = LocalDateTime.now();
        }
    }

    // Getters and Setters
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

    public LocalDateTime getSearchedAt() {
        return searchedAt;
    }

    public void setSearchedAt(LocalDateTime searchedAt) {
        this.searchedAt = searchedAt;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public List<ScrapedContent> getScrapedContentList() {
        return scrapedContentList;
    }

    public void setScrapedContentList(List<ScrapedContent> scrapedContentList) {
        this.scrapedContentList = scrapedContentList;
    }

    // Helper method to add scraped content
    public void addScrapedContent(ScrapedContent scrapedContent) {
        scrapedContentList.add(scrapedContent);
        scrapedContent.setSearchHistory(this);
    }

    // Helper method to remove scraped content
    public void removeScrapedContent(ScrapedContent scrapedContent) {
        scrapedContentList.remove(scrapedContent);
        scrapedContent.setSearchHistory(null);
    }

    @Override
    public String toString() {
        return "SearchHistory{" +
                "id=" + id +
                ", userId=" + userId +
                ", moduleType=" + moduleType +
                ", queryText='" + queryText + '\'' +
                ", searchedAt=" + searchedAt +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SearchHistory that = (SearchHistory) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
