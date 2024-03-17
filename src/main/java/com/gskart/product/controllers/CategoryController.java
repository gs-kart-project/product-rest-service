package com.gskart.product.controllers;

import com.gskart.product.DTOs.CategoryDto;
import com.gskart.product.entities.Category;
import com.gskart.product.exceptions.CategoryNotFoundException;
import com.gskart.product.mappers.CategoryMapper;
import com.gskart.product.services.ICategoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/categories")
public class CategoryController {

    private final ICategoryService categoryService;

    private final CategoryMapper categoryMapper;

    public CategoryController(ICategoryService categoryService, CategoryMapper categoryMapper) {
        this.categoryService = categoryService;
        this.categoryMapper = categoryMapper;
    }

    @GetMapping("")
    public ResponseEntity<List<CategoryDto>> getAll() {
        ResponseEntity<List<CategoryDto>> getAllResponse;
        var categoryList = categoryService.getAll();
        var categoryDtoList = categoryMapper.entityListToDtoList(categoryList);
        if (categoryDtoList == null || categoryDtoList.isEmpty()) {
            getAllResponse = new ResponseEntity<>(HttpStatusCode.valueOf(204));
            return getAllResponse;
        }
        getAllResponse = new ResponseEntity<>(categoryDtoList, HttpStatusCode.valueOf(200));
        return getAllResponse;
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryDto> getById(@PathVariable("id") Long id) {
        Category category = categoryService.getById(id);
        CategoryDto categoryDto = categoryMapper.entityToDto(category);
        ResponseEntity<CategoryDto> categoryDtoResponse;
        if (categoryDto == null) {
            categoryDtoResponse = new ResponseEntity<>(HttpStatusCode.valueOf(204));
            return categoryDtoResponse;
        }

        categoryDtoResponse = new ResponseEntity<>(categoryDto, HttpStatusCode.valueOf(200));
        return categoryDtoResponse;
    }

    @PostMapping("")
    public ResponseEntity<CategoryDto> addNew(@RequestBody CategoryDto categoryDto) {
        Category category = categoryMapper.dtoToEntity(categoryDto);
        Category newCategory = categoryService.save(category);
        CategoryDto newCategoryDto = categoryMapper.entityToDto(newCategory);
        if (newCategoryDto == null) {
            return new ResponseEntity<>(HttpStatusCode.valueOf(400));
        }
        return new ResponseEntity<>(newCategoryDto, HttpStatusCode.valueOf(201));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryDto> update(@RequestBody CategoryDto categoryDto, @PathVariable("id") Long id) throws CategoryNotFoundException {
        Category category = categoryMapper.dtoToEntity(categoryDto);
        Category newCategory = categoryService.update(id, category);
        CategoryDto newCategoryDto = categoryMapper.entityToDto(newCategory);
        if (newCategoryDto == null) {
            return new ResponseEntity<>(HttpStatusCode.valueOf(400));
        }
        return new ResponseEntity<>(newCategoryDto, HttpStatusCode.valueOf(201));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable("id") Long id) throws CategoryNotFoundException {
        categoryService.delete(id);
        return  new ResponseEntity<>(String.format("Category %d has been deleted successfully.", id), HttpStatus.OK);
    }

    @ExceptionHandler(value = CategoryNotFoundException.class)
    public ResponseEntity<String> categoryNotFoundExceptionHandler(CategoryNotFoundException categoryNotFoundException){
        return new ResponseEntity<>(categoryNotFoundException.getMessage(), HttpStatus.NOT_FOUND);
    }
}
