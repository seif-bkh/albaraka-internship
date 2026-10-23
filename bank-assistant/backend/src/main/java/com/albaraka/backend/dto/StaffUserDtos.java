package com.albaraka.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** Safe staff user API records; password hashes are never serialized. */
public final class StaffUserDtos {
    private StaffUserDtos() {
    }

    public record UserResponse(
            Long id,
            String email,
            String nom,
            String prenom,
            String role,
            String roleName,
            boolean actif,
            LocalDateTime dateCreation,
            LocalDateTime derniereConnexion) {
    }

    public record CreateUserRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72) String motDePasse,
            @NotBlank @Size(max = 120) String nom,
            @NotBlank @Size(max = 120) String prenom,
            @NotBlank @Size(max = 64) String role) {
    }

    public record UpdateUserRequest(
            @NotBlank @Size(max = 120) String nom,
            @NotBlank @Size(max = 120) String prenom,
            @NotBlank @Size(max = 64) String role,
            boolean actif,
            @Size(max = 72) String motDePasse) {
    }
}
