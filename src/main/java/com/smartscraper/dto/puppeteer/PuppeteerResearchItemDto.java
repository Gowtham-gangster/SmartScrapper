package com.smartscraper.dto.puppeteer;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PuppeteerResearchItemDto {
    private String title;
    private String authors;
    private String year;

    // JSON field name is "abstract" (reserved keyword in Java), so we map it explicitly.
    @JsonProperty("abstract")
    private String abstractText;
    private String link;

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

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public String getAbstract() {
        return abstractText;
    }

    public void setAbstract(String abstractText) {
        this.abstractText = abstractText;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }
}

