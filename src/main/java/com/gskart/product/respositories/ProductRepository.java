package com.gskart.product.respositories;

import com.gskart.product.entities.Category;
import com.gskart.product.entities.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends CrudRepository<Product, Long>, PagingAndSortingRepository<Product, Long> {
    Optional<Product> findProductByCategory(Category category);

    // Get all products by category ID
    List<Product> findAllByCategoryId(Long categoryId);

    // Bounded/paged variant for batch cascade operations (see CategoryService) that must not load
    // an entire category's products into memory / one transaction at once.
    Page<Product> findAllByCategoryId(Long categoryId, Pageable pageable);

    // Eagerly fetches category so batch backfill (ProductService.reindexAll) doesn't N+1 lazy-load
    // it per row.
    @Override
    @EntityGraph(attributePaths = "category")
    Page<Product> findAll(Pageable pageable);
}
