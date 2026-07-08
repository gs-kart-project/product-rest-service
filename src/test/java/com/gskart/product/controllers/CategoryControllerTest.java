package com.gskart.product.controllers;

import com.gskart.product.entities.Category;
import com.gskart.product.exceptionHandlers.GlobalExceptionHandler;
import com.gskart.product.exceptions.CategoryNotFoundException;
import com.gskart.product.mappers.CategoryMapper;
import com.gskart.product.services.ICategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategoryControllerTest {

    private ICategoryService categoryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        categoryService = mock(ICategoryService.class);
        CategoryController controller = new CategoryController(categoryService, new CategoryMapper());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private Category category(Long id, String name) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setDescription("desc");
        category.setImageUrl("http://img/" + id);
        return category;
    }

    @Test
    void getAllReturns200WithCategories() throws Exception {
        when(categoryService.getAll()).thenReturn(List.of(category(1L, "Electronics")));

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Electronics")));
    }

    @Test
    void getAllReturns204WhenEmpty() throws Exception {
        when(categoryService.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isNoContent());
    }

    @Test
    void getByIdReturns200WhenFound() throws Exception {
        when(categoryService.getById(1L)).thenReturn(category(1L, "Electronics"));

        mockMvc.perform(get("/api/v1/categories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getByIdReturns204WhenMissing() throws Exception {
        when(categoryService.getById(1L)).thenReturn(null);

        mockMvc.perform(get("/api/v1/categories/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void addNewReturns201OnSuccess() throws Exception {
        when(categoryService.save(any(Category.class))).thenReturn(category(1L, "Electronics"));

        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Electronics\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void addNewReturns400OnBlankName() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateReturnsSuccessBody() throws Exception {
        when(categoryService.update(eq(1L), any(Category.class))).thenReturn(category(1L, "Updated"));

        mockMvc.perform(put("/api/v1/categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Updated")));
    }

    @Test
    void updateOfMissingCategoryReturns404() throws Exception {
        when(categoryService.update(eq(99L), any(Category.class)))
                .thenThrow(new CategoryNotFoundException("Category with id: 99 does not exist"));

        mockMvc.perform(put("/api/v1/categories/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns200() throws Exception {
        when(categoryService.delete(1L)).thenReturn(true);

        mockMvc.perform(delete("/api/v1/categories/1"))
                .andExpect(status().isOk());

        verify(categoryService).delete(1L);
    }

    @Test
    void deleteOfMissingCategoryReturns404() throws Exception {
        when(categoryService.delete(99L))
                .thenThrow(new CategoryNotFoundException("Category with id: 99 does not exist"));

        mockMvc.perform(delete("/api/v1/categories/99"))
                .andExpect(status().isNotFound());
    }

    // ---- edge branches ----

    @Test
    void getAllReturns204WhenServiceReturnsNull() throws Exception {
        when(categoryService.getAll()).thenReturn(null);

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isNoContent());
    }

    @Test
    void addNewReturns400WhenServiceYieldsNoCategory() throws Exception {
        when(categoryService.save(any(Category.class))).thenReturn(null);

        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Electronics\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateReturns400WhenMappedResultIsNull() throws Exception {
        when(categoryService.update(eq(1L), any(Category.class))).thenReturn(null);

        mockMvc.perform(put("/api/v1/categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated\"}"))
                .andExpect(status().isBadRequest());
    }
}
