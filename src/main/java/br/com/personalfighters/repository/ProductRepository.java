package br.com.personalfighters.repository;

import br.com.personalfighters.entity.Product;
import br.com.personalfighters.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

  @Query("select p.professional from Product as p where p.id=:id")
  List<User> findAllProductProfessionals(Long id);

  @Query("select p.professional from Product as p where p=:product")
  List<User> findAllProfessionalsByProduct(Product product);

  @Query("select p from Product p where p.professional=:user")
  List<Product> findAllByUser(User user);

  List<Product> findProductByProfessional(User professional);

  @Query("select p from Product p where p.id=:id and p.professional=:professional")
  Product findByProductAndProfessional(Long id, User professional);
}
