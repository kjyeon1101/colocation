package com.sds.colocation.rack;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class RackController {

    private final RackService rackService;

    public RackController(RackService rackService) {
        this.rackService = rackService;
    }

    @GetMapping("/racks")
    public List<RackView> list() {
        return rackService.findAll();
    }

    @GetMapping("/racks/{id}")
    public RackView get(@PathVariable Long id) {
        return rackService.findById(id);
    }

    @PostMapping("/racks")
    @ResponseStatus(HttpStatus.CREATED)
    public RackView create(@Valid @RequestBody RackCreateRequest request) {
        return rackService.create(request);
    }

    @GetMapping("/rack-installations")
    public List<RackInstallationView> installationHistory() {
        return rackService.installationHistory();
    }
}
