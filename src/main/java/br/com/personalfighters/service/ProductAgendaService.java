package br.com.personalfighters.service;

import br.com.personalfighters.entity.Product;
import br.com.personalfighters.entity.ProductAgenda;

public interface ProductAgendaService {
  Iterable<ProductAgenda> findByProductId(Long id);

  void save(Product product);

  void update(Product product);

  void updateAllProductAgendas();
}
