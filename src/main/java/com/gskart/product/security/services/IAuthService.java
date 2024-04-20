package com.gskart.product.security.services;

import com.gskart.product.DTOs.authService.ClaimsResponse;

public interface IAuthService {
    ClaimsResponse getUserClaims(String authHeader);
}
