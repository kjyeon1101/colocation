package com.sds.colocation.support;

import com.sds.colocation.allocation.Allocation;
import com.sds.colocation.allocation.Server;
import com.sds.colocation.allocation.ServerType;
import com.sds.colocation.common.ColocationProperties;
import com.sds.colocation.customer.Customer;
import com.sds.colocation.customer.CustomerTier;
import com.sds.colocation.rack.Rack;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

/** 단위 테스트에서 DB 없이 도메인 객체를 만들기 위한 도우미. */
public final class Fixtures {

    public static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    private Fixtures() {
    }

    /** application.properties의 기본 운영값과 같은 설정. */
    public static ColocationProperties properties() {
        return new ColocationProperties(
                new ColocationProperties.Rack(42, 21, 10_000, 300_000_000L),
                new ColocationProperties.Pricing(150_000L, 400L, 2_800_000L, 5_000_000L),
                new ColocationProperties.Relocation(50, 3),
                new ColocationProperties.Power(5_000, 100_000_000L),
                new ColocationProperties.Notification(0L),
                "Asia/Seoul");
    }

    public static Rack rack(long id, String name, int maxPowerWatts) {
        Rack rack = new Rack(name, 42, maxPowerWatts);
        ReflectionTestUtils.setField(rack, "id", id);
        return rack;
    }

    public static Customer customer(long id, String name, CustomerTier tier) {
        Customer customer = new Customer(name, tier, "담당자", name + "@example.com");
        ReflectionTestUtils.setField(customer, "id", id);
        return customer;
    }

    /** 서버 1대가 임대 공간 전체를 쓰는 U 단위 임대 공간. */
    public static Allocation unitAllocation(long id, Customer customer, Rack rack, int startUnit, int sizeUnits,
                                            ServerType type, int powerWatts, LocalDate startDate) {
        Allocation allocation = new Allocation(customer, rack, "1U", startUnit, sizeUnits, startDate);
        ReflectionTestUtils.setField(allocation, "id", id);
        allocation.addServer(new Server("host-" + id, type, startUnit, sizeUnits, powerWatts, startDate, null));
        return allocation;
    }

    /** 서버가 없는 하프랙/풀랙 임대 공간. */
    public static Allocation rackLease(long id, Customer customer, Rack rack, String leaseUnit, int startUnit,
                                       int sizeUnits, LocalDate startDate) {
        Allocation allocation = new Allocation(customer, rack, leaseUnit, startUnit, sizeUnits, startDate);
        ReflectionTestUtils.setField(allocation, "id", id);
        return allocation;
    }

    public static Server server(String hostname, ServerType type, int startUnit, int sizeUnits, int powerWatts) {
        return new Server(hostname, type, startUnit, sizeUnits, powerWatts, TODAY, null);
    }
}
