package com.albaraka.backend.dto;

import java.util.List;

/** Fresh principal profile used by the client to refresh permissions after sign-in. */
public record CurrentUserResponse(Long id, String email, List<String> roles) {
}
