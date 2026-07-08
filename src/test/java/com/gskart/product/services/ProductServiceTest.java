package com.gskart.product.services;

import com.gskart.product.entities.Category;
import com.gskart.product.entities.Product;
import com.gskart.product.exceptions.ProductAddFailedException;
import com.gskart.product.exceptions.ProductNotFoundException;
import com.gskart.product.respositories.ProductRepository;
import com.gskart.product.security.models.GSKartResourceServerUser;
import com.gskart.product.security.models.GSKartResourceServerUserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductServiceTest {

    private ProductRepository productRepository;
    private GSKartResourceServerUserContext userContext;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        userContext = mock(GSKartResourceServerUserContext.class);
        productService = new ProductService(productRepository, userContext);

        GSKartResourceServerUser user = mock(GSKartResourceServerUser.class);
        when(user.getUsername()).thenReturn("tester");
        when(userContext.getGskartResourceServerUser()).thenReturn(user);
    }

    private Product existing() {
        Category category = new Category();
        category.setId(3L);
        Product product = new Product();
        product.setId(1L);
        product.setName("Old name");
        product.setDescription("old desc");
        product.setImageUrl("old-url");
        product.setPrice(new BigDecimal("10.00"));
        product.setStatus(Product.Status.ACTIVE);
        product.setCategory(category);
        product.setCreatedBy("creator");
        product.setCreatedOn(OffsetDateTime.now().minusDays(1));
        return product;
    }

    @Test
    void addNewStampsAuditFieldsAndActiveStatus() throws ProductAddFailedException {
        Product incoming = new Product();
        incoming.setName("New");
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product saved = productService.addNew(incoming, 3L);

        assertThat(saved.getStatus()).isEqualTo(Product.Status.ACTIVE);
        assertThat(saved.getCreatedOn()).isNotNull();
        verify(productRepository).save(incoming);
    }

    @Test
    void updateCopiesEditableFieldsAndPreservesCategoryStatusAndProvenance() throws ProductNotFoundException {
        Product existing = existing();
        Category originalCategory = existing.getCategory();
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product incoming = new Product();
        incoming.setName("New name");
        incoming.setDescription("new desc");
        incoming.setImageUrl("new-url");
        incoming.setPrice(new BigDecimal("25.50"));

        Product updated = productService.update(1L, incoming);

        // editable fields applied
        assertThat(updated.getName()).isEqualTo("New name");
        assertThat(updated.getDescription()).isEqualTo("new desc");
        assertThat(updated.getPrice()).isEqualByComparingTo("25.50");
        // preserved from the persisted row
        assertThat(updated.getCategory()).isSameAs(originalCategory);
        assertThat(updated.getStatus()).isEqualTo(Product.Status.ACTIVE);
        assertThat(updated.getCreatedBy()).isEqualTo("creator");
        // audit stamped
        assertThat(updated.getModifiedBy()).isEqualTo("tester");
        assertThat(updated.getModifiedOn()).isNotNull();
        verify(productRepository).save(existing);
    }

    @Test
    void updateThrowsWhenProductMissing() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.update(99L, new Product()))
                .isInstanceOf(ProductNotFoundException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void deleteSoftDeletesBySettingDeletedStatus() throws ProductNotFoundException {
        Product existing = existing();
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean result = productService.delete(1L);

        assertThat(result).isTrue();
        assertThat(existing.getStatus()).isEqualTo(Product.Status.DELETED);
        assertThat(existing.getModifiedBy()).isEqualTo("tester");
        verify(productRepository).save(existing);
    }

    @Test
    void deleteThrowsWhenProductMissing() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.delete(99L))
                .isInstanceOf(ProductNotFoundException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void getByIdReturnsNullWhenAbsent() {
        when(productRepository.findById(42L)).thenReturn(Optional.empty());
        assertThat(productService.getById(42L)).isNull();
    }

    @Test
    void getByIdReturnsProductWhenPresent() {
        Product existing = existing();
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        assertThat(productService.getById(1L)).isSameAs(existing);
    }

    @Test
    void getByCategoryDelegatesToRepository() {
        List<Product> products = List.of(existing());
        when(productRepository.findAllByCategoryId(3L)).thenReturn(products);
        assertThat(productService.getByCategory(3L)).isEqualTo(products);
    }

    @Test
    void getAllReturnsAllProducts() {
        List<Product> products = List.of(existing());
        when(productRepository.findAll()).thenReturn(products);
        assertThat(productService.getAll()).isEqualTo(products);
    }
}
