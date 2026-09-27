package com.chris64233.cc.rightslicense;

import com.chris64233.cc.rightslicense.domain.GrantStatus;
import com.chris64233.cc.rightslicense.domain.LicenseGrant;
import com.chris64233.cc.rightslicense.domain.LicenseType;
import com.chris64233.cc.rightslicense.domain.Work;
import com.chris64233.cc.rightslicense.service.SublicenseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SublicenseApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestFixtures fixtures;
    @Autowired
    private SublicenseService sublicenseService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void fullSublicenseFlowOverRestApi() throws Exception {
        Work work = fixtures.createWork("100");
        LicenseGrant root = fixtures.approveRootGrant(work, "发行商甲", LicenseType.EXCLUSIVE,
                "2026-01-01", "2028-12-31", List.of("CN", "JP"), List.of("TV", "WEB"),
                new com.chris64233.cc.rightslicense.domain.SublicensePolicy(
                        true, 2, List.of(), List.of(), null, null));
        String number = "SUB-API-" + UUID.randomUUID().toString().substring(0, 8);

        String body = mockMvc.perform(post("/api/grants/{id}/sublicenses", root.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sublicenseNumber":"%s","applicant":"发行商甲","sublicensee":"渠道A",
                                 "type":"EXCLUSIVE","startDate":"2026-02-01","endDate":"2028-11-30",
                                 "territories":["CN"],"media":["TV"],
                                 "sublicensePolicy":{"sublicensable":true,"maxLevels":1}}
                                """.formatted(number)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.parentGrantId").value(root.getId()))
                .andExpect(jsonPath("$.sublicensePolicy.maxLevels").value(1))
                .andReturn().getResponse().getContentAsString();
        long subId = objectMapper.readTree(body).get("id").asLong();

        // 同号重复提交：幂等返回同一条
        mockMvc.perform(post("/api/grants/{id}/sublicenses", root.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sublicenseNumber":"%s","applicant":"发行商甲","sublicensee":"渠道A",
                                 "type":"EXCLUSIVE","startDate":"2026-02-01","endDate":"2028-11-30",
                                 "territories":["CN"],"media":["TV"]}
                                """.formatted(number)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(subId));

        mockMvc.perform(post("/api/sublicenses/{id}/decisions", subId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"eventNumber\":\"evt-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.applicationStatus").value("APPROVED"));

        // 权利树与有效范围
        mockMvc.perform(get("/api/grants/{id}/tree", root.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].depth").value(1))
                .andExpect(jsonPath("$[1].chainPath").exists());

        mockMvc.perform(get("/api/grants/{id}/effective-scope", root.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effective").value(true))
                .andExpect(jsonPath("$.territories[0]").value("CN"));

        // 撤销根授权后，下级级联终止
        mockMvc.perform(post("/api/grants/{id}/revoke", root.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TERMINATED"));

        JsonNode tree = objectMapper.readTree(mockMvc.perform(get("/api/grants/{id}/tree", root.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long childGrantId = tree.get(1).get("id").asLong();

        JsonNode tasks = objectMapper.readTree(mockMvc.perform(get("/api/cascade/tasks"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode childTask = findNode(tasks, childGrantId);
        org.assertj.core.api.Assertions.assertThat(childTask).isNotNull();
        org.assertj.core.api.Assertions.assertThat(childTask.get("status").asText()).isEqualTo("DONE");

        mockMvc.perform(get("/api/grants/{id}/tree", root.getId()))
                .andExpect(jsonPath("$[1].status").value("TERMINATED"));
    }

    private static JsonNode findNode(JsonNode tasks, long targetGrantId) {
        for (JsonNode task : tasks) {
            if (task.get("targetGrantId").asLong() == targetGrantId) {
                return task;
            }
        }
        return null;
    }

    @Test
    void outOfScopeSublicenseReturns422() throws Exception {
        Work work = fixtures.createWork("100");
        LicenseGrant root = fixtures.approveRootGrant(work, "发行商甲", LicenseType.EXCLUSIVE,
                "2026-01-01", "2028-12-31", List.of("CN"), List.of("TV"),
                new com.chris64233.cc.rightslicense.domain.SublicensePolicy(
                        true, null, List.of(), List.of(), null, null));

        mockMvc.perform(post("/api/grants/{id}/sublicenses", root.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sublicenseNumber":"SUB-BAD-1","applicant":"发行商甲","sublicensee":"渠道A",
                                 "type":"EXCLUSIVE","startDate":"2026-02-01","endDate":"2028-11-30",
                                 "territories":["US"],"media":["TV"]}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }
}
