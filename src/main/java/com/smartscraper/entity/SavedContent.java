package com.smartscraper.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "saved_content",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"user_id", "content_id"})
        },
        indexes = {
                @Index(name = "idx_saved_content_user_id", columnList = "user_id"),
                @Index(name = "idx_saved_content_content_id", columnList = "content_id"),
                @Index(name = "idx_saved_content_saved_at", columnList = "saved_at")
        }
)
public class SavedContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime savedAt;

    // Many-to-one relationship with User
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    // Many-to-one relationship with ScrapedContent
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id", insertable = false, updatable = false)
    private ScrapedContent scrapedContent;

    // Constructors
    public SavedContent() {
    }

    public SavedContent(Long userId, Long contentId) {
        this.userId = userId;
        this.contentId = contentId;
        this.savedAt = LocalDateTime.now();
    }

    // Lifecycle callback to set savedAt before persisting
    @PrePersist
    protected void onCreate() {
        if (savedAt == null) {
            savedAt = LocalDateTime.now();
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

    public Long getContentId() {
        return contentId;
    }

    public void setContentId(Long contentId) {
        this.contentId = contentId;
    }

    public LocalDateTime getSavedAt() {
        return savedAt;
    }

    public void setSavedAt(LocalDateTime savedAt) {
        this.savedAt = savedAt;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public ScrapedContent getScrapedContent() {
        return scrapedContent;
    }

    public void setScrapedContent(ScrapedContent scrapedContent) {
        this.scrapedContent = scrapedContent;
    }

    @Override
    public String toString() {
        return "SavedContent{" +
                "id=" + id +
                ", userId=" + userId +
                ", contentId=" + contentId +
                ", savedAt=" + savedAt +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SavedContent that = (SavedContent) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
