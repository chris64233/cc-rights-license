package com.chris64233.cc.rightslicense;

import com.chris64233.cc.rightslicense.domain.ApplicationStatus;
import com.chris64233.cc.rightslicense.domain.DecisionValue;
import com.chris64233.cc.rightslicense.domain.LicenseApplication;
import com.chris64233.cc.rightslicense.domain.LicenseType;
import com.chris64233.cc.rightslicense.domain.RightsDecision;
import com.chris64233.cc.rightslicense.domain.RightsHolder;
import com.chris64233.cc.rightslicense.domain.Work;
import com.chris64233.cc.rightslicense.service.BusinessException;
import com.chris64233.cc.rightslicense.service.LicenseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class LicenseApprovalTest extends AbstractIntegrationTest {

    @Autowired
    private LicenseService licenseService;

    @Autowired
    private TestFixtures fixtures;

    @Test
    void approvalRequiresFullHundredPercentConsent() {
        Work work = fixtures.createWork("50", "50");
        List<RightsHolder> holders = fixtures.holdersOf(work);
        LicenseApplication application = fixtures.createApplication(work, LicenseType.NON_EXCLUSIVE,
                "2026-01-01", "2026-12-31", List.of("CN"), List.of("WEB"));

        licenseService.submitDecision(application.getId(), holders.get(0).getId(),
                DecisionValue.APPROVE, "evt-1");
        assertThat(licenseService.getApplication(application.getId()).getStatus())
                .isEqualTo(ApplicationStatus.PENDING);
        assertThat(licenseService.approvedShare(application.getId()))
                .isEqualByComparingTo(new BigDecimal("50.00"));

        licenseService.submitDecision(application.getId(), holders.get(1).getId(),
                DecisionValue.APPROVE, "evt-2");
        assertThat(licenseService.getApplication(application.getId()).getStatus())
                .isEqualTo(ApplicationStatus.APPROVED);
    }

    @Test
    void anyRejectionTerminatesApplication() {
        Work work = fixtures.createWork("60", "40");
        List<RightsHolder> holders = fixtures.holdersOf(work);
        LicenseApplication application = fixtures.createApplication(work, LicenseType.EXCLUSIVE,
                "2026-01-01", "2026-12-31", List.of("CN"), List.of("TV"));

        licenseService.submitDecision(application.getId(), holders.get(0).getId(),
                DecisionValue.APPROVE, "evt-1");
        licenseService.submitDecision(application.getId(), holders.get(1).getId(),
                DecisionValue.REJECT, "evt-2");

        assertThat(licenseService.getApplication(application.getId()).getStatus())
                .isEqualTo(ApplicationStatus.REJECTED);
        assertThatThrownBy(() -> licenseService.submitDecision(application.getId(),
                holders.get(0).getId(), DecisionValue.APPROVE, "evt-3"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已终结");
    }

    @Test
    void holderCanOnlyDecideOnce() {
        Work work = fixtures.createWork("50", "50");
        List<RightsHolder> holders = fixtures.holdersOf(work);
        LicenseApplication application = fixtures.createApplication(work, LicenseType.EXCLUSIVE,
                "2026-01-01", "2026-12-31", List.of("CN"), List.of("TV"));

        licenseService.submitDecision(application.getId(), holders.get(0).getId(),
                DecisionValue.APPROVE, "evt-1");
        assertThatThrownBy(() -> licenseService.submitDecision(application.getId(),
                holders.get(0).getId(), DecisionValue.REJECT, "evt-2"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不可修改");
    }

    @Test
    void decisionIsIdempotentByEventNumber() {
        Work work = fixtures.createWork("50", "50");
        List<RightsHolder> holders = fixtures.holdersOf(work);
        LicenseApplication application = fixtures.createApplication(work, LicenseType.EXCLUSIVE,
                "2026-01-01", "2026-12-31", List.of("CN"), List.of("TV"));

        RightsDecision first = licenseService.submitDecision(application.getId(),
                holders.get(0).getId(), DecisionValue.APPROVE, "evt-dup");
        RightsDecision replay = licenseService.submitDecision(application.getId(),
                holders.get(0).getId(), DecisionValue.APPROVE, "evt-dup");

        assertThat(replay.getId()).isEqualTo(first.getId());
        assertThat(licenseService.listDecisions(application.getId())).hasSize(1);
    }

    @Test
    void holderMustBelongToTheWork() {
        Work work = fixtures.createWork("100");
        Work other = fixtures.createWork("100");
        LicenseApplication application = fixtures.createApplication(work, LicenseType.EXCLUSIVE,
                "2026-01-01", "2026-12-31", List.of("CN"), List.of("TV"));
        RightsHolder outsider = fixtures.holdersOf(other).get(0);

        assertThatThrownBy(() -> licenseService.submitDecision(application.getId(),
                outsider.getId(), DecisionValue.APPROVE, "evt-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不是本作品的权利人");
    }

    @Test
    void progressReportsDecisionsAndPendingHolders() {
        Work work = fixtures.createWork("30", "70");
        List<RightsHolder> holders = fixtures.holdersOf(work);
        LicenseApplication application = fixtures.createApplication(work, LicenseType.NON_EXCLUSIVE,
                "2026-01-01", "2026-12-31", List.of("JP"), List.of("WEB"));

        licenseService.submitDecision(application.getId(), holders.get(0).getId(),
                DecisionValue.APPROVE, "evt-1");

        List<RightsDecision> decisions = licenseService.listDecisions(application.getId());
        assertThat(decisions).hasSize(1);
        assertThat(licenseService.approvedShare(application.getId()))
                .isEqualByComparingTo(new BigDecimal("30.00"));
        assertThat(licenseService.getApplication(application.getId()).getStatus())
                .isEqualTo(ApplicationStatus.PENDING);
    }
}
