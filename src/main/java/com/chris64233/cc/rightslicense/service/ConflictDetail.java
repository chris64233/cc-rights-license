package com.chris64233.cc.rightslicense.service;

import com.chris64233.cc.rightslicense.domain.LicenseType;

import java.time.LocalDate;
import java.util.Set;

public record ConflictDetail(
        Long grantId,
        String licensee,
        LicenseType licenseType,
        Set<String> overlappingTerritories,
        Set<String> overlappingMedia,
        LocalDate startDate,
        LocalDate endDate) {
}
