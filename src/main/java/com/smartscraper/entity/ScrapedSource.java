package com.smartscraper.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "research_papers",
        indexes = {
                @Index(name = "idx_research_papers_domain_name", columnList = "domain_name"),
                @Index(name = "idx_research_papers_source_url", columnList = "source_url"),
                @Index(name = "idx_research_papers_year", columnList = "paper_year")
        }
)
public class ScrapedSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "domain_name", nullable = false, length = 255)
    private String domainName;

    @Column(name = "source_url", nullable = false, length = 2048, unique = true)
    private String sourceUrl;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "authors", columnDefinition = "TEXT")
    private String authors;

    @Column(name = "paper_year")
    private Integer year;

    @Column(name = "abstract_text", columnDefinition = "TEXT")
    private String paperAbstract;

    @Column(name = "created_at", nullable = false, updatable = false)
    private java.time.LocalDateTime createdAt;

    // One-to-many relationship with ScrapedContent
    @OneToMany(mappedBy = "scrapedSource", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ScrapedContent> scrapedContentList = new ArrayList<>();

    // Constructors
    public ScrapedSource() {
    }

    public ScrapedSource(String domainName, String sourceUrl) {
        this.domainName = domainName;
        this.sourceUrl = sourceUrl;
    }

    // Lifecycle callback to set createdAt before persisting
    @jakarta.persistence.PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = java.time.LocalDateTime.now();
        }
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDomainName() {
        return domainName;
    }

    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public java.time.LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.time.LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthors() {
        return authors;
    }

    public void setAuthors(String authors) {
        this.authors = authors;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getPaperAbstract() {
        return paperAbstract;
    }

    public void setPaperAbstract(String paperAbstract) {
        this.paperAbstract = paperAbstract;
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
        scrapedContent.setScrapedSource(this);
    }

    // Helper method to remove scraped content
    public void removeScrapedContent(ScrapedContent scrapedContent) {
        scrapedContentList.remove(scrapedContent);
        scrapedContent.setScrapedSource(null);
    }

    @Override
    public String toString() {
        return "ScrapedSource{" +
                "id=" + id +
                ", domainName='" + domainName + '\'' +
                ", sourceUrl='" + sourceUrl + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScrapedSource that = (ScrapedSource) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
