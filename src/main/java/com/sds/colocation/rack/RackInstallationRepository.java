package com.sds.colocation.rack;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RackInstallationRepository extends JpaRepository<RackInstallation, Long> {

    List<RackInstallation> findAllByOrderByInstalledDateDescIdDesc();

    @Query("""
            select coalesce(sum(i.cost), 0) from RackInstallation i
            where i.installedDate >= :from and i.installedDate < :to
            """)
    long sumCostBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
