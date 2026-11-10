package com.sds.colocation.rack;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/** 새 랙 설치 이력. 설치비는 설치한 달의 순수익에서 차감한다. */
@Entity
public class RackInstallation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rack_id")
    private Rack rack;

    @Column(nullable = false)
    private String rackName;

    private int totalUnits;

    private int powerWatts;

    private long cost;

    @Column(nullable = false)
    private LocalDate installedDate;

    protected RackInstallation() {
    }

    public RackInstallation(Rack rack, long cost, LocalDate installedDate) {
        this.rack = rack;
        this.rackName = rack.getName();
        this.totalUnits = rack.getTotalUnits();
        this.powerWatts = rack.getMaxPowerWatts();
        this.cost = cost;
        this.installedDate = installedDate;
    }

    public Long getId() {
        return id;
    }

    public Rack getRack() {
        return rack;
    }

    public String getRackName() {
        return rackName;
    }

    public int getTotalUnits() {
        return totalUnits;
    }

    public int getPowerWatts() {
        return powerWatts;
    }

    public long getCost() {
        return cost;
    }

    public LocalDate getInstalledDate() {
        return installedDate;
    }
}
