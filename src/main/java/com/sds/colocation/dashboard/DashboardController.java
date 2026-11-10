package com.sds.colocation.dashboard;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sds.colocation.common.ColocationProperties;

@RestController
@RequestMapping("/api")
public class DashboardController {

    private final DashboardService dashboardService;
    private final ColocationProperties properties;

    public DashboardController(DashboardService dashboardService, ColocationProperties properties) {
        this.dashboardService = dashboardService;
        this.properties = properties;
    }

    @GetMapping("/dashboard")
    public DashboardView dashboard() {
        return dashboardService.summary();
    }

    /** 화면에서 요금·공사비 미리보기를 계산할 때 쓰는 운영 기준값. */
    @GetMapping("/settings")
    public ColocationProperties settings() {
        return properties;
    }
}
