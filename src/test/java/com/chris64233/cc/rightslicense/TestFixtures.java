package com.chris64233.cc.rightslicense;

import com.chris64233.cc.rightslicense.domain.LicenseApplication;
import com.chris64233.cc.rightslicense.domain.LicenseType;
import com.chris64233.cc.rightslicense.domain.RightsHolder;
import com.chris64233.cc.rightslicense.domain.Work;
import com.chris64233.cc.rightslicense.service.LicenseService;
import com.chris64233.cc.rightslicense.service.WorkService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class TestFixtures {

    private final WorkService workService;
    private final LicenseService licenseService;

    public TestFixtures(WorkService workService, LicenseService licenseService) {
        this.workService = workService;
        this.licenseService = licenseService;
    }

    public String uniqueCode() {
        return "W-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public Work createWork(String... shares) {
        Work work = workService.createWork(uniqueCode(), "测试作品");
        for (int i = 0; i < shares.length; i++) {
            workService.addRightsHolder(work.getCode(), "权利人" + (i + 1), new BigDecimal(shares[i]));
        }
        return work;
    }

    public List<RightsHolder> holdersOf(Work work) {
        return workService.listHolders(work.getCode());
    }

    public LicenseApplication createApplication(Work work, LicenseType type,
                                                String start, String end,
                                                List<String> territories, List<String> media) {
        return licenseService.createApplication(work.getCode(), "被授权方-" + uniqueCode(), type,
                LocalDate.parse(start), LocalDate.parse(end), territories, media);
    }

    public void approveAll(LicenseApplication application, Work work) {
        List<RightsHolder> holders = new ArrayList<>(holdersOf(work));
        for (int i = 0; i < holders.size(); i++) {
            licenseService.submitDecision(application.getId(), holders.get(i).getId(),
                    com.chris64233.cc.rightslicense.domain.DecisionValue.APPROVE,
                    application.getId() + "-evt-" + i);
        }
    }
}
