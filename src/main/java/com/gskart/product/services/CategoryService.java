package com.gskart.product.services;

import com.gskart.product.entities.Category;
import com.gskart.product.exceptions.CategoryNotFoundException;
import com.gskart.product.respositories.CategoryRepository;
import com.gskart.product.security.models.GSKartResourceServerUserContext;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Service
public class CategoryService implements ICategoryService{
    private final CategoryRepository categoryRepository;

    private final GSKartResourceServerUserContext resourceServerUserContext;

    public CategoryService(CategoryRepository categoryRepository, GSKartResourceServerUserContext resourceServerUserContext) {
        this.categoryRepository = categoryRepository;
        this.resourceServerUserContext = resourceServerUserContext;
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
        return true;
    }
}
