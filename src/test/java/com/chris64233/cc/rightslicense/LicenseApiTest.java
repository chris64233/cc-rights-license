package com.chris64233.cc.rightslicense;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LicenseApiTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void fullApprovalFlowOverRestApi() throws Exception {
        String code = "API-" + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post("/api/works")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"title\":\"REST 测试作品\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code));

        mockMvc.perform(post("/api/works/{code}/holders", code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"甲\",\"sharePercent\":50.00}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/works/{code}/holders", code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"乙\",\"sharePercent\":50.00}"))
                .andExpect(status().isCreated());

        String applicationBody = mockMvc.perform(post("/api/works/{code}/applications", code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"licensee":"被授权方A","type":"EXCLUSIVE",
                                 "startDate":"2026-01-01","endDate":"2026-12-31",
                                 "territories":["CN"],"media":["TV"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        long applicationId = objectMapper.readTree(applicationBody).get("id").asLong();

        String holders = mockMvc.perform(get("/api/works/{code}/holders", code))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode holderNodes = objectMapper.readTree(holders);
        long firstHolderId = holderNodes.get(0).get("id").asLong();
        long secondHolderId = holderNodes.get(1).get("id").asLong();

        mockMvc.perform(post("/api/applications/{id}/decisions", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"holderId\":" + firstHolderId
                                + ",\"decision\":\"APPROVE\",\"eventNumber\":\"e-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.applicationStatus").value("PENDING"));

        mockMvc.perform(post("/api/applications/{id}/decisions", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"holderId\":" + secondHolderId
                                + ",\"decision\":\"APPROVE\",\"eventNumber\":\"e-2\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.applicationStatus").value("APPROVED"));

        mockMvc.perform(get("/api/applications/{id}/progress", applicationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedSharePercent").value(100.00))
                .andExpect(jsonPath("$.pendingHolders").isEmpty());

        mockMvc.perform(get("/api/works/{code}/calendar", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].licensee").value("被授权方A"))
                .andExpect(jsonPath("$[0].type").value("EXCLUSIVE"));

        mockMvc.perform(get("/api/applications/{id}/conflicts", applicationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conflictingGrants").isEmpty());
    }

    @Test
    void invalidRequestReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/works")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\",\"title\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
