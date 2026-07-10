package com.gskart.product.mappers;

import com.gskart.product.DTOs.ProductDto;
import com.gskart.product.entities.Product;
import com.gskart.product.fakestore.DTOs.requests.ProductRequest;
import com.gskart.product.fakestore.DTOs.responses.ProductResponse;
import com.gskart.product.search.ProductSearchResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProductMapper {

    public ProductDto entityToDto(Product product){
        if(product == null){
            return  null;
        }

        ProductDto productDto = new ProductDto();
        productDto.setId(product.getId());
        productDto.setName(product.getName());
        productDto.setDescription(product.getDescription());
        productDto.setPrice(product.getPrice());
        productDto.setImageUrl(product.getImageUrl());
        return  productDto;
    }

    public Product dtoToEntity(ProductDto productDto){
        if(productDto == null){
            return  null;
        }
        Product product = new Product();
        product.setId(productDto.getId());
        product.setName(productDto.getName());
        product.setDescription(productDto.getDescription());
        product.setPrice(productDto.getPrice());
        product.setImageUrl(productDto.getImageUrl());
        return product;
    }

    public Product fakeStoreResponseToEntity(ProductResponse response){
        if (response == null){
            return  null;
        }

        Product product = new Product();
        product.setId((long) response.getId());
        product.setName(response.getTitle());
        product.setDescription(response.getDescription());
        product.setPrice(response.getPrice());
        product.setImageUrl(response.getImage());
        return  product;
    }

    public ProductRequest entityToFakeStoreRequest(Product product) {
        ProductRequest productRequest = new ProductRequest();
        productRequest.setTitle(product.getName());
        productRequest.setDescription(product.getDescription());
        productRequest.setPrice(product.getPrice());
        productRequest.setImage(product.getImageUrl());
        return productRequest;
    }

    public List<ProductDto> entityListToDtoList(List<Product> productList){
        if(productList == null){
            return  null;
        }
        List<ProductDto> productDtoList = new ArrayList<>(productList.size());
        for(Product product : productList){
            productDtoList.add(entityToDto(product));
        }

        return productDtoList;
    }

    public ProductDto searchResultToDto(ProductSearchResult searchResult){
        if(searchResult == null){
            return null;
        }

        ProductDto productDto = new ProductDto();
        productDto.setId(searchResult.getProductId());
        productDto.setCategoryId(searchResult.getCategoryId());
        productDto.setName(searchResult.getName());
        productDto.setDescription(searchResult.getDescription());
        productDto.setImageUrl(searchResult.getImageUrl());
        productDto.setPrice(searchResult.getPrice());
        return productDto;
    }

    public List<ProductDto> searchResultListToDtoList(List<ProductSearchResult> searchResultList){
        if(searchResultList == null){
            return null;
        }
        List<ProductDto> productDtoList = new ArrayList<>(searchResultList.size());
        for(ProductSearchResult searchResult : searchResultList){
            productDtoList.add(searchResultToDto(searchResult));
        }

        return productDtoList;
    }
}
