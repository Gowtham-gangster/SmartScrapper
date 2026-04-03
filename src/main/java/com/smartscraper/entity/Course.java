package com.smartscraper.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity representing a course from learning platforms.
 */
@Entity
@Table(
        name = "courses",
        indexes = {
                @Index(name = "idx_courses_search_history_id", columnList = "search_history_id"),
                @Index(name = "idx_courses_platform", columnList = "platform"),
                @Index(name = "idx_courses_relevance_score", columnList = "relevance_score"),
                @Index(name = "idx_courses_rating", columnList = "rating"),
                @Index(name = "idx_courses_created_at", columnList = "created_at")
        }
)
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "search_history_id", nullable = false)
    private Long searchHistoryId;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "platform", nullable = false, length = 100)
    private String platform;

    @Column(name = "instructor", length = 255)
    private String instructor;

    @Column(name = "duration", length = 100)
    private String duration;

    @Column(name = "duration_hours")
    private Double durationHours;

    @Column(name = "rating")
    private Double rating;

    @Column(name = "course_link", nullable = false, length = 2048)
    private String courseLink;

    @Lob
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "relevance_score")
    private Integer relevanceScore;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Many-to-one relationship with SearchHistory
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "search_history_id", insertable = false, updatable = false)
    private SearchHistory searchHistory;

    // Constructors
    public Course() {
    }

    public Course(Long searchHistoryId, String title, String platform, String courseLink) {
        this.searchHistoryId = searchHistoryId;
        this.title = title;
        this.platform = platform;
        this.courseLink = courseLink;
    }

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

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getInstructor() {
        return instructor;
    }

    public void setInstructor(String instructor) {
        this.instructor = instructor;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public Double getDurationHours() {
        return durationHours;
    }

    public void setDurationHours(Double durationHours) {
        this.durationHours = durationHours;
    }

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public String getCourseLink() {
        return courseLink;
    }

    public void setCourseLink(String courseLink) {
        this.courseLink = courseLink;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getRelevanceScore() {
        return relevanceScore;
    }

    public void setRelevanceScore(Integer relevanceScore) {
        this.relevanceScore = relevanceScore;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
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

    @Override
    public String toString() {
        return "Course{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", platform='" + platform + '\'' +
                ", rating=" + rating +
                ", relevanceScore=" + relevanceScore +
                '}';
    }
}
