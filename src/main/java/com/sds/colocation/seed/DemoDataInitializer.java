package com.sds.colocation.seed;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.sds.colocation.common.ColocationProperties;
import com.sds.colocation.allocation.Allocation;
import com.sds.colocation.allocation.AllocationRepository;
import com.sds.colocation.allocation.DiscountPeriod;
import com.sds.colocation.allocation.Server;
import com.sds.colocation.allocation.ServerType;
import com.sds.colocation.customer.Customer;
import com.sds.colocation.customer.CustomerRepository;
import com.sds.colocation.customer.CustomerTier;
import com.sds.colocation.notification.Notification;
import com.sds.colocation.notification.NotificationRepository;
import com.sds.colocation.power.PowerUpgrade;
import com.sds.colocation.power.PowerUpgradeRepository;
import com.sds.colocation.rack.Rack;
import com.sds.colocation.rack.RackRepository;
import com.sds.colocation.relocation.Relocation;
import com.sds.colocation.relocation.RelocationCompletedEvent;
import com.sds.colocation.relocation.RelocationRepository;

/** 시연용 데이터. 날짜는 실행한 날을 기준으로 만든다. */
@Component
@ConditionalOnProperty(name = "colocation.seed.enabled", havingValue = "true")
public class DemoDataInitializer implements ApplicationRunner {

    private final RackRepository rackRepository;
    private final CustomerRepository customerRepository;
    private final AllocationRepository allocationRepository;
    private final RelocationRepository relocationRepository;
    private final PowerUpgradeRepository powerUpgradeRepository;
    private final NotificationRepository notificationRepository;
    private final ColocationProperties properties;
    private final Clock clock;

    private LocalDate today;

    public DemoDataInitializer(RackRepository rackRepository, CustomerRepository customerRepository,
                               AllocationRepository allocationRepository, RelocationRepository relocationRepository,
                               PowerUpgradeRepository powerUpgradeRepository,
                               NotificationRepository notificationRepository,
                               ColocationProperties properties, Clock clock) {
        this.rackRepository = rackRepository;
        this.customerRepository = customerRepository;
        this.allocationRepository = allocationRepository;
        this.relocationRepository = relocationRepository;
        this.powerUpgradeRepository = powerUpgradeRepository;
        this.notificationRepository = notificationRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (rackRepository.count() > 0) {
            return;
        }
        today = LocalDate.now(clock);
        int units = properties.rack().defaultUnits();

        // 시드 랙은 설치비 규칙이 생기기 전부터 있던 랙으로 보고 설치 이력을 남기지 않는다. 전력은 5kW 단위.
        Rack a1 = rackRepository.save(new Rack("A-01", units, 10_000));
        Rack a2 = rackRepository.save(new Rack("A-02", units, 15_000));
        Rack a3 = rackRepository.save(new Rack("A-03", units, 10_000));
        Rack b1 = rackRepository.save(new Rack("B-01", units, 20_000));
        Rack b2 = rackRepository.save(new Rack("B-02", units, 10_000));

        Customer hanbit = customer("한빛커머스", CustomerTier.STANDARD, "김하늘", "ops@hanbit-commerce.kr");
        Customer nuri = customer("누리게임즈", CustomerTier.STANDARD, "이준호", "infra@nurigames.kr");
        Customer aurora = customer("오로라AI", CustomerTier.ENTERPRISE, "박서연", "dc@aurora-ai.kr");
        Customer sejong = customer("세종은행", CustomerTier.ENTERPRISE, "최민석", "it-infra@sejongbank.kr");
        Customer byeolbit = customer("별빛미디어", CustomerTier.STANDARD, "정유나", "tech@byeolbit.media");
        Customer corelogix = customer("코어로직스", CustomerTier.STANDARD, "강도윤", "sre@corelogix.kr");
        Customer green = customer("그린헬스케어", CustomerTier.STANDARD, "윤지아", "infra@greenhealth.kr");
        customer("하이퍼비전", CustomerTier.ENTERPRISE, "오태현", "infra@hypervision.ai");

        // A-01: 세종은행 풀랙
        Allocation sejongFull = lease(sejong, a1, "FULL", 1, units, 170);
        server(sejongFull, "sj-core-db01", ServerType.CPU, 1, 2, 650, 170);
        server(sejongFull, "sj-core-db02", ServerType.CPU, 3, 2, 650, 170);
        server(sejongFull, "sj-app01", ServerType.CPU, 6, 1, 350, 165);
        server(sejongFull, "sj-app02", ServerType.CPU, 7, 1, 350, 165);
        server(sejongFull, "sj-app03", ServerType.CPU, 8, 1, 350, 120);
        server(sejongFull, "sj-app04", ServerType.CPU, 9, 1, 350, 120);
        server(sejongFull, "sj-backup01", ServerType.CPU, 12, 4, 900, 150);
        server(sejongFull, "sj-hsm01", ServerType.CPU, 18, 2, 300, 150);

        // A-02: 일반 고객 혼합 (위쪽 절반은 하프랙 수용 시연용)
        unit(hanbit, a2, "hb-web01", ServerType.CPU, 2, 1, 300, 140);
        unit(hanbit, a2, "hb-web02", ServerType.CPU, 3, 1, 300, 140);
        unit(hanbit, a2, "hb-db01", ServerType.CPU, 5, 2, 700, 140);
        unit(nuri, a2, "nr-game01", ServerType.CPU, 10, 2, 650, 100);
        unit(nuri, a2, "nr-render01", ServerType.GPU, 14, 4, 2_400, 60);
        unit(byeolbit, a2, "bb-encoder01", ServerType.CPU, 22, 2, 550, 120);
        unit(corelogix, a2, "cl-k8s01", ServerType.CPU, 26, 1, 400, 45);
        unit(corelogix, a2, "cl-k8s02", ServerType.CPU, 27, 1, 400, 45);
        unit(corelogix, a2, "cl-k8s03", ServerType.CPU, 28, 1, 400, 45);
        Allocation greenEmr = unit(green, a2, "gh-emr01", ServerType.CPU, 33, 2, 600, 160);

        // A-03: 오로라AI 하프랙 (75일 전 전력 증설 후 GPU 추가)
        upgradePower(a3, 1, 75);
        Allocation auroraHalf = lease(aurora, a3, "HALF", 1, properties.rack().halfUnits(), 150);
        server(auroraHalf, "au-train01", ServerType.GPU, 1, 4, 3_000, 150);
        server(auroraHalf, "au-train02", ServerType.GPU, 5, 4, 3_000, 150);
        server(auroraHalf, "au-train03", ServerType.GPU, 9, 4, 3_000, 74);
        unit(green, a3, "gh-pacs01", ServerType.CPU, 25, 2, 500, 90);
        unit(corelogix, a3, "cl-db01", ServerType.CPU, 30, 2, 700, 30);

        // B-01: GPU 존
        unit(nuri, b1, "nr-ai01", ServerType.GPU, 2, 4, 3_200, 50);
        unit(byeolbit, b1, "bb-gpu01", ServerType.GPU, 8, 8, 6_000, 35);
        Allocation hanbitBatch = unit(hanbit, b1, "hb-batch01", ServerType.CPU, 20, 2, 500, 130);

        // B-02
        unit(corelogix, b2, "cl-gpu01", ServerType.GPU, 4, 2, 1_500, 15);

        // 이전 이력: 하나는 할인 진행 중(재부팅 대기), 하나는 할인 종료
        relocated(hanbitBatch, "A-03", 23, 20, "오로라AI GPU 증설 공간 확보", true);
        relocated(greenEmr, "A-03", 12, 100, "오로라AI 하프랙 임대 공간 확보", false);
    }

    private Customer customer(String name, CustomerTier tier, String contactName, String email) {
        return customerRepository.save(new Customer(name, tier, contactName, email));
    }

    private Allocation lease(Customer customer, Rack rack, String leaseUnit, int start, int size, int daysAgo) {
        return allocationRepository.save(new Allocation(customer, rack, leaseUnit, start, size, today.minusDays(daysAgo)));
    }

    private void server(Allocation lease, String hostname, ServerType type, int start, int size, int watts, int daysAgo) {
        lease.addServer(new Server(hostname, type, start, size, watts, today.minusDays(daysAgo), null));
        lease.getRack().addUsedPower(watts);
    }

    private Allocation unit(Customer customer, Rack rack, String hostname, ServerType type, int start, int size,
                          int watts, int daysAgo) {
        Allocation allocation = new Allocation(customer, rack, "1U", start, size, today.minusDays(daysAgo));
        allocation.addServer(new Server(hostname, type, start, size, watts, today.minusDays(daysAgo), null));
        rack.addUsedPower(watts);
        return allocationRepository.save(allocation);
    }

    private void upgradePower(Rack rack, int steps, int daysAgo) {
        int before = rack.getMaxPowerWatts();
        rack.increaseMaxPower(steps * properties.power().upgradeStepWatts());
        powerUpgradeRepository.save(new PowerUpgrade(rack, before, steps,
                steps * properties.power().upgradeStepCost(), today.minusDays(daysAgo)));
    }

    private void relocated(Allocation allocation, String fromRackName, int fromStartUnit, int daysAgo, String reason,
                           boolean rebootPending) {
        LocalDate relocatedDate = today.minusDays(daysAgo);
        DiscountPeriod discount = allocation.applyDiscount(relocatedDate, properties.relocation().discountMonths(),
                properties.relocation().discountPercent());
        if (rebootPending) {
            allocation.markServersRebootRequired();
        }
        relocationRepository.save(
                new Relocation(allocation, fromRackName, fromStartUnit, relocatedDate, discount, reason));

        Customer customer = allocation.getCustomer();
        RelocationCompletedEvent event = new RelocationCompletedEvent(customer.getId(), customer.getName(),
                customer.getContactEmail(), allocation.getId(), fromRackName, fromStartUnit,
                allocation.getRack().getName(), allocation.getStartUnit(), discount.getPercent(), discount.getEndDate());
        notificationRepository.save(new Notification(customer.getId(), customer.getName(), customer.getContactEmail(),
                event.title(), event.message(), relocatedDate.atTime(LocalTime.of(14, 30))));
    }
}
