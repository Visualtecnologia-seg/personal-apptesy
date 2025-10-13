package br.com.personalfighters.service;

import br.com.personalfighters.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReviewService {

  Review find(Long id);

  Iterable<Review> findAll();

  Review save(Review review);

  Review update(Review review);

  void delete(Long id);

  Page<Review> findByUser(Long id, Pageable pageable);
}
