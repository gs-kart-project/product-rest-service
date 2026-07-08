package com.gskart.product.controllers;

import com.gskart.product.entities.Product;
import com.gskart.product.exceptionHandlers.GlobalExceptionHandler;
import com.gskart.product.exceptions.ProductAddFailedException;
import com.gskart.product.exceptions.ProductNotFoundException;
import com.gskart.product.mappers.ProductMapper;
import com.gskart.product.services.IProductService;
import com.gskart.product.services.ISearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link ProductsController} covering the search, update and delete paths.
 * Uses standalone MockMvc (no Spring context / no security filters) so the tests stay fast and
 * focused on request mapping, bean validation, pagination shape and error mapping. The real
 * {@link ProductMapper} and {@link GlobalExceptionHandler} are wired in; the service layer is mocked.
 */
class ProductsControllerTest {

    private IProductService productService;
    private ISearchService searchService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        productService = mock(IProductService.class);
        searchService = mock(ISearchService.class);
        ProductsController controller =
                new ProductsController(productService, new ProductMapper(), searchService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private Product product(Long id, String name, String price) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setDescription("desc");
        product.setImageUrl("http://img/" + id);
        product.setPrice(new BigDecimal(price));
        return product;
    }

    // ---- search ----

    @Test
    void searchReturns200WithPaginationMetadata() throws Exception {
        Page<Product> page = new PageImpl<>(
                List.of(product(1L, "Laptop", "999.99")), PageRequest.of(0, 10), 1);
        when(searchService.searchProducts(anyString(), anyInt(), anyInt(), anyMap())).thenReturn(page);

        mockMvc.perform(get("/api/v1/products/search").param("query", "lap"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products", hasSize(1)))
                .andExpect(jsonPath("$.products[0].name", is("Laptop")))
                .andExpect(jsonPath("$.currentPage").value(0))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void searchWithNoResultsReturns200WithEmptyList() throws Exception {
        Page<Product> empty = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(searchService.searchProducts(anyString(), anyInt(), anyInt(), anyMap())).thenReturn(empty);

        mockMvc.perform(get("/api/v1/products/search").param("query", "nomatch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products", hasSize(0)))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void searchWithDisallowedSortFieldReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/products/search")
                        .param("query", "lap")
                        .param("sort", "hackerField:asc"))
                .andExpect(status().isBadRequest());
    }

    // ---- delete (soft delete) ----

    @Test
    void deleteReturns200AndInvokesSoftDelete() throws Exception {
        when(productService.delete(5L)).thenReturn(true);

        mockMvc.perform(delete("/api/v1/products/5"))
                .andExpect(status().isOk())
                .andExpect(content().string("Product 5 has been deleted successfully."));

        verify(productService).delete(5L);
    }

    @Test
    void deleteOfMissingProductReturns404() throws Exception {
        when(productService.delete(99L)).thenThrow(new ProductNotFoundException("Product with ID 99 not found"));

        mockMvc.perform(delete("/api/v1/products/99"))
                .andExpect(status().isNotFound());
    }

    // ---- update ----

    @Test
    void updateReturns200WithUpdatedProduct() throws Exception {
        when(productService.update(eq(7L), any(Product.class)))
                .thenReturn(product(7L, "Updated name", "12.50"));

        mockMvc.perform(put("/api/v1/products/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated name\",\"price\":12.50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name", is("Updated name")));
    }

    @Test
    void updateWithBlankNameReturns400() throws Exception {
        mockMvc.perform(put("/api/v1/products/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"price\":12.50}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateOfMissingProductReturns404() throws Exception {
        when(productService.update(eq(99L), any(Product.class)))
                .thenThrow(new ProductNotFoundException("Product with ID 99 not found"));

        mockMvc.perform(put("/api/v1/products/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Valid\",\"price\":12.50}"))
                .andExpect(status().isNotFound());
    }

    // ---- getAll ----

    @Test
    void getAllReturns200WithProducts() throws Exception {
        when(productService.getAll()).thenReturn(List.of(product(1L, "Laptop", "999.99")));

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Laptop")));
    }

    @Test
    void getAllReturns204WhenEmpty() throws Exception {
        when(productService.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isNoContent());
    }

    // ---- getById ----

    @Test
    void getByIdReturns200WhenFound() throws Exception {
        when(productService.getById(1L)).thenReturn(product(1L, "Laptop", "999.99"));

        mockMvc.perform(get("/api/v1/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getByIdReturns204WhenMissing() throws Exception {
        when(productService.getById(1L)).thenReturn(null);

        mockMvc.perform(get("/api/v1/products/1"))
                .andExpect(status().isNoContent());
    }

    // ---- getProductsByCategory ----

    @Test
    void getByCategoryReturns200WithProducts() throws Exception {
        when(productService.getByCategory(3L)).thenReturn(List.of(product(1L, "Laptop", "999.99")));

        mockMvc.perform(get("/api/v1/products/category/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void getByCategoryReturns204WhenEmpty() throws Exception {
        when(productService.getByCategory(3L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/products/category/3"))
                .andExpect(status().isNoContent());
    }

    // ---- addNew ----

    @Test
    void addNewReturns201OnSuccess() throws Exception {
        when(productService.addNew(any(Product.class), eq(3L)))
                .thenReturn(product(1L, "Laptop", "999.99"));

        mockMvc.perform(post("/api/v1/products/category/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Laptop\",\"price\":999.99}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void addNewReturns400WhenServiceYieldsNoProduct() throws Exception {
        when(productService.addNew(any(Product.class), eq(3L))).thenReturn(null);

        mockMvc.perform(post("/api/v1/products/category/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Laptop\",\"price\":999.99}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addNewReturns400OnProductAddFailure() throws Exception {
        when(productService.addNew(any(Product.class), eq(3L)))
                .thenThrow(new ProductAddFailedException("nope"));

        mockMvc.perform(post("/api/v1/products/category/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Laptop\",\"price\":999.99}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addNewReturns400OnInvalidBody() throws Exception {
        mockMvc.perform(post("/api/v1/products/category/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"price\":999.99}"))
                .andExpect(status().isBadRequest());
    }

    // ---- edge branches ----

    @Test
    void getAllReturns204WhenServiceReturnsNull() throws Exception {
        when(productService.getAll()).thenReturn(null);

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isNoContent());
    }

    @Test
    void getByCategoryReturns204WhenServiceReturnsNull() throws Exception {
        when(productService.getByCategory(3L)).thenReturn(null);

        mockMvc.perform(get("/api/v1/products/category/3"))
                .andExpect(status().isNoContent());
    }

    @Test
    void searchWithEmptySortUsesDefaultAndReturns200() throws Exception {
        when(searchService.searchProducts(anyString(), anyInt(), anyInt(), anyMap()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        mockMvc.perform(get("/api/v1/products/search").param("query", "x").param("sort", ""))
                .andExpect(status().isOk());
    }
}
