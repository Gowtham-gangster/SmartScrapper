package com.smartscraper.controller;

import com.smartscraper.dto.ProductDto;
import com.smartscraper.entity.Product;
import com.smartscraper.entity.SearchHistory;
import com.smartscraper.entity.User;
import com.smartscraper.repository.ProductRepository;
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
@RequestMapping("/api/products")
public class ApiProductsController {

    private final UserService userService;
    private final SearchHistoryRepository searchHistoryRepository;
    private final ProductRepository productRepository;

    public ApiProductsController(UserService userService,
                                  SearchHistoryRepository searchHistoryRepository,
                                  ProductRepository productRepository) {
        this.userService = userService;
        this.searchHistoryRepository = searchHistoryRepository;
        this.productRepository = productRepository;
    }

    @GetMapping
    public List<ProductDto> products(
            @RequestParam("searchId") Long searchId,
            Principal principal) {

        User currentUser = userService.getCurrentUser(principal);

        SearchHistory history = searchHistoryRepository.findById(searchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SearchHistory not found"));

        if (!history.getUserId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You don't have permission to view this search");
        }

        List<Product> items = productRepository.findBySearchHistoryIdOrderByPriceAsc(searchId);
        return items.stream().map(p -> {
            ProductDto dto = new ProductDto();
            dto.setId(p.getId());
            dto.setSearchHistoryId(p.getSearchHistoryId());
            dto.setProductName(p.getName());
            dto.setPrice(p.getPrice());
            dto.setCurrency(p.getCurrency());
            dto.setSource(p.getSource());
            dto.setUrl(p.getUrl());
            dto.setImageUrl(p.getImageUrl());
            dto.setRating(p.getRating());
            dto.setReviews(p.getReviews());
            dto.setCreatedAt(p.getCreatedAt());
            return dto;
        }).toList();
    }
}

