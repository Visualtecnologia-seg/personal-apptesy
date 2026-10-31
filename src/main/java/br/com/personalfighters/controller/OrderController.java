package br.com.personalfighters.controller;

import br.com.personalfighters.entity.Order;
import br.com.personalfighters.entity.Transaction;
import br.com.personalfighters.model.Role;
import br.com.personalfighters.model.Status;
import br.com.personalfighters.service.AsaasService;
import br.com.personalfighters.service.OrderService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@CrossOrigin
@RequiredArgsConstructor
@RestController
@Api(tags = {"Orders"})
@RequestMapping("/orders")
public class OrderController {

  private final OrderService orderService;
  private final AsaasService asaasService;

  @GetMapping
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Retorna a lista paginada das orders.")
  public Object findAll(Order order, Pageable pageable) {
    return orderService.findAll(order, pageable);
  }

  @GetMapping("/{id}")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Retorna a order pelo ID.")
  public Object findById(@PathVariable UUID id) {
    return orderService.findById(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Salva uma nova order")
  public void save(@RequestBody Order order) {
    /* TODO Verificar o motivo do pedido não retornar corretamente. Foi colocado como void provisoriamente */
    orderService.save(order);
  }

  /*
   * Customer endpoints
   */
  @GetMapping("/customers/{id}/{status}")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Retorna a lista paginada das orders pelo ID do Customer e uma lista de Status.")
  public Object findByCustomerAndStatus(@PathVariable Long id, @PathVariable List<Status> status, Pageable pageable) {
    return orderService.findByCustomerAndStatus(id, status, pageable);
  }

  @PutMapping("/customers/confirmation/professionals/{id}")
  @ResponseStatus(HttpStatus.OK)
  public void confirmByCustomer(@PathVariable Long id, @RequestBody Order order) {
    orderService.confirmByCustomer(order.getId(), id);
  }

  @PutMapping("/customers/cancellation")
  @ResponseStatus(HttpStatus.OK)
  public void cancelByCustomer(@RequestBody Order order) {
    orderService.cancelOrder(order, Role.CUSTOMER);
  }

  /*
   * Professional endpoints
   */
  @GetMapping("/professionals/{id}")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Retorna a lista paginada das orders pelo ID do Professional")
  public Object findByProfessional(@PathVariable Long id, Pageable pageable) {
    return orderService.findByProfessional(id, pageable);
  }

  @GetMapping("/professionals/{id}/{status}")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Retorna a lista paginada das orders pelo ID do Professional e uma lista de Status.")
  public Object findByProfessionalAndStatus(@PathVariable Long id, @PathVariable List<Status> status, Pageable pageable) {
    return orderService.findByProfessionalAndStatus(id, status, pageable);
  }

  @PutMapping("/professionals/{id}/confirmation")
  @ResponseStatus(HttpStatus.OK)
  public void confirmByProfessional(@PathVariable Long id, @RequestBody Order order) {
    orderService.confirmByProfessional(order.getId(), id);
  }

  @PutMapping("/professionals/{id}/cancellation")
  @ResponseStatus(HttpStatus.OK)
  public void cancelByProfessional(@PathVariable Long id, @RequestBody Order order) {
    orderService.cancelOrder(order, Role.PROFESSIONAL);
  }

  @PutMapping("/professionals/{id}/rejection")
  @ResponseStatus(HttpStatus.OK)
  public void rejectByProfessional(@PathVariable Long id, @RequestBody Order order) {
    orderService.rejectByProfessional(order.getId(), id);
  }

  @PutMapping("/professionals/{id}/start")
  @ResponseStatus(HttpStatus.OK)
  public void startByProfessional(@PathVariable Long id, @RequestBody Order order) {
    orderService.startOrder(order.getId(), id);
  }

  @PutMapping("/professionals/{id}/complete")
  @ResponseStatus(HttpStatus.OK)
  public void completeByProfessional(@PathVariable Long id, @RequestBody Order order) {
    orderService.completeOrder(order.getId(), id);
  }

  @GetMapping("/nearby")
  @ResponseStatus(HttpStatus.OK)
  public Object nearbyOrders(@RequestParam double lat,
                             @RequestParam double lng,
                             @RequestParam(defaultValue = "20") double radiusKm,
                             @RequestParam(required = false, defaultValue = "") String specialties) {
    return orderService.nearbyOrders(lat, lng, radiusKm, specialties);
  }

  @PostMapping("/{orderId}/pix")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Cria cobrança PIX para o pedido via Asaas")
  public Transaction createPixCharge(@PathVariable UUID orderId) {
    Order order = orderService.findById(orderId);
    return asaasService.createPixCharge(order);
  }

  @PostMapping("/{orderId}/dispute")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Abre disputa após conclusão do serviço — bloqueia auto-release por 48h")
  public Map<String, Object> openDispute(@PathVariable UUID orderId,
                                          @RequestBody Map<String, String> body) {
    Order order = orderService.findById(orderId);

    if (order.getDisputeStatus() != null && !order.getDisputeStatus().isBlank()) {
      return Map.of("error", "Já existe uma disputa aberta para este pedido.");
    }
    if (!"DONE".equals(order.getStatus().name())) {
      return Map.of("error", "Disputas só podem ser abertas em serviços já concluídos.");
    }

    String reason = body.getOrDefault("reason", "Sem motivo informado").trim();
    if (reason.isBlank()) {
      return Map.of("error", "Descreva o motivo da disputa.");
    }

    order.setDisputeReason(reason);
    order.setDisputeStatus("OPEN");
    order.setDisputedAt(LocalDateTime.now());
    orderService.update(order);

    return Map.of(
        "success", true,
        "message", "Disputa aberta com sucesso. Nossa equipe analisará em até 48 horas.",
        "disputeStatus", "OPEN"
    );
  }

}
