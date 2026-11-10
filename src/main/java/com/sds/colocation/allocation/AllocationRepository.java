package com.sds.colocation.allocation;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AllocationRepository extends JpaRepository<Allocation, Long> {

    List<Allocation> findByRackIdAndStatus(Long rackId, AllocationStatus status);

    @Query("""
            select distinct c from Allocation c
            join fetch c.customer
            join fetch c.rack
            left join fetch c.servers
            where c.status = com.sds.colocation.allocation.AllocationStatus.ACTIVE
            """)
    List<Allocation> findAllActiveWithDetails();

    @Query("""
            select distinct c from Allocation c
            join fetch c.customer
            join fetch c.rack
            left join fetch c.servers
            """)
    List<Allocation> findAllWithDetails();

    @Query("""
            select distinct c from Allocation c
            join fetch c.customer
            left join fetch c.servers
            where c.rack.id = :rackId
              and c.status = com.sds.colocation.allocation.AllocationStatus.ACTIVE
            """)
    List<Allocation> findActiveByRackId(@Param("rackId") Long rackId);

    @Query("""
            select c from Allocation c
            join fetch c.customer
            join fetch c.rack
            left join fetch c.servers
            where c.id = :id
            """)
    Optional<Allocation> findWithDetailsById(@Param("id") Long id);
}
