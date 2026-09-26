package com.chris64233.cc.rightslicense.web.dto;

import com.chris64233.cc.rightslicense.domain.LicenseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.Set;

public record CreateApplicationRequest(
        @NotBlank String licensee,
        @NotNull LicenseType licenseType,
        @NotEmpty Set<@NotBlank String> territories,
        @NotEmpty Set<@NotBlank String> media,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate) {
}
