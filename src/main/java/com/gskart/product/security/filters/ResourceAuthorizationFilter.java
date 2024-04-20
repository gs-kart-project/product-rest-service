package com.gskart.product.security.filters;

import com.gskart.product.DTOs.authService.ClaimsResponse;
import com.gskart.product.security.models.GSKartResourceServerUser;
import com.gskart.product.security.services.AuthService;
import com.gskart.product.security.models.GSKartResourceServerUserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ResourceAuthorizationFilter extends OncePerRequestFilter {

    private final AuthService authService;

    private final GSKartResourceServerUserContext resourceServerUserContext;

    public ResourceAuthorizationFilter(AuthService authService, GSKartResourceServerUserContext resourceServerUserContext) {
        this.authService = authService;
        this.resourceServerUserContext = resourceServerUserContext;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if(authHeader == null || !authHeader.startsWith("Bearer ")) {
            System.out.println("ResourceAuthorizationFilter.doFilterInternal - Authorization header invalid or not found.");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        try {
            ClaimsResponse claimsResponse = authService.getUserClaims(authHeader);
            if (claimsResponse == null) {
                System.out.println("ResourceAuthorizationFilter.doFilterInternal - claims response is null.");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            String userName = claimsResponse.getUsername();
            if (userName != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                resourceServerUserContext.setGskartResourceServerUser(new GSKartResourceServerUser(claimsResponse));
                UserDetails userDetails = resourceServerUserContext.getGskartResourceServerUser();
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
            filterChain.doFilter(request, response);
        }
        catch (Exception e) {
            System.out.println("ResourceAuthorizationFilter.doFilterInternal - caught an exception. Details are provided below.");
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }
}
