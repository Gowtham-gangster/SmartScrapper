package com.smartscraper.dto;

import java.util.List;

public class ApiResultsResponseDto {
    private String module;
    private List<NewsDto> news;
    private List<ProductDto> products;
    private List<ResearchContentDto> research;
    private List<CourseDto> courses;
    private List<SearchResultDto> searchResults;

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public List<NewsDto> getNews() {
        return news;
    }

    public void setNews(List<NewsDto> news) {
        this.news = news;
    }

    public List<ProductDto> getProducts() {
        return products;
    }

    public void setProducts(List<ProductDto> products) {
        this.products = products;
    }

    public List<ResearchContentDto> getResearch() {
        return research;
    }

    public void setResearch(List<ResearchContentDto> research) {
        this.research = research;
    }

    public List<SearchResultDto> getSearchResults() {
        return searchResults;
    }

    public void setSearchResults(List<SearchResultDto> searchResults) {
        this.searchResults = searchResults;
    }

    public List<CourseDto> getCourses() {
        return courses;
    }

    public void setCourses(List<CourseDto> courses) {
        this.courses = courses;
    }
}

