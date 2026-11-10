package com.sds.colocation.relocation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RelocationRepository extends JpaRepository<Relocation, Long> {

    List<Relocation> findAllByOrderByRelocatedDateDescIdDesc();
}
