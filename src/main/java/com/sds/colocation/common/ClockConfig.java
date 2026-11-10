package com.sds.colocation.common;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(ColocationProperties properties) {
        return Clock.system(ZoneId.of(properties.zone()));
    }
}
