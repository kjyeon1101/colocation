package com.sds.colocation.power;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PowerUpgradeRepository extends JpaRepository<PowerUpgrade, Long> {

    List<PowerUpgrade> findAllByOrderByUpgradedDateDescIdDesc();

    @Query("""
            select coalesce(sum(p.cost), 0) from PowerUpgrade p
            where p.upgradedDate >= :from and p.upgradedDate < :to
            """)
    long sumCostBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
