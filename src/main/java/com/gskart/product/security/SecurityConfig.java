package com.gskart.product.security;

import com.gskart.product.security.filters.ResourceAuthorizationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final ResourceAuthorizationFilter resourceAuthorizationFilter;

    public SecurityConfig(ResourceAuthorizationFilter resourceAuthorizationFilter) {
        this.resourceAuthorizationFilter = resourceAuthorizationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests((authorizeRequests) -> {
                    authorizeRequests.anyRequest().authenticated();
                })
                .addFilterBefore(resourceAuthorizationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
