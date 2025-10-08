package com.prosper.prospermentor.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Standard API response wrapper for consistent response format
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    
    /**
     * Response status - success, error, etc.
     */
    private String status;
    
    /**
     * Human-readable message
     */
    private String message;
    
    /**
     * Response data payload
     */
    private T data;
    
    /**
     * Error details (only present on error responses)
     */
    private ErrorDetails error;
    
    /**
     * Timestamp of the response
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    /**
     * Request tracking ID (optional)
     */
    private String requestId;
    
    /**
     * Pagination metadata (for paginated responses)
     */
    private PaginationMeta pagination;
    
    /**
     * Create a successful response with data
     */
    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.status = "success";
        response.message = "Request completed successfully";
        response.data = data;
        response.timestamp = LocalDateTime.now();
        return response;
    }
    
    /**
     * Create a successful response with data and custom message
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.status = "success";
        response.message = message;
        response.data = data;
        response.timestamp = LocalDateTime.now();
        return response;
    }
    
    /**
     * Create a successful response with just a message
     */
    public static <T> ApiResponse<T> success(String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.status = "success";
        response.message = message;
        response.timestamp = LocalDateTime.now();
        return response;
    }
    
    /**
     * Create an error response
     */
    public static <T> ApiResponse<T> error(String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.status = "error";
        response.message = message;
        response.timestamp = LocalDateTime.now();
        return response;
    }
    
    /**
     * Create an error response with details
     */
    public static <T> ApiResponse<T> error(String message, ErrorDetails errorDetails) {
        ApiResponse<T> response = new ApiResponse<>();
        response.status = "error";
        response.message = message;
        response.error = errorDetails;
        response.timestamp = LocalDateTime.now();
        return response;
    }
    
    /**
     * Create a validation error response
     */
    public static <T> ApiResponse<T> validationError(String message, ErrorDetails errorDetails) {
        ApiResponse<T> response = new ApiResponse<>();
        response.status = "validation_error";
        response.message = message;
        response.error = errorDetails;
        response.timestamp = LocalDateTime.now();
        return response;
    }
    
    /**
     * Create a paginated success response
     */
    public static <T> ApiResponse<T> success(T data, PaginationMeta pagination) {
        ApiResponse<T> response = new ApiResponse<>();
        response.status = "success";
        response.message = "Request completed successfully";
        response.data = data;
        response.pagination = pagination;
        response.timestamp = LocalDateTime.now();
        return response;
    }
    
    /**
     * Error details structure
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorDetails {
        private String code;
        private String field;
        private Object rejectedValue;
        private String details;
    }
    
    /**
     * Pagination metadata
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaginationMeta {
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        private boolean first;
        private boolean last;
        private boolean hasNext;
        private boolean hasPrevious;
    }
}
