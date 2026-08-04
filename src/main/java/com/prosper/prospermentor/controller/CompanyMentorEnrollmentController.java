package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.dto.CompanyMentorDtos;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.service.CompanyMentorEnrollmentService;
import com.prosper.prospermentor.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class CompanyMentorEnrollmentController {

    private final CompanyMentorEnrollmentService companyMentorEnrollmentService;
    private final ProfileService profileService;

    @GetMapping("/company-mentor-invitations/verify")
    public ResponseEntity<ApiResponse<CompanyMentorDtos.VerifyInviteResponse>> verify(@RequestParam String token) {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    "Company mentor invitation verified",
                    companyMentorEnrollmentService.verifyInvitation(token)
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error verifying company mentor invitation: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to verify company mentor invitation"));
        }
    }

    @PostMapping("/company-mentor-invitations/accept")
    public ResponseEntity<ApiResponse<CompanyMentorDtos.PoolMemberDto>> accept(@Valid @RequestBody CompanyMentorDtos.AcceptInviteRequest request,
                                                                               Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = requireMentor(authentication);
            return ResponseEntity.ok(ApiResponse.success(
                    "Company mentor invitation accepted",
                    companyMentorEnrollmentService.acceptInvitation(request.getToken(), userDetails.getUserIdAsUuid())
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error accepting company mentor invitation: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to accept company mentor invitation"));
        }
    }

    @GetMapping("/companies/{companyId}/mentor-pool")
    public ResponseEntity<ApiResponse<CompanyMentorDtos.MentorPoolResponse>> getMentorPool(@PathVariable UUID companyId,
                                                                                           @RequestParam(defaultValue = "0") int page,
                                                                                           @RequestParam(defaultValue = "50") int size,
                                                                                           @RequestParam(required = false) String search,
                                                                                           Authentication authentication) {
        try {
            authorizeCompanyAdmin(authentication, companyId);
            return ResponseEntity.ok(ApiResponse.success(
                    "Company mentor pool retrieved",
                    companyMentorEnrollmentService.getMentorPool(companyId, page, size, search)
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting company mentor pool for company {}: {}", companyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get company mentor pool"));
        }
    }

    @PostMapping("/companies/{companyId}/mentor-invitations")
    public ResponseEntity<ApiResponse<CompanyMentorDtos.InvitationDto>> invite(@PathVariable UUID companyId,
                                                                               @Valid @RequestBody CompanyMentorDtos.InviteRequest request,
                                                                               Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = authorizeCompanyAdmin(authentication, companyId);
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                    "Company mentor invitation sent",
                    companyMentorEnrollmentService.inviteMentor(companyId, request, userDetails.getUserIdAsUuid())
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error inviting company mentor for company {}: {}", companyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to invite company mentor"));
        }
    }

    @PostMapping(value = "/companies/{companyId}/mentor-invitations/validate-import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CompanyMentorDtos.ImportValidationResponse>> validateImport(@PathVariable UUID companyId,
                                                                                                  @RequestParam("file") MultipartFile file,
                                                                                                  Authentication authentication) {
        try {
            authorizeCompanyAdmin(authentication, companyId);
            validateXlsxFile(file);
            return ResponseEntity.ok(ApiResponse.success(
                    "Company mentor import validated",
                    companyMentorEnrollmentService.validateImport(companyId, file)
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error validating company mentor import for company {}: {}", companyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to validate company mentor import"));
        }
    }

    @PostMapping(value = "/companies/{companyId}/mentor-invitations/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CompanyMentorDtos.ImportValidationResponse>> importMentors(@PathVariable UUID companyId,
                                                                                                 @RequestParam("file") MultipartFile file,
                                                                                                 Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = authorizeCompanyAdmin(authentication, companyId);
            validateXlsxFile(file);
            return ResponseEntity.ok(ApiResponse.success(
                    "Company mentors imported",
                    companyMentorEnrollmentService.importMentors(companyId, file, userDetails.getUserIdAsUuid())
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error importing company mentors for company {}: {}", companyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to import company mentors"));
        }
    }

    @PostMapping("/companies/{companyId}/mentor-invitations/{invitationId}/resend")
    public ResponseEntity<ApiResponse<CompanyMentorDtos.InvitationDto>> resend(@PathVariable UUID companyId,
                                                                               @PathVariable UUID invitationId,
                                                                               Authentication authentication) {
        try {
            authorizeCompanyAdmin(authentication, companyId);
            return ResponseEntity.ok(ApiResponse.success(
                    "Company mentor invitation resent",
                    companyMentorEnrollmentService.resendInvitation(companyId, invitationId)
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error resending company mentor invitation {}: {}", invitationId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to resend company mentor invitation"));
        }
    }

    @PatchMapping("/companies/{companyId}/mentor-pool/{membershipId}/visibility")
    public ResponseEntity<ApiResponse<CompanyMentorDtos.PoolMemberDto>> updateVisibility(@PathVariable UUID companyId,
                                                                                         @PathVariable UUID membershipId,
                                                                                         @RequestBody CompanyMentorDtos.VisibilityUpdateRequest request,
                                                                                         Authentication authentication) {
        try {
            authorizeCompanyAdmin(authentication, companyId);
            return ResponseEntity.ok(ApiResponse.success(
                    "Company mentor visibility updated",
                    companyMentorEnrollmentService.updateVisibility(companyId, membershipId, request)
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating company mentor membership {} visibility: {}", membershipId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update company mentor visibility"));
        }
    }

    @DeleteMapping("/companies/{companyId}/mentor-pool/{membershipId}")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable UUID companyId,
                                                    @PathVariable UUID membershipId,
                                                    Authentication authentication) {
        try {
            authorizeCompanyAdmin(authentication, companyId);
            companyMentorEnrollmentService.removeMembership(companyId, membershipId);
            return ResponseEntity.ok(ApiResponse.success("Company mentor removed"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error removing company mentor membership {}: {}", membershipId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to remove company mentor"));
        }
    }

    private SupabaseUserDetails requireMentor(Authentication authentication) {
        SupabaseUserDetails userDetails = requireAuthenticatedUser(authentication);
        if (!userDetails.isMentor()) {
            throw new SecurityException("Mentor access is required");
        }
        return userDetails;
    }

    private SupabaseUserDetails authorizeCompanyAdmin(Authentication authentication, UUID companyId) {
        SupabaseUserDetails userDetails = requireAuthenticatedUser(authentication);
        if (userDetails.isAdmin()) {
            return userDetails;
        }
        if (!userDetails.isCompanyAdmin()) {
            throw new SecurityException("Company admin access is required");
        }

        UUID userId = userDetails.getUserIdAsUuid();
        UUID profileCompanyId = profileService.getProfileWithCompany(userId)
                .map(profile -> profile.getCompany() != null ? profile.getCompany().getId() : null)
                .orElse(null);
        if (profileCompanyId == null || !profileCompanyId.equals(companyId)) {
            throw new SecurityException("Not authorized to access this company");
        }
        return userDetails;
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

    private void validateXlsxFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Mentor import file is required");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException("Mentor import must be an .xlsx file");
        }
    }
}
