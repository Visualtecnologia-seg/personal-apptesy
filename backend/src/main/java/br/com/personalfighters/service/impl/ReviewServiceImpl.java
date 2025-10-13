package br.com.personalfighters.service.impl;

import br.com.personalfighters.entity.Review;
import br.com.personalfighters.entity.User;
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

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewServiceImpl implements ReviewService {

  private final ReviewRepository reviewRepository;
  private final UserService userService;
  private final ProfessionalService professionalService;
  private final OrderService orderService;

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
      User user = userService.findById(review.getProfessional().getId());
      Long count = user.getProfessional().getCount();
      double rating = user.getProfessional().getRating();
      rating = (rating * count + review.getRating()) / (count + 1);
      reviewRepository.save(review);
      professionalService.setRating(user.getProfessional().getId(), rating, count + 1);
      orderService.setReviewed(review.getOrder().getId());
    } catch (Exception e) {
      log.info("Error on save review.");
    }
    return review;
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
        log.info("Successfully deleted Review [" + review.get().getId() + "]");
      } catch (DataIntegrityViolationException e) {
        throw new DataIntegrityException("Não é possível excluir o professional de ID: " + id + ", porque tem entidades relacionadas");
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
}
