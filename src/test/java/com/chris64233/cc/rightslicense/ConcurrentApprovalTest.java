package com.chris64233.cc.rightslicense;

import com.chris64233.cc.rightslicense.domain.ApplicationStatus;
import com.chris64233.cc.rightslicense.domain.DecisionValue;
import com.chris64233.cc.rightslicense.domain.LicenseApplication;
import com.chris64233.cc.rightslicense.domain.LicenseType;
import com.chris64233.cc.rightslicense.domain.RightsHolder;
import com.chris64233.cc.rightslicense.domain.Work;
import com.chris64233.cc.rightslicense.service.LicenseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ConcurrentApprovalTest extends AbstractIntegrationTest {

    @Autowired
    private LicenseService licenseService;

    @Autowired
    private TestFixtures fixtures;

    @Test
    void onlyOneOfTwoConcurrentConflictingApplicationsIsApproved() throws Exception {
        Work work = fixtures.createWork("50", "50");
        List<RightsHolder> holders = fixtures.holdersOf(work);

        LicenseApplication first = fixtures.createApplication(work, LicenseType.EXCLUSIVE,
                "2026-01-01", "2026-12-31", List.of("CN"), List.of("TV"));
        LicenseApplication second = fixtures.createApplication(work, LicenseType.EXCLUSIVE,
                "2026-01-01", "2026-12-31", List.of("CN"), List.of("TV"));

        licenseService.submitDecision(first.getId(), holders.get(0).getId(), DecisionValue.APPROVE, "a-1");
        licenseService.submitDecision(second.getId(), holders.get(0).getId(), DecisionValue.APPROVE, "b-1");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Future<?> firstFuture = executor.submit(() -> {
            ready.countDown();
            await(go);
            licenseService.submitDecision(first.getId(), holders.get(1).getId(), DecisionValue.APPROVE, "a-2");
        });
        Future<?> secondFuture = executor.submit(() -> {
            ready.countDown();
            await(go);
            licenseService.submitDecision(second.getId(), holders.get(1).getId(), DecisionValue.APPROVE, "b-2");
        });
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        firstFuture.get(30, TimeUnit.SECONDS);
        secondFuture.get(30, TimeUnit.SECONDS);
        executor.shutdown();

        ApplicationStatus firstStatus = licenseService.getApplication(first.getId()).getStatus();
        ApplicationStatus secondStatus = licenseService.getApplication(second.getId()).getStatus();
        assertThat(List.of(firstStatus, secondStatus))
                .containsExactlyInAnyOrder(ApplicationStatus.APPROVED, ApplicationStatus.CONFLICT);

        LicenseApplication conflicted = firstStatus == ApplicationStatus.CONFLICT
                ? licenseService.getApplication(first.getId())
                : licenseService.getApplication(second.getId());
        assertThat(conflicted.getConflictReason()).isNotBlank();
        assertThat(licenseService.listDecisions(conflicted.getId())).hasSize(2);

        assertThat(licenseService.getCalendar(work.getCode(),
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"))).hasSize(1);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
