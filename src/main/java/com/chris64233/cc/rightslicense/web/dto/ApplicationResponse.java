package com.chris64233.cc.rightslicense.web.dto;

import com.chris64233.cc.rightslicense.domain.ApplicationStatus;
import com.chris64233.cc.rightslicense.domain.LicenseType;

import java.time.LocalDate;
import java.util.Set;

public record ApplicationResponse(
        Long id,
        String workCode,
        String licensee,
        LicenseType licenseType,
        Set<String> territories,
        Set<String> media,
        LocalDate startDate,
        LocalDate endDate,
        ApplicationStatus status,
        String conflictReason) {
}
