package com.prosper.prospermentor.service;

import com.prosper.prospermentor.controller.dto.ProgramDtos;
import com.prosper.prospermentor.entity.Program;
import com.prosper.prospermentor.entity.ProgramMentor;
import com.prosper.prospermentor.repository.ProgramRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing programs
 */
@Service
@Slf4j
@Transactional
public class ProgramService {

    private final ProgramRepository programRepository;

    public ProgramService(ProgramRepository programRepository) {
        this.programRepository = programRepository;
    }

    /**
     * Get all programs
     */
    @Transactional(readOnly = true)
    public List<Program> getAllPrograms() {
        log.debug("Fetching all programs");
        return programRepository.findAll();
    }

    /**
     * Get all programs ordered by orderId
     */
    @Transactional(readOnly = true)
    public List<Program> getAllProgramsOrdered() {
        log.debug("Fetching all programs ordered by orderId");
        return programRepository.findAllOrderedByOrderId();
    }

    /**
     * Get all LIVE programs ordered by orderId
     */
    @Transactional(readOnly = true)
    public List<Program> getLivePrograms() {
        log.debug("Fetching LIVE programs");
        return programRepository.findLiveProgramsOrdered();
    }

    /**
     * Get program by ID
     */
    @Transactional(readOnly = true)
    public Optional<Program> getProgramById(UUID id) {
        log.debug("Fetching program with id: {}", id);
        return programRepository.findById(id);
    }

    /**
     * Get program by ID with mentors
     */
    @Transactional(readOnly = true)
    public Optional<ProgramDtos.ProgramWithMentorsDto> getProgramByIdWithMentors(UUID id) {
        log.debug("Fetching program with id and mentors: {}", id);

        return programRepository.findByIdWithMentors(id)
                .map(this::convertToProgramWithMentorsDto);
    }

    /**
     * Convert Program entity to ProgramWithMentorsDto
     */
    private ProgramDtos.ProgramWithMentorsDto convertToProgramWithMentorsDto(Program program) {
        List<ProgramDtos.ProgramMentorDto> mentorDtos = program.getProgramMentors().stream()
                .map(pm -> ProgramDtos.ProgramMentorDto.builder()
                        .id(pm.getMentor().getId())
                        .title(pm.getMentor().getTitle())
                        .company(pm.getMentor().getCompany())
                        .yearsExperience(pm.getMentor().getYearsExperience())
                        .hourlyRate(pm.getMentor().getHourlyRate())
                        .rating(pm.getMentor().getRating())
                        .totalSessions(pm.getMentor().getTotalSessions())
                        .bio(pm.getMentor().getBio())
                        .avatarUrl(pm.getMentor().getAvatarUrl())
                        .isAvailable(pm.getMentor().getIsAvailable())
                        .build())
                .collect(Collectors.toList());

        return ProgramDtos.ProgramWithMentorsDto.builder()
                .id(program.getId())
                .legacyId(program.getLegacyId())
                .name(program.getName())
                .description(program.getDescription())
                .imageUrl(program.getImageUrl())
                .videoUrl(program.getVideoUrl())
                .status(program.getStatus())
                .orderId(program.getOrderId())
                .tips(program.getTips())
                .focusAreas(program.getFocusAreas())
                .createdAt(program.getCreatedAt())
                .updatedAt(program.getUpdatedAt())
                .mentors(mentorDtos)
                .build();
    }

    /**
     * Get program by name
     */
    @Transactional(readOnly = true)
    public Optional<Program> getProgramByName(String name) {
        log.debug("Fetching program with name: {}", name);
        return programRepository.findByName(name);
    }

    /**
     * Get program by legacy ID
     */
    @Transactional(readOnly = true)
    public Optional<Program> getProgramByLegacyId(String legacyId) {
        log.debug("Fetching program with legacy ID: {}", legacyId);
        return programRepository.findByLegacyId(legacyId);
    }

    /**
     * Search programs by name (case insensitive)
     */
    @Transactional(readOnly = true)
    public List<Program> searchProgramsByName(String name) {
        log.debug("Searching programs with name containing: {}", name);
        return programRepository.findByNameContainingIgnoreCase(name);
    }

    /**
     * Get programs by status
     */
    @Transactional(readOnly = true)
    public List<Program> getProgramsByStatus(Program.ProgramStatus status) {
        log.debug("Fetching programs with status: {}", status);
        return programRepository.findByStatus(status);
    }

    /**
     * Create a new program
     */
    public Program createProgram(Program program) {
        log.info("Creating new program: {}", program.getName());
        return programRepository.save(program);
    }

    /**
     * Update an existing program
     */
    public Program updateProgram(UUID id, Program programDetails) {
        log.info("Updating program with id: {}", id);

        Program program = programRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Program not found with id: " + id));

        // Update fields
        program.setName(programDetails.getName());
        program.setDescription(programDetails.getDescription());
        program.setImageUrl(programDetails.getImageUrl());
        program.setVideoUrl(programDetails.getVideoUrl());
        program.setStatus(programDetails.getStatus());
        program.setOrderId(programDetails.getOrderId());
        program.setTips(programDetails.getTips());
        program.setFocusAreas(programDetails.getFocusAreas());

        return programRepository.save(program);
    }

    /**
     * Delete a program
     */
    public void deleteProgram(UUID id) {
        log.info("Deleting program with id: {}", id);

        if (!programRepository.existsById(id)) {
            throw new RuntimeException("Program not found with id: " + id);
        }

        programRepository.deleteById(id);
    }

    /**
     * Count all programs
     */
    @Transactional(readOnly = true)
    public long countAllPrograms() {
        return programRepository.count();
    }
}