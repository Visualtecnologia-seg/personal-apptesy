package br.com.personalfighters.service;

import br.com.personalfighters.entity.Favorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface FavoriteService {
  Page<Favorite> findAll(Favorite favorite, Pageable pageable);

  Page<Favorite> findAllByUser(Long id, Pageable pageable);

  Optional<Favorite> findById(Long id);

  Favorite save(Favorite favorite, String ref);

  void delete(Long id) throws Exception;
}
