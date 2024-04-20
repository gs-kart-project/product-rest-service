package com.gskart.product.security.services;

import com.gskart.product.DTOs.authService.ClaimsResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

@Service
public class AuthService implements IAuthService {
    @Value("${gskart.service.auth}")
    private String baseUrl;

    private final String tokenClaimsEndpoint = "/auth/token/claims";

    private final RestTemplate restTemplate;

    public AuthService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * @param authHeader Header value "Bearer {token}"
     * @return
     */
    @Override
    public ClaimsResponse getUserClaims(String authHeader){
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("Authorization", authHeader);
        headers.add("Content-Type", "application/json");
        RequestEntity requestEntity = new RequestEntity(headers, HttpMethod.GET, URI.create(baseUrl + tokenClaimsEndpoint));
        ResponseEntity<ClaimsResponse> claimsResponseResponseEntity = restTemplate.exchange(requestEntity, ClaimsResponse.class);
        if(claimsResponseResponseEntity.getStatusCode().is2xxSuccessful()){
            if(claimsResponseResponseEntity.hasBody()){
                return claimsResponseResponseEntity.getBody();
            }
        }
        // ToDo Log Claims API response
        return null;
    }
}
