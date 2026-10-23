package com.albaraka.backend.dto;

import java.util.List;

public class JwtResponse {
    private String token;
    private String type = "Bearer";
    private String refreshToken;
    private Long id;
    private String email;
    private List<String> roles;

    public JwtResponse(String accessToken, String refreshToken, Long id, String email, List<String> roles) {
        this.token = accessToken;
        this.refreshToken = refreshToken;
        this.id = id;
        this.email = email;
        this.roles = roles;
    }

    public String getToken() { return token; }
    public String getType() { return type; }
    public String getRefreshToken() { return refreshToken; }
    public Long getId() { return id; }
    public String getEmail() { return email; }
    public List<String> getRoles() { return roles; }
}
