package com.smartscraper.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "users",
        indexes = {
                @Index(name = "idx_users_username", columnList = "username"),
                @Index(name = "idx_users_email", columnList = "email")
        }
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", unique = true, nullable = false, length = 50)
    private String username;

    @Column(name = "email", unique = true, nullable = false, length = 100)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // One-to-many relationship with ScrapedData
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ScrapedData> scrapedDataList = new ArrayList<>();

    // One-to-many relationship with SearchHistory
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SearchHistory> searchHistoryList = new ArrayList<>();

    // One-to-many relationship with SavedContent
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SavedContent> savedContentList = new ArrayList<>();

    // Constructors
    public User() {
    }

    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<ScrapedData> getScrapedDataList() {
        return scrapedDataList;
    }

    public void setScrapedDataList(List<ScrapedData> scrapedDataList) {
        this.scrapedDataList = scrapedDataList;
    }

    public List<SearchHistory> getSearchHistoryList() {
        return searchHistoryList;
    }

    public void setSearchHistoryList(List<SearchHistory> searchHistoryList) {
        this.searchHistoryList = searchHistoryList;
    }

    public List<SavedContent> getSavedContentList() {
        return savedContentList;
    }

    public void setSavedContentList(List<SavedContent> savedContentList) {
        this.savedContentList = savedContentList;
    }

    // Helper method to add scraped data
    public void addScrapedData(ScrapedData scrapedData) {
        scrapedDataList.add(scrapedData);
        scrapedData.setUser(this);
    }

    // Helper method to remove scraped data
    public void removeScrapedData(ScrapedData scrapedData) {
        scrapedDataList.remove(scrapedData);
        scrapedData.setUser(null);
    }

    // Helper method to add search history
    public void addSearchHistory(SearchHistory searchHistory) {
        searchHistoryList.add(searchHistory);
        searchHistory.setUser(this);
    }

    // Helper method to remove search history
    public void removeSearchHistory(SearchHistory searchHistory) {
        searchHistoryList.remove(searchHistory);
        searchHistory.setUser(null);
    }

    // Helper method to add saved content
    public void addSavedContent(SavedContent savedContent) {
        savedContentList.add(savedContent);
        savedContent.setUser(this);
    }

    // Helper method to remove saved content
    public void removeSavedContent(SavedContent savedContent) {
        savedContentList.remove(savedContent);
        savedContent.setUser(null);
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return id != null && id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
