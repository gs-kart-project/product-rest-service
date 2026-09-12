package com.gskart.product.integration;

import com.gskart.product.entities.Category;
import com.gskart.product.entities.Product;
import com.gskart.product.search.ProductSearchResult;
import com.gskart.commons.security.ClaimNames;
import com.gskart.commons.security.GSKartResourceServerUser;
import com.gskart.commons.security.GSKartResourceServerUserContext;
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

// Runs the real write -> outbox -> Kafka -> ES -> /search pipeline against actual containers,
// not mocks. Skips SASL/xpack here since that's covered manually elsewhere - this just checks
// the pipeline itself works end to end.
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

    // Swaps out the real JWKS-backed decoder so a test token doesn't need a live auth-rest-service
    // or an actually-signed token.
    @MockitoBean
    JwtDecoder jwtDecoder;

    // Services read the current user off a ThreadLocal that JwtUserContextFilter normally fills
    // in per request. This test calls the service directly, so it has to fill it in itself.
    @BeforeEach
    void seedResourceServerUser() {
        resourceServerUserContext.setGskartResourceServerUser(
                new GSKartResourceServerUser(jwtFor("integration-test", "integration-test@gskart.local", List.of()), List.of()));
    }

    // This test sets the ThreadLocal directly instead of going through the filter, so it has to
    // clear it too - the same cleanup the filter would normally do.
    @AfterEach
    void clearResourceServerUser() {
        resourceServerUserContext.clear();
    }

    private Jwt jwtFor(String username, String email, List<String> roles) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim(ClaimNames.SUB, username)
                .claim(ClaimNames.EMAIL, email)
                .claim(ClaimNames.ROLES, roles)
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

    // Standalone MockMvc (used by ProductsControllerTest) skips security filters entirely, so it
    // can't prove @PreAuthorize actually blocks anyone - only a real Spring context can. This
    // builds MockMvc with the real security filter chain wired in via springSecurity().
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

    // jwt() above hands authorities straight in, skipping the decoder and JwtAuthenticationConverter
    // entirely - so it can't prove setAuthoritiesClaimName("roles") actually works. This test sends
    // a real bearer token through the full chain instead, to prove the "roles" claim is what grants it.
    @Test
    void rolesClaimIsMappedToAnAuthorityByTheRealConverter() throws Exception {
        when(jwtDecoder.decode("developer-token"))
                .thenReturn(jwtFor("authz-test-user", "authz-test-user@gskart.local", List.of("Developer")));

        securedMockMvc().perform(post("/api/v1/products/index-jobs")
                        .header("Authorization", "Bearer developer-token"))
                .andExpect(status().isAccepted());
    }

    // Same gap as the index-jobs test above: standalone MockMvc can't prove @PreAuthorize on
    // POST /category/{categoryId} is enforced, so this uses the same real-security-chain fix.
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
