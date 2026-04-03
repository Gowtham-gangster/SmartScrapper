package com.smartscraper.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "products",
        indexes = {
                @Index(name = "idx_products_search_history_id", columnList = "search_history_id"),
                @Index(name = "idx_products_price", columnList = "price")
        }
)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "search_history_id", nullable = false)
    private Long searchHistoryId;

    @Column(name = "name", nullable = false, length = 500)
    private String name;

    @Column(name = "price", precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "source", nullable = false, length = 255)
    private String source;

    @Column(name = "url", nullable = false, length = 2048)
    private String url;

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    @Column(name = "rating", length = 50)
    private String rating;

    @Column(name = "reviews", length = 50)
    private String reviews;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "search_history_id", insertable = false, updatable = false)
    private SearchHistory searchHistory;

    public Product() {
    }

    public Product(Long searchHistoryId, String name, BigDecimal price, String currency, String source, String url, String imageUrl) {
        this.searchHistoryId = searchHistoryId;
        this.name = name;
        this.price = price;
        this.currency = currency;
        this.source = source;
        this.url = url;
        this.imageUrl = imageUrl;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getRating() {
        return rating;
    }

    public void setRating(String rating) {
        this.rating = rating;
    }

    public String getReviews() {
        return reviews;
    }

    public void setReviews(String reviews) {
        this.reviews = reviews;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getFormattedPrice() {
        if (price == null) {
            return "N/A";
        }
        String c = currency != null ? currency : "";
        return c + " " + price.toString();
    }
}

