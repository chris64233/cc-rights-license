package com.chris64233.cc.rightslicense.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateWorkRequest(
        @NotBlank String workCode,
        @NotBlank String title,
        @NotEmpty List<@Valid RightHolderInput> rightHolders) {
}
