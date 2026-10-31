package br.com.personalfighters.repository;

import br.com.personalfighters.entity.Product;
import br.com.personalfighters.entity.ProductAgenda;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductAgendaRepository extends JpaRepository<ProductAgenda, Long> {
  Iterable<ProductAgenda> findByProduct(Product product);
}
