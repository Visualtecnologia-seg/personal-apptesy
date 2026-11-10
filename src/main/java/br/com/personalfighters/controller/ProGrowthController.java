package br.com.personalfighters.controller;

import br.com.personalfighters.entity.Order;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.repository.OrderRepository;
import br.com.personalfighters.service.AreaService;
import br.com.personalfighters.service.AsaasService;
import br.com.personalfighters.service.NotificationService;
import br.com.personalfighters.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

import static br.com.personalfighters.controller.SocialController.bad;

/** Ferramentas de crescimento do profissional (Entrega 4 · parte 4). */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ProGrowthController {

  private static final ZoneId Z = ZoneId.of("America/Sao_Paulo");
  private static final Map<Integer, BigDecimal> BOOST = Map.of(7, new BigDecimal("19.90"), 30, new BigDecimal("59.90"));
  private final UserService userService;
  private final OrderRepository orderRepository;
  private final AsaasService asaasService;
  private final NotificationService notificationService;
  private final JdbcTemplate jdbc;
  private final br.com.personalfighters.service.SecurityService securityService;
  private final br.com.personalfighters.service.EmailAutomationService emailAutomation;

  private static Timestamp now() { return Timestamp.valueOf(LocalDateTime.now(Z)); }

  // ───────────── Recusar com motivo ─────────────
  @PostMapping("/orders/{id}/refuse-reason")
  public Map<String, Object> refuseReason(@PathVariable UUID id, @RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    String r = Objects.toString(b.get("reason"), "").trim();
    if (!r.isEmpty()) jdbc.update("INSERT INTO order_refusal_ (order_id, professional_id, reason, created_at) VALUES (?,?,?,?)", id, me.getId(), r.length() > 80 ? r.substring(0, 80) : r, now());
    return Map.of("ok", true);
  }

  // ───────────── Meus clientes (CRM), aniversário, "faz tempo", bloquear ─────────────
  @GetMapping("/pro/clients")
  public List<Map<String, Object>> clients() {
    User me = userService.findAuthUser();
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT u.id, u.name, u.birthday, u.avatar_url AS \"avatar\", COUNT(o.id) AS \"services\", MAX(o.date) AS \"lastDate\", " +
            "COALESCE(SUM(o.service_value),0) AS \"total\", " +
            "(SELECT o2.id FROM order_ o2 WHERE o2.customer_id = u.id AND o2.professional_id = ? ORDER BY o2.created_at DESC NULLS LAST LIMIT 1) AS \"lastOrderId\", " +
            "EXISTS(SELECT 1 FROM pro_blocked_client_ b WHERE b.professional_id = ? AND b.client_id = u.id) AS \"blocked\" " +
            "FROM order_ o JOIN user_ u ON u.id = o.customer_id WHERE o.professional_id = ? AND o.status IN ('DONE','REVIEWED','CONFIRMED','IN_PROGRESS','PENDING_CLIENT_CONFIRMATION','AWAITING_PAYMENT') " +
            "GROUP BY u.id, u.name, u.birthday, u.avatar_url ORDER BY MAX(o.date) DESC NULLS LAST LIMIT 300", me.getId(), me.getId(), me.getId());
    return rows;
  }

  @PostMapping("/pro/clients/{clientId}/block")
  public Map<String, Object> block(@PathVariable Long clientId, @RequestBody(required = false) Map<String, Object> b) {
    User me = userService.findAuthUser();
    jdbc.update("INSERT INTO pro_blocked_client_ (professional_id, client_id, reason, created_at) VALUES (?,?,?,?) ON CONFLICT DO NOTHING",
        me.getId(), clientId, b == null ? null : Objects.toString(b.get("reason"), null), now());
    return Map.of("blocked", true);
  }

  @DeleteMapping("/pro/clients/{clientId}/block")
  public Map<String, Object> unblock(@PathVariable Long clientId) {
    User me = userService.findAuthUser();
    jdbc.update("DELETE FROM pro_blocked_client_ WHERE professional_id = ? AND client_id = ?", me.getId(), clientId);
    return Map.of("blocked", false);
  }

  // ───────────── Avaliações com resposta ─────────────
  @GetMapping("/pro/reviews")
  public List<Map<String, Object>> reviews() {
    User me = userService.findAuthUser();
    return jdbc.queryForList(
        "SELECT r.id, r.rating, r.comment, r.date, u.name AS \"clientName\", rr.text AS \"reply\" FROM review_ r LEFT JOIN user_ u ON u.id = r.customer_id " +
            "LEFT JOIN review_reply_ rr ON rr.review_id = r.id WHERE r.professional_id = ? ORDER BY r.date DESC NULLS LAST, r.id DESC LIMIT 200", me.getId());
  }

  @PostMapping("/pro/reviews/{reviewId}/reply")
  public ResponseEntity<?> reply(@PathVariable Long reviewId, @RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    String t = Objects.toString(b.get("text"), "").trim();
    if (t.length() < 2) return bad("Escreva a resposta.");
    Long mine = jdbc.queryForObject("SELECT COUNT(*) FROM review_ WHERE id = ? AND professional_id = ?", Long.class, reviewId, me.getId());
    if (mine == null || mine == 0) return bad("Avaliação não encontrada.");
    jdbc.update("INSERT INTO review_reply_ (review_id, professional_id, text, created_at) VALUES (?,?,?,?) ON CONFLICT (review_id) DO UPDATE SET text = EXCLUDED.text, created_at = EXCLUDED.created_at",
        reviewId, me.getId(), t.length() > 600 ? t.substring(0, 600) : t, now());
    return ResponseEntity.ok(Map.of("ok", true));
  }

  @GetMapping("/professionals/{id}/review-replies")
  public Map<String, Object> replies(@PathVariable Long id) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map<String, Object> r : jdbc.queryForList("SELECT review_id, text FROM review_reply_ WHERE professional_id = ?", id)) out.put(String.valueOf(r.get("review_id")), r.get("text"));
    return out;
  }

  // ───────────── Estatísticas, ranking e demanda ─────────────
  @GetMapping("/pro/stats")
  public Map<String, Object> stats() {
    User me = userService.findAuthUser();
    Long id = me.getId();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("months", jdbc.queryForList(
        "SELECT to_char(date_trunc('month', done_at), 'YYYY-MM') AS month, COUNT(*) AS services, COALESCE(SUM(service_value),0) AS earned " +
            "FROM order_ WHERE professional_id = ? AND status IN ('DONE','REVIEWED') AND done_at >= ? GROUP BY 1 ORDER BY 1",
        id, Timestamp.valueOf(LocalDate.now(Z).withDayOfMonth(1).minusMonths(5).atStartOfDay())));
    out.put("rating", jdbc.queryForObject("SELECT ROUND(AVG(rating)::numeric, 1) FROM review_ WHERE professional_id = ?", BigDecimal.class, id));
    out.put("reviews", n("SELECT COUNT(*) FROM review_ WHERE professional_id = ?", id));
    long accepted = n("SELECT COUNT(*) FROM order_ WHERE professional_id = ? AND status NOT IN ('OPEN','BLOCKED')", id);
    long refused = n("SELECT COUNT(*) FROM order_refusal_ WHERE professional_id = ?", id);
    out.put("acceptRate", accepted + refused == 0 ? null : Math.round(100.0 * accepted / (accepted + refused)));
    long clients = n("SELECT COUNT(DISTINCT customer_id) FROM order_ WHERE professional_id = ? AND status IN ('DONE','REVIEWED')", id);
    long repeat = n("SELECT COUNT(*) FROM (SELECT customer_id FROM order_ WHERE professional_id = ? AND status IN ('DONE','REVIEWED') GROUP BY customer_id HAVING COUNT(*) > 1) x", id);
    out.put("clients", clients);
    out.put("repeatRate", clients == 0 ? null : Math.round(100.0 * repeat / clients));
    out.put("refusalReasons", jdbc.queryForList("SELECT reason, COUNT(*) AS n FROM order_refusal_ WHERE professional_id = ? GROUP BY reason ORDER BY n DESC LIMIT 5", id));
    return out;
  }

  @GetMapping("/pro/ranking")
  public Map<String, Object> ranking() {
    User me = userService.findAuthUser();
    Map<String, Object> out = new LinkedHashMap<>();
    Timestamp month = Timestamp.valueOf(LocalDate.now(Z).withDayOfMonth(1).atStartOfDay());
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT u.id, u.name, u.latitude AS lat, u.longitude AS lng, COUNT(o.id) AS services, " +
            "(SELECT ROUND(AVG(r.rating)::numeric,1) FROM review_ r WHERE r.professional_id = u.id) AS rating " +
            "FROM order_ o JOIN user_ u ON u.id = o.professional_id WHERE o.status IN ('DONE','REVIEWED') AND o.done_at >= ? GROUP BY u.id, u.name, u.latitude, u.longitude ORDER BY services DESC LIMIT 500", month);
    List<Map<String, Object>> near = new ArrayList<>();
    for (Map<String, Object> r : rows) {
      boolean ok = me.getLatitude() == null || !(r.get("lat") instanceof Number)
          || AreaService.km(me.getLatitude(), me.getLongitude(), ((Number) r.get("lat")).doubleValue(), ((Number) r.get("lng")).doubleValue()) <= 30;
      if (ok) { r.remove("lat"); r.remove("lng"); r.put("name", SocialController.firstName(Objects.toString(r.get("name"), ""))); near.add(r); }
    }
    int pos = 0;
    for (int i = 0; i < near.size(); i++) if (Objects.equals(((Number) near.get(i).get("id")).longValue(), me.getId())) pos = i + 1;
    out.put("top", near.subList(0, Math.min(10, near.size())));
    out.put("position", pos == 0 ? null : pos);
    out.put("prize", "O 1º lugar do mês ganha 7 dias de destaque grátis");
    return out;
  }

  @GetMapping("/pro/demand")
  public Map<String, Object> demand() {
    User me = userService.findAuthUser();
    Timestamp since = Timestamp.valueOf(LocalDateTime.now(Z).minusDays(60));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("neighborhoods", jdbc.queryForList(
        "SELECT COALESCE(NULLIF(a.neighborhood,''), a.city, 'Sem bairro') AS name, COUNT(*) AS n FROM order_ o JOIN address_ a ON a.id = o.address_id " +
            "WHERE o.created_at >= ? AND o.product_id IN (SELECT DISTINCT product_id FROM order_ WHERE professional_id = ?) GROUP BY 1 ORDER BY n DESC LIMIT 8", since, me.getId()));
    out.put("hours", jdbc.queryForList(
        "SELECT EXTRACT(ISODOW FROM o.date)::int AS weekday, EXTRACT(HOUR FROM o.start_time)::int AS hour, COUNT(*) AS n FROM order_ o " +
            "WHERE o.created_at >= ? AND o.date IS NOT NULL AND o.start_time IS NOT NULL AND o.product_id IN (SELECT DISTINCT product_id FROM order_ WHERE professional_id = ?) " +
            "GROUP BY 1, 2 ORDER BY n DESC LIMIT 6", since, me.getId()));
    return out;
  }

  // ───────────── Certificados (aprovados no painel) ─────────────
  @GetMapping("/pro/certificates")
  public List<Map<String, Object>> myCerts() {
    User me = userService.findAuthUser();
    return jdbc.queryForList("SELECT id, title, number, status, note, created_at AS \"createdAt\" FROM pro_certificate_ WHERE professional_id = ? ORDER BY created_at DESC", me.getId());
  }

  @PostMapping("/pro/certificates")
  public ResponseEntity<?> addCert(@RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    String title = Objects.toString(b.get("title"), "").trim();
    if (title.length() < 2) return bad("Diga qual é o certificado (ex.: CREF).");
    byte[] data = null; String type = null;
    String raw = Objects.toString(b.get("photo"), "");
    if (!raw.isEmpty()) {
      int c = raw.indexOf(',');
      if (raw.startsWith("data:") && c > 0) { type = raw.substring(5, raw.indexOf(';') > 0 ? raw.indexOf(';') : c); raw = raw.substring(c + 1); }
      try { data = Base64.getMimeDecoder().decode(raw); } catch (Exception e) { return bad("Foto inválida."); }
      if (data.length > 5 * 1024 * 1024) return bad("Foto grande demais.");
    }
    jdbc.update("INSERT INTO pro_certificate_ (id, professional_id, title, number, photo, photo_type, status, created_at) VALUES (?,?,?,?,?,?,'PENDING',?)",
        UUID.randomUUID(), me.getId(), title.length() > 140 ? title.substring(0, 140) : title, Objects.toString(b.get("number"), null), data, br.com.personalfighters.util.MediaTypes.imageOr(type, "image/jpeg"), now());
    return ResponseEntity.ok(myCerts());
  }

  @GetMapping("/professionals/{id}/seals")
  public Map<String, Object> seals(@PathVariable Long id) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("certificates", jdbc.queryForList("SELECT title, number FROM pro_certificate_ WHERE professional_id = ? AND status = 'APPROVED'", id));
    out.put("courses", jdbc.queryForList("SELECT course_key FROM pro_course_ WHERE professional_id = ?", String.class, id));
    long done = n("SELECT COUNT(*) FROM order_ WHERE professional_id = ? AND status IN ('DONE','REVIEWED')", id);
    out.put("level", done >= 100 ? "Ouro" : done >= 30 ? "Prata" : "Bronze");
    out.put("boosted", n("SELECT COUNT(*) FROM pro_boost_ WHERE professional_id = ? AND status = 'ACTIVE' AND ends_at > ?", id, now()) > 0);
    return out;
  }

  @GetMapping("/admin/certificates")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  public List<Map<String, Object>> adminCerts(@RequestParam(defaultValue = "PENDING") String status) {
    return jdbc.queryForList("SELECT c.id, c.title, c.number, c.status, c.note, c.created_at AS \"createdAt\", u.name AS \"proName\", u.id AS \"proId\", (c.photo IS NOT NULL) AS \"hasPhoto\" " +
        "FROM pro_certificate_ c JOIN user_ u ON u.id = c.professional_id WHERE (? = 'ALL' OR c.status = ?) ORDER BY c.created_at DESC LIMIT 200", status, status);
  }

  @GetMapping("/admin/certificates/{id}/photo")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  public ResponseEntity<byte[]> certPhoto(@PathVariable UUID id) {
    User viewer = userService.findAuthUser();
    if (!securityService.canViewDocs(viewer.getId())) return ResponseEntity.status(403).build();
    List<Map<String, Object>> r = jdbc.queryForList("SELECT photo, photo_type, professional_id FROM pro_certificate_ WHERE id = ?", id);
    if (!r.isEmpty()) securityService.logDocAccess(viewer, r.get(0).get("professional_id") == null ? null : ((Number) r.get(0).get("professional_id")).longValue(), "Foto de certificado");
    if (r.isEmpty() || r.get(0).get("photo") == null) return ResponseEntity.notFound().build();
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(Objects.toString(r.get(0).get("photo_type"), "image/jpeg"))).body((byte[]) r.get(0).get("photo"));
  }

  @PostMapping("/admin/certificates/{id}/review")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  public Map<String, Object> reviewCert(@PathVariable UUID id, @RequestBody Map<String, Object> b) {
    boolean ok = Boolean.TRUE.equals(b.get("approve"));
    jdbc.update("UPDATE pro_certificate_ SET status = ?, note = ?, reviewed_at = ? WHERE id = ?", ok ? "APPROVED" : "REJECTED", Objects.toString(b.get("note"), null), now(), id);
    try {
      Long pro = jdbc.queryForObject("SELECT professional_id FROM pro_certificate_ WHERE id = ?", Long.class, id);
      User u = userService.findById(pro);
      notificationService.sendToUser(u, true, ok ? "Seu certificado foi aprovado e já aparece no seu perfil." : "Seu certificado não foi aprovado. Veja o motivo no app.");
    } catch (Exception ignored) { }
    return Map.of("ok", true);
  }

  // ───────────── Destaque pago ─────────────
  @GetMapping("/pro/boost")
  public Map<String, Object> boost() {
    User me = userService.findAuthUser();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("active", jdbc.queryForList("SELECT id, days, ends_at AS \"endsAt\" FROM pro_boost_ WHERE professional_id = ? AND status = 'ACTIVE' AND ends_at > ? ORDER BY ends_at DESC LIMIT 1", me.getId(), now()));
    out.put("pending", jdbc.queryForList("SELECT id, days, price, pix_copy_paste AS \"pixCopyPaste\", pix_qr AS \"pixQrCode\" FROM pro_boost_ WHERE professional_id = ? AND status = 'PENDING' ORDER BY created_at DESC LIMIT 1", me.getId()));
    out.put("options", List.of(Map.of("days", 7, "price", BOOST.get(7)), Map.of("days", 30, "price", BOOST.get(30))));
    out.put("testMode", asaasService.isTestMode());
    return out;
  }

  @PostMapping("/pro/boost")
  public ResponseEntity<?> buyBoost(@RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    int days = AreaService.bd(b.get("days"), "7").intValue();
    BigDecimal price = BOOST.get(days);
    if (price == null) return bad("Escolha 7 ou 30 dias.");
    Map<String, String> c = asaasService.createExtraCharge(me, price, "Destaque OPUS " + days + " dias");
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO pro_boost_ (id, professional_id, days, price, status, pix_payment_id, pix_copy_paste, pix_qr, created_at) VALUES (?,?,?,?, 'PENDING', ?,?,?,?)",
        id, me.getId(), days, price, c.get("id"), c.get("payload"), c.get("encodedImage"), now());
    return ResponseEntity.ok(boost());
  }

  @PostMapping("/pro/boost/{id}/simulate")
  public ResponseEntity<?> simulateBoost(@PathVariable UUID id) {
    if (!asaasService.isTestMode()) return bad("Só no modo de teste.");
    User me = userService.findAuthUser();
    activateBoost(id, me.getId());
    return ResponseEntity.ok(boost());
  }

  /** Chamado pelo webhook do Asaas. */
  public boolean markBoostPaidByPixId(String pixId) {
    List<Map<String, Object>> r = jdbc.queryForList("SELECT id, professional_id FROM pro_boost_ WHERE pix_payment_id = ? AND status = 'PENDING'", pixId);
    if (r.isEmpty()) return false;
    activateBoost((UUID) r.get(0).get("id"), ((Number) r.get(0).get("professional_id")).longValue());
    return true;
  }

  private void activateBoost(UUID id, Long pro) {
    List<Map<String, Object>> r = jdbc.queryForList("SELECT days FROM pro_boost_ WHERE id = ? AND professional_id = ? AND status = 'PENDING'", id, pro);
    if (r.isEmpty()) return;
    int days = ((Number) r.get(0).get("days")).intValue();
    Timestamp from = now();
    List<Timestamp> act = jdbc.queryForList("SELECT ends_at FROM pro_boost_ WHERE professional_id = ? AND status = 'ACTIVE' AND ends_at > ? ORDER BY ends_at DESC LIMIT 1", Timestamp.class, pro, from);
    if (!act.isEmpty()) from = act.get(0);
    jdbc.update("UPDATE pro_boost_ SET status = 'ACTIVE', starts_at = ?, ends_at = ? WHERE id = ?", from, Timestamp.valueOf(from.toLocalDateTime().plusDays(days)), id);
  }

  public List<Long> boostedIds() {
    return jdbc.queryForList("SELECT DISTINCT professional_id FROM pro_boost_ WHERE status = 'ACTIVE' AND ends_at > ?", Long.class, now());
  }

  // ───────────── Despesas ─────────────
  @GetMapping("/pro/expenses")
  public Map<String, Object> expenses() {
    User me = userService.findAuthUser();
    LocalDate m = LocalDate.now(Z).withDayOfMonth(1);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("items", jdbc.queryForList("SELECT id, description, amount, spent_on AS \"date\" FROM pro_expense_ WHERE professional_id = ? AND spent_on >= ? ORDER BY spent_on DESC", me.getId(), Date.valueOf(m)));
    out.put("total", jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM pro_expense_ WHERE professional_id = ? AND spent_on >= ?", BigDecimal.class, me.getId(), Date.valueOf(m)));
    return out;
  }

  @PostMapping("/pro/expenses")
  public ResponseEntity<?> addExpense(@RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    String d = Objects.toString(b.get("description"), "").trim();
    BigDecimal v = AreaService.bd(b.get("amount"), "0");
    if (d.isEmpty() || v.signum() <= 0) return bad("Diga o que foi e o valor.");
    jdbc.update("INSERT INTO pro_expense_ (id, professional_id, description, amount, spent_on, created_at) VALUES (?,?,?,?,?,?)",
        UUID.randomUUID(), me.getId(), d.length() > 140 ? d.substring(0, 140) : d, v, Date.valueOf(LocalDate.now(Z)), now());
    return ResponseEntity.ok(expenses());
  }

  @DeleteMapping("/pro/expenses/{id}")
  public Map<String, Object> delExpense(@PathVariable UUID id) {
    User me = userService.findAuthUser();
    jdbc.update("DELETE FROM pro_expense_ WHERE id = ? AND professional_id = ?", id, me.getId());
    return expenses();
  }

  // ───────────── Cursos rápidos ─────────────
  @GetMapping("/pro/courses")
  public List<String> courses() {
    User me = userService.findAuthUser();
    return jdbc.queryForList("SELECT course_key FROM pro_course_ WHERE professional_id = ?", String.class, me.getId());
  }

  @PostMapping("/pro/courses/{key}")
  public List<String> doneCourse(@PathVariable String key) {
    User me = userService.findAuthUser();
    if (key.matches("[a-z0-9-]{2,40}")) jdbc.update("INSERT INTO pro_course_ (professional_id, course_key, done_at) VALUES (?,?,?) ON CONFLICT DO NOTHING", me.getId(), key, now());
    return courses();
  }

  // ───────────── Ficha de treino e evolução do aluno ─────────────
  @GetMapping("/workout-plans")
  public List<Map<String, Object>> plans(@RequestParam(required = false) Long clientId) {
    User me = userService.findAuthUser();
    if (clientId != null) return jdbc.queryForList("SELECT id, title, items, updated_at AS \"updatedAt\" FROM workout_plan_ WHERE professional_id = ? AND client_id = ? ORDER BY updated_at DESC", me.getId(), clientId);
    return jdbc.queryForList("SELECT w.id, w.title, w.items, w.updated_at AS \"updatedAt\", u.name AS \"proName\" FROM workout_plan_ w JOIN user_ u ON u.id = w.professional_id WHERE w.client_id = ? ORDER BY w.updated_at DESC", me.getId());
  }

  @PutMapping("/workout-plans")
  public ResponseEntity<?> savePlan(@RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    Long client = AreaService.bd(b.get("clientId"), "0").longValue();
    String title = Objects.toString(b.get("title"), "Treino").trim();
    String items = Objects.toString(b.get("items"), "").trim();
    if (client == 0 || items.isEmpty()) return bad("Escreva os exercícios.");
    Long ok = jdbc.queryForObject("SELECT COUNT(*) FROM order_ WHERE professional_id = ? AND customer_id = ?", Long.class, me.getId(), client);
    if (ok == null || ok == 0) return bad("Só para alunos que já contrataram você.");
    UUID id = b.get("id") == null ? UUID.randomUUID() : UUID.fromString(b.get("id").toString());
    jdbc.update("DELETE FROM workout_plan_ WHERE id = ? AND professional_id = ?", id, me.getId());
    jdbc.update("INSERT INTO workout_plan_ (id, professional_id, client_id, title, items, updated_at) VALUES (?,?,?,?,?,?)", id, me.getId(), client, title, items, now());
    try { notificationService.sendToUser(userService.findById(client), false, me.getName().split(" ")[0] + " atualizou a sua ficha de treino."); } catch (Exception ignored) { }
    return ResponseEntity.ok(plans(client));
  }

  @GetMapping("/measures")
  public List<Map<String, Object>> measures(@RequestParam(required = false) Long clientId) {
    User me = userService.findAuthUser();
    Long c = clientId == null ? me.getId() : clientId;
    if (clientId != null) {
      Long ok = jdbc.queryForObject("SELECT COUNT(*) FROM order_ WHERE professional_id = ? AND customer_id = ?", Long.class, me.getId(), clientId);
      if (ok == null || ok == 0) return List.of();
    }
    return jdbc.queryForList("SELECT id, weight, waist, body_fat AS \"bodyFat\", note, measured_on AS \"date\" FROM student_measure_ WHERE client_id = ? ORDER BY measured_on", c);
  }

  @PostMapping("/measures")
  public ResponseEntity<?> addMeasure(@RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    Long client = AreaService.bd(b.get("clientId"), "0").longValue();
    Long ok = jdbc.queryForObject("SELECT COUNT(*) FROM order_ WHERE professional_id = ? AND customer_id = ?", Long.class, me.getId(), client);
    if (ok == null || ok == 0) return bad("Só para alunos que já contrataram você.");
    jdbc.update("INSERT INTO student_measure_ (id, professional_id, client_id, weight, waist, body_fat, note, measured_on, created_at) VALUES (?,?,?,?,?,?,?,?,?)",
        UUID.randomUUID(), me.getId(), client, dec(b.get("weight")), dec(b.get("waist")), dec(b.get("bodyFat")), Objects.toString(b.get("note"), null), Date.valueOf(LocalDate.now(Z)), now());
    return ResponseEntity.ok(measures(client));
  }

  // ───────────── Cartão de visita (público) ─────────────
  @GetMapping("/public/pro/{id}")
  public ResponseEntity<?> publicPro(@PathVariable Long id) {
    List<Map<String, Object>> r = jdbc.queryForList("SELECT u.id, u.name, u.avatar_url AS \"avatar\" FROM user_ u WHERE u.id = ? AND u.professional_id IS NOT NULL", id);
    if (r.isEmpty()) return ResponseEntity.notFound().build();
    Map<String, Object> out = r.get(0);
    out.put("rating", jdbc.queryForObject("SELECT ROUND(AVG(rating)::numeric,1) FROM review_ WHERE professional_id = ?", BigDecimal.class, id));
    out.put("done", n("SELECT COUNT(*) FROM order_ WHERE professional_id = ? AND status IN ('DONE','REVIEWED')", id));
    out.put("seals", seals(id));
    return ResponseEntity.ok(out);
  }

  // ───────────── Rotinas ─────────────
  /** Segunda de manhã: resumo da semana para cada profissional ativo. */
  @Scheduled(cron = "0 0 8 * * MON", zone = "America/Sao_Paulo")
  public void weeklySummary() {
    LocalDate monday = LocalDate.now(Z).with(DayOfWeek.MONDAY);
    Timestamp from = Timestamp.valueOf(monday.minusDays(7).atStartOfDay()), to = Timestamp.valueOf(monday.atStartOfDay());
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT professional_id AS id, COUNT(*) AS n, COALESCE(SUM(service_value),0) AS v FROM order_ WHERE status IN ('DONE','REVIEWED') AND done_at >= ? AND done_at < ? GROUP BY professional_id", from, to);
    for (Map<String, Object> r : rows) {
      Long id = ((Number) r.get("id")).longValue();
      try {
        int ins = jdbc.update("INSERT INTO weekly_summary_sent_ (professional_id, week_start) VALUES (?, ?) ON CONFLICT DO NOTHING", id, Date.valueOf(monday));
        if (ins == 0) continue;
        String v = String.format(new Locale("pt", "BR"), "R$ %,.2f", ((BigDecimal) r.get("v")).doubleValue());
        notificationService.sendToUser(userService.findById(id), true, "Sua semana: " + r.get("n") + " serviço(s) e " + v + " ganhos. Abra o app e veja onde estão pedindo mais.");
        emailAutomation.weeklyPro(id, monday, ((Number) r.get("n")).longValue(), (BigDecimal) r.get("v"));
      } catch (Exception e) { log.warn("Resumo semanal {}: {}", id, e.getMessage()); }
    }
  }

  /** Assinatura mensal: 30 dias depois do pagamento, avisa o aluno para renovar. */
  @Scheduled(cron = "0 30 9 * * *", zone = "America/Sao_Paulo")
  public void monthlyRenewals() {
    try {
      List<Map<String, Object>> rows = jdbc.queryForList(
          "SELECT p.id, p.user_id, k.title FROM package_purchase_ p JOIN lesson_package_ k ON k.id = p.package_id " +
              "WHERE COALESCE(k.monthly,false) = true AND p.status = 'PAID' AND COALESCE(p.renew_notified,false) = false AND p.created_at < ?",
          Timestamp.valueOf(LocalDateTime.now(Z).minusDays(28)));
      for (Map<String, Object> r : rows) {
        jdbc.update("UPDATE package_purchase_ SET renew_notified = true WHERE id = ?", r.get("id"));
        notificationService.sendToUser(userService.findById(((Number) r.get("user_id")).longValue()), false,
            "Sua mensalidade \"" + r.get("title") + "\" vence em breve. Renove pelo perfil do profissional para continuar as aulas.");
      }
    } catch (Exception e) { log.warn("Renovações: {}", e.getMessage()); }
  }

  private long n(String sql, Object... a) { Long v = jdbc.queryForObject(sql, Long.class, a); return v == null ? 0 : v; }
  private static BigDecimal dec(Object o) { try { return o == null || o.toString().isBlank() ? null : new BigDecimal(o.toString().replace(',', '.')); } catch (Exception e) { return null; } }
}
