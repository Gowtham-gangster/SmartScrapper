package com.smartscraper.controller;

import com.smartscraper.dto.ApiResultsResponseDto;
import com.smartscraper.entity.User;
import com.smartscraper.service.ResultsService;
import com.smartscraper.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

@RestController
@RequestMapping("/api")
public class ApiResultsController {

    private static final Logger logger = LoggerFactory.getLogger(ApiResultsController.class);

    private final UserService userService;
    private final ResultsService resultsService;

    public ApiResultsController(UserService userService, ResultsService resultsService) {
        this.userService = userService;
        this.resultsService = resultsService;
    }

    @GetMapping("/results")
    public ApiResultsResponseDto results(
            @RequestParam("searchId") Long searchId,
            Principal principal) {

        User currentUser = userService.getCurrentUser(principal);

        if (searchId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "searchId is required");
        }

        logger.info("REST /api/results user={} searchId={}", currentUser.getId(), searchId);

        ApiResultsResponseDto response = resultsService.getResults(searchId, currentUser.getId());
        
        if (response == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Search results not found or access denied");
        }

        return response;
    }
}

