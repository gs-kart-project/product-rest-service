package com.gskart.product.controllers;

import com.gskart.product.DTOs.ProductDto;
import com.gskart.product.entities.Product;
import com.gskart.product.exceptions.ProductAddFailedException;
import com.gskart.product.exceptions.ProductNotFoundException;
import com.gskart.product.mappers.ProductMapper;
import com.gskart.product.services.IProductService;
import com.gskart.product.services.ISearchService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/products")
public class ProductsController {

    private final IProductService productService;
    private final ProductMapper productMapper;
    private final ISearchService searchService;

    public ProductsController(@Qualifier("gskartProductService") IProductService productService,
                             ProductMapper productMapper,
                             ISearchService searchService) {
        this.productService = productService;
        this.productMapper = productMapper;
        this.searchService = searchService;
    }

    /**
     * Gets all products. Default route to /products
     * @return List of Product response
     */
    @GetMapping("")
    public ResponseEntity<List<ProductDto>> getAll(){
        ResponseEntity<List<ProductDto>> allProductsResponse;
        List<Product> products = this.productService.getAll();

        List<ProductDto> productDtoList = productMapper.entityListToDtoList(products);

        if(products != null && !products.isEmpty()) {
            allProductsResponse = new ResponseEntity<>(productDtoList, HttpStatusCode.valueOf(200));
        }
        else {
            allProductsResponse = new ResponseEntity<>(HttpStatusCode.valueOf(204));
        }

        return allProductsResponse;
    }


    /**
     * Gets product by ID
     * @param id Product's id
     * @return 200 with product. 204 if product is not available.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductDto> getProductById(@PathVariable("id") long id){
        Product product = this.productService.getById(id);
        ProductDto productResponse = productMapper.entityToDto(product);
                ResponseEntity<ProductDto> productResponseEntity;
        if(productResponse == null){
            productResponseEntity = new ResponseEntity<>(HttpStatusCode.valueOf(204));
        }
        else{
            productResponseEntity = new ResponseEntity<>(productResponse, HttpStatusCode.valueOf(200));
        }
        return productResponseEntity;
    }

    /**
     * Get products by category ID
     * @param categoryId Category ID to filter products
     * @return List of products in the specified category
     */
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<ProductDto>> getProductsByCategory(@PathVariable("categoryId") Long categoryId) {
        List<Product> products = this.productService.getByCategory(categoryId);
        List<ProductDto> productDtoList = productMapper.entityListToDtoList(products);

        if (products != null && !products.isEmpty()) {
            return new ResponseEntity<>(productDtoList, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
    }

    /**
     * Search products by keyword
     * @param query Search keyword
     * @param page Page number (default 0)
     * @param size Page size (default 10)
     * @param sort Sort field and direction (e.g., "name:asc" or "price:desc")
     * @return Paginated list of products matching the search criteria
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchProducts(
            @RequestParam("query") String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sort", required = false, defaultValue = "name:asc") String sort) {

        // Parse sort parameter
        Map<String, String> sortProperties = new HashMap<>();
        if (sort != null && !sort.isEmpty()) {
            String[] sortParts = sort.split(":");
            String sortField = sortParts[0];
            String sortDirection = sortParts.length > 1 ? sortParts[1] : "asc";
            sortProperties.put(sortField, sortDirection);
        } else {
            sortProperties.put("name", "asc");
        }

        // Search products
        Page<Product> productPage = searchService.searchProducts(query, page, size, sortProperties);
        List<ProductDto> productDtoList = productMapper.entityListToDtoList(productPage.getContent());

        // Build response with pagination metadata
        Map<String, Object> response = new HashMap<>();
        response.put("products", productDtoList);
        response.put("currentPage", productPage.getNumber());
        response.put("totalItems", productPage.getTotalElements());
        response.put("totalPages", productPage.getTotalPages());

        if (productDtoList.isEmpty()) {
            return new ResponseEntity<>(response, HttpStatus.NO_CONTENT);
        }

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping(value = "/category/{categoryId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public HttpEntity<?> addNew(
            @RequestBody ProductDto productRequest,
            @PathVariable("categoryId") Long categoryId){
        String productCreationFailedMessage = "Unable to create product. Check the request body";
        try {
            Product product = productMapper.dtoToEntity(productRequest);
            Product createdProduct = this.productService.addNew(product, categoryId);
            ProductDto productDto = productMapper.entityToDto(createdProduct);
            ResponseEntity<ProductDto> productResponseEntity;
            if(productDto != null){
                productResponseEntity = new ResponseEntity<>(productDto, HttpStatusCode.valueOf(201));
            }
            else{
                return new ResponseEntity<>(productCreationFailedMessage, HttpStatusCode.valueOf(400));
            }
            return productResponseEntity;
        } catch (ProductAddFailedException e) {
            return new ResponseEntity<>(productCreationFailedMessage, HttpStatusCode.valueOf(400));
        }
        catch (Exception e){
            return  new ResponseEntity<>("Something went wrong while trying to create product", HttpStatusCode.valueOf(500));
        }
    }

    @PreAuthorize("hasAnyAuthority('Developer','Admin')")
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable("id") Long id) throws ProductNotFoundException {
        Product product = this.productService.getById(id);
        if (product == null) {
            throw new ProductNotFoundException(String.format("Product with ID %d not found", id));
        }
        this.productService.update(product);
        return new ResponseEntity<>(String.format("Product %d has been deleted successfully.", id), HttpStatus.OK);
    }

    @PreAuthorize("hasAnyAuthority('Developer','Admin')")
    @PutMapping("/{id}")
    public ResponseEntity<ProductDto> update(@RequestBody ProductDto productDto, @PathVariable("id") Long id) throws ProductNotFoundException {
        Product existingProduct = this.productService.getById(id);
        if (existingProduct == null) {
            throw new ProductNotFoundException(String.format("Product with ID %d not found", id));
        }

        Product product = productMapper.dtoToEntity(productDto);
        product.setId(id);
        Product updatedProduct = this.productService.update(product);
        ProductDto updatedProductDto = productMapper.entityToDto(updatedProduct);

        if (updatedProductDto == null) {
            return new ResponseEntity<>(HttpStatusCode.valueOf(400));
        }
        return new ResponseEntity<>(updatedProductDto, HttpStatusCode.valueOf(200));
    }

    @ExceptionHandler(value = ProductNotFoundException.class)
    public ResponseEntity<String> productNotFoundExceptionHandler(ProductNotFoundException productNotFoundException) {
        return new ResponseEntity<>(productNotFoundException.getMessage(), HttpStatus.NOT_FOUND);
    }
}
