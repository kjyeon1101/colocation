package com.sds.colocation.rack;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Rack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private int totalUnits;

    private int maxPowerWatts;

    /** 현재 사용 중인 전력(W). 서버를 넣고 뺄 때마다 갱신한다. */
    private int usedPowerWatts;

    protected Rack() {
    }

    public Rack(String name, int totalUnits, int maxPowerWatts) {
        if (totalUnits <= 0 || maxPowerWatts <= 0) {
            throw new IllegalArgumentException("랙 크기와 최대 전력은 0보다 커야 합니다.");
        }
        this.name = name;
        this.totalUnits = totalUnits;
        this.maxPowerWatts = maxPowerWatts;
    }

    public boolean isValidRange(int startUnit, int sizeUnits) {
        return startUnit >= 1 && sizeUnits >= 1 && startUnit + sizeUnits - 1 <= totalUnits;
    }

    public void increaseMaxPower(int watts) {
        this.maxPowerWatts += watts;
    }

    public void addUsedPower(int watts) {
        this.usedPowerWatts += watts;
    }

    public void removeUsedPower(int watts) {
        this.usedPowerWatts -= watts;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getTotalUnits() {
        return totalUnits;
    }

    public int getMaxPowerWatts() {
        return maxPowerWatts;
    }

    public int getUsedPowerWatts() {
        return usedPowerWatts;
    }
}
