package com.gskart.product.mappers;

import com.gskart.product.DTOs.ProductDto;
import com.gskart.product.entities.Product;
import com.gskart.product.fakestore.DTOs.requests.ProductRequest;
import com.gskart.product.fakestore.DTOs.responses.ProductResponse;
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
}
