package br.com.personalfighters.service;

import br.com.personalfighters.entity.Product;
import br.com.personalfighters.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProductService {

  Product findById(Long id);

  List<Product> findAll();

  Page<Product> findAll(Product product, Pageable pageable);

  Product save(Product product);

  Product saveUserOnProduct(Product product, User user);

  void saveUserOnProducts(List<Product> products, Long id);

  Product deleteUserFromProduct(Product product, User user);

  void deleteUserFromAllProducts(User user);

  void setActive(Long id);

  List<Product> findProductByProfessional(Long id);
}
