package com.albaraka.backend.repository;

import com.albaraka.backend.model.ConversationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository READ-ONLY pour les conversations (table geree par Python).
 * On utilise des methodes separees pour eviter de passer null en parametre
 * LocalDateTime dans JPQL (PostgreSQL ne peut pas determiner le type de null).
 */
@Repository
public interface ConversationRepository extends JpaRepository<ConversationEntity, Long> {

    /** Conversations owned by an authenticated bank client, newest first (portal). */
    List<ConversationEntity> findByClientIdOrderByDateDebutDesc(Long clientId);


    /** One row per session_id (legacy Python UPSERT key). Take first to be safe. */
    ConversationEntity findFirstBySessionId(String sessionId);

    // ── Sans filtre ──────────────────────────────────────────────────────────
    Page<ConversationEntity> findAllByOrderByDateDebutDesc(Pageable pageable);

    @Query("SELECT COUNT(DISTINCT c.sessionId) FROM ConversationEntity c")
    long countUniqueClients();

    // ── Filtre langue uniquement ─────────────────────────────────────────────
    Page<ConversationEntity> findByLangueOrderByDateDebutDesc(String langue, Pageable pageable);

    // ── Filtre dates uniquement ──────────────────────────────────────────────
    @Query("SELECT c FROM ConversationEntity c " +
           "WHERE c.dateDebut >= :debut AND c.dateDebut <= :fin " +
           "ORDER BY c.dateDebut DESC")
    Page<ConversationEntity> findByDateRange(
        @Param("debut") LocalDateTime debut,
        @Param("fin") LocalDateTime fin,
        Pageable pageable
    );

    // ── Filtre langue + dates ────────────────────────────────────────────────
    @Query("SELECT c FROM ConversationEntity c " +
           "WHERE c.langue = :langue AND c.dateDebut >= :debut AND c.dateDebut <= :fin " +
           "ORDER BY c.dateDebut DESC")
    Page<ConversationEntity> findByLangueAndDateRange(
        @Param("langue") String langue,
        @Param("debut") LocalDateTime debut,
        @Param("fin") LocalDateTime fin,
        Pageable pageable
    );
    @Query("SELECT c.langue, COUNT(c) FROM ConversationEntity c GROUP BY c.langue ORDER BY COUNT(c) DESC")
    List<Object[]> countByLangue();

    @Query(value = "SELECT DATE(date_debut) as jour, COUNT(*) as nb FROM conversations GROUP BY DATE(date_debut) ORDER BY jour DESC LIMIT 30", nativeQuery = true)
    List<Object[]> countByDay();
}
