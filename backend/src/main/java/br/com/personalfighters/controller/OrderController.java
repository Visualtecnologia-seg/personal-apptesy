package br.com.personalfighters.controller;

import br.com.personalfighters.entity.Order;
import br.com.personalfighters.model.Role;
import br.com.personalfighters.model.Status;
import br.com.personalfighters.service.OrderService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@CrossOrigin
@RequiredArgsConstructor
@RestController
@Api(tags = {"Orders"})
@RequestMapping("/orders")
public class OrderController {

  private final OrderService orderService;

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

}
