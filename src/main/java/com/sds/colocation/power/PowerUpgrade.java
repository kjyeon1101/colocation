package com.sds.colocation.power;

import java.time.LocalDate;

import com.sds.colocation.rack.Rack;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/** 랙 전력 증설 공사 이력. */
@Entity
public class PowerUpgrade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rack_id")
    private Rack rack;

    @Column(nullable = false)
    private String rackName;

    private int beforeWatts;

    private int afterWatts;

    private int steps;

    private long cost;

    @Column(nullable = false)
    private LocalDate upgradedDate;

    protected PowerUpgrade() {
    }

    public PowerUpgrade(Rack rack, int beforeWatts, int steps, long cost, LocalDate upgradedDate) {
        this.rack = rack;
        this.rackName = rack.getName();
        this.beforeWatts = beforeWatts;
        this.afterWatts = rack.getMaxPowerWatts();
        this.steps = steps;
        this.cost = cost;
        this.upgradedDate = upgradedDate;
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

    public int getBeforeWatts() {
        return beforeWatts;
    }

    public int getAfterWatts() {
        return afterWatts;
    }

    public int getSteps() {
        return steps;
    }

    public long getCost() {
        return cost;
    }

    public LocalDate getUpgradedDate() {
        return upgradedDate;
    }
}
