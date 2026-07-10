package com.gskart.product.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OutboxJacksonConfig {

    // SB4's auto-configured ObjectMapper is the new Jackson 3 (tools.jackson) mapper; the outbox
    // payload serialization and spring-kafka's Json(De)Serializer still use classic Jackson 2
    // (com.fasterxml.jackson.databind), so that type needs an explicit bean here.
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }
}
