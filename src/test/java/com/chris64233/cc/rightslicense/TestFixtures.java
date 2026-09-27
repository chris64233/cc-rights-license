package com.chris64233.cc.rightslicense;

import com.chris64233.cc.rightslicense.domain.DecisionValue;
import com.chris64233.cc.rightslicense.domain.LicenseApplication;
import com.chris64233.cc.rightslicense.domain.LicenseGrant;
import com.chris64233.cc.rightslicense.domain.LicenseType;
import com.chris64233.cc.rightslicense.domain.RightsHolder;
import com.chris64233.cc.rightslicense.domain.SublicenseApplication;
import com.chris64233.cc.rightslicense.domain.SublicensePolicy;
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

    public String uniqueSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
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
        return licenseService.createApplication(work.getCode(), "被授权方-" + uniqueSuffix(), type,
                LocalDate.parse(start), LocalDate.parse(end), territories, media);
    }

    public void approveAll(LicenseApplication application, Work work) {
        List<RightsHolder> holders = new ArrayList<>(holdersOf(work));
        for (int i = 0; i < holders.size(); i++) {
            licenseService.submitDecision(application.getId(), holders.get(i).getId(),
                    DecisionValue.APPROVE,
                    application.getId() + "-evt-" + i + "-" + uniqueSuffix());
        }
    }

    /** 创建根授权申请（可声明转授权策略）并批准，返回已生效的根授权 */
    public LicenseGrant approveRootGrant(Work work, String licensee, LicenseType type,
                                         String start, String end,
                                         List<String> territories, List<String> media,
                                         SublicensePolicy policy) {
        LicenseApplication application = licenseService.createApplication(
                work.getCode(), licensee, type,
                LocalDate.parse(start), LocalDate.parse(end), territories, media, policy);
        approveAll(application, work);
        if (licenseService.getApplication(application.getId()).getStatus()
                != com.chris64233.cc.rightslicense.domain.ApplicationStatus.APPROVED) {
            throw new IllegalStateException("根授权未批准: "
                    + licenseService.getApplication(application.getId()).getConflictReason());
        }
        // 通过日历拿到授权实体
        return licenseService.getCalendar(work.getCode(),
                LocalDate.parse(start), LocalDate.parse(end)).stream()
                .filter(g -> g.getApplication().getId().equals(application.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("找不到已批准根授权"));
    }
}
