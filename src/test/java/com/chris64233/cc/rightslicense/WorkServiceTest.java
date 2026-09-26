package com.chris64233.cc.rightslicense;

import com.chris64233.cc.rightslicense.domain.LicenseType;
import com.chris64233.cc.rightslicense.domain.Work;
import com.chris64233.cc.rightslicense.service.BusinessException;
import com.chris64233.cc.rightslicense.service.LicenseService;
import com.chris64233.cc.rightslicense.service.WorkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class WorkServiceTest {

    @Autowired
    private WorkService workService;

    @Autowired
    private LicenseService licenseService;

    @Autowired
    private TestFixtures fixtures;

    @Test
    void workCodeMustBeUnique() {
        Work work = fixtures.createWork("100");
        assertThatThrownBy(() -> workService.createWork(work.getCode(), "重复编号"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    void sharesMustNotExceedHundred() {
        Work work = fixtures.createWork("60");
        assertThatThrownBy(() -> workService.addRightsHolder(work.getCode(), "权利人X", new BigDecimal("50")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能超过 100%");
    }

    @Test
    void shareMustBePositiveWithAtMostTwoDecimals() {
        Work work = fixtures.createWork("100");
        assertThatThrownBy(() -> workService.addRightsHolder(work.getCode(), "甲", BigDecimal.ZERO))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> workService.addRightsHolder(work.getCode(), "乙", new BigDecimal("10.001")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("两位小数");
    }

    @Test
    void applicationRequiresSharesToTotalExactlyHundred() {
        Work incomplete = fixtures.createWork("60");
        assertThatThrownBy(() -> licenseService.createApplication(incomplete.getCode(), "被授权方",
                LicenseType.EXCLUSIVE, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"),
                List.of("CN"), List.of("TV")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("精确等于 100%");
    }

    @Test
    void fractionalSharesSummingToHundredAreAccepted() {
        Work work = fixtures.createWork("33.33", "33.33", "33.34");
        var application = licenseService.createApplication(work.getCode(), "被授权方",
                LicenseType.NON_EXCLUSIVE, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-06-30"),
                List.of("CN"), List.of("WEB"));
        assertThat(application.getId()).isNotNull();
    }

    @Test
    void applicationValidation() {
        Work work = fixtures.createWork("100");
        assertThatThrownBy(() -> licenseService.createApplication(work.getCode(), "被授权方",
                LicenseType.EXCLUSIVE, LocalDate.parse("2026-12-31"), LocalDate.parse("2026-01-01"),
                List.of("CN"), List.of("TV")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("起始日期");
        assertThatThrownBy(() -> licenseService.createApplication(work.getCode(), "被授权方",
                LicenseType.EXCLUSIVE, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"),
                List.of(), List.of("TV")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("地域");
        assertThatThrownBy(() -> licenseService.createApplication(work.getCode(), "被授权方",
                LicenseType.EXCLUSIVE, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"),
                List.of("CN"), List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("媒介");
    }
}
