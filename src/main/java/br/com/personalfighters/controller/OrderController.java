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
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;
  private final br.com.personalfighters.service.UserService userService;
  private final br.com.personalfighters.util.Redactor redactor;

  // ── Quem pode mexer em cada pedido (antes qualquer conta logada podia) ──
  private boolean admin() {
    var a = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    return a != null && a.getAuthorities().stream().anyMatch(x -> "ROLE_ADMIN".equals(x.getAuthority()));
  }
  private br.com.personalfighters.entity.User me() { return userService.findAuthUser(); }
  private static org.springframework.web.server.ResponseStatusException denied() {
    return new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso não permitido.");
  }
  private void self(Long id) { if (!admin() && (id == null || !id.equals(me().getId()))) throw denied(); }
  private static boolean is(br.com.personalfighters.entity.User u, Long id) { return u != null && u.getId() != null && u.getId().equals(id); }
  private Order customerOf(UUID id) {
    Order o = orderService.findById(id);
    if (!admin() && !is(o.getCustomer(), me().getId())) throw denied();
    return o;
  }
  /**
   * Pedido novo vindo do app: copia SÓ o que o cliente escolhe (quem, quando, onde, quantas horas).
   * Situação, valores, pagamento, código, disputa etc. nunca vêm do app — antes dava para criar
   * pedido "já pago", com preço inventado, ou sobrescrever o pedido de outra pessoa pelo id.
   */
  private Order cleanNewOrder(Order in) {
    Order o = new Order();
    o.setCustomer(me());
    if (in.getProfessional() != null && in.getProfessional().getId() != null) {
      br.com.personalfighters.entity.User p = new br.com.personalfighters.entity.User();
      p.setId(in.getProfessional().getId());
      o.setProfessional(p);
    }
    if (in.getProduct() != null && in.getProduct().getId() != null) {
      o.setProduct(in.getProduct()); // o servidor recarrega o produto pelo id (nenhum outro campo vale)
    }
    o.setDate(in.getDate());
    o.setStartTime(in.getStartTime());
    o.setEndTime(in.getEndTime());
    if (in.getHours() != null) o.setHours(Math.max(1, Math.min(12, in.getHours())));
    if (in.getNumberOfCustomers() != null) o.setNumberOfCustomers(Math.max(1, Math.min(20, in.getNumberOfCustomers())));
    String addr = in.getServiceAddress();
    o.setServiceAddress(addr == null ? null : (addr.length() > 300 ? addr.substring(0, 300) : addr));
    o.setGender(in.getGender());
    o.setUrgent(Boolean.TRUE.equals(in.getUrgent()));
    o.setStatus(Status.OPEN);
    return o;
  }

  private Order proOf(UUID id, Long proId) {
    self(proId);
    Order o = orderService.findById(id);
    if (!admin() && o.getProfessional() != null && !is(o.getProfessional(), proId)) throw denied();
    return o;
  }

  @GetMapping
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Retorna a lista paginada das orders.")
  public Object findAll(Order order, Pageable pageable) {
    if (!admin()) throw denied();
    return orderService.findAll(order, pageable);
  }

  @GetMapping("/{id}")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Retorna a order pelo ID.")
  public Object findById(@PathVariable UUID id) {
    Order o = orderService.findById(id);
    if (admin()) return o;
    Long uid = me().getId();
    boolean openForPros = o.getStatus() == Status.OPEN && o.getProfessional() == null;
    if (!is(o.getCustomer(), uid) && !is(o.getProfessional(), uid) && !openForPros) throw denied();
    return redactor.strip(o, uid);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Salva uma nova order")
  public Map<String, Object> save(@RequestBody Order order, @RequestParam(value = "mode", required = false) String mode) {
    if (!admin()) order = cleanNewOrder(order); // só os campos que o cliente pode escolher
    if (order.getProfessional() != null && order.getCustomer() != null && java.util.Objects.equals(order.getProfessional().getId(), order.getCustomer().getId())) {
      throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Você não pode contratar a si mesmo.");
    }
    if (order.getProfessional() != null && order.getProfessional().getId() != null && order.getCustomer() != null && order.getCustomer().getId() != null) {
      Long blocked = jdbc.queryForObject("SELECT COUNT(*) FROM pro_blocked_client_ WHERE professional_id = ? AND client_id = ?", Long.class,
          order.getProfessional().getId(), order.getCustomer().getId());
      if (blocked != null && blocked > 0) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Este profissional não está disponível para você. Escolha outro profissional.");
    }
    orderService.save(order);
    if (order.getId() != null && mode != null && java.util.Arrays.asList("HOME", "PRO_PLACE", "ONLINE").contains(mode.toUpperCase())) {
      jdbc.update("UPDATE order_ SET service_mode = ? WHERE id = ?", mode.toUpperCase(), order.getId());
    }
    Map<String, Object> out = new java.util.LinkedHashMap<>();
    out.put("id", order.getId());
    out.put("status", order.getStatus());
    return out;
  }

  /*
   * Customer endpoints
   */
  @GetMapping("/customers/{id}/{status}")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Retorna a lista paginada das orders pelo ID do Customer e uma lista de Status.")
  public Object findByCustomerAndStatus(@PathVariable Long id, @PathVariable List<Status> status, Pageable pageable) {
    self(id);
    return redactor.strip(orderService.findByCustomerAndStatus(id, status, pageable), id);
  }

  @PutMapping("/customers/confirmation/professionals/{id}")
  @ResponseStatus(HttpStatus.OK)
  public void confirmByCustomer(@PathVariable Long id, @RequestBody Order order) {
    customerOf(order.getId());
    orderService.confirmByCustomer(order.getId(), id);
  }

  @PutMapping("/customers/cancellation")
  @ResponseStatus(HttpStatus.OK)
  public void cancelByCustomer(@RequestBody Order order) {
    customerOf(order.getId());
    orderService.cancelOrder(order, Role.CUSTOMER);
  }

  /*
   * Professional endpoints
   */
  @GetMapping("/professionals/{id}")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Retorna a lista paginada das orders pelo ID do Professional")
  public Object findByProfessional(@PathVariable Long id, Pageable pageable) {
    self(id);
    return redactor.strip(orderService.findByProfessional(id, pageable), id);
  }

  @GetMapping("/professionals/{id}/{status}")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Retorna a lista paginada das orders pelo ID do Professional e uma lista de Status.")
  public Object findByProfessionalAndStatus(@PathVariable Long id, @PathVariable List<Status> status, Pageable pageable) {
    self(id);
    return redactor.strip(orderService.findByProfessionalAndStatus(id, status, pageable), id);
  }

  @PutMapping("/professionals/{id}/confirmation")
  @ResponseStatus(HttpStatus.OK)
  public void confirmByProfessional(@PathVariable Long id, @RequestBody Order order) {
    proOf(order.getId(), id);
    orderService.confirmByProfessional(order.getId(), id);
  }

  @PutMapping("/professionals/{id}/cancellation")
  @ResponseStatus(HttpStatus.OK)
  public void cancelByProfessional(@PathVariable Long id, @RequestBody Order order) {
    Order cur = proOf(order.getId(), id);
    // pedido aberto (sem profissional) não é seu para cancelar: use "Recusar"
    if (!admin() && cur.getProfessional() == null) throw denied();
    orderService.cancelOrder(order, Role.PROFESSIONAL);
  }

  @PutMapping("/professionals/{id}/rejection")
  @ResponseStatus(HttpStatus.OK)
  public void rejectByProfessional(@PathVariable Long id, @RequestBody Order order) {
    proOf(order.getId(), id);
    orderService.rejectByProfessional(order.getId(), id);
  }

  @PutMapping("/professionals/{id}/start")
  @ResponseStatus(HttpStatus.OK)
  public void startByProfessional(@PathVariable Long id, @RequestBody Order order) {
    // Atalho antigo: começava o atendimento sem a confirmação de chegada. O app usa o fluxo de chegada.
    throw new org.springframework.web.server.ResponseStatusException(HttpStatus.GONE, "Use \"Cheguei\" no pedido para começar o atendimento.");
  }

  @PutMapping("/professionals/{id}/complete")
  @ResponseStatus(HttpStatus.OK)
  public void completeByProfessional(@PathVariable Long id, @RequestBody Order order) {
    // Atalho antigo: o profissional marcava "Concluído" sozinho, pulando a conferência do cliente.
    throw new org.springframework.web.server.ResponseStatusException(HttpStatus.GONE, "Use \"Terminei\" no pedido: o cliente confere e libera o pagamento.");
  }

  @GetMapping("/nearby")
  @ResponseStatus(HttpStatus.OK)
  public Object nearbyOrders(@RequestParam double lat,
                             @RequestParam double lng,
                             @RequestParam(defaultValue = "20") double radiusKm,
                             @RequestParam(required = false, defaultValue = "") String specialties) {
    var u = me();
    // Só profissional vê chamados abertos por perto (antes qualquer conta via nome e endereço de pedidos alheios)
    // Só profissional com documentos aprovados vê chamados abertos por perto
    if (!admin() && (u.getProfessional() == null || !Boolean.TRUE.equals(u.getVerified()))) throw denied();
    java.util.List<Order> list = new java.util.ArrayList<>();
    for (Order o : orderService.nearbyOrders(lat, lng, Math.max(1, Math.min(radiusKm, 50)), specialties)) {
      // chamados dirigidos a outro profissional não aparecem
      if (admin() || o.getProfessional() == null || java.util.Objects.equals(o.getProfessional().getId(), u.getId())) list.add(o);
    }
    Object tree = redactor.strip(list, u.getId());
    // Endereço completo só depois de aceitar: aqui fica só o bairro/cidade (fim do endereço)
    if (!admin() && tree instanceof com.fasterxml.jackson.databind.node.ArrayNode) {
      for (com.fasterxml.jackson.databind.JsonNode n : (com.fasterxml.jackson.databind.node.ArrayNode) tree) {
        if (n instanceof com.fasterxml.jackson.databind.node.ObjectNode && n.hasNonNull("serviceAddress")) {
          String[] parts = n.get("serviceAddress").asText().split(",");
          String area = parts.length >= 2 ? (parts[parts.length - 2].trim() + ", " + parts[parts.length - 1].trim()) : "Endereço liberado após aceitar";
          ((com.fasterxml.jackson.databind.node.ObjectNode) n).put("serviceAddress", area.replaceAll("\\d", "").trim());
        }
      }
    }
    return tree;
  }

  @PostMapping("/{orderId}/pix")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Cria cobrança PIX para o pedido via Asaas")
  public Transaction createPixCharge(@PathVariable UUID orderId) {
    Order order = customerOf(orderId);
    return asaasService.createPixCharge(order);
  }

  @PostMapping("/{orderId}/dispute")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Abre disputa após conclusão do serviço — bloqueia auto-release por 48h")
  public Map<String, Object> openDispute(@PathVariable UUID orderId,
                                          @RequestBody Map<String, String> body) {
    Order order = orderService.findById(orderId);
    if (!admin() && !is(order.getCustomer(), me().getId()) && !is(order.getProfessional(), me().getId())) throw denied();

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
