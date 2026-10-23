package com.albaraka.backend.controller;

import com.albaraka.backend.dto.*;
import com.albaraka.backend.services.ChatTraceDocuments;
import com.albaraka.backend.model.ConversationEntity;
import com.albaraka.backend.model.Document;
import com.albaraka.backend.model.MessageEntity;
import com.albaraka.backend.repository.ConversationRepository;
import com.albaraka.backend.repository.DocumentRepository;
import com.albaraka.backend.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@CrossOrigin(origins = {"http://localhost:4200", "http://127.0.0.1:4200"}, allowCredentials = "true")
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN_SUPERVISION')")
public class ClientController {

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private DocumentRepository documentRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/clients/conversations
    // Liste paginée de toutes les conversations (IP masquée)
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/api/clients/conversations")
    public Page<ConversationSummaryDto> getConversations(
            @RequestParam(required = false) String langue,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fin,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageRequest pageable = PageRequest.of(page, size);

        boolean hasLangue = langue != null && !langue.isBlank();
        boolean hasDebut  = debut != null;
        boolean hasFin    = fin != null;

        // Si seul debut est fourni, on utilise la date du jour comme fin par défaut
        LocalDateTime debutEff = hasDebut ? debut : null;
        LocalDateTime finEff   = hasFin   ? fin   : (hasDebut ? LocalDateTime.now() : null);

        Page<ConversationEntity> result;
        if (hasLangue && hasDebut) {
            result = conversationRepository.findByLangueAndDateRange(langue, debutEff, finEff, pageable);
        } else if (hasLangue) {
            result = conversationRepository.findByLangueOrderByDateDebutDesc(langue, pageable);
        } else if (hasDebut) {
            result = conversationRepository.findByDateRange(debutEff, finEff, pageable);
        } else {
            result = conversationRepository.findAllByOrderByDateDebutDesc(pageable);
        }

        List<ConversationSummaryDto> dtos = result.getContent().stream()
            .map(c -> new ConversationSummaryDto(
                c.getId(), c.getSessionId(), c.getLangue(),
                c.getNombreMessages(), c.getDateDebut(), c.getDateFin(), c.getLocalisation()
            ))
            .collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, result.getTotalElements());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/clients/conversations/{id}/messages
    // Historique complet d'une conversation (IP visible ici car accès individuel)
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/api/clients/conversations/{conversationId}/messages")
    public ResponseEntity<?> getMessages(@PathVariable Long conversationId) {
        Optional<ConversationEntity> conv = conversationRepository.findById(conversationId);
        if (conv.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<MessageEntity> messages = messageRepository
            .findByConversation_IdOrderByDateCreationAsc(conversationId);

        List<MessageDto> dtos = messages.stream()
            .map(m -> new MessageDto(
                m.getId(), m.getQuestion(), m.getQuestionReformulee(),
                m.getIntention(), m.getReponse(), m.getSourcesUtilisees(),
                m.getRagUtilise(), m.getDateCreation(),
                // L'admin voit les documents visités, pas les alias de citation :
                // c'est la liste complète de ce que la recherche a consulté.
                ChatTraceDocuments.fromTrace(m.getTraceJson(), m.getSourcesUtilisees(), m.getIntention()),
                MessageDto.Observability.of(m)
            ))
            .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("conversationId", conversationId);
        response.put("sessionId", conv.get().getSessionId());
        response.put("langue", conv.get().getLangue());
        response.put("adresseIp", conv.get().getAdresseIp());  // IP complète dans le détail individuel
        response.put("clientId", conv.get().getClientId());
        response.put("dateDebut", conv.get().getDateDebut());
        response.put("dateFin", conv.get().getDateFin());
        response.put("messages", dtos);

        return ResponseEntity.ok(response);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/clients/conversations/{id}/resume
    // Résumé rapide : stats + sources uniques de la conversation
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/api/clients/conversations/{conversationId}/resume")
    public ResponseEntity<?> getResume(@PathVariable Long conversationId) {
        Optional<ConversationEntity> conv = conversationRepository.findById(conversationId);
        if (conv.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ConversationEntity c = conv.get();
        List<MessageEntity> messages = messageRepository
            .findByConversation_IdOrderByDateCreationAsc(conversationId);

        // Collecter toutes les sources uniques sur toute la conversation
        List<String> sourcesUniques = messages.stream()
            .filter(m -> m.getSourcesUtilisees() != null)
            .flatMap(m -> m.getSourcesUtilisees().stream())
            .distinct()
            .sorted()
            .collect(Collectors.toList());

        ConversationResumeDto resume = new ConversationResumeDto(
            c.getId(), c.getLangue(), c.getNombreMessages(),
            c.getDateDebut(), c.getDateFin(), c.getAdresseIp(), sourcesUniques
        );

        return ResponseEntity.ok(resume);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/documents/{documentId}/utilisations
    // Gouvernance : dans quelles conversations ce document a-t-il été source ?
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/api/documents/{documentId}/utilisations")
    public ResponseEntity<?> getDocumentUtilisations(
            @PathVariable Long documentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Optional<Document> doc = documentRepository.findById(documentId);
        if (doc.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // Le nom du fichier est la clé de recherche dans sources_utilisees[]
        String nomFichier = doc.get().getNomFichier();
        if (nomFichier == null || nomFichier.isBlank()) {
            return ResponseEntity.ok(Map.of(
                "documentId", documentId,
                "nomFichier", "N/A",
                "utilisations", List.of(),
                "total", 0
            ));
        }

        PageRequest pageable = PageRequest.of(page, size);
        Page<MessageEntity> messages = messageRepository.findByDocumentSource(nomFichier, pageable);

        List<DocumentUtilisationDto> dtos = messages.getContent().stream()
            .map(m -> {
                String sessionId = m.getConversation() != null
                    ? m.getConversation().getSessionId()
                    : null;
                Long convId = m.getConversation() != null
                    ? m.getConversation().getId()
                    : null;
                return new DocumentUtilisationDto(
                    m.getId(), convId, sessionId,
                    m.getQuestion(), m.getIntention(), m.getDateCreation()
                );
            })
            .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("documentId", documentId);
        response.put("nomFichier", nomFichier);
        response.put("total", messages.getTotalElements());
        response.put("page", page);
        response.put("utilisations", dtos);

        return ResponseEntity.ok(response);
    }
}
