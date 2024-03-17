package com.gskart.product.services;

import com.gskart.product.entities.Category;
import com.gskart.product.exceptions.CategoryNotFoundException;

import java.util.List;

public interface ICategoryService {
    Category getById(Long id);

    List<Category> getAll();

    Category save(Category category);

    Category update(Long id, Category category) throws CategoryNotFoundException;

    boolean delete(Long id) throws CategoryNotFoundException;

}
