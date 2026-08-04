package com.prosper.prospermentor.service;

import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.repository.MenteeProfileRepository;
import com.prosper.prospermentor.repository.MentorSkillRepository;
import com.prosper.prospermentor.repository.MentorProfileRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock private ProfileRepository profileRepository;
    @Mock private MenteeProfileRepository menteeProfileRepository;
    @Mock private MentorProfileRepository mentorProfileRepository;
    @Mock private MentorSkillRepository mentorSkillRepository;
    @Mock private CompanyMentorEnrollmentService companyMentorEnrollmentService;

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
    void createProfileWithDetails_shouldBackfillMissingDetailsWhenBareProfileAlreadyExists() {
        UUID userId = UUID.randomUUID();
        Profile existing = new Profile();
        existing.setId(userId);
        existing.setEmail("info@nautix.io");
        existing.setUsername("info");
        existing.setRole("mentee");
        existing.setIsVerified(false);

        when(profileRepository.existsById(userId)).thenReturn(true);
        when(profileRepository.findById(userId)).thenReturn(Optional.of(existing));
        when(profileRepository.save(existing)).thenReturn(existing);
        when(profileRepository.findByIdWithCompany(userId)).thenReturn(Optional.of(existing));
        when(menteeProfileRepository.findById(userId)).thenReturn(Optional.empty());

        Optional<Map<String, Object>> profile = profileService.createProfileWithDetails(
                userId,
                "info@nautix.io",
                "mentee",
                "Emmanuel",
                "Jabez",
                "+254700000000",
                null
        );

        assertThat(profile).isPresent();
        assertThat(existing.getFirstName()).isEqualTo("Emmanuel");
        assertThat(existing.getLastName()).isEqualTo("Jabez");
        assertThat(existing.getPhone()).isEqualTo("+254700000000");
        assertThat(profile.get()).containsEntry("phone", "+254700000000");
        verify(profileRepository).save(existing);
    }

    @Test
    void getCompleteProfile_shouldExposeMentorSkillTopicsForBooking() {
        UUID userId = UUID.randomUUID();
        Profile mentor = new Profile();
        mentor.setId(userId);
        mentor.setEmail("mentor@prospermentor.com");
        mentor.setRole("mentor");

        when(profileRepository.findByIdWithCompany(userId)).thenReturn(Optional.of(mentor));
        when(mentorProfileRepository.findById(userId)).thenReturn(Optional.empty());
        when(mentorSkillRepository.findSkillNamesByMentorId(userId))
                .thenReturn(List.of("Customer Success", "Career Growth", "Customer Success"));

        Optional<Map<String, Object>> profile = profileService.getCompleteProfile(userId);

        assertThat(profile).isPresent();
        assertThat(profile.get()).containsEntry(
                "mentorSkillTopics",
                List.of("Customer Success", "Career Growth")
        );
        assertThat(profile.get()).containsEntry(
                "topics",
                List.of("Customer Success", "Career Growth")
        );
    }

    @Test
    void toMentorProfileResponse_shouldExposeMentorSkillTopicsForListEndpoints() {
        UUID userId = UUID.randomUUID();
        Profile mentor = new Profile();
        mentor.setId(userId);
        mentor.setEmail("mentor@prospermentor.com");
        mentor.setRole("mentor");

        when(mentorProfileRepository.findById(userId)).thenReturn(Optional.empty());
        when(mentorSkillRepository.findSkillNamesByMentorId(userId))
                .thenReturn(List.of("Customer Success"));

        Map<String, Object> response = profileService.toMentorProfileResponse(mentor);

        assertThat(response).containsEntry("mentorSkillTopics", List.of("Customer Success"));
        assertThat(response).containsEntry("topics", List.of("Customer Success"));
    }
}
