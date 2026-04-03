package com.smartscraper.dto;

import java.util.List;

public class PagedResponse<T> {
    private List<T> items;
    private long totalElements;
    private int totalPages;
    private int page;
    private int size;

    public PagedResponse() {
    }

    public PagedResponse(List<T> items, long totalElements, int totalPages, int page, int size) {
        this.items = items;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.page = page;
        this.size = size;
    }

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}

