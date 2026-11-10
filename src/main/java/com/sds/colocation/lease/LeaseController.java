package com.sds.colocation.lease;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sds.colocation.allocation.AllocationView;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/leases")
public class LeaseController {

    private final LeaseService leaseService;

    public LeaseController(LeaseService leaseService) {
        this.leaseService = leaseService;
    }

    /** 실행하지 않고 이전 계획만 미리 본다. */
    @PostMapping("/plan")
    public LeasePlan plan(@Valid @RequestBody LeaseRequest request) {
        return leaseService.plan(request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AllocationView execute(@Valid @RequestBody LeaseRequest request) {
        return leaseService.execute(request);
    }
}
