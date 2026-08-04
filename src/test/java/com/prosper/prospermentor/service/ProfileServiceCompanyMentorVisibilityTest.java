package com.prosper.prospermentor.service;

import com.prosper.prospermentor.controller.ProfileController;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.repository.MenteeProfileRepository;
import com.prosper.prospermentor.repository.MentorProfileRepository;
import com.prosper.prospermentor.repository.MentorSkillRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceCompanyMentorVisibilityTest {

    private static final UUID MENTOR_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private MenteeProfileRepository menteeProfileRepository;
    @Mock
    private MentorProfileRepository mentorProfileRepository;
    @Mock
    private MentorSkillRepository mentorSkillRepository;
    @Mock
    private CompanyMentorEnrollmentService companyMentorEnrollmentService;

    private ProfileService profileService;

    @BeforeEach
    void setUp() {
        profileService = new ProfileService(
                profileRepository,
                menteeProfileRepository,
                mentorProfileRepository,
                mentorSkillRepository,
                companyMentorEnrollmentService
        );
    }

    @Test
    void getAllMentorsPaginated_shouldUsePublicMentorFilterForCompanyPrivateExclusions() {
        Profile publicMentor = mentor(MENTOR_ID);
        when(profileRepository.findPublicMentorsWithFilters(eq("mentor"), eq(true), eq("leadership"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(publicMentor)));

        assertThat(profileService.getAllMentorsPaginated(0, 10, true, "leadership").getContent())
                .containsExactly(publicMentor);
        verify(profileRepository, never()).findByRoleWithFilters(any(), any(), any(), any());
    }

    @Test
    void isPublicMentorVisible_shouldDelegateToCompanyMentorEnrollmentVisibility() {
        when(companyMentorEnrollmentService.isMentorPubliclyDiscoverable(MENTOR_ID)).thenReturn(true);

        assertThat(profileService.isPublicMentorVisible(MENTOR_ID)).isTrue();
        verify(companyMentorEnrollmentService).isMentorPubliclyDiscoverable(MENTOR_ID);
    }

    @Test
    void getMentorById_shouldReturnNotFoundForCompanyPrivateMentorWithoutPublicApproval() {
        ProfileService profileServiceMock = mock(ProfileService.class);
        when(profileServiceMock.isPublicMentorVisible(MENTOR_ID)).thenReturn(false);

        ResponseEntity<Object> response = new ProfileController(profileServiceMock).getMentorById(MENTOR_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isEqualTo(Map.of("error", "Mentor profile not found"));
        verify(profileServiceMock, never()).getCompleteProfile(MENTOR_ID);
    }

    private Profile mentor(UUID id) {
        Profile profile = new Profile();
        profile.setId(id);
        profile.setRole("mentor");
        profile.setEmail("mentor@example.com");
        return profile;
    }
}
