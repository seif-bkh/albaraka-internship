package com.albaraka.backend.repository;

import com.albaraka.backend.model.AgentEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Current + past addresses of the external RAG service (newest row wins). */
public interface AgentEndpointRepository extends JpaRepository<AgentEndpoint, Long> {

    Optional<AgentEndpoint> findFirstByOrderByUpdatedAtDescIdDesc();
}
