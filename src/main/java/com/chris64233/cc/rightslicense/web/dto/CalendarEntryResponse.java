package com.chris64233.cc.rightslicense.web.dto;

import com.chris64233.cc.rightslicense.domain.LicenseType;

import java.time.LocalDate;
import java.util.Set;

public record CalendarEntryResponse(
        Long grantId,
        Long applicationId,
        String licensee,
        LicenseType licenseType,
        Set<String> territories,
        Set<String> media,
        LocalDate startDate,
        LocalDate endDate) {
}
