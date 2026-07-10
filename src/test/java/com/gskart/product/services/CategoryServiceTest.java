package com.gskart.product.services;

import com.gskart.product.entities.Category;
import com.gskart.product.entities.Product;
import com.gskart.product.exceptions.CategoryNotFoundException;
import com.gskart.product.outbox.OutboxEvent;
import com.gskart.product.outbox.OutboxEventReadyEvent;
import com.gskart.product.outbox.OutboxEventRepository;
import com.gskart.product.outbox.ProductOutboxEventFactory;
import com.gskart.product.respositories.CategoryRepository;
import com.gskart.product.respositories.ProductRepository;
import com.gskart.product.security.models.GSKartResourceServerUser;
import com.gskart.product.security.models.GSKartResourceServerUserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoryServiceTest {

    private static final Pageable FIRST_PAGE = PageRequest.of(0, 200, Sort.by("id"));

    private CategoryRepository categoryRepository;
    private ProductRepository productRepository;
    private OutboxEventRepository outboxEventRepository;
    private ProductOutboxEventFactory productOutboxEventFactory;
    private ApplicationEventPublisher applicationEventPublisher;
    private GSKartResourceServerUserContext userContext;
    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryRepository.class);
        productRepository = mock(ProductRepository.class);
        outboxEventRepository = mock(OutboxEventRepository.class);
        productOutboxEventFactory = mock(ProductOutboxEventFactory.class);
        applicationEventPublisher = mock(ApplicationEventPublisher.class);
        userContext = mock(GSKartResourceServerUserContext.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        categoryService = new CategoryService(categoryRepository, productRepository, outboxEventRepository,
                productOutboxEventFactory, applicationEventPublisher, userContext, transactionManager);

        GSKartResourceServerUser user = mock(GSKartResourceServerUser.class);
        when(user.getUsername()).thenReturn("tester");
        when(userContext.getGskartResourceServerUser()).thenReturn(user);

        when(productRepository.findAllByCategoryId(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
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

    private Product product(Long id) {
        Product product = new Product();
        product.setId(id);
        product.setName("Product " + id);
        product.setStatus(Product.Status.ACTIVE);
        return product;
    }

    private OutboxEvent outboxEventWithId(Long id) {
        OutboxEvent event = new OutboxEvent();
        event.setId(id);
        return event;
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
    void deleteWithNoProductsDoesNotPublishOutboxEvent() throws CategoryNotFoundException {
        Category existing = existing();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));

        categoryService.delete(1L);

        verify(outboxEventRepository, never()).save(any());
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    void deleteCascadesToActiveProductsInCategory() throws CategoryNotFoundException {
        Category existing = existing();
        Product product1 = product(10L);
        Product product2 = product(20L);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        // First page-0 query returns the batch; the loop re-queries page 0 again afterwards (the
        // now-DELETED rows would drop out via @SQLRestriction against a real database) and must get
        // nothing back or the do-while loop never terminates - consecutive thenReturn() values
        // simulate that shrinking result set since a plain mock doesn't apply @SQLRestriction itself.
        when(productRepository.findAllByCategoryId(1L, FIRST_PAGE))
                .thenReturn(new PageImpl<>(List.of(product1, product2)), new PageImpl<>(List.of()));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(productOutboxEventFactory.forProduct(any(Product.class), any(Category.class)))
                .thenAnswer(inv -> outboxEventWithId(((Product) inv.getArgument(0)).getId()));

        categoryService.delete(1L);

        assertThat(product1.getStatus()).isEqualTo(Product.Status.DELETED);
        assertThat(product2.getStatus()).isEqualTo(Product.Status.DELETED);
        assertThat(product1.getModifiedBy()).isEqualTo("tester");
        verify(productRepository).save(product1);
        verify(productRepository).save(product2);
        verify(productOutboxEventFactory).forProduct(product1, existing);
        verify(productOutboxEventFactory).forProduct(product2, existing);
        verify(outboxEventRepository, times(2)).save(any(OutboxEvent.class));

        ArgumentCaptor<OutboxEventReadyEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEventReadyEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getOutboxEventIds()).containsExactlyInAnyOrder(10L, 20L);
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
