package com.smartscraper.exception;

import java.time.Instant;

public class ApiErrorResponse {
    private String error;
    private String message;
    private int status;
    private Instant timestamp;
    private String path;

    public ApiErrorResponse() {
    }

    public ApiErrorResponse(String error, String message, int status, Instant timestamp, String path) {
        this.error = error;
        this.message = message;
        this.status = status;
        this.timestamp = timestamp;
        this.path = path;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}

