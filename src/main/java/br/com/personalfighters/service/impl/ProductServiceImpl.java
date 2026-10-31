package br.com.personalfighters.service.impl;

import br.com.personalfighters.entity.Product;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.repository.ProductRepository;
import br.com.personalfighters.repository.UserRepository;
import br.com.personalfighters.service.ProductAgendaService;
import br.com.personalfighters.service.ProductService;
import br.com.personalfighters.service.impl.exceptions.ObjectNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

  private final ProductRepository productRepository;
  private final UserRepository userRepository;
  private final ProductAgendaService productAgendaService;

  @Override
  public Product findById(Long id) {
    return productRepository.findById(id).orElseThrow(() -> new ObjectNotFoundException("Object not found"));
  }

  @Override
  public List<Product> findAll() {
    return productRepository.findAll();
  }

  @Override
  public Page<Product> findAll(Product product, Pageable pageable) {
    Example<Product> example = Example.of(product,
        ExampleMatcher
            .matching()
            .withIgnoreCase()
            .withIgnoreNullValues()
            .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING)
    );
    return productRepository.findAll(example, pageable);
  }

  @Override
  public Product save(Product product) {
    productRepository.save(product);
    productAgendaService.save(product);
    return product;
  }

  @Override
  public Product saveUserOnProduct(Product product, User user) {
    List<User> list = productRepository.findAllProductProfessionals(product.getId());
    list.add(user);
    product.setProfessional(list);
    return productRepository.save(product);
  }

  @Override
  public void saveUserOnProducts(List<Product> products, Long id) {
    User user = userRepository.findById(id).orElseThrow();
    deleteUserFromAllProducts(user);
    products.forEach(product -> saveUserOnProduct(product, user));
  }

  @Override
  public Product deleteUserFromProduct(Product product, User user) {
    List<User> list = productRepository.findAllProductProfessionals(product.getId());
    list = list.stream().filter(p -> !p.getId().equals(user.getId())).collect(Collectors.toList());
    product.setProfessional(list);
    return productRepository.save(product);
  }

  @Override
  public void deleteUserFromAllProducts(User user) {
    List<Product> list = productRepository.findAll();
    list.forEach(product -> {
      List<User> l = productRepository.findAllProductProfessionals(product.getId());
      l = l.stream().filter(p -> !p.getId().equals(user.getId())).collect(Collectors.toList());
      product.setProfessional(l);
      productRepository.save(product);
    });
  }

  @Override
  public void setActive(Long id) {
    Product product = productRepository.findById(id).orElseThrow(() -> new ObjectNotFoundException("Object not found"));
    product.setActive(!product.getActive());
    productRepository.save(product);
  }

  @Override
  public List<Product> findProductByProfessional(Long id) {
    User user = userRepository.findById(id).orElseThrow();
    return productRepository.findProductByProfessional(user);
  }

}
