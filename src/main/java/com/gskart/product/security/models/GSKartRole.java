package com.gskart.product.security.models;

import com.gskart.product.DTOs.authService.RoleDto;
import org.springframework.security.core.GrantedAuthority;

public class GSKartRole implements GrantedAuthority {

    private final RoleDto role;

    public GSKartRole(RoleDto role) {
        this.role = role;
    }

    @Override
    public String getAuthority() {
       return role.getName();
    }
}
