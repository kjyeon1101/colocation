package com.sds.colocation.relocation;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sds.colocation.rack.RackService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class RelocationController {

    private final RackService rackService;

    public RelocationController(RackService rackService) {
        this.rackService = rackService;
    }

    @PostMapping("/allocations/{allocationId}/relocate")
    public RelocationView relocate(@PathVariable Long allocationId, @Valid @RequestBody RelocateRequest request) {
        return rackService.relocate(allocationId, request);
    }

    @GetMapping("/relocations")
    public List<RelocationView> history() {
        return rackService.relocationHistory();
    }
}
