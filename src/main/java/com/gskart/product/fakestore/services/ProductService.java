package com.gskart.product.fakestore.services;

import com.gskart.product.entities.Product;
import com.gskart.product.exceptions.ProductAddFailedException;
import com.gskart.product.fakestore.DTOs.requests.ProductRequest;
import com.gskart.product.fakestore.DTOs.responses.ProductResponse;
import com.gskart.product.mappers.ProductMapper;
import com.gskart.product.services.IProductService;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

@Service("fakeStoreProductService")
public class ProductService implements IProductService {

    private final RestTemplate restTemplate;
    
    private  final  ProductMapper productMapper;

    private final String baseUrl = "https://fakestoreapi.com";
    private final String products = "/products";

    public ProductService(RestTemplate restTemplate, ProductMapper productMapper) {
        this.restTemplate = restTemplate;
        this.productMapper = productMapper;
    }

    @Override
    public Product addNew(Product product, Long categoryId) throws ProductAddFailedException {
        ProductRequest productRequest = productMapper.entityToFakeStoreRequest(product);
        RequestEntity<ProductRequest> productRequestEntity = new RequestEntity<>(productRequest, HttpMethod.POST,
                URI.create(String.format("%s%s", baseUrl, products)));

        ResponseEntity<ProductResponse> productResponseEntity = this.restTemplate.exchange(productRequestEntity,
                new ParameterizedTypeReference<ProductResponse>() {  });

        if(!productResponseEntity.hasBody()){
            return null;
        }

        if(productResponseEntity.getStatusCode() != HttpStatusCode.valueOf(200)){
            throw new ProductAddFailedException("Product not created");
        }
        return productMapper.fakeStoreResponseToEntity(productResponseEntity.getBody());
    }

    @Override
    public Product update(Product product) {
        return null;
    }

    @Override
    public List<Product> getAll(){
        ResponseEntity<List<ProductResponse>> productResponseEntity = this.restTemplate.exchange(String.format("%s%s", baseUrl, products),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<ProductResponse>>(){});

        if(!productResponseEntity.hasBody()){
            return null;
        }

        List<Product> productList = new ArrayList<>(Objects.requireNonNull(productResponseEntity.getBody()).size());
        for(ProductResponse productResponse : productResponseEntity.getBody()){
            productList.add(productMapper.fakeStoreResponseToEntity(productResponse));
        }

        return productList;
    }

    @Override
    public Product getById(Long id) {
        ProductResponse productResponse = this.restTemplate.getForObject(String.format("%s%s/%d", baseUrl, products, id),
                ProductResponse.class);

        return productMapper.fakeStoreResponseToEntity(productResponse);
    }

    @Override
    public List<Product> getByCategory(Long categoryId) {
        return null;
    }

    private String getRandomCategory(){
        List<String> categories = new ArrayList<>();
        categories.add("electronics");
        categories.add("jewelery");
        categories.add("men's clothing");
        categories.add("women's clothing");
        Random random = new Random();

        return  categories.get(random.nextInt(0, categories.size()));
    }
}
