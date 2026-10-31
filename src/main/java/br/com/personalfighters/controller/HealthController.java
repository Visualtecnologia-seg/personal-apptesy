package br.com.personalfighters.controller;

import br.com.personalfighters.entity.Order;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.model.Status;
import br.com.personalfighters.repository.OrderRepository;
import br.com.personalfighters.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

import static br.com.personalfighters.controller.SocialController.bad;

/**
 * Minha saúde (Q1), dados do relógio/pulseira (Q2) e resumo do treino interpretado (Q3).
 * O profissional do pedido (ou da aula) vê a ficha e os batimentos se o aluno permitir.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class HealthController {

  private final JdbcTemplate jdbc;
  private final UserService userService;
  private final OrderRepository orderRepository;

  @GetMapping("/health/me")
  public Map<String, Object> me() {
    User me = userService.findAuthUser();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("profile", profile(me.getId()));
    out.put("latest", latest(me.getId()));
    out.put("birthYear", me.getBirthday() != null ? me.getBirthday().getYear() : null);
    return out;
  }

  @PutMapping("/health/me")
  public ResponseEntity<?> save(@RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    Double w = num(b.get("weightKg")), h = num(b.get("heightCm"));
    if (w != null && (w < 20 || w > 350)) return bad("Confira o peso.");
    if (h != null && (h < 80 || h > 250)) return bad("Confira a altura (em centímetros).");
    Integer by = b.get("birthYear") == null ? null : (int) Math.round(num(b.get("birthYear")));
    jdbc.update("INSERT INTO health_profile_ (user_id, weight_kg, height_cm, birth_year, conditions, medications, injuries, goals, notes, share_with_pro, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (user_id) DO UPDATE SET weight_kg = EXCLUDED.weight_kg, height_cm = EXCLUDED.height_cm, " +
            "birth_year = EXCLUDED.birth_year, conditions = EXCLUDED.conditions, medications = EXCLUDED.medications, injuries = EXCLUDED.injuries, " +
            "goals = EXCLUDED.goals, notes = EXCLUDED.notes, share_with_pro = EXCLUDED.share_with_pro, updated_at = EXCLUDED.updated_at",
        me.getId(), w, h, by, txt(b.get("conditions")), txt(b.get("medications")), txt(b.get("injuries")), txt(b.get("goals")), txt(b.get("notes")),
        !Boolean.FALSE.equals(b.get("shareWithPro")), Timestamp.valueOf(LocalDateTime.now()));
    return ResponseEntity.ok(profile(me.getId()));
  }

  /** O app manda as leituras do relógio (Health Connect) em lote. */
  @PostMapping("/health/samples")
  public Map<String, Object> samples(@RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    int saved = 0;
    Object list = b.get("samples");
    if (list instanceof List) {
      for (Object o : (List<?>) list) {
        if (!(o instanceof Map) || saved >= 500) continue;
        Map<?, ?> m = (Map<?, ?>) o;
        String kind = Objects.toString(m.get("kind"), "");
        if (!List.of("HR", "STEPS", "KCAL", "SPO2", "SLEEP_MIN", "WEIGHT").contains(kind)) continue;
        Double v = num(m.get("value"));
        LocalDateTime at = time(m.get("at"));
        if (v == null || at == null) continue;
        jdbc.update("INSERT INTO health_sample_ (user_id, kind, value, at, source) VALUES (?, ?, ?, ?, ?)",
            me.getId(), kind, v, Timestamp.valueOf(at), Objects.toString(m.get("source"), "health-connect"));
        saved++;
      }
    }
    jdbc.update("DELETE FROM health_sample_ WHERE user_id = ? AND at < ?", me.getId(), Timestamp.valueOf(LocalDateTime.now().minusDays(60)));
    return Map.of("saved", saved, "latest", latest(me.getId()));
  }

  /** Registra um treino e devolve o resumo explicado em linguagem simples (Q3). */
  @PostMapping("/health/workouts")
  public ResponseEntity<?> workout(@RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    LocalDateTime start = time(b.get("startedAt")), end = time(b.get("endedAt"));
    if (start == null || end == null || !end.isAfter(start)) return bad("Horário do treino inválido.");
    List<Double> hr = jdbc.queryForList("SELECT value FROM health_sample_ WHERE user_id = ? AND kind = 'HR' AND at BETWEEN ? AND ? ORDER BY at",
        Double.class, me.getId(), Timestamp.valueOf(start), Timestamp.valueOf(end));
    Map<String, Object> summary = interpret(me, hr, Duration.between(start, end).toMinutes(), num(b.get("calories")), num(b.get("steps")));
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO workout_ (id, user_id, order_id, class_id, title, started_at, ended_at, avg_hr, max_hr, calories, steps, zones, summary, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        id, me.getId(), uuid(b.get("orderId")), uuid(b.get("classId")), txt(b.get("title")), Timestamp.valueOf(start), Timestamp.valueOf(end),
        summary.get("avgHr"), summary.get("maxHr"), summary.get("calories"), summary.get("steps"), String.valueOf(summary.get("zones")),
        String.join("\n", castList(summary.get("tips"))), Timestamp.valueOf(LocalDateTime.now()));
    summary.put("id", id);
    return ResponseEntity.ok(summary);
  }

  @GetMapping("/health/workouts")
  public List<Map<String, Object>> workouts() {
    User me = userService.findAuthUser();
    return jdbc.queryForList("SELECT id, title, started_at AS \"startedAt\", ended_at AS \"endedAt\", avg_hr AS \"avgHr\", max_hr AS \"maxHr\", calories, steps, summary " +
        "FROM workout_ WHERE user_id = ? ORDER BY started_at DESC LIMIT 30", me.getId());
  }

  /** Para o profissional: ficha e batimentos do cliente do pedido (se ele permitiu). */
  @GetMapping("/orders/{id}/client-health")
  public ResponseEntity<?> clientHealth(@PathVariable UUID id) {
    User me = userService.findAuthUser();
    Order o = orderRepository.findById(id).orElse(null);
    if (o == null || o.getProfessional() == null || !o.getProfessional().getId().equals(me.getId()) || o.getCustomer() == null) return bad("Pedido não encontrado.");
    if (o.getStatus() == Status.CANCELLED || o.getStatus() == Status.BLOCKED || o.getStatus() == Status.OPEN) return bad("A ficha aparece quando o pedido estiver confirmado.");
    return ResponseEntity.ok(forPro(o.getCustomer().getId(), o.getCustomer().getName()));
  }

  Map<String, Object> forPro(Long userId, String name) {
    Map<String, Object> p = profile(userId);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("name", name);
    if (p == null || Boolean.FALSE.equals(p.get("shareWithPro"))) {
      out.put("shared", false);
      return out;
    }
    out.put("shared", true);
    out.put("profile", p);
    out.put("latest", latest(userId));
    out.put("alerts", alerts(p));
    return out;
  }

  // ───────────── regras simples de interpretação ─────────────

  private Map<String, Object> interpret(User me, List<Double> hr, long minutes, Double kcal, Double steps) {
    Map<String, Object> p = profile(me.getId());
    Integer birthYear = p != null && p.get("birthYear") != null ? ((Number) p.get("birthYear")).intValue()
        : me.getBirthday() != null ? me.getBirthday().getYear() : null;
    int age = birthYear == null ? 35 : Math.max(12, LocalDate.now().getYear() - birthYear);
    int maxHr = 220 - age;
    int[] zones = new int[5];
    double sum = 0;
    int peak = 0;
    for (Double v : hr) {
      sum += v;
      peak = (int) Math.max(peak, v);
      double pct = v / maxHr;
      int z = pct < 0.6 ? 0 : pct < 0.7 ? 1 : pct < 0.8 ? 2 : pct < 0.9 ? 3 : 4;
      zones[z]++;
    }
    int avg = hr.isEmpty() ? 0 : (int) Math.round(sum / hr.size());
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("minutes", minutes);
    out.put("avgHr", hr.isEmpty() ? null : avg);
    out.put("maxHr", hr.isEmpty() ? null : peak);
    out.put("hrMaxEstimated", maxHr);
    out.put("calories", kcal == null ? null : (int) Math.round(kcal));
    out.put("steps", steps == null ? null : (int) Math.round(steps));
    List<Integer> zonePct = new ArrayList<>();
    for (int z : zones) zonePct.add(hr.isEmpty() ? 0 : (int) Math.round(100.0 * z / hr.size()));
    out.put("zones", zonePct);
    List<String> tips = new ArrayList<>();
    if (hr.isEmpty()) {
      tips.add("Não recebemos batimentos do relógio neste treino. Confira se ele está ligado ao Health Connect.");
    } else {
      int intense = zonePct.get(3) + zonePct.get(4);
      int moderate = zonePct.get(1) + zonePct.get(2);
      if (intense >= 40) tips.add("Treino intenso: você passou " + intense + "% do tempo acima de 80% do seu limite. Ótimo para condicionamento, mas descanse bem amanhã.");
      else if (moderate >= 50) tips.add("Treino na medida: a maior parte do tempo ficou na zona que mais queima gordura e melhora o fôlego.");
      else tips.add("Treino leve: bom para recuperação e mobilidade. Para evoluir o fôlego, tente ficar mais tempo na zona moderada.");
      if (peak >= maxHr * 0.95) tips.add("Seu batimento chegou perto do máximo (" + peak + " bpm). Se sentiu tontura ou falta de ar, avise o profissional.");
    }
    if (p != null && p.get("conditions") != null && String.valueOf(p.get("conditions")).toLowerCase().contains("hipertens") && peak > maxHr * 0.85) {
      tips.add("Você marcou hipertensão na ficha e o batimento passou de 85% do limite. Converse com o seu médico sobre a intensidade ideal.");
    }
    tips.add("Beba água e, se puder, alongue por 5 minutos.");
    out.put("tips", tips);
    return out;
  }

  private List<String> alerts(Map<String, Object> p) {
    List<String> a = new ArrayList<>();
    String c = String.valueOf(p.getOrDefault("conditions", "")).toLowerCase();
    if (c.contains("hipertens") || c.contains("pressão")) a.add("Hipertensão: evite esforço máximo e pausas longas de respiração presa.");
    if (c.contains("diabet")) a.add("Diabetes: tenha água e um lanche por perto; atenção a tontura.");
    if (c.contains("card") || c.contains("coração")) a.add("Problema cardíaco informado: mantenha intensidade moderada.");
    if (c.contains("asma")) a.add("Asma: pergunte se trouxe a bombinha.");
    if (p.get("injuries") != null && !String.valueOf(p.get("injuries")).isBlank()) a.add("Lesões: " + p.get("injuries"));
    if (p.get("weightKg") != null && p.get("heightCm") != null) {
      double bmi = ((Number) p.get("weightKg")).doubleValue() / Math.pow(((Number) p.get("heightCm")).doubleValue() / 100, 2);
      if (bmi >= 35) a.add("IMC alto (" + Math.round(bmi) + "): comece com baixo impacto.");
    }
    return a;
  }

  Map<String, Object> profile(Long userId) {
    List<Map<String, Object>> r = jdbc.queryForList("SELECT weight_kg AS \"weightKg\", height_cm AS \"heightCm\", birth_year AS \"birthYear\", conditions, medications, injuries, goals, notes, " +
        "share_with_pro AS \"shareWithPro\", updated_at AS \"updatedAt\" FROM health_profile_ WHERE user_id = ?", userId);
    return r.isEmpty() ? null : r.get(0);
  }

  Map<String, Object> latest(Long userId) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (String k : List.of("HR", "STEPS", "KCAL", "SPO2", "SLEEP_MIN")) {
      List<Map<String, Object>> r = jdbc.queryForList("SELECT value, at FROM health_sample_ WHERE user_id = ? AND kind = ? ORDER BY at DESC LIMIT 1", userId, k);
      if (!r.isEmpty()) m.put(k, r.get(0));
    }
    return m;
  }

  @SuppressWarnings("unchecked")
  private static List<String> castList(Object o) { return o instanceof List ? (List<String>) o : List.of(); }

  private static String txt(Object o) {
    String s = Objects.toString(o, "").trim();
    return s.isEmpty() ? null : (s.length() > 1000 ? s.substring(0, 1000) : s);
  }

  private static Double num(Object o) {
    try { return o == null || o.toString().isBlank() ? null : Double.parseDouble(o.toString().replace(',', '.')); } catch (Exception e) { return null; }
  }

  private static UUID uuid(Object o) {
    try { return o == null ? null : UUID.fromString(o.toString()); } catch (Exception e) { return null; }
  }

  private static LocalDateTime time(Object o) {
    if (o == null) return null;
    try { return LocalDateTime.ofInstant(Instant.parse(o.toString()), ZoneId.systemDefault()); } catch (Exception ignored) { }
    try { return LocalDateTime.parse(o.toString()); } catch (Exception e) { return null; }
  }
}
