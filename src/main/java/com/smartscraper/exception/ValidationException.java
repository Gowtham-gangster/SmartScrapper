package com.smartscraper.exception;

public class ValidationException extends RuntimeException {
    
    private String redirectUrl;
    
    public ValidationException(String message) {
        super(message);
    }
    
    public ValidationException(String message, String redirectUrl) {
        super(message);
        this.redirectUrl = redirectUrl;
    }
    
    public String getRedirectUrl() {
        return redirectUrl;
    }
}
