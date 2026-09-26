package com.chris64233.cc.rightslicense.web;

import com.chris64233.cc.rightslicense.domain.LicenseGrant;
import com.chris64233.cc.rightslicense.service.LicenseService;
import com.chris64233.cc.rightslicense.service.WorkService;
import com.chris64233.cc.rightslicense.web.dto.Requests.AddHolderRequest;
import com.chris64233.cc.rightslicense.web.dto.Requests.CreateWorkRequest;
import com.chris64233.cc.rightslicense.web.dto.Responses.GrantResponse;
import com.chris64233.cc.rightslicense.web.dto.Responses.HolderResponse;
import com.chris64233.cc.rightslicense.web.dto.Responses.WorkResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/works")
public class WorkController {

    private final WorkService workService;
    private final LicenseService licenseService;

    public WorkController(WorkService workService, LicenseService licenseService) {
        this.workService = workService;
        this.licenseService = licenseService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkResponse createWork(@Valid @RequestBody CreateWorkRequest request) {
        return WorkResponse.of(workService.createWork(request.code(), request.title()));
    }

    @PostMapping("/{code}/holders")
    @ResponseStatus(HttpStatus.CREATED)
    public HolderResponse addHolder(@PathVariable String code,
                                    @Valid @RequestBody AddHolderRequest request) {
        return HolderResponse.of(workService.addRightsHolder(code, request.name(), request.sharePercent()));
    }

    @GetMapping("/{code}/holders")
    public List<HolderResponse> listHolders(@PathVariable String code) {
        return workService.listHolders(code).stream().map(HolderResponse::of).toList();
    }

    @GetMapping("/{code}/calendar")
    public List<GrantResponse> calendar(
            @PathVariable String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<LicenseGrant> grants = licenseService.getCalendar(code, from, to);
        return grants.stream().map(GrantResponse::of).toList();
    }
}
