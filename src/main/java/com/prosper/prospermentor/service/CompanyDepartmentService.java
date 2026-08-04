package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.AddCompanyDepartmentMembersRequest;
import com.prosper.prospermentor.dto.CompanyDepartmentDto;
import com.prosper.prospermentor.dto.CompanyDepartmentMemberAssignmentResultDto;
import com.prosper.prospermentor.dto.CompanyDepartmentMemberDto;
import com.prosper.prospermentor.dto.CreateCompanyDepartmentRequest;
import com.prosper.prospermentor.dto.UpdateCompanyDepartmentRequest;
import com.prosper.prospermentor.entity.CompanyDepartment;
import com.prosper.prospermentor.entity.CompanyDepartmentMember;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.repository.CompanyDepartmentMemberRepository;
import com.prosper.prospermentor.repository.CompanyDepartmentRepository;
import com.prosper.prospermentor.repository.CompanyRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CompanyDepartmentService {

    private final CompanyRepository companyRepository;
    private final ProfileRepository profileRepository;
    private final CompanyDepartmentRepository companyDepartmentRepository;
    private final CompanyDepartmentMemberRepository companyDepartmentMemberRepository;

    @Transactional(readOnly = true)
    public Page<CompanyDepartmentDto> getDepartments(UUID companyId, String search, Pageable pageable) {
        ensureCompanyExists(companyId);
        String searchTerm = normalizeSearch(search);

        Page<CompanyDepartment> departments = companyDepartmentRepository.findByCompanyIdWithFilters(
                companyId,
                searchTerm,
                pageable
        );

        List<UUID> departmentIds = departments.getContent().stream()
                .map(CompanyDepartment::getId)
                .toList();

        Map<UUID, Long> memberCounts = buildMemberCountMap(departmentIds);

        return departments.map(department -> toDepartmentDto(department, memberCounts.getOrDefault(department.getId(), 0L)));
    }

    public CompanyDepartmentDto createDepartment(UUID companyId,
                                                 CreateCompanyDepartmentRequest request,
                                                 UUID actorUserId) {
        ensureCompanyExists(companyId);

        String name = requireName(request.getName());
        String code = trimToNull(request.getCode());
        String description = trimToNull(request.getDescription());

        if (companyDepartmentRepository.existsByCompany_IdAndNameIgnoreCase(companyId, name)) {
            throw new IllegalArgumentException("A department with this name already exists");
        }

        if (code != null && companyDepartmentRepository.existsByCompany_IdAndCodeIgnoreCase(companyId, code)) {
            throw new IllegalArgumentException("A department with this code already exists");
        }

        CompanyDepartment department = new CompanyDepartment();
        department.setCompany(companyRepository.getReferenceById(companyId));
        department.setName(name);
        department.setCode(code);
        department.setDescription(description);
        department.setIsActive(true);
        department.setStatus(CompanyDepartment.DepartmentStatus.ACTIVE);
        department.setCreatedByUserId(actorUserId);

        CompanyDepartment saved = companyDepartmentRepository.save(department);
        return toDepartmentDto(saved, 0L);
    }

    public CompanyDepartmentDto updateDepartment(UUID companyId,
                                                 UUID departmentId,
                                                 UpdateCompanyDepartmentRequest request) {
        CompanyDepartment department = getDepartmentForCompany(companyId, departmentId);

        if (request.getName() != null) {
            String normalizedName = requireName(request.getName());
            if (companyDepartmentRepository.existsByCompany_IdAndNameIgnoreCaseAndIdNot(companyId, normalizedName, departmentId)) {
                throw new IllegalArgumentException("A department with this name already exists");
            }
            department.setName(normalizedName);
        }

        if (request.getCode() != null) {
            String normalizedCode = trimToNull(request.getCode());
            if (normalizedCode != null
                    && companyDepartmentRepository.existsByCompany_IdAndCodeIgnoreCaseAndIdNot(companyId, normalizedCode, departmentId)) {
                throw new IllegalArgumentException("A department with this code already exists");
            }
            department.setCode(normalizedCode);
        }

        if (request.getDescription() != null) {
            department.setDescription(trimToNull(request.getDescription()));
        }

        if (request.getIsActive() != null) {
            department.setIsActive(request.getIsActive());
            department.setStatus(request.getIsActive()
                    ? CompanyDepartment.DepartmentStatus.ACTIVE
                    : CompanyDepartment.DepartmentStatus.INACTIVE);
        }

        CompanyDepartment saved = companyDepartmentRepository.save(department);
        long memberCount = companyDepartmentMemberRepository.countByDepartment_Id(saved.getId());
        return toDepartmentDto(saved, memberCount);
    }

    public void deleteDepartment(UUID companyId, UUID departmentId) {
        CompanyDepartment department = getDepartmentForCompany(companyId, departmentId);
        companyDepartmentRepository.delete(department);
    }

    @Transactional(readOnly = true)
    public Page<CompanyDepartmentMemberDto> getDepartmentMembers(UUID companyId,
                                                                 UUID departmentId,
                                                                 String search,
                                                                 Pageable pageable) {
        getDepartmentForCompany(companyId, departmentId);
        String searchTerm = normalizeSearch(search);

        return companyDepartmentMemberRepository.findByDepartmentIdWithSearch(departmentId, searchTerm, pageable)
                .map(this::toMemberDto);
    }

    public CompanyDepartmentMemberAssignmentResultDto addMembers(UUID companyId,
                                                                 UUID departmentId,
                                                                 AddCompanyDepartmentMembersRequest request,
                                                                 UUID actorUserId) {
        CompanyDepartment department = getDepartmentForCompany(companyId, departmentId);

        Set<UUID> uniqueProfileIds = request.getProfileIds().stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        if (uniqueProfileIds.isEmpty()) {
            throw new IllegalArgumentException("At least one profileId is required");
        }

        Map<UUID, Profile> profilesById = profileRepository.findAllById(uniqueProfileIds).stream()
                .collect(Collectors.toMap(Profile::getId, profile -> profile));

        List<CompanyDepartmentMemberDto> assignedMembers = new ArrayList<>();
        List<CompanyDepartmentMemberAssignmentResultDto.SkippedProfile> skippedProfiles = new ArrayList<>();

        for (UUID profileId : uniqueProfileIds) {
            Profile profile = profilesById.get(profileId);
            if (profile == null) {
                skippedProfiles.add(CompanyDepartmentMemberAssignmentResultDto.SkippedProfile.builder()
                        .profileId(profileId)
                        .reason("Profile not found")
                        .build());
                continue;
            }

            UUID profileCompanyId = profile.getCompany() != null ? profile.getCompany().getId() : null;
            if (profileCompanyId == null || !profileCompanyId.equals(companyId)) {
                skippedProfiles.add(CompanyDepartmentMemberAssignmentResultDto.SkippedProfile.builder()
                        .profileId(profileId)
                        .reason("Profile does not belong to this company")
                        .build());
                continue;
            }

            if (!isEmployeeRole(profile.getRole())) {
                skippedProfiles.add(CompanyDepartmentMemberAssignmentResultDto.SkippedProfile.builder()
                        .profileId(profileId)
                        .reason("Profile is not an employee")
                        .build());
                continue;
            }

            CompanyDepartmentMember existingMembership = companyDepartmentMemberRepository.findByProfile_Id(profileId).orElse(null);

            if (existingMembership != null && existingMembership.getDepartment().getId().equals(departmentId)) {
                skippedProfiles.add(CompanyDepartmentMemberAssignmentResultDto.SkippedProfile.builder()
                        .profileId(profileId)
                        .reason("Profile is already assigned to this department")
                        .build());
                continue;
            }

            if (existingMembership != null) {
                UUID existingCompanyId = existingMembership.getDepartment().getCompany().getId();
                if (!existingCompanyId.equals(companyId)) {
                    skippedProfiles.add(CompanyDepartmentMemberAssignmentResultDto.SkippedProfile.builder()
                            .profileId(profileId)
                            .reason("Profile is assigned outside this company")
                            .build());
                    continue;
                }

                existingMembership.setDepartment(department);
                existingMembership.setJoinedAt(LocalDateTime.now());
                existingMembership.setCreatedByUserId(actorUserId);
                CompanyDepartmentMember saved = companyDepartmentMemberRepository.save(existingMembership);
                assignedMembers.add(toMemberDto(saved));
                continue;
            }

            CompanyDepartmentMember member = new CompanyDepartmentMember();
            member.setDepartment(department);
            member.setProfile(profile);
            member.setCreatedByUserId(actorUserId);
            member.setJoinedAt(LocalDateTime.now());

            CompanyDepartmentMember saved = companyDepartmentMemberRepository.save(member);
            assignedMembers.add(toMemberDto(saved));
        }

        return CompanyDepartmentMemberAssignmentResultDto.builder()
                .companyId(companyId)
                .departmentId(departmentId)
                .assignedCount(assignedMembers.size())
                .skippedCount(skippedProfiles.size())
                .members(assignedMembers)
                .skippedProfiles(skippedProfiles)
                .build();
    }

    public void removeMember(UUID companyId, UUID departmentId, UUID profileId) {
        getDepartmentForCompany(companyId, departmentId);
        int deleted = companyDepartmentMemberRepository.deleteByDepartment_IdAndProfile_Id(departmentId, profileId);
        if (deleted == 0) {
            throw new NoSuchElementException("Department member not found");
        }
    }

    @Transactional(readOnly = true)
    public CompanyDepartment getDepartmentForCompany(UUID companyId, UUID departmentId) {
        return companyDepartmentRepository.findByIdAndCompany_Id(departmentId, companyId)
                .orElseThrow(() -> new NoSuchElementException("Department not found"));
    }

    private Map<UUID, Long> buildMemberCountMap(List<UUID> departmentIds) {
        if (departmentIds == null || departmentIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Long> counts = new LinkedHashMap<>();
        for (Object[] row : companyDepartmentRepository.countMembersByDepartmentIds(departmentIds)) {
            if (row.length < 2 || row[0] == null || row[1] == null) continue;
            counts.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    private void ensureCompanyExists(UUID companyId) {
        if (!companyRepository.existsById(companyId)) {
            throw new NoSuchElementException("Company not found");
        }
    }

    private String normalizeSearch(String search) {
        return search != null && !search.trim().isBlank() ? search.trim() : "";
    }

    private String requireName(String value) {
        String normalized = value != null ? value.trim() : "";
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Department name is required");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean isEmployeeRole(String role) {
        String normalized = role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
        return "employee".equals(normalized) || "mentee".equals(normalized);
    }

    private CompanyDepartmentDto toDepartmentDto(CompanyDepartment department, long memberCount) {
        CompanyDepartment.DepartmentStatus resolvedStatus = department.getStatus() != null
                ? department.getStatus()
                : (Boolean.TRUE.equals(department.getIsActive())
                ? CompanyDepartment.DepartmentStatus.ACTIVE
                : CompanyDepartment.DepartmentStatus.INACTIVE);

        return CompanyDepartmentDto.builder()
                .id(department.getId())
                .companyId(department.getCompany() != null ? department.getCompany().getId() : null)
                .name(department.getName())
                .code(department.getCode())
                .description(department.getDescription())
                .status(resolvedStatus)
                .isActive(department.getIsActive())
                .memberCount(memberCount)
                .createdAt(department.getCreatedAt())
                .updatedAt(department.getUpdatedAt())
                .build();
    }

    private CompanyDepartmentMemberDto toMemberDto(CompanyDepartmentMember member) {
        Profile profile = member.getProfile();
        return CompanyDepartmentMemberDto.builder()
                .departmentId(member.getDepartment() != null ? member.getDepartment().getId() : null)
                .profileId(profile != null ? profile.getId() : null)
                .profileName(resolveProfileName(profile))
                .profileEmail(profile != null ? profile.getEmail() : null)
                .profileRole(profile != null ? profile.getRole() : null)
                .profileAvatarUrl(profile != null ? profile.getAvatarUrl() : null)
                .joinedAt(member.getJoinedAt())
                .createdAt(member.getCreatedAt())
                .updatedAt(member.getUpdatedAt())
                .build();
    }

    private String resolveProfileName(Profile profile) {
        if (profile == null) {
            return "Employee";
        }

        String firstName = profile.getFirstName() != null ? profile.getFirstName().trim() : "";
        String lastName = profile.getLastName() != null ? profile.getLastName().trim() : "";
        String fullName = String.join(" ", List.of(firstName, lastName)).trim();
        if (!fullName.isBlank()) {
            return fullName;
        }

        if (profile.getUsername() != null && !profile.getUsername().isBlank()) {
            return profile.getUsername();
        }

        if (profile.getEmail() != null && !profile.getEmail().isBlank()) {
            return profile.getEmail();
        }

        return "Employee";
    }
}
