package com.sds.colocation.allocation;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class AllocationController {

    private final AllocationService allocationService;

    public AllocationController(AllocationService allocationService) {
        this.allocationService = allocationService;
    }

    @GetMapping("/allocations")
    public List<AllocationView> list(@RequestParam(required = false) Long customerId,
                                   @RequestParam(defaultValue = "false") boolean includeTerminated) {
        return allocationService.findAll(customerId, includeTerminated);
    }

    @GetMapping("/allocations/{id}")
    public AllocationView get(@PathVariable Long id) {
        return allocationService.findById(id);
    }

    @PutMapping("/allocations/{id}")
    public AllocationView update(@PathVariable Long id, @Valid @RequestBody AllocationUpdateRequest request) {
        return allocationService.update(id, request);
    }

    @PostMapping("/allocations/{id}/terminate")
    public AllocationView terminate(@PathVariable Long id) {
        return allocationService.terminate(id);
    }

    @PostMapping("/servers/{id}/reboot-complete")
    public ServerView completeReboot(@PathVariable Long id) {
        return allocationService.completeReboot(id);
    }
}
