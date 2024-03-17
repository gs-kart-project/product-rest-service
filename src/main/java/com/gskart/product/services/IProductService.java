package com.gskart.product.services;

import com.gskart.product.entities.Product;
import com.gskart.product.exceptions.ProductAddFailedException;
import jdk.jshell.spi.ExecutionControl;

import java.util.List;

public interface IProductService {
    Product addNew(Product product, Long categoryId) throws ProductAddFailedException;
    Product update(Product product);
    List<Product> getAll();
    Product getById(Long id);
    List<Product> getByCategory(Long categoryId) throws ExecutionControl.NotImplementedException;
}
