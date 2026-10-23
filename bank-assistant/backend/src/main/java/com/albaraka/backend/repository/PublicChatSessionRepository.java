package com.albaraka.backend.repository;

import com.albaraka.backend.model.PublicChatSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PublicChatSessionRepository extends JpaRepository<PublicChatSessionEntity, String> {

    @Query("SELECT COUNT(s) FROM PublicChatSessionEntity s WHERE s.ipAddress = :ipAddress AND s.dateCreation > :timeLimit")
    long countRecentSessionsByIp(@Param("ipAddress") String ipAddress, @Param("timeLimit") LocalDateTime timeLimit);

    List<PublicChatSessionEntity> findByUserIdOrderByDateDerniereActiviteDesc(Long userId);
}
