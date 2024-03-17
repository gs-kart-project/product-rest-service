package com.gskart.product.controllers;

import com.gskart.product.DTOs.ProductDto;
import com.gskart.product.entities.Product;
import com.gskart.product.exceptions.ProductAddFailedException;
import com.gskart.product.mappers.ProductMapper;
import com.gskart.product.services.IProductService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
public class ProductsController {

    /*
    TODO Add the endpoints for
     1. Delete product
     2. Update product
     */

    private  final IProductService productService;

    private  final ProductMapper productMapper;

    public ProductsController(@Qualifier("gskartProductService") IProductService productService, ProductMapper productMapper) {
        this.productService = productService;
        this.productMapper = productMapper;
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
}
