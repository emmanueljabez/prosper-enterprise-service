package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.dto.AssignCompanyProgramMentorRequest;
import com.prosper.prospermentor.dto.CompanyProgramMentorCandidateDto;
import com.prosper.prospermentor.dto.EmployeeCompanyProgramMatchDto;
import com.prosper.prospermentor.dto.EmployeeMentorSelectionOptionsDto;
import com.prosper.prospermentor.dto.MatchWorkspaceSummaryDto;
import com.prosper.prospermentor.dto.MentorAssignmentSummaryDto;
import com.prosper.prospermentor.dto.ParticipantMatchWorkspaceUpdateDto;
import com.prosper.prospermentor.entity.CompanyProgram;
import com.prosper.prospermentor.entity.CompanyProgramParticipant;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.service.CompanyProgramMentorAssignmentService;
import com.prosper.prospermentor.service.CompanyProgramMatchWorkspaceService;
import com.prosper.prospermentor.service.CompanyProgramParticipantService;
import com.prosper.prospermentor.service.CompanyProgramService;
import com.prosper.prospermentor.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Company Program Mentor Assignments", description = "Mentor assignment and company program matching APIs")
@RequiredArgsConstructor
@Slf4j
public class CompanyProgramMentorAssignmentController {

    private final CompanyProgramMentorAssignmentService mentorAssignmentService;
    private final CompanyProgramMatchWorkspaceService matchWorkspaceService;
    private final CompanyProgramParticipantService participantService;
    private final CompanyProgramService companyProgramService;
    private final ProfileService profileService;

    @GetMapping("/company-programs/{companyProgramId}/mentor-candidates")
    @Operation(summary = "Get mentor candidates", description = "List mentors that can be assigned to a company program")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMentorCandidates(@PathVariable UUID companyProgramId,
                                                                                @RequestParam(required = false) String search,
                                                                                Authentication authentication) {
        try {
            CompanyProgram companyProgram = companyProgramService.getCompanyProgram(companyProgramId)
                    .orElseThrow(() -> new NoSuchElementException("Company program not found"));
            authorizeCompanyAccess(authentication, companyProgram.getCompany().getId(), true);

            List<CompanyProgramMentorCandidateDto> candidates = mentorAssignmentService.getMentorCandidates(companyProgramId, search);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("companyProgramId", companyProgramId);
            data.put("candidates", candidates);
            data.put("count", candidates.size());
            data.put("search", search != null ? search.trim() : "");

            return ResponseEntity.ok(ApiResponse.success("Mentor candidates retrieved successfully", data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting mentor candidates for company program {}: {}", companyProgramId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get mentor candidates: " + e.getMessage()));
        }
    }

    @PutMapping("/company-program-participants/{participantId}/mentor-assignment")
    @Operation(summary = "Assign mentor", description = "Assign or replace the mentor for a company program employee")
    public ResponseEntity<ApiResponse<MentorAssignmentSummaryDto>> assignMentor(@PathVariable UUID participantId,
                                                                                @Valid @RequestBody AssignCompanyProgramMentorRequest request,
                                                                                Authentication authentication) {
        try {
            CompanyProgramParticipant participant = participantService.getParticipant(participantId)
                    .orElseThrow(() -> new NoSuchElementException("Company program employee not found"));
            SupabaseUserDetails userDetails = authorizeCompanyAccess(
                    authentication,
                    participant.getCompanyProgram().getCompany().getId(),
                    true
            );

            ApiResponse<MentorAssignmentSummaryDto> response = mentorAssignmentService.assignMentor(
                    participantId,
                    request.getMentorId(),
                    userDetails.getUserIdAsUuid()
            );
            if (response.isSuccess()) {
                matchWorkspaceService.markAdminAssignment(participantId, userDetails.getUserIdAsUuid());
            }

            return response.isSuccess()
                    ? ResponseEntity.ok(response)
                    : ResponseEntity.badRequest().body(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error assigning mentor for employee {}: {}", participantId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to assign mentor: " + e.getMessage()));
        }
    }

    @DeleteMapping("/company-program-participants/{participantId}/mentor-assignment")
    @Operation(summary = "Remove mentor assignment", description = "Remove the mentor assigned to a company program employee")
    public ResponseEntity<ApiResponse<Void>> removeMentorAssignment(@PathVariable UUID participantId,
                                                                    Authentication authentication) {
        try {
            CompanyProgramParticipant participant = participantService.getParticipant(participantId)
                    .orElseThrow(() -> new NoSuchElementException("Company program employee not found"));
            authorizeCompanyAccess(authentication, participant.getCompanyProgram().getCompany().getId(), true);

            ApiResponse<Void> response = mentorAssignmentService.removeMentorAssignment(participantId);
            if (response.isSuccess()) {
                matchWorkspaceService.onAssignmentRemoved(participantId);
            }
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error removing mentor assignment for employee {}: {}", participantId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to remove mentor assignment: " + e.getMessage()));
        }
    }

    @GetMapping("/me/company-program-matches")
    @Operation(summary = "Get my company program matches", description = "Get the authenticated employee's mentor assignment status across company programs")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyCompanyProgramMatches(Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = requireAuthenticatedUser(authentication);
            List<EmployeeCompanyProgramMatchDto> matches =
                    mentorAssignmentService.getEmployeeProgramMatches(userDetails.getUserIdAsUuid());
            Map<UUID, MatchWorkspaceSummaryDto> workspaceByParticipantId = matchWorkspaceService.getWorkspaceSummaries(
                    matches.stream().map(EmployeeCompanyProgramMatchDto::getParticipantId).toList()
            );

            for (EmployeeCompanyProgramMatchDto match : matches) {
                match.setMatchWorkspace(workspaceByParticipantId.get(match.getParticipantId()));
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("matches", matches);
            data.put("count", matches.size());

            return ResponseEntity.ok(ApiResponse.success("Company program match status retrieved successfully", data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting company program matches: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get company program matches: " + e.getMessage()));
        }
    }

    @GetMapping("/me/company-program-matches/{participantId}/options")
    @Operation(summary = "Get my mentor selection options", description = "Get shortlist options for employee mentor selection in a company program")
    public ResponseEntity<ApiResponse<EmployeeMentorSelectionOptionsDto>> getMyMentorSelectionOptions(@PathVariable UUID participantId,
                                                                                                        Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = requireAuthenticatedUser(authentication);
            EmployeeMentorSelectionOptionsDto options = matchWorkspaceService.getEmployeeSelectionOptions(
                    participantId,
                    userDetails.getUserIdAsUuid()
            );

            return ResponseEntity.ok(ApiResponse.success("Mentor selection options retrieved successfully", options));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting mentor selection options for participant {}: {}", participantId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get mentor selection options: " + e.getMessage()));
        }
    }

    @PostMapping("/me/company-program-matches/{participantId}/select")
    @Operation(summary = "Select mentor from shortlist", description = "Employee selects a mentor from their company-program shortlist")
    public ResponseEntity<ApiResponse<MentorAssignmentSummaryDto>> selectMentorFromShortlist(@PathVariable UUID participantId,
                                                                                              @Valid @RequestBody AssignCompanyProgramMentorRequest request,
                                                                                              Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = requireAuthenticatedUser(authentication);
            MentorAssignmentSummaryDto assignment = matchWorkspaceService.selectMentorForEmployee(
                    participantId,
                    userDetails.getUserIdAsUuid(),
                    request.getMentorId()
            );

            return ResponseEntity.ok(ApiResponse.success("Mentor selected successfully", assignment));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error selecting mentor from shortlist for participant {}: {}", participantId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to select mentor: " + e.getMessage()));
        }
    }

    @PostMapping("/me/company-program-matches/{participantId}/select-open")
    @Operation(summary = "Select marketplace mentor", description = "Employee selects any eligible marketplace mentor for a company program")
    public ResponseEntity<ApiResponse<MentorAssignmentSummaryDto>> selectMarketplaceMentor(@PathVariable UUID participantId,
                                                                                            @Valid @RequestBody AssignCompanyProgramMentorRequest request,
                                                                                            Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = requireAuthenticatedUser(authentication);
            MentorAssignmentSummaryDto assignment = matchWorkspaceService.selectMarketplaceMentorForEmployee(
                    participantId,
                    userDetails.getUserIdAsUuid(),
                    request.getMentorId(),
                    request.getJourneyInstanceStepId()
            );

            return ResponseEntity.ok(ApiResponse.success("Mentor selected successfully", assignment));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error selecting marketplace mentor for participant {}: {}", participantId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to select mentor: " + e.getMessage()));
        }
    }

    @PostMapping("/company-program-participants/{participantId}/match-workspace/refresh")
    @Operation(summary = "Refresh participant match workspace", description = "Recompute mentor shortlist and selection state for a company-program employee")
    public ResponseEntity<ApiResponse<ParticipantMatchWorkspaceUpdateDto>> refreshParticipantMatchWorkspace(@PathVariable UUID participantId,
                                                                                                             Authentication authentication) {
        try {
            CompanyProgramParticipant participant = participantService.getParticipant(participantId)
                    .orElseThrow(() -> new NoSuchElementException("Company program employee not found"));
            authorizeCompanyAccess(authentication, participant.getCompanyProgram().getCompany().getId(), true);

            ParticipantMatchWorkspaceUpdateDto result = matchWorkspaceService.refreshParticipantWorkspace(participantId, true);
            return ResponseEntity.ok(ApiResponse.success("Participant match workspace refreshed successfully", result));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error refreshing participant match workspace {}: {}", participantId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to refresh participant match workspace: " + e.getMessage()));
        }
    }

    @PostMapping("/company-program-participants/{participantId}/match-workspace/auto-assign")
    @Operation(summary = "Auto-assign mentor from shortlist", description = "Force assign the highest-ranked valid mentor option for a participant")
    public ResponseEntity<ApiResponse<ParticipantMatchWorkspaceUpdateDto>> autoAssignParticipantMentor(@PathVariable UUID participantId,
                                                                                                        Authentication authentication) {
        try {
            CompanyProgramParticipant participant = participantService.getParticipant(participantId)
                    .orElseThrow(() -> new NoSuchElementException("Company program employee not found"));
            SupabaseUserDetails userDetails = authorizeCompanyAccess(
                    authentication,
                    participant.getCompanyProgram().getCompany().getId(),
                    true
            );

            ParticipantMatchWorkspaceUpdateDto result =
                    matchWorkspaceService.forceAutoAssign(participantId, userDetails.getUserIdAsUuid());
            return ResponseEntity.ok(ApiResponse.success("Participant mentor auto-assigned successfully", result));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error auto-assigning mentor for participant {}: {}", participantId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to auto-assign mentor: " + e.getMessage()));
        }
    }

    private SupabaseUserDetails requireAuthenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof SupabaseUserDetails userDetails)) {
            throw new SecurityException("Authentication required");
        }
        if (userDetails.getUserIdAsUuid() == null) {
            throw new SecurityException("Invalid authenticated user");
        }
        return userDetails;
    }

    private SupabaseUserDetails authorizeCompanyAccess(Authentication authentication,
                                                       UUID companyId,
                                                       boolean companyAdminRequired) {
        SupabaseUserDetails userDetails = requireAuthenticatedUser(authentication);

        if (userDetails.isAdmin()) {
            return userDetails;
        }

        if (companyAdminRequired && !userDetails.isCompanyAdmin()) {
            throw new SecurityException("Company admin access is required");
        }

        UUID profileCompanyId = profileService.getProfileWithCompany(userDetails.getUserIdAsUuid())
                .map(profile -> profile.getCompany() != null ? profile.getCompany().getId() : null)
                .orElse(null);

        if (profileCompanyId == null || !profileCompanyId.equals(companyId)) {
            throw new SecurityException("Not authorized to access this company");
        }

        return userDetails;
    }
}
