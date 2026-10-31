package br.com.personalfighters.repository;

import br.com.personalfighters.entity.Review;
import br.com.personalfighters.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {
  Page<Review> findByProfessional(User professional, Pageable pageable);
  java.util.List<Review> findByOrderId(java.util.UUID orderId);
  java.util.List<Review> findByProfessionalAndType(User professional, String type);
  java.util.List<Review> findByCustomerAndType(User customer, String type);
  boolean existsByOrderIdAndReviewerId(java.util.UUID orderId, Long reviewerId);
}
