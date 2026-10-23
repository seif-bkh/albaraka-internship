package com.albaraka.backend.repository;

import com.albaraka.backend.model.Transaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByCompteClientIdOrderByIdDesc(Long clientId, Pageable pageable);

    List<Transaction> findByCompteClientIdAndCompteIdOrderByIdDesc(Long clientId, Long compteId, Pageable pageable);
}
