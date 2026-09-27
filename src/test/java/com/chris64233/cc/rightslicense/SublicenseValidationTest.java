package com.chris64233.cc.rightslicense;

import com.chris64233.cc.rightslicense.domain.LicenseGrant;
import com.chris64233.cc.rightslicense.domain.LicenseType;
import com.chris64233.cc.rightslicense.domain.SublicenseApplication;
import com.chris64233.cc.rightslicense.domain.SublicensePolicy;
import com.chris64233.cc.rightslicense.domain.SublicenseStatus;
import com.chris64233.cc.rightslicense.domain.Work;
import com.chris64233.cc.rightslicense.service.BusinessException;
import com.chris64233.cc.rightslicense.service.SublicenseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class SublicenseValidationTest {

    private static final SublicensePolicy SUB_ALLOWED =
            new SublicensePolicy(true, null, List.of(), List.of(), null, null);

    @Autowired
    private SublicenseService sublicenseService;
    @Autowired
    private TestFixtures fixtures;

    private LicenseGrant rootWith(SublicensePolicy policy, LicenseType type) {
        Work work = fixtures.createWork("100");
        return fixtures.approveRootGrant(work, "发行商甲", type,
                "2026-01-01", "2026-12-31", List.of("CN", "JP"), List.of("TV", "WEB"), policy);
    }

    @Test
    void sublicenseMustBeWithinTerritoryMediaAndDateBounds() {
        LicenseGrant root = rootWith(SUB_ALLOWED, LicenseType.EXCLUSIVE);

        assertThatThrownBy(() -> fixtures.createSublicense(root, "渠道A", LicenseType.EXCLUSIVE,
                "2026-01-01", "2026-12-31", List.of("US"), List.of("TV"), SUB_ALLOWED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("地域");
        assertThatThrownBy(() -> fixtures.createSublicense(root, "渠道A", LicenseType.EXCLUSIVE,
                "2026-01-01", "2026-12-31", List.of("CN"), List.of("RADIO"), SUB_ALLOWED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("媒介");
        assertThatThrownBy(() -> fixtures.createSublicense(root, "渠道A", LicenseType.EXCLUSIVE,
                "2025-12-31", "2026-12-31", List.of("CN"), List.of("TV"), SUB_ALLOWED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("期限");
        assertThatThrownBy(() -> fixtures.createSublicense(root, "渠道A", LicenseType.EXCLUSIVE,
                "2026-01-01", "2027-01-01", List.of("CN"), List.of("TV"), SUB_ALLOWED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("期限");
    }

    @Test
    void policyCanFurtherRestrictTerritoriesMediaDatesAndLevels() {
        SublicensePolicy policy = new SublicensePolicy(true, 1,
                List.of("CN"), List.of("TV"),
                java.time.LocalDate.parse("2026-03-01"), java.time.LocalDate.parse("2026-09-30"));
        LicenseGrant root = rootWith(policy, LicenseType.EXCLUSIVE);

        // 落在策略缩限范围内：允许
        SublicenseApplication allowed = fixtures.createSublicense(root, "渠道A",
                LicenseType.EXCLUSIVE, "2026-03-01", "2026-09-30",
                List.of("CN"), List.of("TV"), SUB_ALLOWED);
        fixtures.approveSublicense(allowed);
        LicenseGrant child = sublicenseService.getRightsTree(root.getId()).stream()
                .filter(g -> g.getDepth() == 1).findFirst().orElseThrow();

        // 地域 JP 不在上级允许转授的地域内
        assertThatThrownBy(() -> fixtures.createSublicense(root, "渠道B", LicenseType.EXCLUSIVE,
                "2026-03-01", "2026-09-30", List.of("JP"), List.of("TV"), SUB_ALLOWED))
                .isInstanceOf(BusinessException.class).hasMessageContaining("地域");
        // 日期超出策略期限
        assertThatThrownBy(() -> fixtures.createSublicense(root, "渠道B", LicenseType.EXCLUSIVE,
                "2026-10-01", "2026-10-31", List.of("CN"), List.of("TV"), SUB_ALLOWED))
                .isInstanceOf(BusinessException.class).hasMessageContaining("期限");
        // 只允许再向下 1 层：child 已位于允许的最深层（第 1 层），其转授权策略被夹取为不可再转授
        LicenseGrant reloaded = sublicenseService.getGrant(child.getId());
        assertThat(reloaded.isSublicensable()).isFalse();
        assertThatThrownBy(() -> fixtures.createSublicense(child, "渠道C", LicenseType.EXCLUSIVE,
                "2026-03-01", "2026-09-30", List.of("CN"), List.of("TV"), SUB_ALLOWED))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不允许转授权");
    }

    @Test
    void childPolicyIsClampedAndCannotReGrantBeyondItsOwnScope() {
        // 根授权可转授 CN/JP × TV/WEB；child 只取得 CN × TV，则其下级策略也只能落在 CN × TV 内
        LicenseGrant root = rootWith(SUB_ALLOWED, LicenseType.EXCLUSIVE);
        SublicenseApplication childApp = fixtures.createSublicense(root, "渠道A",
                LicenseType.EXCLUSIVE, "2026-02-01", "2026-11-30",
                List.of("CN"), List.of("TV"), SUB_ALLOWED);
        fixtures.approveSublicense(childApp);
        LicenseGrant child = sublicenseService.getRightsTree(root.getId()).stream()
                .filter(g -> g.getDepth() == 1).findFirst().orElseThrow();

        assertThatThrownBy(() -> fixtures.createSublicense(child, "渠道C",
                LicenseType.EXCLUSIVE, "2026-03-01", "2026-10-31",
                List.of("JP"), List.of("TV"), SUB_ALLOWED))
                .isInstanceOf(BusinessException.class).hasMessageContaining("地域");
        assertThatThrownBy(() -> fixtures.createSublicense(child, "渠道C",
                LicenseType.EXCLUSIVE, "2026-03-01", "2026-10-31",
                List.of("CN"), List.of("WEB"), SUB_ALLOWED))
                .isInstanceOf(BusinessException.class).hasMessageContaining("媒介");
    }

    @Test
    void applicantMustBeCurrentLicenseeAndSublicensingMustBeEnabled() {
        LicenseGrant root = rootWith(SublicensePolicy.disabled(), LicenseType.EXCLUSIVE);

        assertThatThrownBy(() -> fixtures.createSublicense(root, "渠道A", LicenseType.EXCLUSIVE,
                "2026-01-01", "2026-06-30", List.of("CN"), List.of("TV"), SUB_ALLOWED))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不允许转授权");

        Work work2 = fixtures.createWork("100");
        LicenseGrant enabled = fixtures.approveRootGrant(work2, "发行商乙", LicenseType.EXCLUSIVE,
                "2026-01-01", "2026-12-31", List.of("CN"), List.of("TV"), SUB_ALLOWED);
        assertThatThrownBy(() -> sublicenseService.createApplication(
                "SUB-X-" + System.nanoTime(), enabled.getId(), "冒名者", "渠道A",
                LicenseType.EXCLUSIVE, java.time.LocalDate.parse("2026-01-01"),
                java.time.LocalDate.parse("2026-06-30"),
                List.of("CN"), List.of("TV"), SUB_ALLOWED))
                .isInstanceOf(BusinessException.class).hasMessageContaining("当前被授权方");
    }

    @Test
    void nonExclusiveParentCannotGrantExclusiveButCanGrantNonExclusive() {
        LicenseGrant root = rootWith(SUB_ALLOWED, LicenseType.NON_EXCLUSIVE);

        assertThatThrownBy(() -> fixtures.createSublicense(root, "渠道A", LicenseType.EXCLUSIVE,
                "2026-01-01", "2026-06-30", List.of("CN"), List.of("TV"), SUB_ALLOWED))
                .isInstanceOf(BusinessException.class).hasMessageContaining("独占");

        SublicenseApplication nonExclusive = fixtures.createSublicense(root, "渠道A",
                LicenseType.NON_EXCLUSIVE, "2026-01-01", "2026-06-30",
                List.of("CN"), List.of("TV"), SUB_ALLOWED);
        fixtures.approveSublicense(nonExclusive);
        assertThat(sublicenseService.getApplication(nonExclusive.getId()).getStatus())
                .isEqualTo(SublicenseStatus.APPROVED);
    }

    @Test
    void multiCellApplicationIsValidatedAtomically() {
        // JP × WEB 这一组合虽然本身合法，但 CN × TV 与已有独占下级冲突，整份申请必须整体失败
        LicenseGrant root = rootWith(SUB_ALLOWED, LicenseType.EXCLUSIVE);
        SublicenseApplication first = fixtures.createSublicense(root, "渠道A",
                LicenseType.EXCLUSIVE, "2026-01-01", "2026-12-31",
                List.of("CN"), List.of("TV"), SUB_ALLOWED);
        fixtures.approveSublicense(first);

        SublicenseApplication second = fixtures.createSublicense(root, "渠道B",
                LicenseType.EXCLUSIVE, "2026-06-01", "2026-06-30",
                List.of("CN", "JP"), List.of("TV", "WEB"), SUB_ALLOWED);
        fixtures.approveSublicense(second);
        assertThat(sublicenseService.getApplication(second.getId()).getStatus())
                .isEqualTo(SublicenseStatus.CONFLICT);
        // 没有任何部分生效：树中仍只有 1 个下级
        assertThat(sublicenseService.getRightsTree(root.getId()))
                .filteredOn(g -> g.getDepth() == 1).hasSize(1);
    }

    @Test
    void newSublicenseRejectedWhenParentNoLongerActive() {
        LicenseGrant root = rootWith(SUB_ALLOWED, LicenseType.EXCLUSIVE);
        sublicenseService.revokeGrant(root.getId());

        assertThatThrownBy(() -> fixtures.createSublicense(root, "渠道A", LicenseType.EXCLUSIVE,
                "2026-01-01", "2026-06-30", List.of("CN"), List.of("TV"), SUB_ALLOWED))
                .isInstanceOf(BusinessException.class).hasMessageContaining("TERMINATED");
    }
}
