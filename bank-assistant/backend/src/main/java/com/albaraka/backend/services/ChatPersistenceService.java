package com.albaraka.backend.services;

import com.albaraka.backend.model.ConversationEntity;
import com.albaraka.backend.model.MessageEntity;
import com.albaraka.backend.repository.ConversationRepository;
import com.albaraka.backend.repository.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Single writer for the {@code conversations} / {@code messages} tables (Phase
 * 3.2 ownership handover). Faithfully reproduces the legacy
 * {@code api.py::persister_echange} contract so history rendering and the stats
 * dashboards keep working unchanged:
 *
 * <ul>
 *   <li>one {@code conversations} row per {@code session_id}, upserted — on first
 *       exchange {@code date_debut=date_fin=now, nombre_messages=1}; on later
 *       exchanges {@code date_fin=now} and {@code nombre_messages}+1;</li>
 *   <li>one {@code messages} row per exchange holding BOTH the user question and
 *       the agent reply (plus reformulation/intent/sources/rag flag).</li>
 * </ul>
 *
 * <p>NOT yet wired to any controller — wiring it is the coordinated handover
 * step that also stops the legacy Python writer (so rows are never duplicated).
 */
@Service
public class ChatPersistenceService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public ChatPersistenceService(ConversationRepository conversationRepository,
                                  MessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    /**
     * Persist one exchange (a user question plus the agent's structured reply).
     */
    @Transactional
    public void recordExchange(String sessionId, String langue, String adresseIp,
                               String question, String questionReformulee, String intention,
                               String reponse, List<String> sources, boolean ragUtilise) {
        recordExchange(sessionId, langue, adresseIp, question, questionReformulee, intention,
                reponse, sources, ragUtilise, null, null, null);
    }

    /** Full variant: also stores grounding outcome, domain and the agent trace (monitoring). */
    @Transactional
    public void recordExchange(String sessionId, String langue, String adresseIp,
                               String question, String questionReformulee, String intention,
                               String reponse, List<String> sources, boolean ragUtilise,
                               Boolean grounded, String domaine, String traceJson) {
        recordExchange(sessionId, langue, adresseIp, question, questionReformulee, intention,
                reponse, sources, ragUtilise, grounded, domaine, traceJson, null);
    }

    /**
     * Variant for an authenticated bank client: the conversation row is stamped
     * with their {@code client_id} so the portal can list their real history.
     * Anonymous sessions pass {@code clientId = null} and stay anonymous.
     */
    @Transactional
    public void recordExchange(String sessionId, String langue, String adresseIp,
                               String question, String questionReformulee, String intention,
                               String reponse, List<String> sources, boolean ragUtilise,
                               Boolean grounded, String domaine, String traceJson,
                               Long clientId) {
        recordExchange(sessionId, langue, adresseIp, question, questionReformulee, intention,
                reponse, sources, ragUtilise, grounded, domaine, traceJson, clientId, null, null, null, null);
    }

    /** Full observability variant including model, tokens, latency and real timestamps. */
    @Transactional
    public MessageEntity recordExchange(String sessionId, String langue, String adresseIp,
                                        String question, String questionReformulee, String intention,
                                        String reponse, List<String> sources, boolean ragUtilise,
                                        Boolean grounded, String domaine, String traceJson,
                                        Long clientId, String model, Integer tokensIn,
                                        Integer tokensOut, Integer latencyMs) {
        LocalDateTime now = LocalDateTime.now();
        ConversationEntity conversation = getOrCreateConversation(sessionId, langue, adresseIp, now, clientId);

        MessageEntity message = new MessageEntity();
        message.setConversation(conversation);
        message.setQuestion(question);
        message.setQuestionReformulee(questionReformulee);
        message.setIntention(intention);
        message.setReponse(reponse);
        message.setSourcesUtilisees(sources == null ? new ArrayList<>() : sources);
        message.setRagUtilise(ragUtilise);
        message.setDateCreation(now);
        message.setAskedAt(now);
        message.setAnsweredAt(now);
        message.setGrounded(grounded);
        message.setDomaine(domaine);
        message.setLangue(langue);
        message.setTraceJson(traceJson);
        message.setUserId(clientId);
        message.setModel(model);
        message.setTokensIn(tokensIn);
        message.setTokensOut(tokensOut);
        message.setLatencyMs(latencyMs);
        message.setStatus(Boolean.FALSE.equals(grounded) ? "REFUSED" : "ANSWERED");
        return messageRepository.save(message);
    }

    /**
     * Step 1 of real-time tracking: save the question immediately as it enters Spring Boot.
     */
    @Transactional
    public MessageEntity startExchange(String sessionId, String langue, String adresseIp,
                                       String question, Long clientId) {
        return startExchange(sessionId, langue, adresseIp, question, clientId, clientId);
    }

    /**
     * Step 1 of real-time tracking (overload with explicit userId): save the question immediately.
     */
    @Transactional
    public MessageEntity startExchange(String sessionId, String langue, String adresseIp,
                                       String question, Long clientId, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        ConversationEntity conversation = getOrCreateConversation(sessionId, langue, adresseIp, now, clientId);

        MessageEntity message = new MessageEntity();
        message.setConversation(conversation);
        message.setQuestion(question);
        message.setDateCreation(now);
        message.setAskedAt(now);
        message.setUserId(userId != null ? userId : clientId);
        message.setLangue(langue);
        message.setStatus("IN_PROGRESS");
        message.setSourcesUtilisees(new ArrayList<>());
        return messageRepository.save(message);
    }

    /**
     * Step 2 of real-time tracking: update the question row once the answer is ready.
     */
    @Transactional
    public void completeExchange(Long messageId, String intention, String reponse, List<String> sources,
                                 boolean ragUtilise, Boolean grounded, String domaine,
                                 String traceJson, String model,
                                 Integer tokensIn, Integer tokensOut, Long latencyMs,
                                 String status, LocalDateTime answeredAt) {
        if (messageId == null) return;
        messageRepository.findById(messageId).ifPresent(m -> {
            m.setAnsweredAt(answeredAt != null ? answeredAt : LocalDateTime.now());
            m.setReponse(reponse);
            m.setSourcesUtilisees(sources == null ? new ArrayList<>() : sources);
            m.setRagUtilise(ragUtilise);
            m.setGrounded(grounded);
            m.setDomaine(domaine);
            m.setIntention(intention);
            m.setTraceJson(traceJson);
            m.setModel(model);
            m.setTokensIn(tokensIn);
            m.setTokensOut(tokensOut);
            m.setLatencyMs(latencyMs != null ? latencyMs.intValue() : null);
            m.setStatus(status != null ? status : (Boolean.FALSE.equals(grounded) ? "REFUSED" : "ANSWERED"));
            messageRepository.save(m);
        });
    }

    @Transactional
    public void completeExchange(Long messageId, String reponse, List<String> sources,
                                 boolean ragUtilise, Boolean grounded, String domaine,
                                 String intention, String traceJson, String model,
                                 Integer tokensIn, Integer tokensOut, Integer latencyMs,
                                 String status) {
        completeExchange(messageId, intention, reponse, sources, ragUtilise, grounded,
                domaine, traceJson, model, tokensIn, tokensOut,
                latencyMs != null ? latencyMs.longValue() : null, status, LocalDateTime.now());
    }

    private ConversationEntity getOrCreateConversation(String sessionId, String langue,
                                                       String adresseIp, LocalDateTime now,
                                                       Long clientId) {
        ConversationEntity conversation = conversationRepository.findFirstBySessionId(sessionId);
        if (conversation == null) {
            conversation = new ConversationEntity();
            conversation.setSessionId(sessionId);
            conversation.setLangue(langue);
            conversation.setAdresseIp(adresseIp);
            conversation.setLocalisation(adresseIp);
            conversation.setDateDebut(now);
            conversation.setDateFin(now);
            conversation.setNombreMessages(1);
        } else {
            conversation.setLangue(langue);
            conversation.setLocalisation(adresseIp);
            conversation.setDateFin(now);
            conversation.setNombreMessages(
                    (conversation.getNombreMessages() == null ? 0 : conversation.getNombreMessages()) + 1);
        }
        if (clientId != null) {
            conversation.setClientId(clientId);
        }
        return conversationRepository.save(conversation);
    }

    /**
     * The last {@code n} exchanges for a session, oldest first — used by the
     * forward-to-agent call so the stateless agent gets recent context.
     */
    @Transactional(readOnly = true)
    public List<MessageEntity> lastTurns(String sessionId, int n) {
        List<MessageEntity> all = messageRepository.findMessagesBySessionIdOrderByDateCreationAsc(sessionId);
        if (all.size() <= n) {
            return all;
        }
        return all.subList(all.size() - n, all.size());
    }
}
