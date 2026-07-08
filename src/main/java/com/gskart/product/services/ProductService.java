package com.gskart.product.services;

import com.gskart.product.entities.Product;
import com.gskart.product.exceptions.ProductAddFailedException;
import com.gskart.product.exceptions.ProductNotFoundException;
import com.gskart.product.respositories.ProductRepository;
import com.gskart.product.security.models.GSKartResourceServerUserContext;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service("gskartProductService")
public class ProductService implements IProductService{

    private final ProductRepository productRepository;

    private final GSKartResourceServerUserContext resourceServerUserContext;

    public ProductService(ProductRepository productRepository,
                          GSKartResourceServerUserContext resourceServerUserContext) {
        this.productRepository = productRepository;
        this.resourceServerUserContext = resourceServerUserContext;
    }

    @Override
    public Product addNew(Product product, Long categoryId) throws ProductAddFailedException {
        product.setCreatedOn(OffsetDateTime.now(ZoneOffset.UTC));
        product.setStatus(Product.Status.ACTIVE);
        return productRepository.save(product);
    }

    @Override
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
        return productRepository.save(existingProduct);
    }

    @Override
    public boolean delete(Long id) throws ProductNotFoundException {
        Product existingProduct = productRepository.findById(id).orElse(null);
        if (existingProduct == null) {
            throw new ProductNotFoundException(String.format("Product with ID %d not found", id));
        }

        // Soft delete: mark DELETED so @SQLRestriction hides it from all subsequent reads.
        existingProduct.setStatus(Product.Status.DELETED);
        existingProduct.setModifiedOn(OffsetDateTime.now(ZoneOffset.UTC));
        existingProduct.setModifiedBy(resourceServerUserContext.getGskartResourceServerUser().getUsername());
        productRepository.save(existingProduct);
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
}
