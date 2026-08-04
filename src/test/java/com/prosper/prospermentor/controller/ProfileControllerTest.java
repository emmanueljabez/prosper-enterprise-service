package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.service.ProfileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock private ProfileService profileService;

    @Test
    void getAllMentors_shouldReturnMentorResponsesWithCanonicalTopics() {
        Profile mentor = new Profile();
        mentor.setId(UUID.randomUUID());
        mentor.setRole("mentor");

        Map<String, Object> mentorResponse = Map.of(
                "id", mentor.getId(),
                "mentorSkillTopics", List.of("Customer Success"),
                "topics", List.of("Customer Success")
        );

        when(profileService.getAllMentorsPaginated(0, 10, null, null))
                .thenReturn(new PageImpl<>(List.of(mentor), PageRequest.of(0, 10), 1));
        when(profileService.toMentorProfileResponse(mentor)).thenReturn(mentorResponse);

        ResponseEntity<Object> response = new ProfileController(profileService)
                .getAllMentors(0, 10, null, null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsEntry("success", true);
        assertThat(body.get("mentors")).isEqualTo(List.of(mentorResponse));
        verify(profileService).toMentorProfileResponse(mentor);
    }
}
