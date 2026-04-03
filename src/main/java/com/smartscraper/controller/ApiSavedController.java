package com.smartscraper.controller;

import com.smartscraper.dto.PagedResponse;
import com.smartscraper.dto.SavedContentDto;
import com.smartscraper.entity.SavedContent;
import com.smartscraper.entity.User;
import com.smartscraper.service.SavedContentService;
import com.smartscraper.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ApiSavedController {

    private static final Logger logger = LoggerFactory.getLogger(ApiSavedController.class);

    private final UserService userService;
    private final SavedContentService savedContentService;

    public ApiSavedController(UserService userService, SavedContentService savedContentService) {
        this.userService = userService;
        this.savedContentService = savedContentService;
    }

    @GetMapping("/saved")
    public PagedResponse<SavedContentDto> saved(
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "10") int size,
            Principal principal) {

        User currentUser = userService.getCurrentUser(principal);

        if (page < 0) page = 0;
        if (size < 1) size = 10;

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "savedAt"));
        Page<SavedContent> savedPage = savedContentService.getSavedContentWithDetails(currentUser.getId(), pageable);

        if (savedPage.isEmpty()) {
            return new PagedResponse<>(Collections.emptyList(), 0L, 0, page, size);
        }

        List<SavedContentDto> items = savedContentService.getSavedContentDtos(currentUser.getId(), pageable, savedPage);

        logger.info("REST /api/saved user={} page={} size={} total={}",
                currentUser.getId(), page, size, savedPage.getTotalElements());

        return new PagedResponse<>(items, savedPage.getTotalElements(), savedPage.getTotalPages(), page, size);
    }
}

