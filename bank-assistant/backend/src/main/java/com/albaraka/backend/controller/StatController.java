package com.albaraka.backend.controller;

import com.albaraka.backend.repository.ConversationRepository;
import com.albaraka.backend.repository.DocumentRepository;
import com.albaraka.backend.repository.MessageRepository;
import com.albaraka.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/stats")
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN_SUPERVISION', 'ROLE_ADMIN_DOCUMENTS', 'ROLE_VIEWER')")
public class StatController {

    @Autowired UserRepository userRepository;
    @Autowired DocumentRepository documentRepository;
    @Autowired ConversationRepository conversationRepository;
    @Autowired MessageRepository messageRepository;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        // Utilisateurs
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.findAll().stream()
            .filter(u -> u.isActif()).count();

        // Documents
        long totalDocs = documentRepository.count();
        long indexedDocs = documentRepository.findAll().stream()
            .filter(d -> d.getStatutIndexation() != null
                && "INDEXE".equals(d.getStatutIndexation().name()))
            .count();

        // Conversations et messages
        long totalConversations = conversationRepository.count();
        long totalMessages = messageRepository.count();

        // Répartition par langue
        List<Object[]> parLangueRaw = conversationRepository.countByLangue();
        List<Map<String, Object>> langueStats = parLangueRaw.stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("langue", row[0] != null ? row[0].toString() : "N/A");
            m.put("count", row[1]);
            return m;
        }).collect(Collectors.toList());

        // Répartition par intention
        List<Object[]> parIntentionRaw = messageRepository.countByIntention();
        List<Map<String, Object>> intentionStats = parIntentionRaw.stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("intention", row[0] != null ? row[0].toString() : "N/A");
            m.put("count", row[1]);
            return m;
        }).collect(Collectors.toList());

        // Activité par jour (30 derniers jours)
        List<Object[]> parJourRaw = conversationRepository.countByDay();
        List<Map<String, Object>> jourStats = parJourRaw.stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("jour", row[0] != null ? row[0].toString() : "");
            m.put("count", row[1]);
            return m;
        }).collect(Collectors.toList());
        Collections.reverse(jourStats); // ordre chronologique

        long totalClients = conversationRepository.countUniqueClients();

        stats.put("totalUsers", totalUsers);
        stats.put("activeUsers", activeUsers);
        stats.put("totalClients", totalClients);
        stats.put("totalDocuments", totalDocs);
        stats.put("indexedDocuments", indexedDocs);
        stats.put("totalConversations", totalConversations);
        stats.put("totalMessages", totalMessages);
        stats.put("parLangue", langueStats);
        stats.put("parIntention", intentionStats);
        stats.put("parJour", jourStats);

        return ResponseEntity.ok(stats);
    }
}
