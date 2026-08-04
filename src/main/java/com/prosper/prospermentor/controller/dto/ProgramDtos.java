package com.prosper.prospermentor.controller.dto;

import com.prosper.prospermentor.entity.Program;
import com.prosper.prospermentor.entity.ProgramMentor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTOs for program-related API operations
 */
public class ProgramDtos {

    /**
     * DTO for mentor information in program context
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProgramMentorDto {
        private UUID id;
        private String title;
        private String company;
        private Integer yearsExperience;
        private BigDecimal hourlyRate;
        private BigDecimal rating;
        private Integer totalSessions;
        private String bio;
        private String avatarUrl;
        private Boolean isAvailable;
    }

    /**
     * DTO for program with mentors
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProgramWithMentorsDto {
        private UUID id;
        private String legacyId;
        private String name;
        private String description;
        private String imageUrl;
        private String videoUrl;
        private Program.ProgramStatus status;
        private Integer orderId;
        private String[] tips;
        private String[] focusAreas;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private List<ProgramMentor> mentors;
    }
}