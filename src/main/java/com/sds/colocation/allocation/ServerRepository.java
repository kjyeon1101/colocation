package com.sds.colocation.allocation;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ServerRepository extends JpaRepository<Server, Long> {

    Optional<Server> findByRequestKey(String requestKey);
}
