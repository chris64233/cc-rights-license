package com.chris64233.cc.rightslicense;

import com.chris64233.cc.rightslicense.domain.GrantStatus;
import com.chris64233.cc.rightslicense.domain.LicenseGrant;
import com.chris64233.cc.rightslicense.domain.LicenseType;
import com.chris64233.cc.rightslicense.domain.SublicenseApplication;
import com.chris64233.cc.rightslicense.domain.SublicensePolicy;
import com.chris64233.cc.rightslicense.domain.SublicenseStatus;
import com.chris64233.cc.rightslicense.domain.Work;
import com.chris64233.cc.rightslicense.service.SublicenseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SublicenseConcurrencyTest {

    private static final SublicensePolicy SUB_ALLOWED =
            new SublicensePolicy(true, null, List.of(), List.of(), null, null);

    @Autowired
    private SublicenseService sublicenseService;
    @Autowired
    private com.chris64233.cc.rightslicense.service.CascadeService cascadeService;
    @Autowired
    private TestFixtures fixtures;

    @Test
    void approvalRacingWithParentRevocationCannotEscape() throws Exception {
        Work work = fixtures.createWork("100");
        LicenseGrant root = fixtures.approveRootGrant(work, "发行商", LicenseType.EXCLUSIVE,
                "2026-01-01", "2028-12-31", List.of("CN"), List.of("TV"), SUB_ALLOWED);
        SublicenseApplication child = fixtures.createSublicense(root, "渠道A",
                LicenseType.EXCLUSIVE, "2026-02-01", "2028-11-30",
                List.of("CN"), List.of("TV"), SUB_ALLOWED);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Future<?> revoke = executor.submit(() -> {
            ready.countDown();
            await(go);
            sublicenseService.revokeGrant(root.getId());
        });
        Future<?> approve = executor.submit(() -> {
            ready.countDown();
            await(go);
            sublicenseService.submitDecision(child.getId(),
                    com.chris64233.cc.rightslicense.domain.DecisionValue.APPROVE,
                    "race-evt-" + UUID.randomUUID());
        });
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        revoke.get(30, TimeUnit.SECONDS);
        approve.get(30, TimeUnit.SECONDS);
        executor.shutdown();
        // 撤销事务的级联传播在提交后异步执行，这里显式排空待处理任务后再断言
        cascadeService.processPending();

        SublicenseStatus status = sublicenseService.getApplication(child.getId()).getStatus();
        List<LicenseGrant> tree = sublicenseService.getRightsTree(root.getId());

        if (status == SublicenseStatus.APPROVED) {
            // 批准先于撤销：下级短暂生效，但撤销必须级联到它
            LicenseGrant childGrant = tree.stream().filter(g -> g.getDepth() == 1).findFirst().orElseThrow();
            assertThat(childGrant.getStatus()).isEqualTo(GrantStatus.TERMINATED);
        } else {
            // 撤销先于批准：申请不能逃逸为生效授权
            assertThat(status).isEqualTo(SublicenseStatus.CONFLICT);
            assertThat(tree).filteredOn(g -> g.getDepth() == 1).isEmpty();
        }
        // 任何情况下树中都不存在有效的下级授权
        assertThat(tree).filteredOn(g -> g.getStatus() == GrantStatus.ACTIVE && g.getDepth() > 0)
                .isEmpty();
    }

    @Test
    void twoConcurrentExclusiveSublicensesOnSameScopeCannotBothActivate() throws Exception {
        Work work = fixtures.createWork("100");
        LicenseGrant root = fixtures.approveRootGrant(work, "发行商", LicenseType.EXCLUSIVE,
                "2026-01-01", "2028-12-31", List.of("CN"), List.of("TV"), SUB_ALLOWED);
        SublicenseApplication first = fixtures.createSublicense(root, "渠道A",
                LicenseType.EXCLUSIVE, "2026-02-01", "2028-11-30",
                List.of("CN"), List.of("TV"), SUB_ALLOWED);
        SublicenseApplication second = fixtures.createSublicense(root, "渠道B",
                LicenseType.EXCLUSIVE, "2026-02-01", "2028-11-30",
                List.of("CN"), List.of("TV"), SUB_ALLOWED);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Future<?> f1 = executor.submit(() -> {
            ready.countDown();
            await(go);
            sublicenseService.submitDecision(first.getId(),
                    com.chris64233.cc.rightslicense.domain.DecisionValue.APPROVE, "c-1");
        });
        Future<?> f2 = executor.submit(() -> {
            ready.countDown();
            await(go);
            sublicenseService.submitDecision(second.getId(),
                    com.chris64233.cc.rightslicense.domain.DecisionValue.APPROVE, "c-2");
        });
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        f1.get(30, TimeUnit.SECONDS);
        f2.get(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(List.of(
                sublicenseService.getApplication(first.getId()).getStatus(),
                sublicenseService.getApplication(second.getId()).getStatus()))
                .containsExactlyInAnyOrder(SublicenseStatus.APPROVED, SublicenseStatus.CONFLICT);
        assertThat(sublicenseService.getRightsTree(root.getId()))
                .filteredOn(g -> g.getDepth() == 1 && g.getStatus() == GrantStatus.ACTIVE)
                .hasSize(1);
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
