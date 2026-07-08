package com.gskart.product.services;

import com.gskart.product.entities.Product;
import com.gskart.product.exceptions.ProductAddFailedException;
import com.gskart.product.exceptions.ProductNotFoundException;

import java.util.List;

public interface IProductService {
    Product addNew(Product product, Long categoryId) throws ProductAddFailedException;
    Product update(Long id, Product product) throws ProductNotFoundException;
    boolean delete(Long id) throws ProductNotFoundException;
    List<Product> getAll();
    Product getById(Long id);
    List<Product> getByCategory(Long categoryId);
}
