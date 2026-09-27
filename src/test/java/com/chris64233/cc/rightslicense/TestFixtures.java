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
import com.chris64233.cc.rightslicense.service.SublicenseService;
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
    private final SublicenseService sublicenseService;

    public TestFixtures(WorkService workService, LicenseService licenseService,
                        SublicenseService sublicenseService) {
        this.workService = workService;
        this.licenseService = licenseService;
        this.sublicenseService = sublicenseService;
    }

    public String uniqueCode() {
        return "W-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
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
        return licenseService.createApplication(work.getCode(), uniqueName("被授权方"), type,
                LocalDate.parse(start), LocalDate.parse(end), territories, media);
    }

    /** 创建已生效（APPROVED + grant）的根授权，返回其授权记录。 */
    public LicenseGrant approveRootGrant(Work work, String licensee, LicenseType type,
                                         String start, String end,
                                         List<String> territories, List<String> media,
                                         SublicensePolicy policy) {
        LicenseApplication application = licenseService.createApplication(
                work.getCode(), licensee, type,
                LocalDate.parse(start), LocalDate.parse(end), territories, media, policy);
        approveAll(application, work);
        return licenseService.getApplication(application.getId()).getStatus()
                == com.chris64233.cc.rightslicense.domain.ApplicationStatus.APPROVED
                ? licenseService.getGrantByApplicationId(application.getId())
                : null;
    }

    public void approveAll(LicenseApplication application, Work work) {
        List<RightsHolder> holders = new ArrayList<>(holdersOf(work));
        for (int i = 0; i < holders.size(); i++) {
            licenseService.submitDecision(application.getId(), holders.get(i).getId(),
                    DecisionValue.APPROVE, application.getId() + "-evt-" + i + "-"
                            + UUID.randomUUID().toString().substring(0, 6));
        }
    }

    /** 创建转授权申请。 */
    public SublicenseApplication createSublicense(LicenseGrant parent,
                                                  String sublicensee, LicenseType type,
                                                  String start, String end,
                                                  List<String> territories, List<String> media,
                                                  SublicensePolicy policy) {
        return createSublicense(parent, uniqueName("SUB-NO"), sublicensee, type,
                start, end, territories, media, policy);
    }

    public SublicenseApplication createSublicense(LicenseGrant parent, String sublicenseNumber,
                                                  String sublicensee, LicenseType type,
                                                  String start, String end,
                                                  List<String> territories, List<String> media,
                                                  SublicensePolicy policy) {
        return sublicenseService.createApplication(
                sublicenseNumber, parent.getId(), parent.getLicensee(), sublicensee,
                type, LocalDate.parse(start), LocalDate.parse(end),
                territories, media, policy);
    }

    /** 批准转授权申请。 */
    public void approveSublicense(SublicenseApplication application) {
        sublicenseService.submitDecision(application.getId(), DecisionValue.APPROVE,
                "sub-evt-" + application.getId() + "-" + UUID.randomUUID().toString().substring(0, 6));
    }

    public SublicenseApplication approveSublicenseGrant(LicenseGrant parent,
                                                        String sublicensee, LicenseType type,
                                                        String start, String end,
                                                        List<String> territories,
                                                        List<String> media,
                                                        SublicensePolicy policy) {
        SublicenseApplication application = createSublicense(
                parent, sublicensee, type, start, end, territories, media, policy);
        approveSublicense(application);
        return sublicenseService.getApplication(application.getId());
    }
}
