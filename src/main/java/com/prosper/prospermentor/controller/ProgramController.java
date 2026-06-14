package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.controller.dto.ProgramDtos;
import com.prosper.prospermentor.entity.Program;
import com.prosper.prospermentor.service.ProgramService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for managing programs
 */
@RestController
@RequestMapping("/api/v1/programs")
@Tag(name = "Programs", description = "Program management APIs")
@Slf4j
public class ProgramController {

    private final ProgramService programService;

    public ProgramController(ProgramService programService) {
        this.programService = programService;
    }

    /**
     * Get all programs with mentors (paginated) with optional search
     */
    @GetMapping
    @Operation(summary = "Get all programs", description = "Get all programs ordered by orderId with mentors (paginated, with optional search by name)")
    public ResponseEntity<Map<String, Object>> getAllPrograms(
            @RequestParam(required = false) String searchTerm,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            log.info("Searching programs with term: '{}' - page: {}, size: {}", searchTerm, page, size);
        } else {
            log.info("Getting all programs with mentors - page: {}, size: {}", page, size);
        }

        try {
            // Validate pagination parameters
            if (page < 0) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Page number cannot be negative");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            if (size < 1 || size > 100) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Page size must be between 1 and 100");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            Pageable pageable = PageRequest.of(page, size);
            Page<Program> programsPage = programService.getAllProgramsWithMentorsPaginated(searchTerm, pageable);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("programs", programsPage.getContent());

            // Add search term to response if provided
            if (searchTerm != null && !searchTerm.trim().isEmpty()) {
                response.put("searchTerm", searchTerm.trim());
            }

            // Pagination metadata
            Map<String, Object> pagination = new HashMap<>();
            pagination.put("currentPage", programsPage.getNumber());
            pagination.put("pageSize", programsPage.getSize());
            pagination.put("totalElements", programsPage.getTotalElements());
            pagination.put("totalPages", programsPage.getTotalPages());
            pagination.put("first", programsPage.isFirst());
            pagination.put("last", programsPage.isLast());
            pagination.put("hasNext", programsPage.hasNext());
            pagination.put("hasPrevious", programsPage.hasPrevious());

            response.put("pagination", pagination);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting programs: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to get programs: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get all LIVE programs with mentors
     */
    @GetMapping("/live")
    @Operation(summary = "Get live programs", description = "Get all LIVE programs ordered by orderId with mentors")
    public ResponseEntity<Map<String, Object>> getLivePrograms() {
        log.info("Getting LIVE programs with mentors");

        try {
            List<ProgramDtos.ProgramWithMentorsDto> programs = programService.getLiveProgramsWithMentors();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", programs.size());
            response.put("programs", programs);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting LIVE programs: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to get LIVE programs: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get program by ID with mentors
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get program by ID", description = "Get a specific program by its ID with mentors")
    public ResponseEntity<Map<String, Object>> getProgramById(@PathVariable UUID id) {
        log.info("Getting program with id: {}", id);

        try {
            return programService.getProgramByIdWithMentors(id)
                    .map(program -> {
                        Map<String, Object> response = new HashMap<>();
                        response.put("success", true);
                        response.put("program", program);
                        return ResponseEntity.ok(response);
                    })
                    .orElseGet(() -> {
                        Map<String, Object> response = new HashMap<>();
                        response.put("success", false);
                        response.put("message", "Program not found with id: " + id);
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                    });

        } catch (Exception e) {
            log.error("Error getting program by id: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to get program: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Search programs by name with mentors
     */
    @GetMapping("/search")
    @Operation(summary = "Search programs", description = "Search programs by name (case insensitive) with mentors")
    public ResponseEntity<Map<String, Object>> searchPrograms(@RequestParam String name) {
        log.info("Searching programs with name: {} (with mentors)", name);

        try {
            List<ProgramDtos.ProgramWithMentorsDto> programs = programService.searchProgramsByNameWithMentors(name);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", programs.size());
            response.put("programs", programs);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error searching programs: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to search programs: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get programs by status with mentors
     */
    @GetMapping("/status/{status}")
    @Operation(summary = "Get programs by status", description = "Get all programs with a specific status with mentors")
    public ResponseEntity<Map<String, Object>> getProgramsByStatus(@PathVariable String status) {
        log.info("Getting programs with status: {} (with mentors)", status);

        try {
            Program.ProgramStatus programStatus = Program.ProgramStatus.valueOf(status.toUpperCase());
            List<ProgramDtos.ProgramWithMentorsDto> programs = programService.getProgramsByStatusWithMentors(programStatus);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", programs.size());
            response.put("programs", programs);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Invalid status: {}", status);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Invalid status: " + status + ". Valid statuses: LIVE, DRAFT, ARCHIVED");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);

        } catch (Exception e) {
            log.error("Error getting programs by status: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to get programs: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Create a new program
     */
    @PostMapping
    @Operation(summary = "Create program", description = "Create a new program")
    public ResponseEntity<Map<String, Object>> createProgram(@RequestBody Program program) {
        log.info("Creating new program: {}", program.getName());

        try {
            Program createdProgram = programService.createProgram(program);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Program created successfully");
            response.put("program", createdProgram);

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            log.error("Error creating program: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to create program: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Update an existing program
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update program", description = "Update an existing program")
    public ResponseEntity<Map<String, Object>> updateProgram(
            @PathVariable UUID id,
            @RequestBody Program programDetails) {
        log.info("Updating program with id: {}", id);

        try {
            Program updatedProgram = programService.updateProgram(id, programDetails);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Program updated successfully");
            response.put("program", updatedProgram);

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("Program not found: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);

        } catch (Exception e) {
            log.error("Error updating program: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to update program: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Delete a program
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete program", description = "Delete a program by ID")
    public ResponseEntity<Map<String, Object>> deleteProgram(@PathVariable UUID id) {
        log.info("Deleting program with id: {}", id);

        try {
            programService.deleteProgram(id);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Program deleted successfully");

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("Program not found: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);

        } catch (Exception e) {
            log.error("Error deleting program: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to delete program: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get program count
     */
    @GetMapping("/count")
    @Operation(summary = "Get program count", description = "Get total number of programs")
    public ResponseEntity<Map<String, Object>> getProgramCount() {
        log.info("Getting program count");

        try {
            long count = programService.countAllPrograms();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", count);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting program count: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to get program count: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}