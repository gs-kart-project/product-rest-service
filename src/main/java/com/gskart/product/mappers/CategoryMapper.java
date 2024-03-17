package com.gskart.product.mappers;

import com.gskart.product.DTOs.CategoryDto;
import com.gskart.product.entities.Category;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CategoryMapper {
    public CategoryDto entityToDto(Category category){
        if(category == null)
            return  null;

        CategoryDto categoryDto = new CategoryDto();
        categoryDto.setId(category.getId());
        categoryDto.setName(category.getName());
        categoryDto.setDescription(category.getDescription());
        categoryDto.setImageUrl(category.getImageUrl());

        return categoryDto;
    }

    public Category dtoToEntity(CategoryDto categoryDto){
        if (categoryDto == null)
            return null;

        Category category = new Category();
        category.setId(categoryDto.getId());
        category.setName(categoryDto.getName());
        category.setDescription(categoryDto.getDescription());
        category.setImageUrl(categoryDto.getImageUrl());

        return category;
    }

    public List<CategoryDto> entityListToDtoList(List<Category> categories){
        if(categories == null)
            return null;

        List<CategoryDto> categoryDtoList = new ArrayList<>(categories.size());
        for(Category category : categories){
            categoryDtoList.add(entityToDto(category));
        }
        return categoryDtoList;
    }
}
