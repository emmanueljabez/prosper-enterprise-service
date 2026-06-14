package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.config.RestTemplateConfig;
import com.prosper.prospermentor.security.JwtAuthenticationFilter;
import com.prosper.prospermentor.service.NautixWhatsAppService;
import com.prosper.prospermentor.service.SessionBookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SessionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({NautixWhatsAppService.class, RestTemplateConfig.class})
@TestPropertySource(properties = {
        "nautix.whatsapp.enabled=true",
        "nautix.whatsapp.base-url=http://localhost:7091",
        "nautix.whatsapp.api-key=test-api-key",
        "nautix.whatsapp.language=en_US",
        "nautix.whatsapp.responder-display-name=ProsperMentor"
})
class SessionWhatsAppE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestTemplate restTemplate;

    @MockBean
    private SessionBookingService sessionBookingService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    void shouldSendTestWhatsAppThroughNautixThirdPartyEndpoint() throws Exception {
        mockServer.expect(requestTo("http://localhost:7091/api/thirdparty/v1/messages/template/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("API-KEY", "test-api-key"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().json("""
                        {
                          "recipient": "+254700000002",
                          "templateName": "prosper_mentor_session_request",
                          "language": "en_US",
                          "bodyParameters": ["Mentor Name","Mentee Name","https://linkedin.com/in/mentee"],
                          "responderDisplayName": "ProsperMentor"
                        }
                        """, true))
                .andRespond(MockRestResponseCreators.withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));

        String requestBody = """
                {
                  "phoneNumber": "+254700000002",
                  "templateName": "prosper_mentor_session_request",
                  "bodyParameters": ["Mentor Name","Mentee Name","https://linkedin.com/in/mentee"]
                }
                """;

        mockMvc.perform(post("/api/v1/sessions/test/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.status").value("sent"))
                .andExpect(jsonPath("$.data.templateName").value("prosper_mentor_session_request"));

        mockServer.verify();
    }

    @Test
    void shouldRejectInvalidPhoneNumberForTestWhatsAppEndpoint() throws Exception {
        String requestBody = """
                {
                  "phoneNumber": "abc",
                  "templateName": "prosper_mentor_session_request",
                  "bodyParameters": ["Mentor Name","Mentee Name","https://linkedin.com/in/mentee"]
                }
                """;

        mockMvc.perform(post("/api/v1/sessions/test/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Recipient phone number must be a valid E.164 number"));

        mockServer.verify();
    }
}
