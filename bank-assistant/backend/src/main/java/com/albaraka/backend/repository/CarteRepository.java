package com.albaraka.backend.repository;

import com.albaraka.backend.model.Carte;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CarteRepository extends JpaRepository<Carte, Long> {
    List<Carte> findByClientIdOrderByIdAsc(Long clientId);
}
