package com.sds.colocation.power;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sds.colocation.rack.RackService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class PowerUpgradeController {

    private final RackService rackService;

    public PowerUpgradeController(RackService rackService) {
        this.rackService = rackService;
    }

    @PostMapping("/racks/{rackId}/power-upgrades")
    @ResponseStatus(HttpStatus.CREATED)
    public PowerUpgradeView upgrade(@PathVariable Long rackId, @Valid @RequestBody PowerUpgradeRequest request) {
        return rackService.upgradePower(rackId, request);
    }

    @GetMapping("/power-upgrades")
    public List<PowerUpgradeView> history() {
        return rackService.powerUpgradeHistory();
    }
}
