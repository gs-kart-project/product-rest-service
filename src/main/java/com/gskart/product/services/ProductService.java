package com.gskart.product.services;

import com.gskart.product.entities.Category;
import com.gskart.product.entities.Product;
import com.gskart.product.exceptions.ProductAddFailedException;
import com.gskart.product.exceptions.ProductNotFoundException;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service("gskartProductService")
public class ProductService implements IProductService{

    // Batch size for reindexAll's paged backfill (M2 fix) - keeps each transaction/row-set bounded
    // instead of loading the whole catalog into memory and one giant transaction.
    private static final int REINDEX_BATCH_SIZE = 200;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ProductOutboxEventFactory productOutboxEventFactory;
    private final ApplicationEventPublisher applicationEventPublisher;

    private final GSKartResourceServerUserContext resourceServerUserContext;
    private final TransactionTemplate requiresNewTransactionTemplate;

    public ProductService(ProductRepository productRepository,
                          CategoryRepository categoryRepository,
                          OutboxEventRepository outboxEventRepository,
                          ProductOutboxEventFactory productOutboxEventFactory,
                          ApplicationEventPublisher applicationEventPublisher,
                          GSKartResourceServerUserContext resourceServerUserContext,
                          PlatformTransactionManager transactionManager) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.productOutboxEventFactory = productOutboxEventFactory;
        this.applicationEventPublisher = applicationEventPublisher;
        this.resourceServerUserContext = resourceServerUserContext;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional
    public Product addNew(Product product, Long categoryId) throws ProductAddFailedException {
        Category category = categoryRepository.findById(categoryId).orElse(null);
        if (category == null) {
            throw new ProductAddFailedException(String.format("Category with ID %d not found", categoryId));
        }

        product.setCategory(category);
        product.setCreatedOn(OffsetDateTime.now(ZoneOffset.UTC));
        product.setStatus(Product.Status.ACTIVE);
        Product savedProduct = productRepository.save(product);
        enqueueOutboxEvent(savedProduct, category);
        return savedProduct;
    }

    @Override
    @Transactional
    public Product update(Long id, Product product) throws ProductNotFoundException {
        Product existingProduct = productRepository.findById(id).orElse(null);
        if (existingProduct == null) {
            throw new ProductNotFoundException(String.format("Product with ID %d not found", id));
        }

        // Copy only the client-editable fields; category, status and audit provenance
        // (createdOn/createdBy) are preserved from the persisted row.
        existingProduct.setName(product.getName());
        existingProduct.setDescription(product.getDescription());
        existingProduct.setPrice(product.getPrice());
        existingProduct.setImageUrl(product.getImageUrl());
        existingProduct.setModifiedOn(OffsetDateTime.now(ZoneOffset.UTC));
        existingProduct.setModifiedBy(resourceServerUserContext.getGskartResourceServerUser().getUsername());
        Product savedProduct = productRepository.save(existingProduct);
        enqueueOutboxEvent(savedProduct, savedProduct.getCategory());
        return savedProduct;
    }

    @Override
    @Transactional
    public boolean delete(Long id) throws ProductNotFoundException {
        Product existingProduct = productRepository.findById(id).orElse(null);
        if (existingProduct == null) {
            throw new ProductNotFoundException(String.format("Product with ID %d not found", id));
        }

        // Soft delete: mark DELETED so @SQLRestriction hides it from all subsequent reads. The
        // outbox event carries the same DELETED status, so the indexer soft-deletes the ES doc too.
        existingProduct.setStatus(Product.Status.DELETED);
        existingProduct.setModifiedOn(OffsetDateTime.now(ZoneOffset.UTC));
        existingProduct.setModifiedBy(resourceServerUserContext.getGskartResourceServerUser().getUsername());
        Product savedProduct = productRepository.save(existingProduct);
        enqueueOutboxEvent(savedProduct, savedProduct.getCategory());
        return true;
    }

    @Override
    public List<Product> getAll() {
        return (List<Product>) productRepository.findAll();
    }

    @Override
    public Product getById(Long id) {
        var optionalProduct = productRepository.findById(id);
        return optionalProduct.orElse(null);
    }

    @Override
    public List<Product> getByCategory(Long categoryId) {
        return productRepository.findAllByCategoryId(categoryId);
    }

    @Override
    public int reindexAll() {
        // Bulk backfill: just enqueue PENDING rows and let the scheduled relay drain them at its
        // normal cadence, rather than firing hundreds of immediate-publish tasks at once. Paged in
        // bounded batches (M2 fix) - each batch is its own short REQUIRES_NEW transaction, rather
        // than loading the whole catalog into memory and holding one giant transaction open.
        int totalEnqueued = 0;
        int pageNumber = 0;
        Page<Product> page;
        do {
            Pageable pageable = PageRequest.of(pageNumber, REINDEX_BATCH_SIZE, Sort.by("id"));
            page = requiresNewTransactionTemplate.execute(status -> enqueueReindexBatch(pageable));
            totalEnqueued += page.getNumberOfElements();
            pageNumber++;
        } while (page.hasNext());
        return totalEnqueued;
    }

    private Page<Product> enqueueReindexBatch(Pageable pageable) {
        Page<Product> products = productRepository.findAll(pageable);
        for (Product product : products) {
            outboxEventRepository.save(productOutboxEventFactory.forProduct(product, product.getCategory()));
        }
        return products;
    }

    private void enqueueOutboxEvent(Product product, Category category) {
        OutboxEvent outboxEvent = productOutboxEventFactory.forProduct(product, category);
        outboxEventRepository.save(outboxEvent);
        applicationEventPublisher.publishEvent(new OutboxEventReadyEvent(List.of(outboxEvent.getId())));
    }
}
