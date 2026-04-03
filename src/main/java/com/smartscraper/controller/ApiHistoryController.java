package com.smartscraper.controller;

import com.smartscraper.dto.PagedResponse;
import com.smartscraper.dto.SearchHistoryDto;
import com.smartscraper.entity.ModuleType;
import com.smartscraper.entity.SearchHistory;
import com.smartscraper.entity.User;
import com.smartscraper.repository.SearchHistoryRepository;
import com.smartscraper.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ApiHistoryController {

    private final UserService userService;
    private final SearchHistoryRepository searchHistoryRepository;

    public ApiHistoryController(UserService userService, SearchHistoryRepository searchHistoryRepository) {
        this.userService = userService;
        this.searchHistoryRepository = searchHistoryRepository;
    }

    @GetMapping("/history")
    @Transactional(readOnly = true)
    public PagedResponse<SearchHistoryDto> history(
            @RequestParam(value = "module", required = false) String module,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "10") int size,
            Principal principal) {

        User currentUser = userService.getCurrentUser(principal);
        if (page < 0) page = 0;
        if (size < 1) size = 10;

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "searchedAt"));

        Page<SearchHistory> resultPage;
        if (module == null || module.isBlank() || module.equalsIgnoreCase("ALL")) {
            resultPage = searchHistoryRepository.findByUserIdOrderBySearchedAtDesc(currentUser.getId(), pageable);
        } else {
            ModuleType moduleType = parseModuleType(module);
            if (moduleType == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid module");
            }
            resultPage = searchHistoryRepository.findByUserIdAndModuleTypeOrderBySearchedAtDesc(
                    currentUser.getId(), moduleType, pageable);
        }

        List<SearchHistoryDto> items = resultPage.getContent().stream().map(h -> {
            SearchHistoryDto dto = new SearchHistoryDto();
            dto.setId(h.getId());
            dto.setUserId(h.getUserId());
            dto.setModule(h.getModuleType().name());
            dto.setKeyword(h.getQueryText());
            dto.setSearchedAt(h.getSearchedAt());
            return dto;
        }).toList();

        return new PagedResponse<>(items, resultPage.getTotalElements(), resultPage.getTotalPages(), page, size);
    }

    private ModuleType parseModuleType(String module) {
        if (module == null) return null;
        String s = module.trim();
        String norm = s.replaceAll("[^A-Za-z]", "").toUpperCase();
        for (ModuleType mt : ModuleType.values()) {
            if (mt.name().equals(norm)) return mt;
            if (mt.getDisplayName().equalsIgnoreCase(s)) return mt;
        }
        if (s.equalsIgnoreCase("Normal")) return ModuleType.SEARCH;
        return null;
    }
}

