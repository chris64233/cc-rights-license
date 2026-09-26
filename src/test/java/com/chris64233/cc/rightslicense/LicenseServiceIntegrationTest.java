package com.chris64233.cc.rightslicense;

import com.chris64233.cc.rightslicense.domain.ApplicationStatus;
import com.chris64233.cc.rightslicense.domain.DecisionType;
import com.chris64233.cc.rightslicense.domain.LicenseType;
import com.chris64233.cc.rightslicense.service.BusinessException;
import com.chris64233.cc.rightslicense.service.ConflictDetail;
import com.chris64233.cc.rightslicense.service.LicenseService;
import com.chris64233.cc.rightslicense.service.WorkService;
import com.chris64233.cc.rightslicense.web.dto.ApplicationResponse;
import com.chris64233.cc.rightslicense.web.dto.CreateApplicationRequest;
import com.chris64233.cc.rightslicense.web.dto.CreateWorkRequest;
import com.chris64233.cc.rightslicense.web.dto.DecisionRequest;
import com.chris64233.cc.rightslicense.web.dto.ProgressResponse;
import com.chris64233.cc.rightslicense.web.dto.RightHolderInput;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class LicenseServiceIntegrationTest {

    @Autowired
    WorkService workService;

    @Autowired
    LicenseService licenseService;

    private String newWork(String... holderShares) {
        String code = "W-" + UUID.randomUUID();
        List<RightHolderInput> holders = new java.util.ArrayList<>();
        for (int i = 0; i < holderShares.length; i++) {
            holders.add(new RightHolderInput("holder-" + i, new BigDecimal(holderShares[i])));
        }
        workService.createWork(new CreateWorkRequest(code, "测试作品", holders));
        return code;
    }

    private ApplicationResponse newApplication(String workCode, LicenseType type,
                                               Set<String> territories, Set<String> media,
                                               String start, String end) {
        return licenseService.createApplication(workCode, new CreateApplicationRequest(
                "licensee-" + UUID.randomUUID(), type, territories, media,
                LocalDate.parse(start), LocalDate.parse(end)));
    }

    private void approveAll(String workCode, Long applicationId, int holderCount) {
        for (int i = 0; i < holderCount; i++) {
            licenseService.recordDecision(applicationId, new DecisionRequest(
                    "holder-" + i, DecisionType.APPROVE, "evt-" + applicationId + "-" + i));
        }
    }

    @Test
    void workSharesMustSumToExactly100() {
        String code = "W-" + UUID.randomUUID();
        CreateWorkRequest bad = new CreateWorkRequest(code, "作品", List.of(
                new RightHolderInput("a", new BigDecimal("60")),
                new RightHolderInput("b", new BigDecimal("30"))));
        assertThatThrownBy(() -> workService.createWork(bad))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("100%");

        String okCode = newWork("33.33", "33.33", "33.34");
        assertThat(workService.getWork(okCode).totalSharePercent().compareTo(new BigDecimal("100"))).isZero();
    }

    @Test
    void duplicateWorkCodeRejected() {
        String code = newWork("100");
        assertThatThrownBy(() -> workService.createWork(new CreateWorkRequest(code, "重复",
                List.of(new RightHolderInput("x", new BigDecimal("100"))))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void approvalRequiresFullHundredPercent() {
        String code = newWork("50", "50");
        ApplicationResponse app = newApplication(code, LicenseType.NON_EXCLUSIVE,
                Set.of("CN"), Set.of("STREAM"), "2026-01-01", "2026-12-31");

        ProgressResponse progress = licenseService.recordDecision(app.id(),
                new DecisionRequest("holder-0", DecisionType.APPROVE, "evt-p1"));
        assertThat(progress.status()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(progress.approvedSharePercent().compareTo(new BigDecimal("50"))).isZero();
        assertThat(progress.remainingSharePercent().compareTo(new BigDecimal("50"))).isZero();

        progress = licenseService.recordDecision(app.id(),
                new DecisionRequest("holder-1", DecisionType.APPROVE, "evt-p2"));
        assertThat(progress.status()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(licenseService.getCalendar(code)).hasSize(1);
    }

    @Test
    void rejectTerminatesApplicationAndBlocksFurtherDecisions() {
        String code = newWork("50", "50");
        ApplicationResponse app = newApplication(code, LicenseType.EXCLUSIVE,
                Set.of("CN"), Set.of("STREAM"), "2026-01-01", "2026-12-31");

        ProgressResponse progress = licenseService.recordDecision(app.id(),
                new DecisionRequest("holder-0", DecisionType.REJECT, "evt-r1"));
        assertThat(progress.status()).isEqualTo(ApplicationStatus.REJECTED);

        assertThatThrownBy(() -> licenseService.recordDecision(app.id(),
                new DecisionRequest("holder-1", DecisionType.APPROVE, "evt-r2")))
                .isInstanceOf(BusinessException.class);
        assertThat(licenseService.getCalendar(code)).isEmpty();
    }

    @Test
    void holderCanOnlyDecideOnceAndEventIdIsIdempotent() {
        String code = newWork("50", "50");
        ApplicationResponse app = newApplication(code, LicenseType.NON_EXCLUSIVE,
                Set.of("CN"), Set.of("STREAM"), "2026-01-01", "2026-12-31");

        DecisionRequest request = new DecisionRequest("holder-0", DecisionType.APPROVE, "evt-idem-1");
        ProgressResponse first = licenseService.recordDecision(app.id(), request);
        ProgressResponse replay = licenseService.recordDecision(app.id(), request);
        assertThat(replay.decisions()).hasSize(1);
        assertThat(replay.status()).isEqualTo(first.status());

        assertThatThrownBy(() -> licenseService.recordDecision(app.id(),
                new DecisionRequest("holder-0", DecisionType.APPROVE, "evt-idem-2")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不可重复");

        assertThatThrownBy(() -> licenseService.recordDecision(app.id(),
                new DecisionRequest("stranger", DecisionType.APPROVE, "evt-idem-3")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("权利人");
    }

    @Test
    void exclusiveBlockedByExistingNonExclusiveOnOverlap() {
        String code = newWork("100");
        ApplicationResponse nonExclusive = newApplication(code, LicenseType.NON_EXCLUSIVE,
                Set.of("CN"), Set.of("STREAM"), "2026-01-01", "2026-12-31");
        approveAll(code, nonExclusive.id(), 1);
        assertThat(licenseService.getApplication(nonExclusive.id()).status()).isEqualTo(ApplicationStatus.APPROVED);

        ApplicationResponse exclusive = newApplication(code, LicenseType.EXCLUSIVE,
                Set.of("CN"), Set.of("STREAM"), "2026-06-01", "2027-05-31");
        approveAll(code, exclusive.id(), 1);

        ApplicationResponse result = licenseService.getApplication(exclusive.id());
        assertThat(result.status()).isEqualTo(ApplicationStatus.CONFLICT);
        assertThat(result.conflictReason()).contains("冲突");
        assertThat(licenseService.getConflicts(exclusive.id())).hasSize(1);
        assertThat(licenseService.getCalendar(code)).hasSize(1);
    }

    @Test
    void exclusiveBlockedByExistingExclusiveAndClosedIntervalTouches() {
        String code = newWork("100");
        ApplicationResponse first = newApplication(code, LicenseType.EXCLUSIVE,
                Set.of("CN"), Set.of("STREAM"), "2026-01-01", "2026-06-30");
        approveAll(code, first.id(), 1);

        // 闭区间：开始日期等于已有授权的结束日期也算重叠
        ApplicationResponse touching = newApplication(code, LicenseType.EXCLUSIVE,
                Set.of("CN"), Set.of("STREAM"), "2026-06-30", "2026-12-31");
        approveAll(code, touching.id(), 1);
        assertThat(licenseService.getApplication(touching.id()).status()).isEqualTo(ApplicationStatus.CONFLICT);

        // 紧挨着但不重叠则允许
        ApplicationResponse adjacent = newApplication(code, LicenseType.EXCLUSIVE,
                Set.of("CN"), Set.of("STREAM"), "2026-07-01", "2026-12-31");
        approveAll(code, adjacent.id(), 1);
        assertThat(licenseService.getApplication(adjacent.id()).status()).isEqualTo(ApplicationStatus.APPROVED);
    }

    @Test
    void nonExclusiveCanCoexistWithNonExclusiveButNotExclusive() {
        String code = newWork("100");
        ApplicationResponse first = newApplication(code, LicenseType.NON_EXCLUSIVE,
                Set.of("CN"), Set.of("STREAM"), "2026-01-01", "2026-12-31");
        approveAll(code, first.id(), 1);

        ApplicationResponse second = newApplication(code, LicenseType.NON_EXCLUSIVE,
                Set.of("CN"), Set.of("STREAM"), "2026-03-01", "2026-09-30");
        approveAll(code, second.id(), 1);
        assertThat(licenseService.getApplication(second.id()).status()).isEqualTo(ApplicationStatus.APPROVED);

        ApplicationResponse exclusive = newApplication(code, LicenseType.EXCLUSIVE,
                Set.of("CN"), Set.of("STREAM"), "2026-05-01", "2026-08-31");
        approveAll(code, exclusive.id(), 1);
        assertThat(licenseService.getApplication(exclusive.id()).status()).isEqualTo(ApplicationStatus.CONFLICT);

        // 非独占申请与已批准独占重叠时被阻挡
        String code2 = newWork("100");
        ApplicationResponse ex = newApplication(code2, LicenseType.EXCLUSIVE,
                Set.of("JP"), Set.of("TV"), "2026-01-01", "2026-12-31");
        approveAll(code2, ex.id(), 1);
        ApplicationResponse ne = newApplication(code2, LicenseType.NON_EXCLUSIVE,
                Set.of("JP"), Set.of("TV"), "2026-06-01", "2026-06-30");
        approveAll(code2, ne.id(), 1);
        assertThat(licenseService.getApplication(ne.id()).status()).isEqualTo(ApplicationStatus.CONFLICT);
    }

    @Test
    void disjointTerritoryOrMediaAllowsOverlap() {
        String code = newWork("100");
        ApplicationResponse first = newApplication(code, LicenseType.EXCLUSIVE,
                Set.of("CN"), Set.of("STREAM"), "2026-01-01", "2026-12-31");
        approveAll(code, first.id(), 1);

        ApplicationResponse otherTerritory = newApplication(code, LicenseType.EXCLUSIVE,
                Set.of("US"), Set.of("STREAM"), "2026-01-01", "2026-12-31");
        approveAll(code, otherTerritory.id(), 1);
        assertThat(licenseService.getApplication(otherTerritory.id()).status()).isEqualTo(ApplicationStatus.APPROVED);

        ApplicationResponse otherMedia = newApplication(code, LicenseType.EXCLUSIVE,
                Set.of("CN"), Set.of("TV"), "2026-01-01", "2026-12-31");
        approveAll(code, otherMedia.id(), 1);
        assertThat(licenseService.getApplication(otherMedia.id()).status()).isEqualTo(ApplicationStatus.APPROVED);
    }

    @Test
    void multiTerritoryMediaApprovalIsAtomic() {
        String code = newWork("100");
        ApplicationResponse existing = newApplication(code, LicenseType.EXCLUSIVE,
                Set.of("CN"), Set.of("STREAM"), "2026-01-01", "2026-12-31");
        approveAll(code, existing.id(), 1);

        // CN/STREAM 组合冲突，US/TV 组合空闲：整份申请必须失败，不能部分生效
        ApplicationResponse multi = newApplication(code, LicenseType.EXCLUSIVE,
                Set.of("CN", "US"), Set.of("STREAM", "TV"), "2026-06-01", "2026-06-30");
        approveAll(code, multi.id(), 1);

        ApplicationResponse result = licenseService.getApplication(multi.id());
        assertThat(result.status()).isEqualTo(ApplicationStatus.CONFLICT);
        assertThat(licenseService.getCalendar(code)).hasSize(1);
        List<ConflictDetail> conflicts = licenseService.getConflicts(multi.id());
        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.get(0).overlappingTerritories()).containsExactly("CN");
        assertThat(conflicts.get(0).overlappingMedia()).containsExactly("STREAM");
    }

    @Test
    void concurrentConflictingApprovalsAtMostOneSucceeds() throws Exception {
        String code = newWork("50", "50");
        ApplicationResponse appA = newApplication(code, LicenseType.EXCLUSIVE,
                Set.of("CN"), Set.of("STREAM"), "2026-01-01", "2026-12-31");
        ApplicationResponse appB = newApplication(code, LicenseType.EXCLUSIVE,
                Set.of("CN"), Set.of("STREAM"), "2026-06-01", "2027-05-31");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger errors = new AtomicInteger();
        for (Long appId : List.of(appA.id(), appB.id())) {
            executor.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    for (int i = 0; i < 2; i++) {
                        licenseService.recordDecision(appId, new DecisionRequest(
                                "holder-" + i, DecisionType.APPROVE,
                                "evt-conc-" + appId + "-" + i));
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            });
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        assertThat(errors.get()).isZero();

        ApplicationStatus statusA = licenseService.getApplication(appA.id()).status();
        ApplicationStatus statusB = licenseService.getApplication(appB.id()).status();
        assertThat(Set.of(statusA, statusB))
                .isEqualTo(Set.of(ApplicationStatus.APPROVED, ApplicationStatus.CONFLICT));
        assertThat(licenseService.getCalendar(code)).hasSize(1);

        // 失败申请保留完整决定记录并标记冲突原因
        Long failedId = statusA == ApplicationStatus.CONFLICT ? appA.id() : appB.id();
        ProgressResponse failed = licenseService.getProgress(failedId);
        assertThat(failed.decisions()).hasSize(2);
        assertThat(failed.conflictReason()).contains("冲突");
    }
}
