package com.cloud.guardrails.integration;

import com.cloud.guardrails.dto.EventDTO;
import com.cloud.guardrails.entity.CloudAccount;
import com.cloud.guardrails.entity.Organization;
import com.cloud.guardrails.repository.CloudAccountRepository;
import com.cloud.guardrails.repository.OrganizationRepository;
import com.cloud.guardrails.service.EventProducer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InternalAwsIngestionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private CloudAccountRepository cloudAccountRepository;

    @MockBean
    private EventProducer eventProducer;

    @Test
    void awsIngressRejectsInvalidSecret() throws Exception {
        mockMvc.perform(post("/internal/aws/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Guardrails-Ingestion-Secret", "wrong-secret")
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid ingestion secret"));
    }

    @Test
    void awsIngressNormalizesEventBridgeCloudTrailPayload() throws Exception {
        Organization organization = organizationRepository.save(Organization.builder()
                .name("Ingress Org")
                .createdAt(LocalDateTime.now())
                .build());

        CloudAccount account = cloudAccountRepository.save(CloudAccount.builder()
                .name("Ingress Account")
                .accountId("123456789012")
                .provider("AWS")
                .region("us-east-1")
                .organization(organization)
                .createdAt(LocalDateTime.now())
                .accessKey("enc:v1:test")
                .secretKey("enc:v1:test")
                .build());

        Map<String, Object> payload = Map.of(
                "id", "envelope-1",
                "account", "123456789012",
                "time", "2026-04-11T00:00:00Z",
                "detail-type", "AWS API Call via CloudTrail",
                "detail", Map.of(
                        "eventID", "evt-123",
                        "eventName", "AuthorizeSecurityGroupIngress",
                        "recipientAccountId", "123456789012",
                        "sourceIPAddress", "203.0.113.10",
                        "requestParameters", Map.of(
                                "groupId", "sg-123456",
                                "ipPermissions", Map.of(
                                        "items", java.util.List.of(
                                                Map.of(
                                                        "fromPort", 22,
                                                        "ipRanges", Map.of(
                                                                "items", java.util.List.of(
                                                                        Map.of("cidrIp", "0.0.0.0/0")
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );

        mockMvc.perform(post("/internal/aws/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Guardrails-Ingestion-Secret", "test-ingestion-secret")
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isAccepted());

        ArgumentCaptor<EventDTO> eventCaptor = ArgumentCaptor.forClass(EventDTO.class);
        verify(eventProducer).sendEvent(eventCaptor.capture());

        EventDTO event = eventCaptor.getValue();
        assertEquals("evt-123", event.getExternalEventId());
        assertEquals("AuthorizeSecurityGroupIngress", event.getEventType());
        assertEquals("sg-123456", event.getResourceId());
        assertEquals(organization.getId(), event.getOrganizationId());
        assertEquals(account.getId(), event.getCloudAccountId());
        assertEquals(22, event.getPayload().get("port"));
        assertEquals("0.0.0.0/0", event.getPayload().get("cidr"));
        assertEquals("203.0.113.10", event.getPayload().get("sourceIp"));
        assertTrue(event.getPayload().containsKey("rawEventBridgeEnvelope"));
    }
}
