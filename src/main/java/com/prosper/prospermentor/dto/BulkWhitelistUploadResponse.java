package com.prosper.prospermentor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO for bulk whitelist upload response
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkWhitelistUploadResponse {

    private int totalProcessed;
    private int successCount;
    private int failureCount;
    private int duplicateCount;
    private List<String> successEmails = new ArrayList<>();
    private List<WhitelistError> errors = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WhitelistError {
        private int row;
        private String email;
        private String reason;
    }
}
