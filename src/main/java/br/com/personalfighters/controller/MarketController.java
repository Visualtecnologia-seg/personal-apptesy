package br.com.personalfighters.controller;

import br.com.personalfighters.entity.Order;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.repository.OrderRepository;
import br.com.personalfighters.service.NotificationService;
import br.com.personalfighters.service.UserService;
import br.com.personalfighters.util.Pricing;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

import static br.com.personalfighters.controller.SocialController.bad;
import static br.com.personalfighters.controller.SocialController.firstName;

/** Materiais no orçamento, cobranças avulsas (visita técnica, etapas), lojas parceiras e seguros (vitrine). */
@RestController
@RequiredArgsConstructor
public class MarketController {

  private final UserService userService;
  private final OrderRepository orderRepository;
  private final NotificationService notificationService;
  private final JdbcTemplate jdbc;

  private static Timestamp now() { return Timestamp.valueOf(LocalDateTime.now()); }

  // ───────────── Materiais ─────────────

  @GetMapping("/orders/{id}/materials")
  public ResponseEntity<?> materials(@PathVariable UUID id) {
    User me = userService.findAuthUser();
    Order o = orderRepository.findById(id).orElse(null);
    if (o == null || !(isPro(o, me) || isCustomer(o, me))) return bad("Pedido não encontrado.");
    return ResponseEntity.ok(state(id));
  }

  /** Profissional manda/atualiza a lista de materiais (antes do cliente aceitar). */
  @PutMapping("/orders/{id}/materials")
  public ResponseEntity<?> saveMaterials(@PathVariable UUID id, @RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    Order o = orderRepository.findById(id).orElse(null);
    if (o == null || !isPro(o, me)) return bad("Pedido não encontrado.");
    String st = jdbc.queryForObject("SELECT materials_status FROM order_ WHERE id = ?", String.class, id);
    if ("ACCEPTED".equals(st)) return bad("O cliente já aceitou os materiais. Para mudar, combine pelo chat e mande um extra.");
    jdbc.update("DELETE FROM order_material_ WHERE order_id = ?", id);
    Object items = b.get("items");
    int pos = 0;
    if (items instanceof List) {
      for (Object it : (List<?>) items) {
        if (!(it instanceof Map) || pos >= 40) continue;
        Map<?, ?> m = (Map<?, ?>) it;
        String name = Objects.toString(m.get("name"), "").trim();
        if (name.isEmpty()) continue;
        jdbc.update("INSERT INTO order_material_ (id, order_id, name, qty, unit, price, buyer, position, created_at) VALUES (?,?,?,?,?,?,?,?,?)",
            UUID.randomUUID(), id, name.length() > 140 ? name.substring(0, 140) : name, num(m.get("qty"), "1"), str(m.get("unit"), 20),
            num(m.get("price"), "0"), "PRO", pos++, now());
      }
    }
    Object w = b.get("warrantyDays");
    if (w != null) jdbc.update("UPDATE order_ SET warranty_days = ? WHERE id = ?", num(w, "0").intValue(), id);
    jdbc.update("UPDATE order_ SET materials_status = ? WHERE id = ?", pos > 0 ? "SENT" : null, id);
    if (pos > 0) notificationService.sendToUser(o.getCustomer(), false,
        firstName(o.getProfessional()) + " mandou a lista de materiais. Escolha quem compra cada item: você, o profissional ou se você já tem.");
    return ResponseEntity.ok(state(id));
  }

  /** Cliente escolhe quem compra cada item e aceita. Os itens do profissional viram uma cobrança no cofre. */
  @PostMapping("/orders/{id}/materials/accept")
  public ResponseEntity<?> accept(@PathVariable UUID id, @RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    Order o = orderRepository.findById(id).orElse(null);
    if (o == null || !isCustomer(o, me)) return bad("Pedido não encontrado.");
    String st = jdbc.queryForObject("SELECT materials_status FROM order_ WHERE id = ?", String.class, id);
    if ("ACCEPTED".equals(st)) return ResponseEntity.ok(state(id));
    Object choices = b.get("choices");
    if (choices instanceof Map) {
      for (Map.Entry<?, ?> e : ((Map<?, ?>) choices).entrySet()) {
        String buyer = Objects.toString(e.getValue(), "PRO");
        if (!Arrays.asList("CLIENT", "PRO", "HAVE").contains(buyer)) continue;
        try { jdbc.update("UPDATE order_material_ SET buyer = ? WHERE id = ? AND order_id = ?", buyer, UUID.fromString(e.getKey().toString()), id); } catch (Exception ignored) { }
      }
    }
    BigDecimal pro = jdbc.queryForObject("SELECT COALESCE(SUM(qty * price), 0) FROM order_material_ WHERE order_id = ? AND buyer = 'PRO'", BigDecimal.class, id);
    UUID extraId = null;
    if (pro != null && pro.signum() > 0) {
      extraId = createCharge(o, "Materiais comprados pelo profissional", pro.setScale(2, RoundingMode.HALF_UP));
      jdbc.update("UPDATE order_ SET materials_extra_id = ? WHERE id = ?", extraId, id);
    }
    jdbc.update("UPDATE order_ SET materials_status = 'ACCEPTED' WHERE id = ?", id);
    notificationService.sendToUser(o.getProfessional(), true, firstName(o.getCustomer()) + " aceitou a lista de materiais.");
    return ResponseEntity.ok(state(id));
  }

  /** Foto da nota de um material comprado pelo profissional. */
  @PostMapping("/orders/{id}/materials/{itemId}/receipt")
  public ResponseEntity<?> receipt(@PathVariable UUID id, @PathVariable UUID itemId, @RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    Order o = orderRepository.findById(id).orElse(null);
    if (o == null || !isPro(o, me)) return bad("Pedido não encontrado.");
    String raw = Objects.toString(b.get("photo"), "");
    String type = "image/jpeg";
    int comma = raw.indexOf(',');
    if (raw.startsWith("data:") && comma > 0) { type = raw.substring(5, raw.indexOf(';') > 0 ? raw.indexOf(';') : comma); raw = raw.substring(comma + 1); }
    byte[] data;
    try { data = Base64.getMimeDecoder().decode(raw); } catch (Exception e) { return bad("Foto inválida."); }
    if (data.length == 0 || data.length > 4 * 1024 * 1024) return bad("Foto grande demais.");
    jdbc.update("UPDATE order_material_ SET receipt = ?, receipt_type = ? WHERE id = ? AND order_id = ?", data, type, itemId, id);
    return ResponseEntity.ok(state(id));
  }

  @GetMapping("/orders/{id}/materials/{itemId}/receipt")
  public ResponseEntity<byte[]> receiptGet(@PathVariable UUID id, @PathVariable UUID itemId) {
    User me = userService.findAuthUser();
    Order o = orderRepository.findById(id).orElse(null);
    if (o == null || !(isPro(o, me) || isCustomer(o, me))) return ResponseEntity.notFound().build();
    List<Map<String, Object>> r = jdbc.queryForList("SELECT receipt, receipt_type FROM order_material_ WHERE id = ? AND order_id = ?", itemId, id);
    if (r.isEmpty() || r.get(0).get("receipt") == null) return ResponseEntity.notFound().build();
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(Objects.toString(r.get(0).get("receipt_type"), "image/jpeg"))).body((byte[]) r.get(0).get("receipt"));
  }

  /** Cobrança avulsa pelo cofre: visita técnica (descontada se fechar), etapas de obra, etc. */
  @PostMapping("/orders/{id}/charges")
  public ResponseEntity<?> charge(@PathVariable UUID id, @RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    Order o = orderRepository.findById(id).orElse(null);
    if (o == null || !isPro(o, me)) return bad("Pedido não encontrado.");
    String desc = str(b.get("description"), 140);
    BigDecimal v = num(b.get("value"), "0");
    if (desc == null || v.compareTo(BigDecimal.ONE) < 0) return bad("Diga o que é e o valor (mínimo R$ 1).");
    UUID extra = createCharge(o, desc, v.setScale(2, RoundingMode.HALF_UP));
    notificationService.sendToUser(o.getCustomer(), false, firstName(o.getProfessional()) + " enviou uma cobrança: " + desc + ". O valor fica guardado no cofre até você aprovar o serviço.");
    return ResponseEntity.ok(Map.of("extraId", extra));
  }

  private UUID createCharge(Order o, String desc, BigDecimal serviceValue) {
    UUID extraId = UUID.randomUUID();
    jdbc.update("INSERT INTO order_extra_ (id, order_id, description, service_value, client_total, status, created_at) VALUES (?, ?, ?, ?, ?, 'PENDING_PAYMENT', ?)",
        extraId, o.getId(), desc, serviceValue, Pricing.clientTotal(serviceValue), now());
    return extraId;
  }

  private Map<String, Object> state(UUID id) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("items", jdbc.queryForList(
        "SELECT id, name, qty, unit, price, buyer, (receipt IS NOT NULL) AS \"hasReceipt\" FROM order_material_ WHERE order_id = ? ORDER BY position", id));
    Map<String, Object> o = jdbc.queryForMap("SELECT materials_status, warranty_days, service_value FROM order_ WHERE id = ?", id);
    out.put("status", o.get("materials_status"));
    out.put("warrantyDays", o.get("warranty_days"));
    out.put("labor", o.get("service_value"));
    out.put("feePercent", Pricing.FEE_RATE.multiply(new BigDecimal("100")));
    return out;
  }

  // ───────────── Lojas parceiras ─────────────

  @GetMapping("/stores/partners")
  public List<Map<String, Object>> partners(@RequestParam(required = false) Double lat, @RequestParam(required = false) Double lng) {
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT id, name, category, whatsapp, address, lat, lng, coupon_code AS \"couponCode\", coupon_text AS \"couponText\", hours FROM partner_store_ WHERE active = true");
    if (lat != null && lng != null) {
      for (Map<String, Object> r : rows) {
        Object a = r.get("lat"), g = r.get("lng");
        if (a instanceof Number && g instanceof Number) r.put("km", br.com.personalfighters.service.AreaService.km(lat, lng, ((Number) a).doubleValue(), ((Number) g).doubleValue()));
      }
      rows.removeIf(r -> r.get("km") instanceof Double && (Double) r.get("km") > 30);
      rows.sort(Comparator.comparingDouble(r -> r.get("km") instanceof Double ? (Double) r.get("km") : 999));
    }
    return rows;
  }

  // ───────────── Seguros (vitrine) ─────────────

  @GetMapping("/insurance/plans")
  public Map<String, Object> plans(@RequestParam(defaultValue = "PRO") String audience) {
    User me = userService.findAuthUser();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("plans", jdbc.queryForList(
        "SELECT p.id, p.title, p.kind, p.partner, p.coverage, p.price_text AS \"price\", p.link, " +
            "(SELECT COUNT(*) FROM insurance_interest_ i WHERE i.plan_key = p.id::text) AS \"waiting\" " +
            "FROM insurance_plan_ p WHERE active = true AND audience = ? ORDER BY position, created_at", audience.toUpperCase(Locale.ROOT)));
    out.put("mine", jdbc.queryForList("SELECT plan_key FROM insurance_interest_ WHERE user_id = ?", String.class, me.getId()));
    Map<String, Object> counts = new LinkedHashMap<>();
    for (Map<String, Object> r : jdbc.queryForList("SELECT plan_key, COUNT(*) AS n FROM insurance_interest_ GROUP BY plan_key")) counts.put((String) r.get("plan_key"), r.get("n"));
    out.put("waiting", counts);
    return out;
  }

  @PostMapping("/insurance/interest")
  public Map<String, Object> interest(@RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    String key = str(b.get("plan"), 80);
    if (key != null) jdbc.update("INSERT INTO insurance_interest_ (user_id, plan_key, created_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING", me.getId(), key, now());
    return Map.of("ok", true);
  }

  /** "Seja parceiro OPUS": loja ou seguradora deixa o contato. */
  @PostMapping("/partners/lead")
  public ResponseEntity<?> lead(@RequestBody Map<String, Object> b) {
    String company = str(b.get("company"), 140);
    if (company == null) return bad("Diga o nome da empresa.");
    String kind = "INSURER".equals(b.get("kind")) ? "INSURER" : "STORE";
    jdbc.update("INSERT INTO partner_lead_ (id, kind, company, contact, phone, email, message, created_at) VALUES (?,?,?,?,?,?,?,?)",
        UUID.randomUUID(), kind, company, str(b.get("contact"), 140), str(b.get("phone"), 40), str(b.get("email"), 140), str(b.get("message"), 2000), now());
    return ResponseEntity.ok(Map.of("ok", true));
  }

  // ───────────── Painel ─────────────

  @GetMapping("/admin/partners")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  public Map<String, Object> adminAll() {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("stores", jdbc.queryForList("SELECT id, name, category, whatsapp, address, lat, lng, coupon_code AS \"couponCode\", coupon_text AS \"couponText\", hours, active FROM partner_store_ ORDER BY created_at DESC"));
    out.put("plans", jdbc.queryForList("SELECT id, audience, title, kind, partner, coverage, price_text AS \"price\", link, active, position FROM insurance_plan_ ORDER BY audience, position, created_at"));
    out.put("interest", jdbc.queryForList("SELECT plan_key AS \"plan\", COUNT(*) AS n FROM insurance_interest_ GROUP BY plan_key ORDER BY n DESC"));
    out.put("leads", jdbc.queryForList("SELECT id, kind, company, contact, phone, email, message, created_at AS \"createdAt\" FROM partner_lead_ ORDER BY created_at DESC LIMIT 200"));
    return out;
  }

  @PostMapping("/admin/partners/stores")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  public ResponseEntity<?> saveStore(@RequestBody Map<String, Object> b) {
    String name = str(b.get("name"), 140);
    if (name == null) return bad("Nome da loja.");
    UUID id = b.get("id") == null ? UUID.randomUUID() : UUID.fromString(b.get("id").toString());
    jdbc.update("DELETE FROM partner_store_ WHERE id = ?", id);
    jdbc.update("INSERT INTO partner_store_ (id, name, category, whatsapp, address, lat, lng, coupon_code, coupon_text, hours, active, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
        id, name, str(b.get("category"), 80), digits(b.get("whatsapp")), str(b.get("address"), 255), dbl(b.get("lat")), dbl(b.get("lng")),
        str(b.get("couponCode"), 30), str(b.get("couponText"), 140), str(b.get("hours"), 80), !Boolean.FALSE.equals(b.get("active")), now());
    return ResponseEntity.ok(adminAll());
  }

  @DeleteMapping("/admin/partners/stores/{id}")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  public Map<String, Object> delStore(@PathVariable UUID id) {
    jdbc.update("DELETE FROM partner_store_ WHERE id = ?", id);
    return adminAll();
  }

  @PostMapping("/admin/partners/plans")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  public ResponseEntity<?> savePlan(@RequestBody Map<String, Object> b) {
    String title = str(b.get("title"), 120);
    if (title == null) return bad("Nome do plano.");
    UUID id = b.get("id") == null ? UUID.randomUUID() : UUID.fromString(b.get("id").toString());
    jdbc.update("DELETE FROM insurance_plan_ WHERE id = ?", id);
    jdbc.update("INSERT INTO insurance_plan_ (id, audience, title, kind, partner, coverage, price_text, link, active, position, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
        id, "CLIENT".equals(b.get("audience")) ? "CLIENT" : "PRO", title, str(b.get("kind"), 60), str(b.get("partner"), 120), str(b.get("coverage"), 2000),
        str(b.get("price"), 60), str(b.get("link"), 255), !Boolean.FALSE.equals(b.get("active")), num(b.get("position"), "0").intValue(), now());
    return ResponseEntity.ok(adminAll());
  }

  @DeleteMapping("/admin/partners/plans/{id}")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  public Map<String, Object> delPlan(@PathVariable UUID id) {
    jdbc.update("DELETE FROM insurance_plan_ WHERE id = ?", id);
    return adminAll();
  }

  // ───────────── utilitários ─────────────

  private static BigDecimal num(Object o, String def) {
    try { return new BigDecimal(Objects.toString(o, def).replace(',', '.').trim()); } catch (Exception e) { return new BigDecimal(def); }
  }
  private static Double dbl(Object o) {
    try { return o == null || o.toString().isBlank() ? null : Double.valueOf(o.toString().replace(',', '.')); } catch (Exception e) { return null; }
  }
  private static String str(Object o, int max) {
    if (o == null) return null;
    String s = o.toString().trim();
    if (s.isEmpty()) return null;
    return s.length() > max ? s.substring(0, max) : s;
  }
  private static String digits(Object o) {
    String d = Objects.toString(o, "").replaceAll("[^0-9]", "");
    return d.isEmpty() ? null : (d.startsWith("55") || d.length() > 11 ? d : "55" + d);
  }
  private static boolean isPro(Order o, User me) { return o.getProfessional() != null && o.getProfessional().getId().equals(me.getId()); }
  private static boolean isCustomer(Order o, User me) { return o.getCustomer() != null && o.getCustomer().getId().equals(me.getId()); }
}
