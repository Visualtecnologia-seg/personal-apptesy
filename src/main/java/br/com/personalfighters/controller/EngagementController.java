package br.com.personalfighters.controller;

import br.com.personalfighters.entity.*;
import br.com.personalfighters.model.Role;
import br.com.personalfighters.model.Status;
import br.com.personalfighters.repository.*;
import br.com.personalfighters.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Funcionalidades de engajamento:
 *  - Pontos: níveis e resgate (100 pontos = R$ 10 de desconto num pedido).
 *  - Chamado urgente ("Preciso agora"): vai para os profissionais próximos; o primeiro que aceitar leva.
 *  - Garantia OPUS: o cliente pede retorno em até 30 dias após o serviço.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class EngagementController {

  static final int POINTS_PER_REDEEM = 100;
  static final BigDecimal REDEEM_VALUE = new BigDecimal("10.00");
  static final int WARRANTY_DAYS = 30;
  static final double URGENT_RADIUS_KM = 25.0;

  private final UserService userService;
  private final UserRepository userRepository;
  private final OrderRepository orderRepository;
  private final PointsRepository pointsRepository;
  private final UrgentRequestRepository urgentRepository;
  public static volatile BigDecimal MIN_ORDER_FOR_REDEEM = new BigDecimal("100");
  private final WarrantyClaimRepository warrantyRepository;

  // ═════════════════════════════ PONTOS ═════════════════════════════

  @GetMapping("/points/me/summary")
  public Map<String, Object> pointsSummary() {
    User me = userService.findAuthUser();
    List<Points> history = pointsRepository.findByUserOrderByCreatedAtDesc(me);
    int balance = history.stream().mapToInt(p -> p.getAmount() == null ? 0 : p.getAmount()).sum();
    int earned = history.stream().mapToInt(p -> p.getAmount() == null ? 0 : Math.max(0, p.getAmount())).sum();
    String level; Integer next; String nextName;
    if (earned >= 150) { level = "OURO"; next = null; nextName = null; }
    else if (earned >= 50) { level = "PRATA"; next = 150; nextName = "OURO"; }
    else { level = "BRONZE"; next = 50; nextName = "PRATA"; }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("balance", balance);
    out.put("earned", earned);
    out.put("level", level);
    out.put("nextLevel", nextName);
    out.put("nextLevelAt", next);
    out.put("pointsPerRedeem", POINTS_PER_REDEEM);
    out.put("redeemValue", REDEEM_VALUE);
    out.put("canRedeem", balance >= POINTS_PER_REDEEM);
    out.put("minOrderForRedeem", MIN_ORDER_FOR_REDEEM);
    out.put("history", history.stream().limit(30).map(p -> {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("amount", p.getAmount());
      m.put("reason", p.getReason());
      m.put("createdAt", p.getCreatedAt());
      return m;
    }).collect(Collectors.toList()));
    return out;
  }

  @PostMapping("/points/me/redeem/{orderId}")
  @Transactional
  public ResponseEntity<?> redeem(@PathVariable UUID orderId) {
    User me = userService.findAuthUser();
    Order order = orderRepository.findById(orderId).orElse(null);
    if (order == null) return bad("Pedido não encontrado.");
    if (order.getCustomer() == null || !order.getCustomer().getId().equals(me.getId())) return bad("Este pedido não é seu.");
    if (Boolean.TRUE.equals(order.getPixPaid())) return bad("Este pedido já foi pago.");
    if (order.getPixChargeId() != null) return bad("O PIX deste pedido já foi gerado. Use os pontos no próximo pedido.");
    if (order.getStatus() == Status.CANCELLED || order.getStatus() == Status.DONE || order.getStatus() == Status.REVIEWED) {
      return bad("Não é possível usar pontos neste pedido.");
    }
    if (order.getDiscountAmount() != null && order.getDiscountAmount().signum() > 0) return bad("Este pedido já tem desconto.");
    if (order.getTotalCost() == null || order.getTotalCost().compareTo(MIN_ORDER_FOR_REDEEM) < 0) {
      return bad("Os pontos podem ser usados em serviços a partir de R$ 100.");
    }
    Integer balance = pointsRepository.sumAmountByUser(me);
    if (balance == null || balance < POINTS_PER_REDEEM) {
      return bad("Você precisa de " + POINTS_PER_REDEEM + " pontos para resgatar.");
    }
    pointsRepository.save(Points.builder()
        .user(me)
        .amount(-POINTS_PER_REDEEM)
        .reason("Resgate: R$ 10 de desconto")
        .orderId(orderId)
        .build());
    order.setDiscountAmount(REDEEM_VALUE);
    orderRepository.save(order);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("discount", REDEEM_VALUE);
    out.put("totalCost", order.getTotalCost());
    out.put("toPay", order.getTotalCost().subtract(REDEEM_VALUE));
    out.put("balance", balance - POINTS_PER_REDEEM);
    return ResponseEntity.ok(out);
  }

  // ═════════════════════════════ CHAMADO URGENTE ═════════════════════════════

  @PostMapping("/urgent")
  public ResponseEntity<?> createUrgent(@RequestBody Map<String, Object> body) {
    User me = userService.findAuthUser();
    Optional<UrgentRequest> last = urgentRepository.findFirstByCustomerIdOrderByCreatedAtDesc(me.getId());
    if (last.isPresent() && "OPEN".equals(last.get().getStatus()) && last.get().getExpiresAt().isAfter(LocalDateTime.now())) {
      return bad("Você já tem um chamado urgente aberto.");
    }
    String description = Objects.toString(body.get("description"), "").trim();
    String address = Objects.toString(body.get("address"), "").trim();
    if (description.length() < 5) return bad("Descreva rapidamente o que aconteceu.");
    if (address.isEmpty()) return bad("Informe o endereço.");
    Double lat = toDouble(body.get("latitude"));
    Double lng = toDouble(body.get("longitude"));
    if (lat == null || lng == null) return bad("Precisamos da sua localização para achar profissionais próximos.");
    UrgentRequest u = UrgentRequest.builder()
        .customerId(me.getId())
        .categoryName(Objects.toString(body.get("categoryName"), "Serviços de Urgência"))
        .description(description)
        .address(address)
        .latitude(lat)
        .longitude(lng)
        .status("OPEN")
        .build();
    urgentRepository.save(u);
    me.setLatitude(lat);
    me.setLongitude(lng);
    userRepository.save(me);
    return ResponseEntity.ok(urgentView(u, null));
  }

  @GetMapping("/urgent/me")
  public ResponseEntity<?> myUrgent() {
    User me = userService.findAuthUser();
    return urgentRepository.findFirstByCustomerIdOrderByCreatedAtDesc(me.getId())
        .map(u -> {
          if ("OPEN".equals(u.getStatus()) && u.getExpiresAt().isBefore(LocalDateTime.now())) {
            u.setStatus("EXPIRED");
            urgentRepository.save(u);
          }
          return ResponseEntity.ok((Object) urgentView(u, null));
        })
        .orElse(ResponseEntity.ok((Object) Map.of("status", "NONE")));
  }

  @PutMapping("/urgent/{id}/cancel")
  public ResponseEntity<?> cancelUrgent(@PathVariable UUID id) {
    User me = userService.findAuthUser();
    UrgentRequest u = urgentRepository.findById(id).orElse(null);
    if (u == null || !u.getCustomerId().equals(me.getId())) return bad("Chamado não encontrado.");
    if (!"OPEN".equals(u.getStatus())) return bad("Este chamado não está mais aberto.");
    u.setStatus("CANCELLED");
    urgentRepository.save(u);
    return ResponseEntity.ok(urgentView(u, null));
  }

  /** Lista para o profissional: chamados abertos perto dele. */
  @GetMapping("/urgent/nearby")
  public List<Map<String, Object>> nearbyUrgent(@RequestParam double lat, @RequestParam double lng) {
    User me = userService.findAuthUser();
    me.setLatitude(lat);
    me.setLongitude(lng);
    userRepository.save(me);
    return urgentRepository.findByStatusAndExpiresAtAfter("OPEN", LocalDateTime.now()).stream()
        .filter(u -> !u.getCustomerId().equals(me.getId()))
        .filter(u -> u.getLatitude() != null && u.getLongitude() != null)
        .map(u -> new AbstractMap.SimpleEntry<>(u, distanceKm(lat, lng, u.getLatitude(), u.getLongitude())))
        .filter(e -> e.getValue() <= URGENT_RADIUS_KM)
        .sorted(Comparator.comparingDouble(e -> e.getValue()))
        .map(e -> urgentView(e.getKey(), e.getValue()))
        .collect(Collectors.toList());
  }

  @PutMapping("/urgent/{id}/accept")
  @Transactional
  public ResponseEntity<?> acceptUrgent(@PathVariable UUID id) {
    User me = userService.findAuthUser();
    if (me.getRole() == null || !me.getRole().contains(Role.PROFESSIONAL)) return bad("Apenas profissionais podem aceitar.");
    if (!Boolean.TRUE.equals(me.getVerified())) return bad("Verifique sua identidade no app para aceitar chamados urgentes.");
    int won = urgentRepository.take(id, me.getId(), LocalDateTime.now());
    if (won == 0) return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "Outro profissional já aceitou este chamado."));
    UrgentRequest u = urgentRepository.findById(id).orElseThrow();
    log.info("Chamado urgente {} aceito pelo profissional {}", id, me.getId());
    return ResponseEntity.ok(urgentView(u, null));
  }

  private Map<String, Object> urgentView(UrgentRequest u, Double distance) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", u.getId());
    m.put("status", u.getStatus());
    m.put("categoryName", u.getCategoryName());
    m.put("description", u.getDescription());
    m.put("address", u.getAddress());
    m.put("createdAt", u.getCreatedAt());
    m.put("expiresAt", u.getExpiresAt());
    if (distance != null) m.put("distanceKm", Math.round(distance * 10.0) / 10.0);
    userRepository.findById(u.getCustomerId()).ifPresent(c -> m.put("customerName", firstName(c.getName())));
    if (u.getTakenBy() != null) {
      userRepository.findById(u.getTakenBy()).ifPresent(p -> {
        Map<String, Object> pro = new LinkedHashMap<>();
        pro.put("id", p.getId());
        pro.put("name", p.getName());
        pro.put("verified", Boolean.TRUE.equals(p.getVerified()));
        if (p.getProfessional() != null) {
          pro.put("hourlyRate", p.getProfessional().getHourlyRate());
          pro.put("rating", p.getProfessional().getRating());
        }
        if (p.getLatitude() != null && u.getLatitude() != null) {
          pro.put("distanceKm", Math.round(distanceKm(p.getLatitude(), p.getLongitude(), u.getLatitude(), u.getLongitude()) * 10.0) / 10.0);
        }
        m.put("professional", pro);
      });
    }
    return m;
  }

  // ═════════════════════════════ GARANTIA ═════════════════════════════

  @GetMapping("/warranty/orders/{orderId}")
  public ResponseEntity<?> warrantyForOrder(@PathVariable UUID orderId) {
    User me = userService.findAuthUser();
    Order order = orderRepository.findById(orderId).orElse(null);
    if (order == null || !isParty(order, me)) return bad("Pedido não encontrado.");
    Map<String, Object> out = new LinkedHashMap<>();
    LocalDateTime deadline = warrantyDeadline(order);
    out.put("deadline", deadline);
    boolean finished = order.getStatus() == Status.DONE || order.getStatus() == Status.REVIEWED;
    out.put("eligible", finished && deadline != null && deadline.isAfter(LocalDateTime.now()));
    warrantyRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId).ifPresent(c -> out.put("claim", claimView(c)));
    return ResponseEntity.ok(out);
  }

  @PostMapping("/warranty/orders/{orderId}")
  public ResponseEntity<?> openWarranty(@PathVariable UUID orderId, @RequestBody Map<String, String> body) {
    User me = userService.findAuthUser();
    Order order = orderRepository.findById(orderId).orElse(null);
    if (order == null || order.getCustomer() == null || !order.getCustomer().getId().equals(me.getId())) return bad("Pedido não encontrado.");
    if (order.getStatus() != Status.DONE && order.getStatus() != Status.REVIEWED) return bad("A garantia vale para serviços concluídos.");
    LocalDateTime deadline = warrantyDeadline(order);
    if (deadline == null || deadline.isBefore(LocalDateTime.now())) return bad("O prazo de " + WARRANTY_DAYS + " dias da garantia já passou.");
    Optional<WarrantyClaim> existing = warrantyRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId);
    if (existing.isPresent() && !"RESOLVED".equals(existing.get().getStatus())) return bad("Já existe uma garantia aberta para este pedido.");
    String reason = Objects.toString(body.get("reason"), "").trim();
    if (reason.length() < 10) return bad("Conte com mais detalhes o que precisa ser refeito.");
    WarrantyClaim c = WarrantyClaim.builder()
        .orderId(orderId)
        .customerId(me.getId())
        .professionalId(order.getProfessional() == null ? null : order.getProfessional().getId())
        .reason(reason)
        .status("OPEN")
        .build();
    warrantyRepository.save(c);
    return ResponseEntity.ok(claimView(c));
  }

  @GetMapping("/warranty/professional/me")
  public List<Map<String, Object>> myWarrantyClaims() {
    User me = userService.findAuthUser();
    return warrantyRepository.findByProfessionalIdOrderByCreatedAtDesc(me.getId()).stream()
        .map(this::claimView).collect(Collectors.toList());
  }

  @PutMapping("/warranty/{id}")
  public ResponseEntity<?> updateWarranty(@PathVariable UUID id, @RequestBody Map<String, String> body) {
    User me = userService.findAuthUser();
    WarrantyClaim c = warrantyRepository.findById(id).orElse(null);
    if (c == null) return bad("Garantia não encontrada.");
    boolean admin = me.getRole() != null && me.getRole().contains(Role.ADMIN);
    boolean pro = c.getProfessionalId() != null && c.getProfessionalId().equals(me.getId());
    boolean customer = c.getCustomerId().equals(me.getId());
    String status = Objects.toString(body.get("status"), "").toUpperCase();
    if ("SCHEDULED".equals(status) && (pro || admin)) {
      c.setScheduledFor(Objects.toString(body.get("scheduledFor"), "").trim());
    } else if ("RESOLVED".equals(status) && (customer || admin)) {
      // só o cliente (ou o admin) confirma que o problema foi resolvido
    } else {
      return bad("Ação não permitida.");
    }
    String note = Objects.toString(body.get("note"), "").trim();
    if (!note.isEmpty() && (pro || admin)) c.setProfessionalNote(note);
    c.setStatus(status);
    c.setUpdatedAt(LocalDateTime.now());
    warrantyRepository.save(c);
    return ResponseEntity.ok(claimView(c));
  }

  private Map<String, Object> claimView(WarrantyClaim c) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", c.getId());
    m.put("orderId", c.getOrderId());
    m.put("status", c.getStatus());
    m.put("reason", c.getReason());
    m.put("professionalNote", c.getProfessionalNote());
    m.put("scheduledFor", c.getScheduledFor());
    m.put("createdAt", c.getCreatedAt());
    m.put("updatedAt", c.getUpdatedAt());
    userRepository.findById(c.getCustomerId()).ifPresent(u -> m.put("customerName", u.getName()));
    return m;
  }

  private LocalDateTime warrantyDeadline(Order order) {
    LocalDateTime base = order.getDoneAt();
    if (base == null && order.getDate() != null) base = order.getDate().atStartOfDay();
    if (base == null) base = order.getCreatedAt();
    return base == null ? null : base.plusDays(WARRANTY_DAYS);
  }

  private static boolean isParty(Order o, User me) {
    return (o.getCustomer() != null && o.getCustomer().getId().equals(me.getId()))
        || (o.getProfessional() != null && o.getProfessional().getId().equals(me.getId()));
  }

  // ═════════════════════════════ utilitários ═════════════════════════════

  private static Double toDouble(Object o) {
    if (o == null) return null;
    try { return Double.parseDouble(o.toString()); } catch (Exception e) { return null; }
  }

  private static String firstName(String name) {
    if (name == null || name.isBlank()) return "Cliente";
    return name.trim().split("\\s+")[0];
  }

  static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
    double r = 6371.0;
    double dLat = Math.toRadians(lat2 - lat1);
    double dLon = Math.toRadians(lon2 - lon1);
    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
        + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
    return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }

  private static ResponseEntity<Map<String, String>> bad(String msg) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", msg));
  }
}
