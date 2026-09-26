package com.chris64233.cc.rightslicense;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LicenseApiTest {

    @Autowired
    MockMvc mockMvc;

    private String createWork(String code, String holdersJson) throws Exception {
        mockMvc.perform(post("/api/works")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workCode":"%s","title":"测试作品","rightHolders":%s}
                                """.formatted(code, holdersJson)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalSharePercent").value(100));
        return code;
    }

    private long createApplication(String workCode, String type, String territories,
                                   String media, String start, String end) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/works/{code}/applications", workCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"licensee":"被授权方","licenseType":"%s","territories":%s,"media":%s,
                                 "startDate":"%s","endDate":"%s"}
                                """.formatted(type, territories, media, start, end)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private void decide(long applicationId, String holder, String decision, String eventId, int status)
            throws Exception {
        mockMvc.perform(post("/api/applications/{id}/decisions", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"holderName":"%s","decision":"%s","eventId":"%s"}
                                """.formatted(holder, decision, eventId)))
                .andExpect(status().is(status));
    }

    @Test
    void fullApprovalFlowOverRest() throws Exception {
        String code = "W-" + UUID.randomUUID();
        createWork(code, """
                [{"holderName":"甲","sharePercent":60},{"holderName":"乙","sharePercent":40}]""");

        long appId = createApplication(code, "EXCLUSIVE", "[\"CN\"]", "[\"STREAM\"]",
                "2026-01-01", "2026-12-31");

        decide(appId, "甲", "APPROVE", "evt-api-1", 200);
        mockMvc.perform(get("/api/applications/{id}/progress", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.approvedSharePercent").value(60))
                .andExpect(jsonPath("$.remainingSharePercent").value(40))
                .andExpect(jsonPath("$.decisions", hasSize(1)));

        decide(appId, "乙", "APPROVE", "evt-api-2", 200);
        mockMvc.perform(get("/api/applications/{id}", appId))
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(get("/api/works/{code}/calendar", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].licenseType").value("EXCLUSIVE"))
                .andExpect(jsonPath("$[0].territories[0]").value("CN"));
    }

    @Test
    void conflictFlowExposesReasonAndDetails() throws Exception {
        String code = "W-" + UUID.randomUUID();
        createWork(code, "[{\"holderName\":\"甲\",\"sharePercent\":100}]");

        long first = createApplication(code, "EXCLUSIVE", "[\"CN\"]", "[\"STREAM\"]",
                "2026-01-01", "2026-12-31");
        decide(first, "甲", "APPROVE", "evt-cf-1", 200);

        long second = createApplication(code, "NON_EXCLUSIVE", "[\"CN\"]", "[\"STREAM\"]",
                "2026-06-01", "2026-06-30");
        decide(second, "甲", "APPROVE", "evt-cf-2", 200);

        mockMvc.perform(get("/api/applications/{id}", second))
                .andExpect(jsonPath("$.status").value("CONFLICT"))
                .andExpect(jsonPath("$.conflictReason", containsString("冲突")));

        mockMvc.perform(get("/api/applications/{id}/conflicts", second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].licenseType").value("EXCLUSIVE"))
                .andExpect(jsonPath("$[0].overlappingTerritories[0]").value("CN"));
    }

    @Test
    void validationAndIdempotencyOverRest() throws Exception {
        String code = "W-" + UUID.randomUUID();

        // 份额不等于 100% → 400
        mockMvc.perform(post("/api/works")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workCode":"%s","title":"x","rightHolders":[{"holderName":"甲","sharePercent":90}]}
                                """.formatted(code)))
                .andExpect(status().isBadRequest());

        createWork(code, "[{\"holderName\":\"甲\",\"sharePercent\":100}]");

        // 结束日期早于开始日期 → 400
        mockMvc.perform(post("/api/works/{code}/applications", code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"licensee":"x","licenseType":"EXCLUSIVE","territories":["CN"],"media":["TV"],
                                 "startDate":"2026-12-31","endDate":"2026-01-01"}
                                """))
                .andExpect(status().isBadRequest());

        long appId = createApplication(code, "NON_EXCLUSIVE", "[\"CN\"]", "[\"TV\"]",
                "2026-01-01", "2026-12-31");

        // 事件号幂等：重复提交返回相同结果而非报错
        decide(appId, "甲", "APPROVE", "evt-idem-api", 200);
        decide(appId, "甲", "APPROVE", "evt-idem-api", 200);
        mockMvc.perform(get("/api/applications/{id}/progress", appId))
                .andExpect(jsonPath("$.decisions", hasSize(1)))
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // 已结束的申请拒绝新决定 → 409
        decide(appId, "甲", "APPROVE", "evt-late", 409);

        // 不存在的作品/申请 → 404
        mockMvc.perform(get("/api/works/{code}", "NOPE")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/applications/{id}", 999999)).andExpect(status().isNotFound());
    }
}
