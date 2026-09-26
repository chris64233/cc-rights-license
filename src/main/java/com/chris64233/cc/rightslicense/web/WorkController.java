package com.chris64233.cc.rightslicense.web;

import com.chris64233.cc.rightslicense.service.LicenseService;
import com.chris64233.cc.rightslicense.service.WorkService;
import com.chris64233.cc.rightslicense.web.dto.ApplicationResponse;
import com.chris64233.cc.rightslicense.web.dto.CalendarEntryResponse;
import com.chris64233.cc.rightslicense.web.dto.CreateApplicationRequest;
import com.chris64233.cc.rightslicense.web.dto.CreateWorkRequest;
import com.chris64233.cc.rightslicense.web.dto.WorkResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
        return workService.createWork(request);
    }

    @GetMapping("/{workCode}")
    public WorkResponse getWork(@PathVariable String workCode) {
        return workService.getWork(workCode);
    }

    @PostMapping("/{workCode}/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse createApplication(@PathVariable String workCode,
                                                 @Valid @RequestBody CreateApplicationRequest request) {
        return licenseService.createApplication(workCode, request);
    }

    @GetMapping("/{workCode}/calendar")
    public List<CalendarEntryResponse> getCalendar(@PathVariable String workCode) {
        return licenseService.getCalendar(workCode);
    }
}
