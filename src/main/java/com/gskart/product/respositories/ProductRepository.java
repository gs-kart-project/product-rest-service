package com.gskart.product.respositories;

import com.gskart.product.entities.Category;
import com.gskart.product.entities.Product;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface ProductRepository extends CrudRepository<Product, Long> {
    Optional<Product> findProductByCategory(Category category);
}
