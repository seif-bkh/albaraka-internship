package com.albaraka.backend.dto;

import java.time.LocalDateTime;

public class ChatMessageHistoryDTO {
    private String role;
    private String content;
    private String time;
    private LocalDateTime dateCreation;
    private Integer tokens;

    public ChatMessageHistoryDTO() {}

    public ChatMessageHistoryDTO(String role, String content, String time, LocalDateTime dateCreation, Integer tokens) {
        this.role = role;
        this.content = content;
        this.time = time;
        this.dateCreation = dateCreation;
        this.tokens = tokens;
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    
    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
    
    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }
    
    public Integer getTokens() { return tokens; }
    public void setTokens(Integer tokens) { this.tokens = tokens; }
}
