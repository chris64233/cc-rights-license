package com.chris64233.cc.rightslicense;

import com.chris64233.cc.rightslicense.domain.ApplicationStatus;
import com.chris64233.cc.rightslicense.domain.LicenseApplication;
import com.chris64233.cc.rightslicense.domain.LicenseGrant;
import com.chris64233.cc.rightslicense.domain.LicenseType;
import com.chris64233.cc.rightslicense.domain.Work;
import com.chris64233.cc.rightslicense.service.LicenseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LicenseConflictTest {

    @Autowired
    private LicenseService licenseService;

    @Autowired
    private TestFixtures fixtures;

    private ApplicationStatus approveNewApplication(Work work, LicenseType type,
                                                    String start, String end,
                                                    List<String> territories, List<String> media) {
        LicenseApplication application = fixtures.createApplication(work, type, start, end, territories, media);
        fixtures.approveAll(application, work);
        return licenseService.getApplication(application.getId()).getStatus();
    }

    @Test
    void exclusiveBlocksAnyOverlappingGrant() {
        Work work = fixtures.createWork("100");
        assertThat(approveNewApplication(work, LicenseType.NON_EXCLUSIVE,
                "2026-01-01", "2026-12-31", List.of("CN"), List.of("TV")))
                .isEqualTo(ApplicationStatus.APPROVED);

        LicenseApplication exclusive = fixtures.createApplication(work, LicenseType.EXCLUSIVE,
                "2026-06-01", "2026-06-30", List.of("CN"), List.of("TV"));
        fixtures.approveAll(exclusive, work);

        LicenseApplication result = licenseService.getApplication(exclusive.getId());
        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.CONFLICT);
        assertThat(result.getConflictReason()).contains("CN").contains("TV");
        assertThat(licenseService.listDecisions(exclusive.getId())).isNotEmpty();
    }

    @Test
    void nonExclusiveConflictsWithExclusiveButCoexistsWithNonExclusive() {
        Work work = fixtures.createWork("100");
        assertThat(approveNewApplication(work, LicenseType.NON_EXCLUSIVE,
                "2026-01-01", "2026-12-31", List.of("CN"), List.of("WEB")))
                .isEqualTo(ApplicationStatus.APPROVED);
        assertThat(approveNewApplication(work, LicenseType.NON_EXCLUSIVE,
                "2026-01-01", "2026-12-31", List.of("CN"), List.of("WEB")))
                .isEqualTo(ApplicationStatus.APPROVED);

        Work work2 = fixtures.createWork("100");
        assertThat(approveNewApplication(work2, LicenseType.EXCLUSIVE,
                "2026-01-01", "2026-12-31", List.of("CN"), List.of("WEB")))
                .isEqualTo(ApplicationStatus.APPROVED);
        assertThat(approveNewApplication(work2, LicenseType.NON_EXCLUSIVE,
                "2026-03-01", "2026-03-31", List.of("CN"), List.of("WEB")))
                .isEqualTo(ApplicationStatus.CONFLICT);
    }

    @Test
    void dateIntervalsAreClosed() {
        Work work = fixtures.createWork("100");
        assertThat(approveNewApplication(work, LicenseType.EXCLUSIVE,
                "2026-01-01", "2026-01-31", List.of("CN"), List.of("TV")))
                .isEqualTo(ApplicationStatus.APPROVED);

        assertThat(approveNewApplication(work, LicenseType.EXCLUSIVE,
                "2026-01-31", "2026-02-28", List.of("CN"), List.of("TV")))
                .isEqualTo(ApplicationStatus.CONFLICT);
        assertThat(approveNewApplication(work, LicenseType.EXCLUSIVE,
                "2026-02-01", "2026-02-28", List.of("CN"), List.of("TV")))
                .isEqualTo(ApplicationStatus.APPROVED);
    }

    @Test
    void disjointTerritoryOrMediumDoesNotConflict() {
        Work work = fixtures.createWork("100");
        assertThat(approveNewApplication(work, LicenseType.EXCLUSIVE,
                "2026-01-01", "2026-12-31", List.of("CN"), List.of("TV")))
                .isEqualTo(ApplicationStatus.APPROVED);

        assertThat(approveNewApplication(work, LicenseType.EXCLUSIVE,
                "2026-01-01", "2026-12-31", List.of("JP"), List.of("TV")))
                .isEqualTo(ApplicationStatus.APPROVED);
        assertThat(approveNewApplication(work, LicenseType.EXCLUSIVE,
                "2026-01-01", "2026-12-31", List.of("CN"), List.of("WEB")))
                .isEqualTo(ApplicationStatus.APPROVED);
    }

    @Test
    void multiTerritoryMultiMediumApprovalIsAtomic() {
        Work work = fixtures.createWork("100");
        assertThat(approveNewApplication(work, LicenseType.EXCLUSIVE,
                "2026-01-01", "2026-12-31", List.of("CN"), List.of("TV")))
                .isEqualTo(ApplicationStatus.APPROVED);

        LicenseApplication application = fixtures.createApplication(work, LicenseType.EXCLUSIVE,
                "2026-06-01", "2026-06-30", List.of("CN", "JP"), List.of("TV", "WEB"));
        fixtures.approveAll(application, work);

        assertThat(licenseService.getApplication(application.getId()).getStatus())
                .isEqualTo(ApplicationStatus.CONFLICT);
        List<LicenseGrant> calendar = licenseService.getCalendar(work.getCode(),
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"));
        assertThat(calendar).hasSize(1);
    }

    @Test
    void calendarAndConflictDetailsAreQueryable() {
        Work work = fixtures.createWork("100");
        assertThat(approveNewApplication(work, LicenseType.EXCLUSIVE,
                "2026-01-01", "2026-12-31", List.of("CN"), List.of("TV")))
                .isEqualTo(ApplicationStatus.APPROVED);

        List<LicenseGrant> calendar = licenseService.getCalendar(work.getCode(), null, null);
        assertThat(calendar).hasSize(1);
        assertThat(licenseService.getCalendar(work.getCode(),
                LocalDate.parse("2027-01-01"), LocalDate.parse("2027-12-31"))).isEmpty();

        LicenseApplication conflicting = fixtures.createApplication(work, LicenseType.EXCLUSIVE,
                "2026-05-01", "2026-05-31", List.of("CN"), List.of("TV"));
        fixtures.approveAll(conflicting, work);

        LicenseApplication result = licenseService.getApplication(conflicting.getId());
        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.CONFLICT);
        assertThat(result.getConflictReason()).contains("已批准授权");
        assertThat(licenseService.findConflictingGrants(conflicting.getId()))
                .extracting(LicenseGrant::getId)
                .containsExactly(calendar.get(0).getId());
    }
}
