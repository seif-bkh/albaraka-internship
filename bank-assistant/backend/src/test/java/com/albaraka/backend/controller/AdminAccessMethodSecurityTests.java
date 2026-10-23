package com.albaraka.backend.controller;

import com.albaraka.backend.dto.AdminAccessDtos.PermissionDto;
import com.albaraka.backend.services.AdminAccessAuditService;
import com.albaraka.backend.services.AdminRoleService;
import com.albaraka.backend.services.AgentAdminAuditService;
import com.albaraka.backend.services.AgentEndpointService;
import com.albaraka.backend.dto.AgentAdminDtos.EndpointRequest;
import com.albaraka.backend.services.RagLabClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Exercises the actual @PreAuthorize proxies used by scoped console endpoints. */
@SpringJUnitConfig(AdminAccessMethodSecurityTests.MethodSecurityTestConfig.class)
class AdminAccessMethodSecurityTests {
    private final ObjectMapper mapper = new ObjectMapper();

    @org.springframework.beans.factory.annotation.Autowired
    private AdminRaglabController raglab;

    @org.springframework.beans.factory.annotation.Autowired
    private AdminAccessController accessControl;

    @org.springframework.beans.factory.annotation.Autowired
    private RagLabClient raglabClient;

    @org.springframework.beans.factory.annotation.Autowired
    private AdminAgentController agent;

    @org.springframework.beans.factory.annotation.Autowired
    private AgentEndpointService endpoints;

    @org.springframework.beans.factory.annotation.Autowired
    private AdminRoleService roleService;

    @org.springframework.beans.factory.annotation.Autowired
    private AdminAccessAuditService audit;

    @org.springframework.beans.factory.annotation.Autowired
    private AgentAdminAuditService agentAudit;

    @BeforeEach
    void resetMocks() {
        reset(raglabClient, roleService, audit, agentAudit, endpoints);
        when(raglabClient.searchChunks(anyString(), isNull())).thenReturn(result());
        when(raglabClient.search(anyString(), isNull(), isNull(), isNull(), isNull())).thenReturn(result());
        when(raglabClient.updateProfile(anyMap())).thenReturn(result());
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void viewMayUseTheHistoricalNoModelCitationLookup() {
        authenticate("PERM_RAGLAB_CONSOLE_VIEW");

        raglab.searchChunks(Map.of("text", "citation"));

        verify(raglabClient).searchChunks("citation", null);
    }

    @Test
    void viewCanInspectMaskedKeyStatusButOnlyManageCanChangeKeys() {
        when(raglabClient.keys()).thenReturn(result());
        when(raglabClient.setKey(anyMap())).thenReturn(result());
        authenticate("PERM_RAGLAB_CONSOLE_VIEW");

        raglab.keys();
        assertThrows(AccessDeniedException.class,
                () -> raglab.setKey(Map.of("key_env", "GROQ_API_KEY", "value", "fake-test-key"), authentication()));
        verify(raglabClient).keys();
        verify(raglabClient, never()).setKey(anyMap());

        authenticate("PERM_RAGLAB_CONSOLE_MANAGE");
        raglab.setKey(Map.of("key_env", "GROQ_API_KEY", "value", "fake-test-key"), authentication());
        verify(raglabClient).setKey(Map.of("key_env", "GROQ_API_KEY", "value", "fake-test-key"));
    }

    @Test
    void viewCannotRunRankedRetrievalAndTheServiceIsNotCalled() {
        authenticate("PERM_RAGLAB_CONSOLE_VIEW");

        assertThrows(AccessDeniedException.class,
                () -> raglab.search(Map.of("question", "query"), authentication()));
        verify(raglabClient, never()).search(anyString(), any(), any(), any(), any());
    }

    @Test
    void runMayExecuteSearchButCannotChangeRaglabSettings() {
        authenticate("PERM_RAGLAB_CONSOLE_RUN");

        raglab.search(Map.of("question", "query"), authentication());
        assertThrows(AccessDeniedException.class,
                () -> raglab.updateProfile(Map.of("chat_model", "new-model"), authentication()));

        verify(raglabClient).search("query", null, null, null, null);
        verify(raglabClient, never()).updateProfile(anyMap());
    }

    @Test
    void viewCannotChangeTheAssistantEndpointButManageCan() {
        EndpointRequest request = new EndpointRequest("https://raglab.example", "configuration change");
        authenticate("PERM_RAGLAB_CONSOLE_VIEW");

        assertThrows(AccessDeniedException.class, () -> agent.setEndpoint(request, authentication()));
        verify(endpoints, never()).set(anyString(), anyString(), anyString());

        authenticate("PERM_RAGLAB_CONSOLE_MANAGE");
        when(endpoints.set("https://raglab.example", "configuration change", "operator@example.test"))
                .thenReturn(new AgentEndpointService.EndpointView("https://raglab.example", "DATABASE",
                        null, "operator@example.test", LocalDateTime.now()));

        agent.setEndpoint(request, authentication());

        verify(endpoints).set("https://raglab.example", "configuration change", "operator@example.test");
    }

    @Test
    void manageMayUpdateSettingsAndIsTheOnlyDynamicPermissionForAccessAdministration() {
        authenticate("PERM_RAGLAB_CONSOLE_MANAGE");
        when(roleService.listSupportedPermissions()).thenReturn(List.of(
                new PermissionDto("DOCUMENTS_READ", "DOCUMENTS", "Lire", "Lire les documents", 10)));

        raglab.updateProfile(Map.of("chunk_size", 512), authentication());
        assertThrows(AccessDeniedException.class, () -> accessControl.permissions());

        authenticate("ROLE_SUPER_ADMIN");
        assertEquals(1, accessControl.permissions().size());
        verify(raglabClient).updateProfile(Map.of("chunk_size", 512));
        verify(roleService).listSupportedPermissions();
    }

    @Test
    void onlyManageOrTheGrandfatheredDocumentsRoleCanStartRaglabIngestion() {
        when(raglabClient.ingest(false)).thenReturn(result());

        authenticate("PERM_DOCUMENTS_PROCESS");
        assertThrows(AccessDeniedException.class, () -> raglab.ingest(false, authentication()));
        verify(raglabClient, never()).ingest(false);

        authenticate("PERM_RAGLAB_CONSOLE_VIEW");
        assertThrows(AccessDeniedException.class, () -> raglab.ingest(false, authentication()));
        verify(raglabClient, never()).ingest(false);

        // The legacy role alone is not enough if either migrated capability was removed.
        authenticate("ROLE_ADMIN_DOCUMENTS", "PERM_DOCUMENTS_PROCESS");
        assertThrows(AccessDeniedException.class, () -> raglab.ingest(false, authentication()));
        verify(raglabClient, never()).ingest(false);

        // Preserve the exact legacy operation without granting RAGLab Manage.
        authenticate("ROLE_ADMIN_DOCUMENTS", "PERM_RAGLAB_CONSOLE_VIEW", "PERM_DOCUMENTS_PROCESS");
        raglab.ingest(false, authentication());

        authenticate("PERM_RAGLAB_CONSOLE_MANAGE");
        raglab.ingest(false, authentication());
        verify(raglabClient, org.mockito.Mockito.times(2)).ingest(false);
    }

    @Test
    void raglabFeedMutationIsManageOnlyExceptForTheMigratedDocumentsRole() {
        when(raglabClient.pushDocument(anyString(), any(), anyMap())).thenReturn(result());
        Map<String, Object> body = Map.of("id", "terms");

        authenticate("PERM_DOCUMENTS_CREATE", "PERM_RAGLAB_CONSOLE_VIEW");
        assertThrows(AccessDeniedException.class,
                () -> raglab.pushDocumentJson(body, "terms", false, authentication()));
        verify(raglabClient, never()).pushDocument(anyString(), any(), anyMap());

        authenticate("ROLE_ADMIN_DOCUMENTS", "PERM_DOCUMENTS_CREATE");
        assertThrows(AccessDeniedException.class,
                () -> raglab.pushDocumentJson(body, "terms", false, authentication()));
        verify(raglabClient, never()).pushDocument(anyString(), any(), anyMap());

        authenticate("ROLE_ADMIN_DOCUMENTS", "PERM_RAGLAB_CONSOLE_VIEW", "PERM_DOCUMENTS_CREATE");
        raglab.pushDocumentJson(body, "terms", false, authentication());

        authenticate("PERM_RAGLAB_CONSOLE_MANAGE");
        raglab.pushDocumentJson(body, "terms", false, authentication());
        verify(raglabClient, org.mockito.Mockito.times(2))
                .pushDocument("terms", false, body);
    }

    @Test
    void indexingAPushedRaglabDocumentNeedsManageOrLegacyProcessGrant() {
        when(raglabClient.pushDocument(anyString(), any(), anyMap())).thenReturn(result());
        Map<String, Object> body = Map.of("id", "terms");

        authenticate("PERM_DOCUMENTS_CREATE", "PERM_DOCUMENTS_PROCESS");
        assertThrows(AccessDeniedException.class,
                () -> raglab.pushDocumentJson(body, "terms", true, authentication()));
        verify(raglabClient, never()).pushDocument(anyString(), any(), anyMap());

        authenticate("ROLE_ADMIN_DOCUMENTS", "PERM_RAGLAB_CONSOLE_VIEW", "PERM_DOCUMENTS_CREATE");
        assertThrows(AccessDeniedException.class,
                () -> raglab.pushDocumentJson(body, "terms", true, authentication()));
        verify(raglabClient, never()).pushDocument(anyString(), any(), anyMap());

        authenticate("ROLE_ADMIN_DOCUMENTS", "PERM_RAGLAB_CONSOLE_VIEW",
                "PERM_DOCUMENTS_CREATE", "PERM_DOCUMENTS_PROCESS");
        raglab.pushDocumentJson(body, "terms", true, authentication());

        authenticate("PERM_RAGLAB_CONSOLE_MANAGE");
        raglab.pushDocumentJson(body, "terms", true, authentication());
        verify(raglabClient, org.mockito.Mockito.times(2))
                .pushDocument("terms", true, body);
    }

    @Test
    void theRaglabFeedIsReadByRaglabViewNotDocumentsRead() {
        when(raglabClient.documents()).thenReturn(result());
        authenticate("PERM_DOCUMENTS_READ");
        assertThrows(AccessDeniedException.class, () -> raglab.documents());
        verify(raglabClient, never()).documents();

        authenticate("PERM_RAGLAB_CONSOLE_VIEW");
        raglab.documents();
        verify(raglabClient).documents();
    }

    @Test
    void raglabIngestionStatusIsNotGrantedByTheStandaloneHistoryCapability() {
        when(raglabClient.ingestStatus()).thenReturn(result());
        authenticate("PERM_DOCUMENTS_INGESTION_READ");
        assertThrows(AccessDeniedException.class, () -> raglab.ingestStatus());
        verify(raglabClient, never()).ingestStatus();

        authenticate("PERM_RAGLAB_CONSOLE_VIEW");
        raglab.ingestStatus();
        verify(raglabClient).ingestStatus();
    }

    private void authenticate(String... authorities) {
        var auth = new UsernamePasswordAuthenticationToken("operator@example.test", "n/a",
                AuthorityUtils.createAuthorityList(authorities));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Authentication authentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private RagLabClient.Result result() {
        return new RagLabClient.Result(mapper.createObjectNode(), 1L);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
        @Bean
        AgentEndpointService agentEndpointService() {
            return mock(AgentEndpointService.class);
        }

        @Bean
        com.albaraka.backend.services.CorpusIndexService corpusIndexService() {
            return mock(com.albaraka.backend.services.CorpusIndexService.class);
        }

        @Bean
        AdminAgentController agentController(AgentEndpointService endpoints,
                                             RagLabClient raglabClient,
                                             AgentAdminAuditService audit,
                                             com.albaraka.backend.services.CorpusIndexService corpus) {
            return new AdminAgentController(endpoints, raglabClient, audit, corpus);
        }

        @Bean
        RagLabClient raglabClient() {
            return mock(RagLabClient.class);
        }

        @Bean
        AdminAccessAuditService auditService() {
            return mock(AdminAccessAuditService.class);
        }

        @Bean
        AgentAdminAuditService agentAdminAuditService() {
            return mock(AgentAdminAuditService.class);
        }

        @Bean
        AdminRoleService roleService() {
            return mock(AdminRoleService.class);
        }

        @Bean
        AdminRaglabController raglabController(RagLabClient client, AgentAdminAuditService audit) {
            return new AdminRaglabController(client, audit);
        }

        @Bean
        AdminAccessController accessControlController(AdminRoleService roles, AdminAccessAuditService audit) {
            return new AdminAccessController(roles, audit);
        }
    }
}
