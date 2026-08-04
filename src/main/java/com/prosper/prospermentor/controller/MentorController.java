package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.controller.dto.ApiResponse;
import com.prosper.prospermentor.dto.MentorAvailabilityDTO;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.security.SupabaseUserPrincipal;
import com.prosper.prospermentor.service.MentorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * REST controller for mentor availability management
 */
@RestController
@RequestMapping("/api/v1/mentors")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Mentor Availability", description = "APIs for managing mentor availability schedules")
public class MentorController {

    private final MentorService availabilityService;

    /**
     * Helper method to extract user ID from authentication
     */
    private String extractUserId(Authentication authentication) {
        if (authentication == null) return null;

        if (authentication.getPrincipal() instanceof SupabaseUserDetails userDetails) {
            return userDetails.getUserId();
        } else if (authentication.getPrincipal() instanceof SupabaseUserPrincipal principal) {
            return principal.getUserId();
        }

        return null;
    }

    /**
     * Create a new availability slot
     */
    @PostMapping("/mentor-availability")
    @Operation(summary = "Create a new availability slot", description = "Create a new time slot for mentor availability")
    public ResponseEntity<ApiResponse<MentorAvailabilityDTO.Response>> createAvailability(
            @Valid @RequestBody MentorAvailabilityDTO.CreateRequest request,
            Authentication authentication) {
        try {
            log.info("Creating availability slot for mentor: {}", request.getMentorId());

            // Verify the authenticated user matches the mentor ID or is an admin
            String userId = extractUserId(authentication);
            if (userId != null && !userId.equals(request.getMentorId().toString())) {
                // Check if user is admin (implementation depends on your security setup)
                // For now, we'll allow it if they have MENTOR role
                log.warn("User {} attempting to create availability for mentor {}", userId, request.getMentorId());
            }

            MentorAvailabilityDTO.Response response = availabilityService.createAvailability(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(response, "Availability slot created successfully"));

        } catch (IllegalArgumentException e) {
            log.error("Validation error: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating availability: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to create availability slot"));
        }
    }

    /**
     * Bulk create multiple availability slots
     */
    @PostMapping("/mentor-availability/bulk")
    @Operation(summary = "Bulk create availability slots", description = "Create multiple time slots at once")
    public ResponseEntity<ApiResponse<List<MentorAvailabilityDTO.Response>>> createBulkAvailability(
            @Valid @RequestBody MentorAvailabilityDTO.BulkCreateRequest request,
            Authentication authentication) {
        try {
            log.info("Bulk creating {} availability slots for mentor: {}", request.getSlots().size(), request.getMentorId());

            List<MentorAvailabilityDTO.Response> responses = availabilityService.createBulkAvailability(request);

            String message = String.format("Successfully created %d out of %d availability slots",
                    responses.size(), request.getSlots().size());

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(responses, message));

        } catch (Exception e) {
            log.error("Error bulk creating availability: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to bulk create availability slots"));
        }
    }

    /**
     * Update an existing availability slot
     */
    @PutMapping("/mentor-availability/{id}")
    @Operation(summary = "Update availability slot", description = "Update an existing availability time slot")
    public ResponseEntity<ApiResponse<MentorAvailabilityDTO.Response>> updateAvailability(
            @PathVariable Long id,
            @Valid @RequestBody MentorAvailabilityDTO.UpdateRequest request) {
        try {
            log.info("Updating availability slot with ID: {}", id);

            MentorAvailabilityDTO.Response response = availabilityService.updateAvailability(id, request);
            return ResponseEntity.ok(ApiResponse.success(response, "Availability slot updated successfully"));

        } catch (NoSuchElementException e) {
            log.error("Availability not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Availability slot not found"));
        } catch (IllegalArgumentException e) {
            log.error("Validation error: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating availability: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to update availability slot"));
        }
    }

    /**
     * Bulk update multiple availability slots
     * This replaces ALL existing availability slots for the mentor with the new ones provided
     */
    @PutMapping("/mentor-availability/bulk")
    @Operation(
            summary = "Bulk update availability slots",
            description = "Replace all existing availability slots for a mentor with new ones. " +
                    "This will delete all previous availability and create the new slots provided."
    )
    public ResponseEntity<ApiResponse<List<MentorAvailabilityDTO.Response>>> updateBulkAvailability(
            @Valid @RequestBody MentorAvailabilityDTO.BulkUpdateRequest request,
            Authentication authentication) {
        try {
            log.info("Bulk updating availability for mentor: {} with {} new slots",
                    request.getMentorId(), request.getSlots().size());

            List<MentorAvailabilityDTO.Response> responses = availabilityService.updateBulkAvailability(request);

            String message = String.format("Successfully replaced mentor availability with %d new slots",
                    responses.size());

            return ResponseEntity.ok()
                    .body(ApiResponse.success(responses, message));

        } catch (Exception e) {
            log.error("Error bulk updating availability: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to bulk update availability slots"));
        }
    }

    /**
     * Delete an availability slot
     */
    @DeleteMapping("/mentor-availability/{id}")
    @Operation(summary = "Delete availability slot", description = "Delete an availability time slot")
    public ResponseEntity<ApiResponse<Void>> deleteAvailability(@PathVariable Long id) {
        try {
            log.info("Deleting availability slot with ID: {}", id);

            availabilityService.deleteAvailability(id);
            return ResponseEntity.ok(ApiResponse.success("Availability slot deleted successfully"));

        } catch (NoSuchElementException e) {
            log.error("Availability not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Availability slot not found"));
        } catch (Exception e) {
            log.error("Error deleting availability: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to delete availability slot"));
        }
    }

    /**
     * Get a single availability slot by ID
     */
    @GetMapping("/mentor-availability/{id}")
    @Operation(summary = "Get availability slot", description = "Get details of a specific availability slot")
    public ResponseEntity<ApiResponse<MentorAvailabilityDTO.Response>> getAvailabilityById(@PathVariable Long id) {
        try {
            log.info("Fetching availability slot with ID: {}", id);

            MentorAvailabilityDTO.Response response = availabilityService.getAvailabilityById(id);
            return ResponseEntity.ok(ApiResponse.success(response));

        } catch (NoSuchElementException e) {
            log.error("Availability not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Availability slot not found"));
        } catch (Exception e) {
            log.error("Error fetching availability: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch availability slot"));
        }
    }

    /**
     * Get all availability slots for a mentor
     */
    @GetMapping("/mentor-availability/mentor/{mentorId}")
    @Operation(summary = "Get mentor availability", description = "Get all availability slots for a specific mentor")
    public ResponseEntity<ApiResponse<List<MentorAvailabilityDTO.Response>>> getMentorAvailability(
            @PathVariable UUID mentorId,
            @Parameter(description = "Filter for active slots only") @RequestParam(required = false, defaultValue = "true") Boolean activeOnly) {
        try {
            log.info("Fetching availability for mentor: {} (activeOnly: {})", mentorId, activeOnly);

            List<MentorAvailabilityDTO.Response> responses = availabilityService.getMentorAvailability(mentorId, activeOnly);
            return ResponseEntity.ok(ApiResponse.success(responses));

        } catch (Exception e) {
            log.error("Error fetching mentor availability: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch mentor availability"));
        }
    }

    /**
     * Get mentor's weekly schedule grouped by day
     */
    @GetMapping("/mentor-availability/mentor/{mentorId}/weekly")
    @Operation(summary = "Get weekly schedule", description = "Get mentor's complete weekly schedule grouped by day")
    public ResponseEntity<ApiResponse<MentorAvailabilityDTO.WeeklyScheduleResponse>> getMentorWeeklySchedule(
            @PathVariable UUID mentorId,
            @Parameter(description = "Filter for active slots only") @RequestParam(required = false, defaultValue = "true") Boolean activeOnly) {
        try {
            log.info("Fetching weekly schedule for mentor: {}", mentorId);

            MentorAvailabilityDTO.WeeklyScheduleResponse response =
                    availabilityService.getMentorWeeklySchedule(mentorId, activeOnly);
            return ResponseEntity.ok(ApiResponse.success(response));

        } catch (Exception e) {
            log.error("Error fetching weekly schedule: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch weekly schedule"));
        }
    }

    /**
     * Get availability for a specific day
     */
    @GetMapping("/mentor-availability/mentor/{mentorId}/day/{dayOfWeek}")
    @Operation(summary = "Get day availability", description = "Get mentor's availability for a specific day of the week")
    public ResponseEntity<ApiResponse<List<MentorAvailabilityDTO.Response>>> getMentorAvailabilityByDay(
            @PathVariable UUID mentorId,
            @PathVariable DayOfWeek dayOfWeek,
            @Parameter(description = "Filter for active slots only") @RequestParam(required = false, defaultValue = "true") Boolean activeOnly) {
        try {
            log.info("Fetching availability for mentor: {} on {}", mentorId, dayOfWeek);

            List<MentorAvailabilityDTO.Response> responses =
                    availabilityService.getMentorAvailabilityByDay(mentorId, dayOfWeek, activeOnly);
            return ResponseEntity.ok(ApiResponse.success(responses));

        } catch (Exception e) {
            log.error("Error fetching day availability: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch day availability"));
        }
    }

    /**
     * Toggle availability slot active status
     */
    @PatchMapping("/mentor-availability/{id}/toggle")
    @Operation(summary = "Toggle availability status", description = "Toggle the active status of an availability slot")
    public ResponseEntity<ApiResponse<MentorAvailabilityDTO.Response>> toggleAvailabilityStatus(@PathVariable Long id) {
        try {
            log.info("Toggling status for availability slot with ID: {}", id);

            MentorAvailabilityDTO.Response response = availabilityService.toggleAvailabilityStatus(id);
            return ResponseEntity.ok(ApiResponse.success(response, "Availability status toggled successfully"));

        } catch (NoSuchElementException e) {
            log.error("Availability not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Availability slot not found"));
        } catch (Exception e) {
            log.error("Error toggling availability status: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to toggle availability status"));
        }
    }

    /**
     * Delete all availability slots for a mentor
     */
    @DeleteMapping("/mentor-availability/mentor/{mentorId}/all")
    @Operation(summary = "Delete all availability", description = "Delete all availability slots for a mentor")
    public ResponseEntity<ApiResponse<Void>> deleteAllMentorAvailability(@PathVariable UUID mentorId) {
        try {
            log.info("Deleting all availability for mentor: {}", mentorId);

            availabilityService.deleteAllMentorAvailability(mentorId);
            return ResponseEntity.ok(ApiResponse.success("All availability slots deleted successfully"));

        } catch (Exception e) {
            log.error("Error deleting all availability: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to delete all availability slots"));
        }
    }

    /**
     * Delete availability slots for a specific day
     */
    @DeleteMapping("/mentor-availability/mentor/{mentorId}/day/{dayOfWeek}")
    @Operation(summary = "Delete day availability", description = "Delete all availability slots for a specific day")
    public ResponseEntity<ApiResponse<Void>> deleteMentorAvailabilityByDay(
            @PathVariable UUID mentorId,
            @PathVariable DayOfWeek dayOfWeek) {
        try {
            log.info("Deleting availability for mentor: {} on {}", mentorId, dayOfWeek);

            availabilityService.deleteMentorAvailabilityByDay(mentorId, dayOfWeek);
            return ResponseEntity.ok(ApiResponse.success(
                    String.format("Availability slots for %s deleted successfully", dayOfWeek)));

        } catch (Exception e) {
            log.error("Error deleting day availability: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to delete day availability slots"));
        }
    }
}
