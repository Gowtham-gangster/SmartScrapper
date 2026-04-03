package com.smartscraper.dto;

import jakarta.validation.constraints.NotBlank;

public class ScrapeRequestDto {
    @NotBlank
    private String module;

    @NotBlank
    private String query;

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
}

