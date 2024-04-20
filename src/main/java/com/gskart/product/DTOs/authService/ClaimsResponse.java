package com.gskart.product.DTOs.authService;

import lombok.Data;

import java.util.Set;

@Data
public class ClaimsResponse {
    private String username;
    private String email;
    private Set<RoleDto> roles;
}
