package com.gskart.product.services;

import com.gskart.product.entities.Product;
import com.gskart.product.exceptions.ProductAddFailedException;
import com.gskart.product.respositories.ProductRepository;
import jdk.jshell.spi.ExecutionControl;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service("gskartProductService")
public class ProductService implements IProductService{

    private  final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public Product addNew(Product product, Long categoryId) throws ProductAddFailedException {
        product.setCreatedOn(OffsetDateTime.now(ZoneOffset.UTC));
        product.setStatus(Product.Status.ACTIVE);
        return productRepository.save(product);
    }

    @Override
    public Product update(Product product) {
        return productRepository.save(product);
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
    public List<Product> getByCategory(Long categoryId) throws ExecutionControl.NotImplementedException {
        //productRepository.findByCategory()
        // ToDo Learn about joining tables using Spring data JPA. Fetch products by given category id
        throw new ExecutionControl.NotImplementedException("Learn about joining tables using Spring data JPA. Fetch products by given category id");
    }
}
