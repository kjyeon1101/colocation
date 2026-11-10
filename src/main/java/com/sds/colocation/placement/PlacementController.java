package com.sds.colocation.placement;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sds.colocation.rack.RackService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/placements")
public class PlacementController {

    private final RackService rackService;

    public PlacementController(RackService rackService) {
        this.rackService = rackService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlacementResult place(@Valid @RequestBody PlacementRequest request) {
        return rackService.place(request);
    }

    @PostMapping("/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    public List<PlacementResult> placeBulk(@Valid @RequestBody BulkPlacementRequest request) {
        return rackService.placeBulk(request);
    }
}
