package br.com.personalfighters.repository;

import br.com.personalfighters.entity.Review;
import br.com.personalfighters.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {
  Page<Review> findByProfessional(User professional, Pageable pageable);
}
