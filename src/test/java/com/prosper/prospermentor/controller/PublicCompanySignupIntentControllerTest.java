package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.dto.CreateCompanySignupIntentRequest;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.service.CompanyAdminRegistrationService;
import com.prosper.prospermentor.service.CompanySignupIntentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicCompanySignupIntentControllerTest {

    @Mock
    private CompanySignupIntentService companySignupIntentService;
    @Mock
    private CompanyAdminRegistrationService companyAdminRegistrationService;

    @InjectMocks
    private PublicCompanySignupIntentController publicCompanySignupIntentController;

    @Test
    void createIntent_shouldReturnConflictWhenSignupCannotBeCreated() {
        CreateCompanySignupIntentRequest request = new CreateCompanySignupIntentRequest();
        request.setCompanyName("Kenya Airways");
        request.setWorkEmail("ops@kenya-airways.test");
        request.setPhoneNumber("+254700000000");
        request.setFirstName("Ada");
        request.setLastName("Lovelace");

        when(companySignupIntentService.createIntent(
                "Kenya Airways",
                "ops@kenya-airways.test",
                "+254700000000",
                "Ada",
                "Lovelace",
                null,
                null
        )).thenThrow(new IllegalStateException("A company with this email address already exists"));

        ResponseEntity<ApiResponse<Map<String, Object>>> response = publicCompanySignupIntentController.createIntent(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("A company with this email address already exists");
    }
}
