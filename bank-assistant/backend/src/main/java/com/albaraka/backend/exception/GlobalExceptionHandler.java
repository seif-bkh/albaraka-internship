package com.albaraka.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import com.albaraka.backend.services.RagLabException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Turns exceptions into a consistent JSON error body:
 *
 * <pre>{ "timestamp": ..., "status": <int>, "error": <reason>, "message": <detail> }</pre>
 *
 * so the frontend never has to guess from an opaque 500.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        Map<String, Object> errorDetails = new LinkedHashMap<>();
        errorDetails.put("timestamp", new Date());
        errorDetails.put("status", status.value());
        errorDetails.put("error", status.getReasonPhrase());
        errorDetails.put("message", message);
        return new ResponseEntity<>(errorDetails, status);
    }

    /** 400 — bean validation failures (@Valid request bodies). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("timestamp", new Date());
        details.put("status", HttpStatus.BAD_REQUEST.value());
        details.put("error", "Bad Request");
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        details.put("fields", fieldErrors);
        return new ResponseEntity<>(details, HttpStatus.BAD_REQUEST);
    }

    /** 413 — file exceeds the configured multipart / upload cap. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUpload(MaxUploadSizeExceededException ex) {
        return body(HttpStatus.PAYLOAD_TOO_LARGE,
                "Fichier trop volumineux. La taille maximale autorisée est de 25 Mo.");
    }

    /** 404 — `findById(...).orElseThrow()` / empty result. */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoSuchElementException ex) {
        return body(HttpStatus.NOT_FOUND, "Ressource introuvable.");
    }

    /** 400 — explicit domain/bad-input rejections (e.g. disallowed upload). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return body(HttpStatus.BAD_REQUEST,
                ex.getMessage() != null ? ex.getMessage() : "Requête invalide.");
    }

    /**
     * 502/503 — the external RAG service is unset, unreachable or answered
     * something we cannot use. {@code reason} is the stable machine code the
     * console shows; {@code context} is the address we were talking to.
     */
    @ExceptionHandler(RagLabException.class)
    public ResponseEntity<Map<String, Object>> handleRagLab(RagLabException ex) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("timestamp", new Date());
        details.put("status", ex.getStatus());
        details.put("error", "RAGLab");
        details.put("reason", ex.getReason());
        details.put("message", ex.getMessage());
        if (ex.getContext() != null) {
            details.put("context", ex.getContext());
        }
        // The service can answer a code Spring does not model; report that as a bad
        // gateway rather than letting valueOf() throw inside the handler.
        HttpStatus status = HttpStatus.resolve(ex.getStatus());
        return new ResponseEntity<>(details, status == null ? HttpStatus.BAD_GATEWAY : status);
    }

    /** 500 — anything else; keep the server from leaking internals. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> globalExceptionHandler(Exception ex) {
        return body(HttpStatus.INTERNAL_SERVER_ERROR,
                ex.getMessage() != null ? ex.getMessage() : "Erreur interne du serveur.");
    }
}
