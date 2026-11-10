package com.sds.colocation.allocation;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class Server {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "allocation_id")
    private Allocation allocation;

    @Column(nullable = false)
    private String hostname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ServerType type;

    private int startUnit;

    private int sizeUnits;

    private int powerWatts;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ServerStatus status = ServerStatus.RUNNING;

    private LocalDate installedDate;

    /** 같은 입고 요청이 두 번 처리되지 않도록 클라이언트가 보내는 요청 키. */
    @Column(unique = true)
    private String requestKey;

    protected Server() {
    }

    public Server(String hostname, ServerType type, int startUnit, int sizeUnits, int powerWatts,
                  LocalDate installedDate, String requestKey) {
        if (sizeUnits <= 0 || powerWatts <= 0) {
            throw new IllegalArgumentException("서버 크기와 전력은 0보다 커야 합니다.");
        }
        this.hostname = hostname;
        this.type = type;
        this.startUnit = startUnit;
        this.sizeUnits = sizeUnits;
        this.powerWatts = powerWatts;
        this.installedDate = installedDate;
        this.requestKey = requestKey;
    }

    void assignTo(Allocation allocation) {
        this.allocation = allocation;
    }

    void shift(int offset) {
        this.startUnit += offset;
    }

    void requireReboot() {
        this.status = ServerStatus.REBOOT_REQUIRED;
    }

    public void completeReboot() {
        this.status = ServerStatus.RUNNING;
    }

    public boolean overlaps(int otherStart, int otherSize) {
        int end = startUnit + sizeUnits - 1;
        int otherEnd = otherStart + otherSize - 1;
        return startUnit <= otherEnd && otherStart <= end;
    }

    public Long getId() {
        return id;
    }

    public Allocation getAllocation() {
        return allocation;
    }

    public String getHostname() {
        return hostname;
    }

    public ServerType getType() {
        return type;
    }

    public int getStartUnit() {
        return startUnit;
    }

    public int getSizeUnits() {
        return sizeUnits;
    }

    public int getPowerWatts() {
        return powerWatts;
    }

    public ServerStatus getStatus() {
        return status;
    }

    public LocalDate getInstalledDate() {
        return installedDate;
    }

    public String getRequestKey() {
        return requestKey;
    }
}
