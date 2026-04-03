package com.smartscraper.controller;

import com.smartscraper.dto.NewsDto;
import com.smartscraper.entity.News;
import com.smartscraper.entity.SearchHistory;
import com.smartscraper.entity.User;
import com.smartscraper.repository.NewsRepository;
import com.smartscraper.repository.SearchHistoryRepository;
import com.smartscraper.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/news")
public class ApiNewsController {

    private final UserService userService;
    private final SearchHistoryRepository searchHistoryRepository;
    private final NewsRepository newsRepository;

    public ApiNewsController(UserService userService,
                              SearchHistoryRepository searchHistoryRepository,
                              NewsRepository newsRepository) {
        this.userService = userService;
        this.searchHistoryRepository = searchHistoryRepository;
        this.newsRepository = newsRepository;
    }

    @GetMapping
    public List<NewsDto> news(
            @RequestParam("searchId") Long searchId,
            Principal principal) {

        User currentUser = userService.getCurrentUser(principal);

        SearchHistory history = searchHistoryRepository.findById(searchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SearchHistory not found"));

        if (!history.getUserId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You don't have permission to view this search");
        }

        List<News> items = newsRepository.findBySearchHistoryIdOrderByPublishedDateDesc(searchId);
        return items.stream().map(n -> {
            NewsDto dto = new NewsDto();
            dto.setId(n.getId());
            dto.setSearchHistoryId(n.getSearchHistoryId());
            dto.setTitle(n.getTitle());
            dto.setSource(n.getSource());
            dto.setPublishedDate(n.getPublishedDate());
            dto.setLink(n.getLink());
            dto.setSummary(n.getSummary());
            return dto;
        }).toList();
    }
}

