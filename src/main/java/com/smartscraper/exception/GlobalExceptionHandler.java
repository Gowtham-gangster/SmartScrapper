package com.smartscraper.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ScrapingException.class)
    public String handleScraping(ScrapingException ex, HttpServletRequest request, Model model) {
        logger.error("MVC scraping error on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        model.addAttribute("errorCode", "502");
        model.addAttribute("errorMessage", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public String handleBadRequest(IllegalArgumentException ex, HttpServletRequest request, Model model) {
        logger.warn("MVC bad request on {}: {}", request.getRequestURI(), ex.getMessage());
        model.addAttribute("errorCode", "400");
        model.addAttribute("errorMessage", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public String handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request, Model model) {
        logger.warn("MVC resource not found on {}: {}", request.getRequestURI(), ex.getMessage());
        model.addAttribute("errorCode", "404");
        model.addAttribute("errorMessage", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(ValidationException.class)
    public String handleValidation(ValidationException ex, HttpServletRequest request, Model model) {
        logger.warn("MVC validation error on {}: {}", request.getRequestURI(), ex.getMessage());
        model.addAttribute("errorCode", "400");
        model.addAttribute("errorMessage", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(Exception.class)
    public String handleGeneric(Exception ex, HttpServletRequest request, Model model) {
        logger.error("MVC unexpected error on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        model.addAttribute("errorCode", "500");
        model.addAttribute("errorMessage", "An unexpected error occurred. Please try again later.");
        return "error";
    }
}

