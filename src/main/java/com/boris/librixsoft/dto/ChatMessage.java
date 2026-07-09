package com.boris.librixsoft.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

public record ChatMessage(String role, String content) {
}
