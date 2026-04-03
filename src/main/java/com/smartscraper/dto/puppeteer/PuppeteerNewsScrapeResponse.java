package com.smartscraper.dto.puppeteer;

import java.util.List;

public class PuppeteerNewsScrapeResponse {
    private List<PuppeteerNewsItemDto> items;
    private int page;
    private int maxPages;
    private int returnedCount;
    private boolean hasMore;

    public List<PuppeteerNewsItemDto> getItems() {
        return items;
    }

    public void setItems(List<PuppeteerNewsItemDto> items) {
        this.items = items;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getMaxPages() {
        return maxPages;
    }

    public void setMaxPages(int maxPages) {
        this.maxPages = maxPages;
    }

    public int getReturnedCount() {
        return returnedCount;
    }

    public void setReturnedCount(int returnedCount) {
        this.returnedCount = returnedCount;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }
}

