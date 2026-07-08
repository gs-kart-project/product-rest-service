package com.gskart.product.services;

import com.gskart.product.entities.Category;
import com.gskart.product.exceptions.CategoryNotFoundException;
import com.gskart.product.respositories.CategoryRepository;
import com.gskart.product.security.models.GSKartResourceServerUser;
import com.gskart.product.security.models.GSKartResourceServerUserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoryServiceTest {

    private CategoryRepository categoryRepository;
    private GSKartResourceServerUserContext userContext;
    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryRepository.class);
        userContext = mock(GSKartResourceServerUserContext.class);
        categoryService = new CategoryService(categoryRepository, userContext);

        GSKartResourceServerUser user = mock(GSKartResourceServerUser.class);
        when(user.getUsername()).thenReturn("tester");
        when(userContext.getGskartResourceServerUser()).thenReturn(user);
    }

    private Category existing() {
        Category category = new Category();
        category.setId(1L);
        category.setName("Old");
        category.setDescription("old desc");
        category.setImageUrl("old-url");
        category.setStatus(Category.Status.ACTIVE);
        return category;
    }

    @Test
    void saveStampsCreatorAndActiveStatus() {
        Category incoming = new Category();
        incoming.setName("Electronics");
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        Category saved = categoryService.save(incoming);

        assertThat(saved.getStatus()).isEqualTo(Category.Status.ACTIVE);
        assertThat(saved.getCreatedBy()).isEqualTo("tester");
        assertThat(saved.getCreatedOn()).isNotNull();
    }

    @Test
    void updateAppliesFieldsAndStampsModifier() throws CategoryNotFoundException {
        Category existing = existing();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        Category incoming = new Category();
        incoming.setName("Updated");
        incoming.setDescription("new desc");
        incoming.setImageUrl("new-url");
        incoming.setStatus(Category.Status.INACTIVE);

        Category updated = categoryService.update(1L, incoming);

        assertThat(updated.getName()).isEqualTo("Updated");
        assertThat(updated.getDescription()).isEqualTo("new desc");
        assertThat(updated.getStatus()).isEqualTo(Category.Status.INACTIVE);
        assertThat(updated.getModifiedBy()).isEqualTo("tester");
        assertThat(updated.getModifiedOn()).isNotNull();
    }

    @Test
    void updateThrowsWhenMissing() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.update(99L, new Category()))
                .isInstanceOf(CategoryNotFoundException.class);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void deleteSoftDeletesBySettingDeletedStatus() throws CategoryNotFoundException {
        Category existing = existing();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));

        boolean result = categoryService.delete(1L);

        assertThat(result).isTrue();
        assertThat(existing.getStatus()).isEqualTo(Category.Status.DELETED);
        assertThat(existing.getModifiedBy()).isEqualTo("tester");
        verify(categoryRepository).save(existing);
    }

    @Test
    void deleteThrowsWhenMissing() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.delete(99L))
                .isInstanceOf(CategoryNotFoundException.class);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void getByIdReturnsNullWhenAbsent() {
        when(categoryRepository.findById(42L)).thenReturn(Optional.empty());
        assertThat(categoryService.getById(42L)).isNull();
    }

    @Test
    void getAllReturnsAllCategories() {
        List<Category> categories = List.of(existing());
        when(categoryRepository.findAll()).thenReturn(categories);
        assertThat(categoryService.getAll()).isEqualTo(categories);
    }
}
