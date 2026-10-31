package br.com.personalfighters.repository;

import br.com.personalfighters.entity.Favorite;
import br.com.personalfighters.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
  Page<Favorite> findAllByUser(User user, Pageable pageable);

  Long countByUserAndProfessional(User user, User professional);
}
