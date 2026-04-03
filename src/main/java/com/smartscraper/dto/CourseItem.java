package com.smartscraper.dto;

import java.time.LocalDateTime;

/**
 * DTO for course information scraped from learning platforms.
 */
public class CourseItem {
    private String title;
    private String platform;
    private String instructor;
    private String duration;
    private Double durationHours;
    private Double rating;
    private String courseLink;
    private String description;
    private Integer relevanceScore;
    private LocalDateTime lastUpdated;

    public CourseItem() {
    }

    public CourseItem(String title, String platform, String courseLink) {
        this.title = title;
        this.platform = platform;
        this.courseLink = courseLink;
    }

    // Getters and Setters
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

    @Override
    public String toString() {
        return "CourseItem{" +
                "title='" + title + '\'' +
                ", platform='" + platform + '\'' +
                ", rating=" + rating +
                ", relevanceScore=" + relevanceScore +
                '}';
    }
}
