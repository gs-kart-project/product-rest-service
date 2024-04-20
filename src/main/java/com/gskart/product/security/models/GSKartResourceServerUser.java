package com.gskart.product.security.models;

import com.gskart.product.DTOs.authService.ClaimsResponse;
import com.mysql.cj.util.StringUtils;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public class GSKartResourceServerUser implements UserDetails {
    private final ClaimsResponse claimsResponse;

    public GSKartResourceServerUser(ClaimsResponse claimsResponse) {
        this.claimsResponse = claimsResponse;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if(claimsResponse == null || claimsResponse.getRoles().isEmpty()) {
            return new ArrayList<>();
        }
        Collection<GSKartRole> gsKartRoleList = claimsResponse.getRoles().stream()
                .map(GSKartRole::new)
                .toList();
        return gsKartRoleList;
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return claimsResponse.getUsername();
    }
    // ToDo isAccountNonExpired, isAccountNonLocked, isCredentialsNonExpired, isEnabled should be set in claims response
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
