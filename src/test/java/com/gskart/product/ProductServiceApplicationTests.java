package com.gskart.product;

import com.gskart.product.entities.Category;
import com.gskart.product.entities.Product;
import com.gskart.product.exceptions.ProductAddFailedException;
import com.gskart.product.services.ICategoryService;
import com.gskart.product.services.IProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;

@SpringBootTest
class ProductServiceApplicationTests {

	@Autowired
	@Qualifier("gskartProductService")
	IProductService productService;

	@Autowired
	ICategoryService categoryService;

	@Test
	void contextLoads() {
	}

	@Test
	public void saveProductCascadeTest() {
		Category category = new Category();
		category.setName("Home appliances");
		//category.setStatus(Category.Status.ACTIVE);
		category.setDescription("Household appliances.");
		category = categoryService.save(category);

		Product product = new Product();
		product.setName("Microwave Oven");
		product.setDescription("Microwave oven, appliance that cooks food by means of high-frequency electromagnetic waves called microwaves.");
		product.setPrice(90d);
		product.setCategory(category);

		/*if(category.getProducts() == null){
			category.setProducts(new ArrayList<>());
		}
		category.getProducts().add(product);
		category = categoryService.save(category);*/


        try {
            productService.addNew(product, category.getId());
        } catch (ProductAddFailedException e) {
            throw new RuntimeException(e);
        }

    }

}