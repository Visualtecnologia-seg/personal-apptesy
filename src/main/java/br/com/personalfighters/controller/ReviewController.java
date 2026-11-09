package br.com.personalfighters.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import br.com.personalfighters.entity.Review;
import br.com.personalfighters.service.ReviewService;
import br.com.personalfighters.utils.Views;
import com.fasterxml.jackson.annotation.JsonView;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@CrossOrigin
@RestController
@RequiredArgsConstructor
@Api(tags = {"Reviews"})
@RequestMapping("/reviews")
public class ReviewController {

  private final ReviewService reviewService;
  private final br.com.personalfighters.service.UserService userService;
  private final br.com.personalfighters.repository.OrderRepository orderRepository;

  @GetMapping("/{id}")
  @ApiOperation(value = "Retorna review pelo ID.")
  @ResponseStatus(HttpStatus.OK)
  @JsonView(Views.FirstLevel.class)
  public Object find(@PathVariable Long id) {
    return reviewService.find(id);
  }

  @GetMapping("/users/{id}")
  @ApiOperation(value = "Retorna a lista de reviews do professional.")
  @ResponseStatus(HttpStatus.OK)
  @JsonView(Views.FirstLevel.class)
  public Object findByUser(@PathVariable Long id, Pageable pageable) {
    return reviewService.findByUser(id, pageable);
  }

  @PostMapping
  @ApiOperation(value = "Salva a review.")
  @ResponseStatus(HttpStatus.OK)
  @JsonView(Views.FirstLevel.class)
  public Object save(@RequestBody Review review) {
    // Só quem participou do pedido avalia, uma vez, e no próprio nome (antes dava para criar avaliação falsa).
    if (review.getOrder() == null || review.getOrder().getId() == null)
      throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Pedido não informado.");
    var o = orderRepository.findById(review.getOrder().getId()).orElseThrow(() ->
        new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
    var me = userService.findAuthUser();
    boolean client = o.getCustomer() != null && me.getId().equals(o.getCustomer().getId());
    boolean pro = o.getProfessional() != null && me.getId().equals(o.getProfessional().getId());
    if (!client && !pro) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso não permitido.");
    String st = o.getStatus() == null ? "" : o.getStatus().name();
    if (!st.equals("DONE") && !st.equals("REVIEWED"))
      throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Só dá para avaliar depois que o serviço termina.");
    if ((client && Boolean.TRUE.equals(o.getClientReviewed())) || (pro && Boolean.TRUE.equals(o.getProReviewed())))
      throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Você já avaliou este pedido.");
    review.setOrder(o);
    review.setReviewer(me);
    review.setCustomer(o.getCustomer());
    review.setProfessional(o.getProfessional());
    review.setType(client ? "CLIENT_TO_PRO" : "PRO_TO_CLIENT");
    review.setReviewerRole(client ? "CUSTOMER" : "PROFESSIONAL");
    return reviewService.save(review);
  }

  @GetMapping("/orders/{orderId}")
  @ApiOperation(value = "Retorna reviews de um pedido (reveladas).")
  @ResponseStatus(HttpStatus.OK)
  @JsonView(Views.FirstLevel.class)
  public Object findByOrder(@PathVariable String orderId) {
    return reviewService.findByOrder(java.util.UUID.fromString(orderId));
  }

  @GetMapping("/orders/{orderId}/has-reviewed/{reviewerId}")
  @ApiOperation(value = "Verifica se o usuário já avaliou este pedido.")
  @ResponseStatus(HttpStatus.OK)
  public Object hasReviewed(@PathVariable String orderId, @PathVariable Long reviewerId) {
    return java.util.Map.of("reviewed",
        reviewService.hasReviewed(java.util.UUID.fromString(orderId), reviewerId));
  }

  @PreAuthorize("hasRole('ROLE_ADMIN')")
  @DeleteMapping("/{id}")
  @ApiOperation(value = "Deleta review pelo ID.")
  @ResponseStatus(HttpStatus.OK)
  public void delete(@PathVariable Long id) {
    reviewService.delete(id);
  }
}
