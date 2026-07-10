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
import com.gskart.product.security.models.GSKartResourceServerUserContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class CategoryService implements ICategoryService{

    // Batch size for the category-delete cascade (M2 fix) - keeps each transaction/row-set bounded
    // instead of loading every product in the category into memory and one giant transaction.
    private static final int CASCADE_BATCH_SIZE = 200;

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ProductOutboxEventFactory productOutboxEventFactory;
    private final ApplicationEventPublisher applicationEventPublisher;

    private final GSKartResourceServerUserContext resourceServerUserContext;
    private final TransactionTemplate requiresNewTransactionTemplate;

    public CategoryService(CategoryRepository categoryRepository,
                            ProductRepository productRepository,
                            OutboxEventRepository outboxEventRepository,
                            ProductOutboxEventFactory productOutboxEventFactory,
                            ApplicationEventPublisher applicationEventPublisher,
                            GSKartResourceServerUserContext resourceServerUserContext,
                            PlatformTransactionManager transactionManager) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.productOutboxEventFactory = productOutboxEventFactory;
        this.applicationEventPublisher = applicationEventPublisher;
        this.resourceServerUserContext = resourceServerUserContext;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public Category getById(Long id) {
        var optionalCategory = categoryRepository.findById(id);
        return optionalCategory.orElse(null);
    }

    @Override
    public List<Category> getAll() {
        return (List<Category>) categoryRepository.findAll();
    }

    @Override
    public Category save(Category category) {
        category.setCreatedOn(OffsetDateTime.now(ZoneOffset.UTC));
        category.setCreatedBy(resourceServerUserContext.getGskartResourceServerUser().getUsername());
        category.setStatus(Category.Status.ACTIVE);
        return categoryRepository.save(category);
    }

    @Override
    public Category update(Long id, Category category) throws CategoryNotFoundException {
        Optional<Category> existingCategoryOptional = categoryRepository.findById(id);

        if(existingCategoryOptional.isEmpty()){
            throw new CategoryNotFoundException(String.format("Category with id: %d does not exist", id));
        }

        Category existingCategory = existingCategoryOptional.get();
        existingCategory.setDescription(category.getDescription());
        existingCategory.setName(category.getName());
        existingCategory.setImageUrl(category.getImageUrl());
        existingCategory.setModifiedOn(OffsetDateTime.now(ZoneOffset.UTC));
        existingCategory.setModifiedBy(resourceServerUserContext.getGskartResourceServerUser().getUsername());
        existingCategory.setStatus(category.getStatus());

        return categoryRepository.save(existingCategory);
    }

    // Not @Transactional: the category flip and each cascade batch below are deliberately separate,
    // independently-committed transactions (see cascadeDeleteProducts) so a large category never
    // holds one giant transaction. Committing the category status first (durably) before cascading
    // means a failure mid-cascade never leaves the category rolled back to ACTIVE while some of its
    // products are already DELETED - the worst case is an incomplete cascade, which a repeat
    // delete(id) call safely resumes (already-DELETED products are excluded by @SQLRestriction).
    @Override
    public boolean delete(Long id) throws CategoryNotFoundException {
        Optional<Category> optionalCategory = categoryRepository.findById(id);
        if(optionalCategory.isEmpty()){
            throw new CategoryNotFoundException(String.format("Category with id: %d does not exist", id));
        }
        Category category = optionalCategory.get();
        category.setStatus(Category.Status.DELETED);
        category.setModifiedOn(OffsetDateTime.now(ZoneOffset.UTC));
        category.setModifiedBy(resourceServerUserContext.getGskartResourceServerUser().getUsername());
        categoryRepository.save(category);

        cascadeDeleteProducts(id, category);
        return true;
    }

    // Cascade: every still-active product under this category is soft-deleted too, each with its
    // own outbox event so the search index reflects the cascade (not just the DB). Paged in bounded
    // batches (M2 fix), each its own short REQUIRES_NEW transaction, rather than loading every
    // product in the category into memory and one giant transaction; each batch's outbox rows are
    // published as soon as that batch commits instead of waiting for the whole cascade to finish.
    // Deleted rows drop out of subsequent findAllByCategoryId calls (@SQLRestriction), so re-querying
    // page 0 each time always returns the next remaining chunk.
    private void cascadeDeleteProducts(Long categoryId, Category category) {
        Pageable firstPage = PageRequest.of(0, CASCADE_BATCH_SIZE, Sort.by("id"));
        Page<Product> batch;
        do {
            batch = requiresNewTransactionTemplate.execute(status -> cascadeDeleteBatch(categoryId, category, firstPage));
        } while (batch != null && !batch.isEmpty());
    }

    private Page<Product> cascadeDeleteBatch(Long categoryId, Category category, Pageable pageable) {
        Page<Product> activeProducts = productRepository.findAllByCategoryId(categoryId, pageable);
        List<Long> outboxEventIds = new ArrayList<>();
        for (Product product : activeProducts) {
            product.setStatus(Product.Status.DELETED);
            product.setModifiedOn(OffsetDateTime.now(ZoneOffset.UTC));
            product.setModifiedBy(resourceServerUserContext.getGskartResourceServerUser().getUsername());
            Product savedProduct = productRepository.save(product);

            OutboxEvent outboxEvent = productOutboxEventFactory.forProduct(savedProduct, category);
            outboxEventRepository.save(outboxEvent);
            outboxEventIds.add(outboxEvent.getId());
        }
        if (!outboxEventIds.isEmpty()) {
            applicationEventPublisher.publishEvent(new OutboxEventReadyEvent(outboxEventIds));
        }
        return activeProducts;
    }
}
