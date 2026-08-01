package com.gskart.product.integration;

import com.gskart.product.entities.Category;
import com.gskart.product.entities.Product;
import com.gskart.product.search.ProductSearchResult;
import com.gskart.product.security.models.GSKartResourceServerUser;
import com.gskart.product.security.models.GSKartResourceServerUserContext;
import com.gskart.product.services.ICategoryService;
import com.gskart.product.services.ISearchService;
import com.gskart.product.services.IProductService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Full write -> outbox -> Kafka -> ES -> /search pipeline against real infra (Testcontainers), as
// opposed to the mocked unit tests elsewhere. Security (SASL/xpack) is exercised manually via the
// docker-compose stack (see TRACKER.md) - this test uses plain containers so it stays fast and
// focuses on the pipeline logic itself, not re-proving auth already covered manually.
@Testcontainers
@SpringBootTest
class ProductSearchPipelineIntegrationTest {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("productDb");

    @Container
    static final ElasticsearchContainer ELASTICSEARCH =
            new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:9.2.8")
                    .withEnv("xpack.security.enabled", "false")
                    .withEnv("discovery.type", "single-node");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.3.1");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.url", MYSQL::getJdbcUrl);
        registry.add("spring.flyway.user", MYSQL::getUsername);
        registry.add("spring.flyway.password", MYSQL::getPassword);

        registry.add("spring.elasticsearch.uris", () -> "http://" + ELASTICSEARCH.getHttpHostAddress());
        registry.add("spring.elasticsearch.username", () -> "");
        registry.add("spring.elasticsearch.password", () -> "");

        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.security.protocol", () -> "PLAINTEXT");
        registry.add("spring.kafka.properties.sasl.mechanism", () -> "");
        registry.add("spring.kafka.properties.sasl.jaas.config", () -> "");
    }

    @Autowired
    @Qualifier("gskartProductService")
    IProductService productService;

    @Autowired
    ICategoryService categoryService;

    @Autowired
    ISearchService searchService;

    @Autowired
    GSKartResourceServerUserContext resourceServerUserContext;

    @Autowired
    WebApplicationContext webApplicationContext;

    // Overrides the JWKS-backed decoder autoconfigured from jwk-set-uri, so tests that decode a
    // real bearer token don't need a live auth-rest-service or a signed token.
    @MockitoBean
    JwtDecoder jwtDecoder;

    // The service layer reads the current user off a ThreadLocal that's normally populated by
    // JwtUserContextFilter on each HTTP request (for createdBy/modifiedBy auditing); this test
    // calls the service directly, so it has to seed that ThreadLocal itself.
    @BeforeEach
    void seedResourceServerUser() {
        resourceServerUserContext.setGskartResourceServerUser(
                new GSKartResourceServerUser(jwtFor("integration-test", "integration-test@gskart.local", List.of()), List.of()));
    }

    // Mirrors JwtUserContextFilter's own finally-block cleanup - this test seeds
    // the ThreadLocal directly (bypassing the filter that would normally clear it), so it has to
    // clear it itself too.
    @AfterEach
    void clearResourceServerUser() {
        resourceServerUserContext.clear();
    }

    private Jwt jwtFor(String username, String email, List<String> roles) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("sub", username)
                .claim("email", email)
                .claim("roles", roles)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }

    @Test
    void writeFlowsThroughOutboxKafkaIntoSearchIndex() throws Exception {
        Category category = new Category();
        category.setName("Integration Test Category");
        category.setDescription("Created by the pipeline integration test");
        category = categoryService.save(category);

        Product product = new Product();
        product.setName("Integration Test Widget");
        product.setDescription("A widget used to prove the pipeline works end to end");
        product.setPrice(BigDecimal.valueOf(42));

        Product saved = productService.addNew(product, category.getId());

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(250))
                .untilAsserted(() -> {
                    Page<ProductSearchResult> results =
                            searchService.searchProducts("Widget", 0, 10, Map.of());
                    assertThat(results.getContent())
                            .anyMatch(doc -> doc.getProductId().equals(saved.getId()));
                });
    }

    // m8 fix: standalone MockMvc (used by ProductsControllerTest) has no security filters, so it
    // can't prove @PreAuthorize is actually enforced - only a full Spring context test can. Builds
    // MockMvc against the real WebApplicationContext with the real Spring Security filter chain
    // via the springSecurity() configurer, which also wires the jwt() post-processor below.
    private MockMvc securedMockMvc() {
        return MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void indexJobIsForbiddenForCallerWithoutDeveloperOrAdminRole() throws Exception {
        securedMockMvc().perform(post("/api/v1/products/index-jobs")
                        .with(jwt().authorities(new SimpleGrantedAuthority("Customer"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void indexJobSucceedsForCallerWithDeveloperRole() throws Exception {
        securedMockMvc().perform(post("/api/v1/products/index-jobs")
                        .with(jwt().authorities(new SimpleGrantedAuthority("Developer"))))
                .andExpect(status().isAccepted());
    }

    // The jwt() post-processor above injects authorities directly and bypasses both the decoder
    // and the JwtAuthenticationConverter - it doesn't prove setAuthoritiesClaimName("roles") is
    // wired correctly. This test drives a real bearer token through the full chain (mocked decoder,
    // real converter) to prove the flat "roles" claim is what actually grants the authority.
    @Test
    void rolesClaimIsMappedToAnAuthorityByTheRealConverter() throws Exception {
        when(jwtDecoder.decode("developer-token"))
                .thenReturn(jwtFor("authz-test-user", "authz-test-user@gskart.local", List.of("Developer")));

        securedMockMvc().perform(post("/api/v1/products/index-jobs")
                        .header("Authorization", "Bearer developer-token"))
                .andExpect(status().isAccepted());
    }

    // Standalone MockMvc (used by ProductsControllerTest) has no security filters, so it can't
    // prove the @PreAuthorize on POST /category/{categoryId} is actually enforced either - same gap
    // as the index-jobs endpoint above, closed the same way.
    @Test
    void addProductIsForbiddenForCallerWithoutDeveloperOrAdminRole() throws Exception {
        Category category = new Category();
        category.setName("Guarded Category");
        category.setDescription("Exercises the @PreAuthorize guard on POST /category/{id}");
        category = categoryService.save(category);

        securedMockMvc().perform(post("/api/v1/products/category/" + category.getId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("Customer")))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content("{\"name\":\"Guarded Product\",\"price\":9.99}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void addProductSucceedsForCallerWithDeveloperRole() throws Exception {
        Category category = new Category();
        category.setName("Allowed Category");
        category.setDescription("Exercises the @PreAuthorize guard on POST /category/{id}");
        category = categoryService.save(category);

        securedMockMvc().perform(post("/api/v1/products/category/" + category.getId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("Developer")))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content("{\"name\":\"Allowed Product\",\"price\":9.99}"))
                .andExpect(status().isCreated());
    }
}
