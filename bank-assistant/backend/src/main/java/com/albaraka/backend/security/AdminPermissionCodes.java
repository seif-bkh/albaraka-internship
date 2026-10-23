package com.albaraka.backend.security;

import java.util.List;
import java.util.Set;

/** Closed set of permission keys that are understood and enforced by this application. */
public final class AdminPermissionCodes {
    public static final String DOCUMENTS_READ = "DOCUMENTS_READ";
    public static final String DOCUMENTS_CREATE = "DOCUMENTS_CREATE";
    public static final String DOCUMENTS_UPDATE = "DOCUMENTS_UPDATE";
    public static final String DOCUMENTS_DELETE = "DOCUMENTS_DELETE";
    public static final String DOCUMENTS_PROCESS = "DOCUMENTS_PROCESS";
    public static final String DOCUMENTS_INGESTION_READ = "DOCUMENTS_INGESTION_READ";

    public static final String RAGLAB_VIEW = "RAGLAB_CONSOLE_VIEW";
    public static final String RAGLAB_RUN = "RAGLAB_CONSOLE_RUN";
    public static final String RAGLAB_MANAGE = "RAGLAB_CONSOLE_MANAGE";

    public static final List<String> ORDERED = List.of(
            DOCUMENTS_READ,
            DOCUMENTS_CREATE,
            DOCUMENTS_UPDATE,
            DOCUMENTS_DELETE,
            DOCUMENTS_PROCESS,
            DOCUMENTS_INGESTION_READ,
            RAGLAB_VIEW,
            RAGLAB_RUN,
            RAGLAB_MANAGE);

    public static final Set<String> ALL = Set.copyOf(ORDERED);

    private AdminPermissionCodes() {
    }

    public static String authority(String permissionCode) {
        return "PERM_" + permissionCode;
    }
}
