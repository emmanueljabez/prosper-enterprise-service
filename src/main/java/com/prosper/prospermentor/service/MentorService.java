package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.MentorAvailabilityDTO;
import com.prosper.prospermentor.entity.MentorAvailability;
import com.prosper.prospermentor.repository.MentorAvailabilityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service class for MentorAvailability operations
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class MentorService {

    private final MentorAvailabilityRepository availabilityRepository;

    /**
     * Create a new availability slot for a mentor
     */
    public MentorAvailabilityDTO.Response createAvailability(MentorAvailabilityDTO.CreateRequest request) {
        log.info("Creating availability for mentor: {}", request.getMentorId());

        // Validate time range
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new IllegalArgumentException("End time must be after start time");
        }

        // Check for overlapping slots
        List<MentorAvailability> overlapping = availabilityRepository.findOverlappingSlotsForNew(
                request.getMentorId(),
                request.getDayOfWeek(),
                request.getStartTime(),
                request.getEndTime()
        );

        if (!overlapping.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("Time slot overlaps with existing availability on %s", request.getDayOfWeek())
            );
        }

        MentorAvailability availability = new MentorAvailability();
        availability.setMentorId(request.getMentorId());
        availability.setDayOfWeek(request.getDayOfWeek());
        availability.setStartTime(request.getStartTime());
        availability.setEndTime(request.getEndTime());
        availability.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);

        MentorAvailability saved = availabilityRepository.save(availability);
        log.info("Successfully created availability with ID: {}", saved.getId());

        return mapToResponse(saved);
    }

    /**
     * Bulk create multiple availability slots for a mentor
     */
    public List<MentorAvailabilityDTO.Response> createBulkAvailability(MentorAvailabilityDTO.BulkCreateRequest request) {
        log.info("Creating {} availability slots for mentor: {}", request.getSlots().size(), request.getMentorId());

        List<MentorAvailabilityDTO.Response> responses = new ArrayList<>();

        for (MentorAvailabilityDTO.BulkCreateRequest.TimeSlot slot : request.getSlots()) {
            try {
                MentorAvailabilityDTO.CreateRequest createRequest = MentorAvailabilityDTO.CreateRequest.builder()
                        .mentorId(request.getMentorId())
                        .dayOfWeek(slot.getDayOfWeek())
                        .startTime(slot.getStartTime())
                        .endTime(slot.getEndTime())
                        .isActive(slot.getIsActive())
                        .build();

                responses.add(createAvailability(createRequest));
            } catch (IllegalArgumentException e) {
                log.warn("Skipping slot due to error: {}", e.getMessage());
                // Continue with next slot
            }
        }

        log.info("Successfully created {} out of {} availability slots", responses.size(), request.getSlots().size());
        return responses;
    }

    /**
     * Update an existing availability slot
     */
    public MentorAvailabilityDTO.Response updateAvailability(Long id, MentorAvailabilityDTO.UpdateRequest request) {
        log.info("Updating availability with ID: {}", id);

        MentorAvailability availability = availabilityRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Availability not found with ID: " + id));

        // Update fields if provided
        if (request.getDayOfWeek() != null) {
            availability.setDayOfWeek(request.getDayOfWeek());
        }
        if (request.getStartTime() != null) {
            availability.setStartTime(request.getStartTime());
        }
        if (request.getEndTime() != null) {
            availability.setEndTime(request.getEndTime());
        }
        if (request.getIsActive() != null) {
            availability.setIsActive(request.getIsActive());
        }

        // Validate time range
        if (!availability.getEndTime().isAfter(availability.getStartTime())) {
            throw new IllegalArgumentException("End time must be after start time");
        }

        // Check for overlapping slots (excluding current slot)
        List<MentorAvailability> overlapping = availabilityRepository.findOverlappingSlots(
                availability.getMentorId(),
                availability.getDayOfWeek(),
                availability.getStartTime(),
                availability.getEndTime(),
                id
        );

        if (!overlapping.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("Time slot overlaps with existing availability on %s", availability.getDayOfWeek())
            );
        }

        MentorAvailability updated = availabilityRepository.save(availability);
        log.info("Successfully updated availability with ID: {}", id);

        return mapToResponse(updated);
    }

    /**
     * Bulk update multiple availability slots
     * This will delete all existing availability for the mentor and create new ones
     */
    public List<MentorAvailabilityDTO.Response> updateBulkAvailability(MentorAvailabilityDTO.BulkUpdateRequest request) {
        log.info("Bulk updating availability for mentor: {} with {} new slots",
                request.getMentorId(), request.getSlots().size());

        // First, delete all existing availability for this mentor
        log.info("Deleting all existing availability slots for mentor: {}", request.getMentorId());
        deleteAllMentorAvailability(request.getMentorId());

        // Now create the new availability slots
        List<MentorAvailabilityDTO.Response> responses = new ArrayList<>();

        for (MentorAvailabilityDTO.BulkUpdateRequest.TimeSlot slot : request.getSlots()) {
            try {
                MentorAvailabilityDTO.CreateRequest createRequest = MentorAvailabilityDTO.CreateRequest.builder()
                        .mentorId(request.getMentorId())
                        .dayOfWeek(slot.getDayOfWeek())
                        .startTime(slot.getStartTime())
                        .endTime(slot.getEndTime())
                        .isActive(slot.getIsActive())
                        .build();

                responses.add(createAvailability(createRequest));
            } catch (IllegalArgumentException e) {
                log.warn("Skipping slot due to validation error: {}", e.getMessage());
                // Continue with next slot
            }
        }

        log.info("Successfully replaced availability for mentor: {} with {} new slots",
                request.getMentorId(), responses.size());
        return responses;
    }

    /**
     * Delete an availability slot
     */
    public void deleteAvailability(Long id) {
        log.info("Deleting availability with ID: {}", id);

        if (!availabilityRepository.existsById(id)) {
            throw new NoSuchElementException("Availability not found with ID: " + id);
        }

        availabilityRepository.deleteById(id);
        log.info("Successfully deleted availability with ID: {}", id);
    }

    /**
     * Get a single availability slot by ID
     */
    @Transactional(readOnly = true)
    public MentorAvailabilityDTO.Response getAvailabilityById(Long id) {
        log.info("Fetching availability with ID: {}", id);

        MentorAvailability availability = availabilityRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Availability not found with ID: " + id));

        return mapToResponse(availability);
    }

    /**
     * Get all availability slots for a mentor
     */
    @Transactional(readOnly = true)
    public List<MentorAvailabilityDTO.Response> getMentorAvailability(UUID mentorId, Boolean activeOnly) {
        log.info("Fetching availability for mentor: {} (activeOnly: {})", mentorId, activeOnly);

        List<MentorAvailability> availabilities;
        if (activeOnly != null && activeOnly) {
            availabilities = availabilityRepository.findByMentorIdAndIsActiveTrue(mentorId);
        } else {
            availabilities = availabilityRepository.findByMentorId(mentorId);
        }

        return availabilities.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get mentor's weekly schedule grouped by day
     */
    @Transactional(readOnly = true)
    public MentorAvailabilityDTO.WeeklyScheduleResponse getMentorWeeklySchedule(UUID mentorId, Boolean activeOnly) {
        log.info("Fetching weekly schedule for mentor: {}", mentorId);

        List<MentorAvailability> availabilities;
        if (activeOnly != null && activeOnly) {
            availabilities = availabilityRepository.findByMentorIdAndIsActiveTrue(mentorId);
        } else {
            availabilities = availabilityRepository.findByMentorId(mentorId);
        }

        // Group by day of week
        Map<DayOfWeek, List<MentorAvailability>> groupedByDay = availabilities.stream()
                .collect(Collectors.groupingBy(MentorAvailability::getDayOfWeek));

        // Convert to response format
        List<MentorAvailabilityDTO.DayAvailabilityResponse> schedule = groupedByDay.entrySet().stream()
                .map(entry -> {
                    List<MentorAvailabilityDTO.DayAvailabilityResponse.TimeSlotResponse> timeSlots = entry.getValue().stream()
                            .sorted(Comparator.comparing(MentorAvailability::getStartTime))
                            .map(av -> MentorAvailabilityDTO.DayAvailabilityResponse.TimeSlotResponse.builder()
                                    .id(av.getId())
                                    .startTime(av.getStartTime())
                                    .endTime(av.getEndTime())
                                    .isActive(av.getIsActive())
                                    .durationInMinutes(av.getDurationInMinutes())
                                    .build())
                            .collect(Collectors.toList());

                    return MentorAvailabilityDTO.DayAvailabilityResponse.builder()
                            .dayOfWeek(entry.getKey())
                            .timeSlots(timeSlots)
                            .build();
                })
                .sorted(Comparator.comparing(MentorAvailabilityDTO.DayAvailabilityResponse::getDayOfWeek))
                .collect(Collectors.toList());

        long activeSlots = availabilities.stream()
                .filter(MentorAvailability::isAvailable)
                .count();

        return MentorAvailabilityDTO.WeeklyScheduleResponse.builder()
                .mentorId(mentorId)
                .totalSlots((long) availabilities.size())
                .activeSlots(activeSlots)
                .schedule(schedule)
                .build();
    }

    /**
     * Get availability for a specific day
     */
    @Transactional(readOnly = true)
    public List<MentorAvailabilityDTO.Response> getMentorAvailabilityByDay(
            UUID mentorId, DayOfWeek dayOfWeek, Boolean activeOnly) {
        log.info("Fetching availability for mentor: {} on {}", mentorId, dayOfWeek);

        List<MentorAvailability> availabilities;
        if (activeOnly != null && activeOnly) {
            availabilities = availabilityRepository.findByMentorIdAndDayOfWeekAndIsActiveTrue(mentorId, dayOfWeek);
        } else {
            availabilities = availabilityRepository.findByMentorIdAndDayOfWeek(mentorId, dayOfWeek);
        }

        return availabilities.stream()
                .sorted(Comparator.comparing(MentorAvailability::getStartTime))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Delete all availability slots for a mentor
     */
    public void deleteAllMentorAvailability(UUID mentorId) {
        log.info("Deleting all availability for mentor: {}", mentorId);
        availabilityRepository.deleteByMentorId(mentorId);
        log.info("Successfully deleted all availability for mentor: {}", mentorId);
    }

    /**
     * Delete all availability slots for a mentor on a specific day
     */
    public void deleteMentorAvailabilityByDay(UUID mentorId, DayOfWeek dayOfWeek) {
        log.info("Deleting availability for mentor: {} on {}", mentorId, dayOfWeek);
        availabilityRepository.deleteByMentorIdAndDayOfWeek(mentorId, dayOfWeek);
        log.info("Successfully deleted availability for mentor: {} on {}", mentorId, dayOfWeek);
    }

    /**
     * Toggle availability slot active status
     */
    public MentorAvailabilityDTO.Response toggleAvailabilityStatus(Long id) {
        log.info("Toggling status for availability with ID: {}", id);

        MentorAvailability availability = availabilityRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Availability not found with ID: " + id));

        availability.setIsActive(!availability.getIsActive());
        MentorAvailability updated = availabilityRepository.save(availability);

        log.info("Successfully toggled status for availability with ID: {} to {}", id, updated.getIsActive());
        return mapToResponse(updated);
    }

    /**
     * Map entity to response DTO
     */
    private MentorAvailabilityDTO.Response mapToResponse(MentorAvailability availability) {
        return MentorAvailabilityDTO.Response.builder()
                .id(availability.getId())
                .mentorId(availability.getMentorId())
                .dayOfWeek(availability.getDayOfWeek())
                .startTime(availability.getStartTime())
                .endTime(availability.getEndTime())
                .isActive(availability.getIsActive())
                .durationInMinutes(availability.getDurationInMinutes())
                .createdAt(availability.getCreatedAt())
                .updatedAt(availability.getUpdatedAt())
                .build();
    }
}
