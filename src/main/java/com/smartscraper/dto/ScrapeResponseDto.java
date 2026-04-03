package com.smartscraper.dto;

public class ScrapeResponseDto {
    private Long searchHistoryId;
    private String module;
    private String query;
    private String status;
    private int resultsCount;

    public ScrapeResponseDto() {
    }

    public ScrapeResponseDto(Long searchHistoryId, String module, String query, String status, int resultsCount) {
        this.searchHistoryId = searchHistoryId;
        this.module = module;
        this.query = query;
        this.status = status;
        this.resultsCount = resultsCount;
    }

    public Long getSearchHistoryId() {
        return searchHistoryId;
    }

    public void setSearchHistoryId(Long searchHistoryId) {
        this.searchHistoryId = searchHistoryId;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getResultsCount() {
        return resultsCount;
    }

    public void setResultsCount(int resultsCount) {
        this.resultsCount = resultsCount;
    }
}

