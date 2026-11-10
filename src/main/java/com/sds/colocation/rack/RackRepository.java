package com.sds.colocation.rack;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RackRepository extends JpaRepository<Rack, Long> {

    List<Rack> findAllByOrderByIdAsc();

    boolean existsByName(String name);
}
