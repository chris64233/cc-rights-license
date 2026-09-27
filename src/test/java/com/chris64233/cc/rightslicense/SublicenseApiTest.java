package com.chris64233.cc.rightslicense;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

@AutoConfigureMockMvc
class SublicenseApiTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void fullSublicenseFlowOverRestApi() throws Exception {
        String code = "SUB-API-" + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post("/api/works").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"title\":\"转授权 REST 作品\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/works/{code}/holders", code).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"甲\",\"sharePercent\":100.00}")).andExpect(status().isCreated());

        // 根授权：独占、允许转授到第 3 层
        String rootBody = mockMvc.perform(post("/api/works/{code}/applications", code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"licensee":"平台A","type":"EXCLUSIVE",
                                 "startDate":"2026-01-01","endDate":"2026-12-31",
                                 "territories":["CN","JP"],"media":["TV","WEB"],
                                 "sublicense":{"allowed":true,"maxDepth":3}}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long rootAppId = objectMapper.readTree(rootBody).get("id").asLong();

        long holderId = objectMapper.readTree(
                mockMvc.perform(get("/api/works/{code}/holders", code)).andReturn()
                        .getResponse().getContentAsString()).get(0).get("id").asLong();
        mockMvc.perform(post("/api/applications/{id}/decisions", rootAppId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"holderId\":" + holderId + ",\"decision\":\"APPROVE\",\"eventNumber\":\"g1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.applicationStatus").value("APPROVED"));

        String rootGrantNo = "G-" + rootAppId;

        // 平台 A 向渠道 B 转授
        mockMvc.perform(post("/api/sublicenses").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sublicenseNo":"SUB-API-1","parentGrantNo":"%s",
                                 "licensee":"渠道B","type":"EXCLUSIVE",
                                 "startDate":"2026-02-01","endDate":"2026-06-30",
                                 "territories":["CN"],"media":["TV"],
                                 "sublicense":{"allowed":false}}
                                """.formatted(rootGrantNo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.parentVersion").value(1));

        long subAppId = objectMapper.readTree(mockMvc.perform(get("/api/sublicenses/by-no/{no}", "SUB-API-1"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        // 非持权方不能审批
        mockMvc.perform(post("/api/sublicenses/{id}/decisions", subAppId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decider\":\"陌生人\",\"decision\":\"APPROVE\",\"eventNumber\":\"d0\"}"))
                .andExpect(status().isBadRequest());

        // 持权方平台 A 批准
        mockMvc.perform(post("/api/sublicenses/{id}/decisions", subAppId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decider\":\"平台A\",\"decision\":\"APPROVE\",\"eventNumber\":\"d1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.applicationStatus").value("APPROVED"))
                .andExpect(jsonPath("$.parentVersion").value(1));

        // 权利树
        mockMvc.perform(get("/api/grants/{no}/tree", rootGrantNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grant.grantNo").value(rootGrantNo))
                .andExpect(jsonPath("$.children[0].grant.grantNo").value("SUB-API-1"))
                .andExpect(jsonPath("$.children[0].grant.depth").value(2));

        // 撤销根，下级随之终止
        mockMvc.perform(post("/api/grants/{no}/revoke", rootGrantNo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventNo\":\"rev-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TERMINATED"));
        mockMvc.perform(post("/api/grants/propagations/retry")).andExpect(status().isOk());
        mockMvc.perform(get("/api/grants/{no}", "SUB-API-1"))
                .andExpect(jsonPath("$.status").value("TERMINATED"))
                .andExpect(jsonPath("$.haltReason").value("PARENT_REVOKED"));
        mockMvc.perform(get("/api/grants/propagations/pending-count"))
                .andExpect(jsonPath("$.pending").value(0));
    }
}
