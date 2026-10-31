package br.com.personalfighters.service.impl;

import br.com.personalfighters.entity.Order;
import br.com.personalfighters.entity.Review;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.repository.OrderRepository;
import br.com.personalfighters.repository.ReviewRepository;
import br.com.personalfighters.service.OrderService;
import br.com.personalfighters.service.ProfessionalService;
import br.com.personalfighters.service.ReviewService;
import br.com.personalfighters.service.UserService;
import br.com.personalfighters.service.impl.exceptions.DataIntegrityException;
import br.com.personalfighters.service.impl.exceptions.ObjectNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewServiceImpl implements ReviewService {

  private final ReviewRepository reviewRepository;
  private final UserService userService;
  private final ProfessionalService professionalService;
  private final OrderService orderService;
  private final OrderRepository orderRepository;

  @Override
  public Review find(Long id) {
    return reviewRepository.findById(id).orElseThrow(() ->
        new ObjectNotFoundException("Review não encontrado - ID: " + id)
    );
  }

  @Override
  public Iterable<Review> findAll() {
    return reviewRepository.findAll();
  }

  @Override
  public Review save(Review review) {
    try {
      review.setDate(LocalDate.now());
      double globalRating = computeGlobal(review);
      review.setRating((int) Math.round(globalRating));
      review.setRevealed(false);

      reviewRepository.save(review);

      Order order = orderRepository.findById(review.getOrder().getId())
          .orElseThrow(() -> new ObjectNotFoundException("Pedido não encontrado"));

      String type = review.getType() != null ? review.getType() : "CLIENT_TO_PRO";

      if ("CLIENT_TO_PRO".equals(type)) {
        order.setClientReviewed(true);
      } else {
        order.setProReviewed(true);
      }

      boolean bothReviewed = Boolean.TRUE.equals(order.getClientReviewed())
          && Boolean.TRUE.equals(order.getProReviewed());

      if (bothReviewed) {
        order.setIsReviewed(true);
        revealReviews(order.getId());
        recalcProfessionalRating(order);
        recalcClientRating(order);
      }

      orderRepository.save(order);

    } catch (Exception e) {
      log.error("Error saving review: {}", e.getMessage(), e);
    }
    return review;
  }

  private double computeGlobal(Review r) {
    int count = 0;
    double sum = 0;
    if (r.getCat1() != null) { sum += r.getCat1(); count++; }
    if (r.getCat2() != null) { sum += r.getCat2(); count++; }
    if (r.getCat3() != null) { sum += r.getCat3(); count++; }
    if (r.getCat4() != null) { sum += r.getCat4(); count++; }
    if (count == 0 && r.getRating() != null) return r.getRating();
    return count > 0 ? sum / count : 0;
  }

  private void revealReviews(UUID orderId) {
    List<Review> reviews = reviewRepository.findByOrderId(orderId);
    for (Review rev : reviews) {
      rev.setRevealed(true);
      reviewRepository.save(rev);
    }
  }

  private void recalcProfessionalRating(Order order) {
    try {
      if (order.getProfessional() == null) return;
      User proUser = userService.findById(order.getProfessional().getId());
      if (proUser.getProfessional() == null) return;
      List<Review> proReviews = reviewRepository.findByProfessionalAndType(proUser, "CLIENT_TO_PRO");
      if (proReviews.isEmpty()) return;
      double avg = proReviews.stream()
          .filter(r -> r.getRating() != null)
          .mapToInt(Review::getRating).average().orElse(0);
      professionalService.setRating(proUser.getProfessional().getId(), avg, (long) proReviews.size());
    } catch (Exception e) {
      log.error("Error recalculating professional rating: {}", e.getMessage());
    }
  }

  private void recalcClientRating(Order order) {
    // Client rating field not yet available; placeholder for future implementation
  }

  @Override
  public Review update(Review review) {
    return reviewRepository.save(review);
  }

  @Override
  public void delete(Long id) {
    Optional<Review> review = reviewRepository.findById(id);
    if (review.isPresent()) {
      try {
        reviewRepository.delete(review.get());
        log.info("Successfully deleted Review [{}]", review.get().getId());
      } catch (DataIntegrityViolationException e) {
        throw new DataIntegrityException("Não é possível excluir o review de ID: " + id);
      }
    } else {
      throw new ObjectNotFoundException("Review não encontrado - ID: " + id);
    }
  }

  @Override
  public Page<Review> findByUser(Long id, Pageable pageable) {
    User user = userService.findById(id);
    return reviewRepository.findByProfessional(user, pageable);
  }

  @Override
  public List<Review> findByOrder(UUID orderId) {
    return reviewRepository.findByOrderId(orderId);
  }

  @Override
  public boolean hasReviewed(UUID orderId, Long reviewerId) {
    return reviewRepository.existsByOrderIdAndReviewerId(orderId, reviewerId);
  }
}
