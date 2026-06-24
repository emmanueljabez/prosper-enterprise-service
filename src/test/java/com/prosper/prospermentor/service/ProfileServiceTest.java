package com.prosper.prospermentor.service;

import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.repository.MenteeProfileRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock private ProfileRepository profileRepository;
    @Mock private MenteeProfileRepository menteeProfileRepository;
    @Mock private MentorProfileRepository mentorProfileRepository;

    private ProfileService profileService;

    @BeforeEach
    void setUp() {
        profileService = new ProfileService(
                profileRepository,
                menteeProfileRepository,
                mentorProfileRepository
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
}
