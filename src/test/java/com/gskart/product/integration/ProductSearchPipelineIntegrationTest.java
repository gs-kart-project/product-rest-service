package com.gskart.product.integration;

import com.gskart.product.DTOs.authService.ClaimsResponse;
import com.gskart.product.DTOs.authService.RoleDto;
import com.gskart.product.entities.Category;
import com.gskart.product.entities.Product;
import com.gskart.product.search.ProductSearchResult;
import com.gskart.product.security.models.GSKartResourceServerUser;
import com.gskart.product.security.models.GSKartResourceServerUserContext;
import com.gskart.product.security.services.AuthService;
import com.gskart.product.services.ICategoryService;
import com.gskart.product.services.ISearchService;
import com.gskart.product.services.IProductService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
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
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
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

    // Replaces the real AuthService (which would otherwise call out to auth-rest-service) so the
    // authz tests below can control the caller's role without a live auth service.
    @MockitoBean
    AuthService authService;

    // The service layer reads the current user off a ThreadLocal that's normally populated by
    // ResourceAuthorizationFilter on each HTTP request (for createdBy/modifiedBy auditing); this
    // test calls the service directly, so it has to seed that ThreadLocal itself.
    @BeforeEach
    void seedResourceServerUser() {
        ClaimsResponse claims = new ClaimsResponse();
        claims.setUsername("integration-test");
        claims.setEmail("integration-test@gskart.local");
        claims.setRoles(Set.of());
        resourceServerUserContext.setGskartResourceServerUser(new GSKartResourceServerUser(claims));
    }

    // Mirrors ResourceAuthorizationFilter's own finally-block cleanup (ADR-D14) - this test seeds
    // the ThreadLocal directly (bypassing the filter that would normally clear it), so it has to
    // clear it itself too (n11 fix).
    @AfterEach
    void clearResourceServerUser() {
        resourceServerUserContext.clear();
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
    // (springSecurityFilterChain) attached, and swaps in a mocked AuthService so the caller's role
    // is controllable without a live auth-rest-service.
    private MockMvc securedMockMvc() {
        Filter springSecurityFilterChain = webApplicationContext.getBean("springSecurityFilterChain", Filter.class);
        return MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    private ClaimsResponse claimsWithRole(String roleName) {
        RoleDto role = new RoleDto();
        role.setName(roleName);
        ClaimsResponse claims = new ClaimsResponse();
        claims.setUsername("authz-test-user");
        claims.setEmail("authz-test-user@gskart.local");
        claims.setRoles(Set.of(role));
        return claims;
    }

    @Test
    void indexJobIsForbiddenForCallerWithoutDeveloperOrAdminRole() throws Exception {
        when(authService.getUserClaims(anyString())).thenReturn(claimsWithRole("Customer"));

        securedMockMvc().perform(post("/api/v1/products/index-jobs").header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void indexJobSucceedsForCallerWithDeveloperRole() throws Exception {
        when(authService.getUserClaims(anyString())).thenReturn(claimsWithRole("Developer"));

        securedMockMvc().perform(post("/api/v1/products/index-jobs").header("Authorization", "Bearer test-token"))
                .andExpect(status().isAccepted());
    }
}
