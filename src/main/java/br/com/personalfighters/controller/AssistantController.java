package br.com.personalfighters.controller;

import br.com.personalfighters.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.*;
import java.util.*;

/**
 * Assistente do painel (sem IA externa): entende perguntas por palavras-chave e responde com os dados reais.
 * Também monta o resumo do dia e a lista de avisos (o sino).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AssistantController {

  private static final ZoneId Z = ZoneId.of("America/Sao_Paulo");
  private static final String PAID = "o.pix_paid = TRUE";
  private static final String FEE = "COALESCE(SUM(o.total_cost - COALESCE(o.discount_amount,0) - COALESCE(o.service_value, o.total_cost/1.05)),0)";
  private final JdbcTemplate jdbc;
  private final SettingsService settings;

  // ───────────── utilidades ─────────────

  private static LocalDateTime now() { return LocalDateTime.now(Z); }
  private static Timestamp ts(LocalDateTime t) { return Timestamp.valueOf(t); }
  private long n(String sql, Object... a) { try { Long v = jdbc.queryForObject(sql, Long.class, a); return v == null ? 0 : v; } catch (Exception e) { log.debug("assistente: {}", e.getMessage()); return 0; } }
  private BigDecimal m(String sql, Object... a) { try { BigDecimal v = jdbc.queryForObject(sql, BigDecimal.class, a); return v == null ? BigDecimal.ZERO : v; } catch (Exception e) { return BigDecimal.ZERO; } }
  private List<Map<String, Object>> q(String sql, Object... a) { try { return jdbc.queryForList(sql, a); } catch (Exception e) { log.debug("assistente: {}", e.getMessage()); return List.of(); } }
  static String brl(BigDecimal v) {
    BigDecimal x = v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP);
    String s = String.format(Locale.US, "%,.2f", x).replace(',', 'X').replace('.', ',').replace('X', '.');
    return "R$ " + s;
  }
  static String norm(String s) {
    String t = Normalizer.normalize(Objects.toString(s, "").toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    return " " + t.replaceAll("[^a-z0-9#]+", " ").trim() + " ";
  }
  private static boolean has(String t, String... words) { for (String w : words) if (t.contains(w)) return true; return false; }
  private static Map<String, Object> act(String label, String go, Object arg) {
    Map<String, Object> a = new LinkedHashMap<>(); a.put("label", label); a.put("go", go); if (arg != null) a.put("arg", String.valueOf(arg)); return a;
  }
  private static Map<String, Object> reply(String text, List<Map<String, Object>> actions) {
    Map<String, Object> r = new LinkedHashMap<>(); r.put("text", text); r.put("actions", actions == null ? List.of() : actions); return r;
  }
  private static String first(Object name) { String s = Objects.toString(name, "").trim(); return s.contains(" ") ? s.substring(0, s.indexOf(' ')) : s; }
  private static String mins(long m) { return m >= 60 ? (m / 60) + " h " + (m % 60) + " min" : m + " min"; }

  /** Período citado na pergunta: [início, fim, nome, início anterior]. */
  private Object[] period(String t) {
    LocalDate d = LocalDate.now(Z);
    if (has(t, " hoje ")) return new Object[]{d.atStartOfDay(), now(), "hoje", d.minusDays(1).atStartOfDay()};
    if (has(t, " ontem ")) return new Object[]{d.minusDays(1).atStartOfDay(), d.atStartOfDay(), "ontem", d.minusDays(2).atStartOfDay()};
    if (has(t, " ano ")) return new Object[]{d.withDayOfYear(1).atStartOfDay(), now(), "neste ano", d.withDayOfYear(1).minusYears(1).atStartOfDay()};
    if (has(t, " mes ", " mensal ")) return new Object[]{d.withDayOfMonth(1).atStartOfDay(), now(), "neste mês", d.withDayOfMonth(1).minusMonths(1).atStartOfDay()};
    return new Object[]{d.minusDays(6).atStartOfDay(), now(), "nos últimos 7 dias", d.minusDays(13).atStartOfDay()};
  }
  private static String delta(BigDecimal a, BigDecimal b) {
    if (b == null || b.signum() == 0) return "";
    long p = a.subtract(b).multiply(BigDecimal.valueOf(100)).divide(b, 0, RoundingMode.HALF_UP).longValue();
    return " (" + (p >= 0 ? "+" : "") + p + "% que o período anterior)";
  }

  // ───────────── avisos (o sino) ─────────────

  @GetMapping("/notifications")
  public Map<String, Object> notifications() {
    List<Map<String, Object>> items = new ArrayList<>();
    for (Map<String, Object> s : q("SELECT s.id, s.started_at, s.acknowledged_at, u.name FROM protection_session_ s JOIN user_ u ON u.id = s.user_id WHERE s.active = TRUE AND s.level >= 3 ORDER BY s.started_at")) {
      boolean ack = s.get("acknowledged_at") != null;
      items.add(item("SOS", ack ? "SOS em acompanhamento" : "SOS: " + s.get("name") + " pediu ajuda", ack ? Objects.toString(s.get("name")) : "Abra agora e ligue para a pessoa", s.get("started_at"), "sos", null, !ack));
    }
    int hours = Math.max(1, settings.getInt(SettingsService.DISPUTE_TIMEOUT_HOURS));
    for (Map<String, Object> o : q("SELECT o.id, o.disputed_at, c.name AS cn, p.name AS pn FROM order_ o LEFT JOIN user_ c ON c.id = o.customer_id LEFT JOIN user_ p ON p.id = o.professional_id WHERE o.dispute_status = 'OPEN' ORDER BY o.disputed_at")) {
      String sub = first(o.get("cn")) + " × " + first(o.get("pn"));
      String title = "Disputa aberta";
      boolean urgent = false;
      if (o.get("disputed_at") instanceof Timestamp) {
        long left = Duration.between(now(), ((Timestamp) o.get("disputed_at")).toLocalDateTime().plusHours(hours)).toMinutes();
        title = left > 0 ? "Disputa vence em " + mins(left) : "Disputa passou do prazo";
        urgent = left <= 45;
      }
      items.add(item("DISPUTE", title, "Pedido #" + code(o.get("id")) + " · " + sub, o.get("disputed_at"), "pedidos", o.get("id"), urgent));
    }
    for (Map<String, Object> v : q("SELECT v.user_id, v.submitted_at, u.name FROM identity_verification_ v JOIN user_ u ON u.id = v.user_id WHERE v.status = 'PENDING' ORDER BY v.submitted_at"))
      items.add(item("VERIFY", "Documento para aprovar", Objects.toString(v.get("name")), v.get("submitted_at"), "verif", null, false));
    // ── Segurança: comportamento estranho ──
    for (Map<String, Object> e : q("SELECT e.kind, e.detail, e.created_at, u.name, u.id FROM security_event_ e JOIN user_ u ON u.id = e.user_id WHERE e.kind IN ('NOT_ME','WITHDRAW_HELD') AND e.created_at > ? ORDER BY e.id DESC LIMIT 10", ts(now().minusDays(3))))
      items.add(item("SECURITY", "NOT_ME".equals(e.get("kind")) ? "🚨 \"Não fui eu\": possível conta invadida" : "🚨 Saque tentado logo após trocar a chave PIX",
          e.get("name") + " · conta desconectada e saques segurados por 24 h", e.get("created_at"), "user", e.get("id"), true));
    for (Map<String, Object> e : q("SELECT c.changed_at, u.name, u.id, c.old_key_end, c.new_key_end FROM pix_key_change_ c JOIN user_ u ON u.id = c.user_id WHERE COALESCE(c.old_key_end, '') <> '' AND c.changed_at > ? ORDER BY c.id DESC LIMIT 10", ts(now().minusDays(2))))
      items.add(item("SECURITY", "Chave PIX trocada", e.get("name") + " · ••••" + e.get("old_key_end") + " → ••••" + e.get("new_key_end") + " · saque liberado só depois de 24 h", e.get("changed_at"), "user", e.get("id"), false));
    for (Map<String, Object> e : q("SELECT d.device_id, COUNT(DISTINCT d.user_id) AS n, STRING_AGG(DISTINCT u.name, ', ') AS names, MAX(d.last_seen) AS last FROM user_device_ d JOIN user_ u ON u.id = d.user_id " +
        "WHERE d.first_seen > ? GROUP BY d.device_id HAVING COUNT(DISTINCT d.user_id) >= 3 ORDER BY last DESC LIMIT 5", ts(now().minusDays(30))))
      items.add(item("SECURITY", e.get("n") + " contas no mesmo celular", Objects.toString(e.get("names")), e.get("last"), "usuarios", null, false));
    for (Map<String, Object> e : q("SELECT o.customer_id AS id, u.name, COUNT(*) AS n, MAX(o.cancelled_at) AS last FROM order_ o JOIN user_ u ON u.id = o.customer_id " +
        "WHERE o.status = 'CANCELLED' AND o.cancelled_by = 'CLIENT' AND o.cancelled_at > ? GROUP BY o.customer_id, u.name HAVING COUNT(*) >= 3 ORDER BY last DESC LIMIT 5", ts(now().minusDays(7))))
      items.add(item("SECURITY", "Cliente cancelou " + e.get("n") + " pedidos em 7 dias", Objects.toString(e.get("name")), e.get("last"), "user", e.get("id"), false));
    for (Map<String, Object> p : q("SELECT st.user_id, st.topic, st.updated_at, u.name FROM support_state_ st JOIN user_ u ON u.id = st.user_id WHERE st.priority = TRUE ORDER BY st.updated_at DESC LIMIT 10"))
      items.add(item("SUPPORT", "Suporte com prioridade: " + Objects.toString(p.get("topic"), "").replace('-', ' '), Objects.toString(p.get("name")) + " · a Opi chamou a equipe", p.get("updated_at"), "suporte", null, true));
    long sup = n("SELECT COUNT(DISTINCT m.user_id) FROM support_message_ m LEFT JOIN support_state_ st ON st.user_id = m.user_id WHERE m.from_admin = FALSE AND m.read_by_admin = FALSE AND COALESCE(st.priority, FALSE) = FALSE");
    if (sup > 0) items.add(item("SUPPORT", sup + " conversa(s) esperando a equipe", "A Opi já fez o primeiro atendimento · responda pela Central", null, "suporte", null, false));
    for (Map<String, Object> w : q("SELECT w.amount, w.created_at, u.name FROM wallet_entry_ w JOIN user_ u ON u.id = w.professional_id WHERE w.kind = 'WITHDRAW' AND w.status = 'FAILED' AND w.created_at > ? ORDER BY w.created_at DESC LIMIT 5", ts(now().minusDays(3))))
      items.add(item("WITHDRAW", "Saque PIX falhou", w.get("name") + " · " + brl((BigDecimal) w.get("amount")), w.get("created_at"), "fin", null, false));
    long cert = n("SELECT COUNT(*) FROM pro_certificate_ WHERE status = 'PENDING'");
    if (cert > 0) items.add(item("CERT", cert + " certificado(s) para conferir", "Profissionais pediram selo", null, "certs", null, false));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("items", items);
    out.put("count", items.size());
    out.put("sosActive", n("SELECT COUNT(*) FROM protection_session_ WHERE active = TRUE AND level >= 3 AND acknowledged_at IS NULL"));
    return out;
  }

  private static String code(Object id) { return String.valueOf(id).substring(0, Math.min(6, String.valueOf(id).length())).toUpperCase(); }
  private static Map<String, Object> item(String kind, String title, String sub, Object at, String go, Object arg, boolean urgent) {
    Map<String, Object> i = new LinkedHashMap<>();
    i.put("kind", kind); i.put("title", title); i.put("sub", sub); i.put("at", at); i.put("go", go); if (arg != null) i.put("arg", String.valueOf(arg)); i.put("urgent", urgent);
    return i;
  }

  // ───────────── resumo do dia ─────────────

  @GetMapping("/assistant/summary")
  public Map<String, Object> summary() {
    LocalDateTime today = LocalDate.now(Z).atStartOfDay();
    long orders = n("SELECT COUNT(*) FROM order_ WHERE created_at >= ?", ts(today));
    BigDecimal fee = m("SELECT " + FEE + " FROM order_ o WHERE " + PAID + " AND o.status <> 'CANCELLED' AND o.created_at >= ?", ts(today));
    BigDecimal feeY = m("SELECT " + FEE + " FROM order_ o WHERE " + PAID + " AND o.status <> 'CANCELLED' AND o.created_at >= ? AND o.created_at < ?", ts(today.minusDays(1)), ts(today));
    BigDecimal cofre = m("SELECT COALESCE(SUM(o.total_cost - COALESCE(o.discount_amount,0)),0) FROM order_ o WHERE " + PAID + " AND COALESCE(o.pix_released,FALSE) = FALSE AND o.status NOT IN ('CANCELLED')");
    StringBuilder t = new StringBuilder("Hoje entraram **" + orders + " pedido" + (orders == 1 ? "" : "s") + "** e sua taxa foi **" + brl(fee) + "**" + delta(fee, feeY).replace("período anterior", "ontem") + ". ");
    t.append("Tem **").append(brl(cofre)).append(" no cofre**.");
    List<Map<String, Object>> acts = new ArrayList<>();
    List<String> need = new ArrayList<>();
    for (Map<String, Object> i : castItems(notifications().get("items"))) {
      if (need.size() >= 4) break;
      need.add(String.valueOf(i.get("title")).toLowerCase(Locale.ROOT).startsWith("sos") ? "**" + i.get("title") + "**" : String.valueOf(i.get("title")).toLowerCase(Locale.ROOT));
      String lbl = Map.of("SOS", "Abrir SOS", "DISPUTE", "Abrir disputa", "VERIFY", "Ver documentos", "SUPPORT", "Abrir suporte", "WITHDRAW", "Ver saque", "CERT", "Ver certificados").getOrDefault(String.valueOf(i.get("kind")), "Abrir");
      if (acts.size() < 3 && acts.stream().noneMatch(a -> lbl.equals(a.get("label")))) acts.add(act(lbl, String.valueOf(i.get("go")), i.get("arg")));
    }
    t.append(need.isEmpty() ? " Nada urgente agora." : " Precisa de você: " + String.join(", ", need) + ".");
    return reply(t.toString(), acts);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> castItems(Object o) { return o instanceof List ? (List<Map<String, Object>>) o : List.of(); }

  // ───────────── perguntas ─────────────

  @PostMapping("/assistant/ask")
  public Map<String, Object> ask(@RequestBody Map<String, Object> body) {
    String raw = Objects.toString(body.get("q"), "").trim();
    String t = norm(raw);
    if (t.isBlank()) return help();
    try {
      // Como funciona (explicações)
      if (has(t, " como funciona", " o que e ", " o que acontece", " explica", " regra ")) {
        Map<String, Object> e = explain(t);
        if (e != null) return e;
      }
      // Pedido por código
      java.util.regex.Matcher mm = java.util.regex.Pattern.compile("(?:^|[^0-9a-z])#?([0-9a-f]{6,8})(?:$|[^0-9a-z])").matcher(raw.toLowerCase());
      if (mm.find() && (has(t, " pedido") || raw.trim().matches("#?[0-9a-fA-F]{6,8}") || raw.contains("#"))) return orderByCode(mm.group(1));
      if (has(t, " sos ", " socorro", " emergencia", " perigo")) return sos();
      if (has(t, " disputa", " reclama", " briga", " problema")) return disputes();
      if (has(t, " saque", " sacou", " sacar", " sacaram", " pix ")) return withdrawals(t);
      if (has(t, " etapa")) return stages();
      if (has(t, " orcamento", " orcamentos", " comparar", " grupo ")) return quotes();
      if (has(t, " cupo")) return coupons();
      if (has(t, " loja", " parceir", " seguro")) return partners();
      if (has(t, " certificad", " selo")) return certificates();
      if (has(t, " suporte", " mensagem", " mensagens", " atendimento")) return support();
      if (has(t, " verific", " document", " aprovar ", " selfie")) return verifications();
      if (has(t, " bloque")) return blocked();
      if (has(t, " cofre", " guardado", " travado", " retido")) return cofre();
      if (has(t, " cancel", " devolv", " reembols")) return cancels(t);
      if (has(t, " melhor", " melhores", " top ", " ranking", " destaque")) return top(t);
      if (has(t, " cadastr", " novos ", " novas ", " quantos clientes", " quantos profissionais", " usuarios")) return signups(t);
      if (has(t, " movimentad", " faturamento", " faturou", " vendas", " volume")) return gmv(t);
      // Pessoa pelo nome ("pedidos da Carla", "quanto a Carla ganhou")
      Map<String, Object> person = person(raw, t);
      if (person != null) return person;
      if (has(t, " ganhei", " ganho", " receita", " taxa", " lucro", " lucrei", " ganhamos", " quanto entrou", " rendeu")) return revenue(t);
      if (has(t, " pedido", " servico", " servicos")) return ordersCount(t);
      Map<String, Object> nav = navigate(t);
      if (nav != null) return nav;
    } catch (Exception e) {
      log.warn("Assistente falhou para '{}': {}", raw, e.getMessage());
      return reply("Não consegui buscar isso agora. Tente de novo em alguns segundos.", List.of());
    }
    return help();
  }

  private Map<String, Object> help() {
    return reply("Não entendi bem. Você pode perguntar, por exemplo: **quanto ganhei essa semana**, **tem disputa vencendo**, **quanto tem no cofre**, **quem falta verificar**, **saques de hoje**, **pedidos da Carla**, **pedido A3F1C2** ou **como funciona o cofre**.",
        List.of(act("Quanto ganhei na semana?", "ask", "quanto ganhei na semana"), act("Tem disputa vencendo?", "ask", "tem disputa vencendo"), act("Quem falta verificar?", "ask", "quem falta verificar")));
  }

  private Map<String, Object> revenue(String t) {
    Object[] p = period(t);
    LocalDateTime a = (LocalDateTime) p[0], b = (LocalDateTime) p[1], prev = (LocalDateTime) p[3];
    BigDecimal fee = m("SELECT " + FEE + " FROM order_ o WHERE " + PAID + " AND o.status <> 'CANCELLED' AND o.created_at >= ? AND o.created_at < ?", ts(a), ts(b));
    BigDecimal feeP = m("SELECT " + FEE + " FROM order_ o WHERE " + PAID + " AND o.status <> 'CANCELLED' AND o.created_at >= ? AND o.created_at < ?", ts(prev), ts(a));
    long cnt = n("SELECT COUNT(*) FROM order_ o WHERE " + PAID + " AND o.status <> 'CANCELLED' AND o.created_at >= ? AND o.created_at < ?", ts(a), ts(b));
    BigDecimal boost = m("SELECT COALESCE(SUM(price),0) FROM pro_boost_ WHERE status IN ('PAID','ACTIVE') AND created_at >= ? AND created_at < ?", ts(a), ts(b));
    String extra = boost.signum() > 0 ? " Mais " + brl(boost) + " de destaques pagos." : "";
    return reply("Sua taxa " + p[2] + " foi **" + brl(fee) + "** em " + cnt + " pedido(s) pago(s)" + delta(fee, feeP) + "." + extra, List.of(act("Abrir Financeiro", "fin", null)));
  }

  private Map<String, Object> gmv(String t) {
    Object[] p = period(t);
    BigDecimal v = m("SELECT COALESCE(SUM(o.total_cost - COALESCE(o.discount_amount,0)),0) FROM order_ o WHERE " + PAID + " AND o.created_at >= ? AND o.created_at < ?", ts((LocalDateTime) p[0]), ts((LocalDateTime) p[1]));
    BigDecimal vp = m("SELECT COALESCE(SUM(o.total_cost - COALESCE(o.discount_amount,0)),0) FROM order_ o WHERE " + PAID + " AND o.created_at >= ? AND o.created_at < ?", ts((LocalDateTime) p[3]), ts((LocalDateTime) p[0]));
    return reply("Os clientes pagaram **" + brl(v) + "** " + p[2] + delta(v, vp) + ".", List.of(act("Abrir Financeiro", "fin", null)));
  }

  private Map<String, Object> ordersCount(String t) {
    Object[] p = period(t);
    long all = n("SELECT COUNT(*) FROM order_ WHERE created_at >= ? AND created_at < ?", ts((LocalDateTime) p[0]), ts((LocalDateTime) p[1]));
    long done = n("SELECT COUNT(*) FROM order_ WHERE status IN ('DONE','REVIEWED') AND created_at >= ? AND created_at < ?", ts((LocalDateTime) p[0]), ts((LocalDateTime) p[1]));
    long open = n("SELECT COUNT(*) FROM order_ WHERE status = 'OPEN'");
    return reply("Entraram **" + all + " pedidos** " + p[2] + ", " + done + " já concluídos. Agora há " + open + " esperando um profissional aceitar.", List.of(act("Abrir Pedidos", "pedidos", null)));
  }

  private Map<String, Object> cofre() {
    BigDecimal v = m("SELECT COALESCE(SUM(o.total_cost - COALESCE(o.discount_amount,0)),0) FROM order_ o WHERE " + PAID + " AND COALESCE(o.pix_released,FALSE) = FALSE AND o.status NOT IN ('CANCELLED')");
    long c = n("SELECT COUNT(*) FROM order_ o WHERE " + PAID + " AND COALESCE(o.pix_released,FALSE) = FALSE AND o.status NOT IN ('CANCELLED')");
    long approve = n("SELECT COUNT(*) FROM order_ WHERE status = 'PENDING_CLIENT_CONFIRMATION'");
    BigDecimal wallets = m("SELECT COALESCE(SUM(CASE WHEN kind IN ('CREDIT','STAGE') THEN amount ELSE -amount END),0) FROM wallet_entry_ WHERE status = 'DONE'");
    return reply("Tem **" + brl(v) + " guardado no cofre** em " + c + " pedido(s). " + approve + " esperam o cliente aprovar. Nas carteiras dos profissionais (já liberado, ainda não sacado) há " + brl(wallets) + ".",
        List.of(act("Ver pedidos no cofre", "pedidos", "ESCROW")));
  }

  private Map<String, Object> disputes() {
    List<Map<String, Object>> it = castItems(notifications().get("items"));
    List<Map<String, Object>> d = new ArrayList<>();
    for (Map<String, Object> i : it) if ("DISPUTE".equals(i.get("kind"))) d.add(i);
    if (d.isEmpty()) return reply("Nenhuma disputa aberta agora. 👍", List.of());
    StringBuilder s = new StringBuilder("Há **" + d.size() + " disputa(s) aberta(s)**:");
    List<Map<String, Object>> acts = new ArrayList<>();
    for (Map<String, Object> i : d) { s.append("\n• ").append(i.get("title")).append(" — ").append(i.get("sub")); if (acts.size() < 3) acts.add(act("Abrir " + String.valueOf(i.get("sub")).split(" · ")[0], "pedidos", i.get("arg"))); }
    s.append("\nSe ninguém decidir no prazo, o valor ").append("true".equalsIgnoreCase(settings.get(SettingsService.DISPUTE_TIMEOUT_REFUND)) ? "volta para o cliente." : "vai para o profissional.");
    return reply(s.toString(), acts);
  }

  private Map<String, Object> sos() {
    List<Map<String, Object>> s = q("SELECT s.started_at, s.acknowledged_at, u.name FROM protection_session_ s JOIN user_ u ON u.id = s.user_id WHERE s.active = TRUE AND s.level >= 3 ORDER BY s.started_at");
    long today = n("SELECT COUNT(*) FROM protection_session_ WHERE level >= 3 AND started_at >= ?", ts(LocalDate.now(Z).atStartOfDay()));
    if (s.isEmpty()) return reply("Nenhum SOS ativo agora. Hoje foram " + today + " acionamento(s).", List.of(act("Ver histórico de SOS", "sos", null)));
    StringBuilder b = new StringBuilder("**" + s.size() + " SOS ativo(s):**");
    for (Map<String, Object> r : s) b.append("\n• ").append(r.get("name")).append(r.get("acknowledged_at") == null ? " — ninguém assumiu ainda!" : " — em acompanhamento");
    return reply(b.toString(), List.of(act("Abrir SOS agora", "sos", null)));
  }

  private Map<String, Object> verifications() {
    List<Map<String, Object>> v = q("SELECT u.name, v.submitted_at FROM identity_verification_ v JOIN user_ u ON u.id = v.user_id WHERE v.status = 'PENDING' ORDER BY v.submitted_at LIMIT 10");
    if (v.isEmpty()) return reply("Nenhum documento esperando aprovação. Tudo em dia.", List.of());
    StringBuilder b = new StringBuilder("**" + v.size() + " pessoa(s) esperando verificação:**");
    for (Map<String, Object> r : v) {
      String ago = r.get("submitted_at") instanceof Timestamp ? " (há " + mins(Duration.between(((Timestamp) r.get("submitted_at")).toLocalDateTime(), now()).toMinutes()) + ")" : "";
      b.append("\n• ").append(r.get("name")).append(ago);
    }
    return reply(b.toString(), List.of(act("Abrir Verificações", "verif", null)));
  }

  private Map<String, Object> blocked() {
    List<Map<String, Object>> v = q("SELECT name, blocked_reason FROM user_ WHERE COALESCE(blocked,FALSE) = TRUE ORDER BY id DESC LIMIT 10");
    if (v.isEmpty()) return reply("Ninguém está bloqueado.", List.of());
    StringBuilder b = new StringBuilder("**" + v.size() + " bloqueado(s):**");
    for (Map<String, Object> r : v) b.append("\n• ").append(r.get("name")).append(r.get("blocked_reason") != null ? " — " + r.get("blocked_reason") : "");
    return reply(b.toString(), List.of(act("Abrir bloqueados", "usuarios", "BLOCKED")));
  }

  private Map<String, Object> withdrawals(String t) {
    Object[] p = period(t);
    BigDecimal v = m("SELECT COALESCE(SUM(amount),0) FROM wallet_entry_ WHERE kind = 'WITHDRAW' AND status = 'DONE' AND created_at >= ? AND created_at < ?", ts((LocalDateTime) p[0]), ts((LocalDateTime) p[1]));
    List<Map<String, Object>> rows = q("SELECT u.name, w.amount, w.status FROM wallet_entry_ w JOIN user_ u ON u.id = w.professional_id WHERE w.kind = 'WITHDRAW' AND w.created_at >= ? AND w.created_at < ? ORDER BY w.created_at DESC LIMIT 8", ts((LocalDateTime) p[0]), ts((LocalDateTime) p[1]));
    StringBuilder b = new StringBuilder("Os profissionais sacaram **" + brl(v) + "** " + p[2] + ".");
    for (Map<String, Object> r : rows) b.append("\n• ").append(r.get("name")).append(" — ").append(brl((BigDecimal) r.get("amount"))).append("FAILED".equals(r.get("status")) ? " (falhou)" : "");
    return reply(b.toString(), List.of(act("Abrir Financeiro", "fin", null)));
  }

  private Map<String, Object> stages() {
    List<Map<String, Object>> r = q("SELECT s.order_id, s.idx, s.label, c.name AS cn FROM order_stage_ s JOIN order_ o ON o.id = s.order_id LEFT JOIN user_ c ON c.id = o.customer_id WHERE s.status = 'DONE' ORDER BY s.done_at LIMIT 10");
    BigDecimal rel = m("SELECT COALESCE(SUM(amount),0) FROM wallet_entry_ WHERE kind = 'STAGE' AND created_at >= ?", ts(LocalDate.now(Z).withDayOfMonth(1).atStartOfDay()));
    StringBuilder b = new StringBuilder(r.isEmpty() ? "Nenhuma etapa esperando o cliente aprovar." : "**" + r.size() + " etapa(s) esperando o cliente aprovar:**");
    List<Map<String, Object>> acts = new ArrayList<>();
    for (Map<String, Object> x : r) { b.append("\n• Pedido #").append(code(x.get("order_id"))).append(" — ").append(x.get("label")).append(" (").append(first(x.get("cn"))).append(")"); if (acts.size() < 2) acts.add(act("Abrir #" + code(x.get("order_id")), "pedidos", x.get("order_id"))); }
    b.append("\nNeste mês já foram liberados ").append(brl(rel)).append(" por etapas.");
    return reply(b.toString(), acts);
  }

  private Map<String, Object> quotes() {
    long groups = n("SELECT COUNT(DISTINCT quote_group) FROM order_ WHERE quote_group IS NOT NULL");
    long month = n("SELECT COUNT(DISTINCT quote_group) FROM order_ WHERE quote_group IS NOT NULL AND created_at >= ?", ts(LocalDate.now(Z).withDayOfMonth(1).atStartOfDay()));
    return reply("Há **" + groups + " pedido(s) de orçamento em grupo** ainda comparando (" + month + " criados neste mês). Quando o cliente escolhe um profissional, os outros pedidos são cancelados sem custo.", List.of(act("Abrir Pedidos", "pedidos", null)));
  }

  private Map<String, Object> coupons() {
    List<Map<String, Object>> c = q("SELECT code, used, max_uses FROM coupon_ WHERE active = TRUE ORDER BY created_at DESC LIMIT 8");
    if (c.isEmpty()) return reply("Nenhum cupom ativo.", List.of(act("Criar cupom", "cupons", null)));
    StringBuilder b = new StringBuilder("**" + c.size() + " cupom(ns) ativo(s):**");
    for (Map<String, Object> r : c) b.append("\n• ").append(r.get("code")).append(" — usado ").append(r.get("used")).append(r.get("max_uses") != null ? " de " + r.get("max_uses") : "").append(" vez(es)");
    return reply(b.toString(), List.of(act("Abrir Cupons", "cupons", null)));
  }

  private Map<String, Object> partners() {
    long stores = n("SELECT COUNT(*) FROM partner_store_ WHERE active = TRUE");
    long leads = n("SELECT COUNT(*) FROM partner_lead_");
    long ins = n("SELECT COUNT(*) FROM insurance_interest_");
    return reply("Há **" + stores + " loja(s) parceira(s)** ativas, " + leads + " pedido(s) para ser parceiro e " + ins + " pessoa(s) interessadas em seguro.", List.of(act("Abrir Lojas e seguros", "parceiros", null)));
  }

  private Map<String, Object> certificates() {
    long p = n("SELECT COUNT(*) FROM pro_certificate_ WHERE status = 'PENDING'");
    long a = n("SELECT COUNT(*) FROM pro_certificate_ WHERE status = 'APPROVED'");
    return reply(p == 0 ? "Nenhum certificado esperando. " + a + " já aprovado(s)." : "**" + p + " certificado(s)** esperando você conferir. " + a + " já aprovado(s).", List.of(act("Abrir Certificados", "certs", null)));
  }

  private Map<String, Object> support() {
    long s = n("SELECT COUNT(DISTINCT user_id) FROM support_message_ WHERE from_admin = FALSE AND read_by_admin = FALSE");
    return reply(s == 0 ? "Nenhuma conversa de suporte esperando resposta." : "**" + s + " pessoa(s)** esperando resposta no suporte.", List.of(act("Abrir Suporte", "suporte", null)));
  }

  private Map<String, Object> cancels(String t) {
    Object[] p = period(t);
    long c = n("SELECT COUNT(*) FROM order_ WHERE status = 'CANCELLED' AND created_at >= ? AND created_at < ?", ts((LocalDateTime) p[0]), ts((LocalDateTime) p[1]));
    BigDecimal r = m("SELECT COALESCE(SUM(client_refund),0) FROM order_ WHERE client_refund > 0 AND created_at >= ? AND created_at < ?", ts((LocalDateTime) p[0]), ts((LocalDateTime) p[1]));
    return reply("Foram **" + c + " cancelamento(s)** " + p[2] + " e " + brl(r) + " devolvidos aos clientes.", List.of(act("Ver cancelados", "pedidos", "CANCELLED")));
  }

  private Map<String, Object> top(String t) {
    Object[] p = has(t, " semana", " hoje ", " ano ") ? period(t) : new Object[]{LocalDate.now(Z).withDayOfMonth(1).atStartOfDay(), now(), "neste mês", null};
    List<Map<String, Object>> r = q("SELECT u.id, u.name, COUNT(o.id) AS done, (SELECT ROUND(AVG(rv.rating)::numeric,1) FROM review_ rv WHERE rv.professional_id = u.id AND rv.rating IS NOT NULL) AS rating " +
        "FROM order_ o JOIN user_ u ON u.id = o.professional_id WHERE o.status IN ('DONE','REVIEWED') AND o.created_at >= ? GROUP BY u.id, u.name ORDER BY done DESC LIMIT 5", ts((LocalDateTime) p[0]));
    if (r.isEmpty()) return reply("Ainda não há serviços concluídos " + p[2] + ".", List.of());
    StringBuilder b = new StringBuilder("**Melhores profissionais " + p[2] + ":**");
    int i = 1;
    for (Map<String, Object> x : r) b.append("\n").append(i++).append("º ").append(x.get("name")).append(" — ").append(x.get("done")).append(" serviço(s)").append(x.get("rating") != null ? " · ★ " + x.get("rating").toString().replace('.', ',') : "");
    return reply(b.toString(), List.of(act("Abrir ficha de " + first(r.get(0).get("name")), "user", r.get(0).get("id"))));
  }

  private Map<String, Object> signups(String t) {
    Object[] p = period(t);
    long c = n("SELECT COUNT(DISTINCT u.id) FROM user_ u JOIN user_role r ON r.user_id = u.id WHERE r.role = 'CUSTOMER' AND u.created_at_ >= ?", ts((LocalDateTime) p[0]));
    long pr = n("SELECT COUNT(DISTINCT u.id) FROM user_ u JOIN user_role r ON r.user_id = u.id WHERE r.role = 'PROFESSIONAL' AND u.created_at_ >= ?", ts((LocalDateTime) p[0]));
    long tc = n("SELECT COUNT(DISTINCT user_id) FROM user_role WHERE role = 'CUSTOMER'");
    long tp = n("SELECT COUNT(DISTINCT user_id) FROM user_role WHERE role = 'PROFESSIONAL'");
    return reply("Novos cadastros " + p[2] + ": **" + c + " cliente(s)** e **" + pr + " profissional(is)**. No total são " + tc + " clientes e " + tp + " profissionais.", List.of(act("Abrir Usuários", "usuarios", null)));
  }

  private Map<String, Object> orderByCode(String c) {
    List<Map<String, Object>> r = q("SELECT o.id, o.status, o.total_cost, o.dispute_status, c.name AS cn, p.name AS pn, pr.name AS svc FROM order_ o LEFT JOIN user_ c ON c.id = o.customer_id LEFT JOIN user_ p ON p.id = o.professional_id LEFT JOIN product_ pr ON pr.id = o.product_id WHERE CAST(o.id AS TEXT) LIKE ? LIMIT 1", c + "%");
    if (r.isEmpty()) return reply("Não achei o pedido #" + c.toUpperCase() + ". Confira as letras e números.", List.of());
    Map<String, Object> o = r.get(0);
    String st = statusPt(Objects.toString(o.get("status")), o.get("dispute_status"));
    return reply("**Pedido #" + code(o.get("id")) + "** · " + Objects.toString(o.get("svc"), "Serviço") + "\n" + Objects.toString(o.get("cn"), "Cliente") + " → " + Objects.toString(o.get("pn"), "sem profissional") +
        "\nValor: " + (o.get("total_cost") == null ? "a definir" : brl((BigDecimal) o.get("total_cost"))) + " · Situação: **" + st + "**", List.of(act("Abrir pedido", "pedidos", o.get("id"))));
  }

  private static String statusPt(String s, Object dispute) {
    if ("OPEN".equals(Objects.toString(dispute, ""))) return "em disputa";
    switch (s) {
      case "OPEN": return "aguardando profissional";
      case "CONFIRMED": return "confirmado";
      case "AWAITING_PAYMENT": return "pago, no cofre";
      case "IN_PROGRESS": return "em andamento";
      case "PENDING_CLIENT_CONFIRMATION": return "esperando o cliente aprovar";
      case "DONE": case "REVIEWED": return "concluído";
      case "CANCELLED": return "cancelado";
      default: return s.toLowerCase();
    }
  }

  /** Procura uma pessoa citada pelo nome. */
  private Map<String, Object> person(String raw, String t) {
    Set<String> stop = new HashSet<>(Arrays.asList("quanto", "quantos", "quantas", "pedidos", "pedido", "da", "do", "de", "a", "o", "e", "ganhou", "ganha", "recebeu", "nota", "ficha", "abrir", "abre", "mostra", "mostrar", "ver", "esse", "essa", "este", "mes", "semana", "hoje", "ano", "como", "esta", "qual", "quem", "tem", "servicos", "servico", "cliente", "profissional", "sobre", "me", "fala", "dados", "info", "que"));
    List<String> words = new ArrayList<>();
    for (String w : t.trim().split(" ")) if (w.length() >= 3 && !stop.contains(w)) words.add(w);
    if (words.isEmpty() || words.size() > 3) return null;
    String like = "%" + String.join("%", words) + "%";
    List<Map<String, Object>> r = q("SELECT u.id, u.name, (SELECT STRING_AGG(role, ',') FROM user_role WHERE user_id = u.id) AS roles FROM user_ u WHERE u.username NOT LIKE 'painel:%' AND " +
        "LOWER(TRANSLATE(u.name, 'áàâãéêíóôõúçÁÀÂÃÉÊÍÓÔÕÚÇ', 'aaaaeeiooouc' || 'aaaaeeiooouc')) LIKE ? ORDER BY u.id DESC LIMIT 5", like);
    if (r.isEmpty()) return null;
    if (r.size() > 1) {
      StringBuilder b = new StringBuilder("Achei " + r.size() + " pessoas com esse nome:");
      List<Map<String, Object>> acts = new ArrayList<>();
      for (Map<String, Object> x : r) { b.append("\n• ").append(x.get("name")); if (acts.size() < 3) acts.add(act("Abrir " + x.get("name"), "user", x.get("id"))); }
      return reply(b.toString(), acts);
    }
    Map<String, Object> u = r.get(0);
    Long id = ((Number) u.get("id")).longValue();
    boolean pro = Objects.toString(u.get("roles"), "").contains("PROFESSIONAL");
    LocalDateTime month = LocalDate.now(Z).withDayOfMonth(1).atStartOfDay();
    StringBuilder b = new StringBuilder("**" + u.get("name") + "** (" + (pro ? "profissional" : "cliente") + ")");
    if (pro) {
      BigDecimal earned = m("SELECT COALESCE(SUM(amount),0) FROM wallet_entry_ WHERE professional_id = ? AND kind IN ('CREDIT','STAGE') AND created_at >= ?", id, ts(month));
      long done = n("SELECT COUNT(*) FROM order_ WHERE professional_id = ? AND status IN ('DONE','REVIEWED')", id);
      BigDecimal cof = m("SELECT COALESCE(SUM(o.service_value),0) FROM order_ o WHERE o.professional_id = ? AND " + PAID + " AND COALESCE(o.pix_released,FALSE) = FALSE AND o.status <> 'CANCELLED'", id);
      Object rating = q("SELECT ROUND(AVG(rating)::numeric,1) AS r FROM review_ WHERE professional_id = ? AND rating IS NOT NULL", id).stream().findFirst().map(x -> x.get("r")).orElse(null);
      b.append("\nRecebeu **").append(brl(earned)).append("** neste mês · ").append(done).append(" serviço(s) concluído(s)").append(rating != null ? " · ★ " + rating.toString().replace('.', ',') : "");
      if (cof.signum() > 0) b.append("\nAinda tem ").append(brl(cof)).append(" guardado no cofre.");
    } else {
      long cnt = n("SELECT COUNT(*) FROM order_ WHERE customer_id = ?", id);
      BigDecimal spent = m("SELECT COALESCE(SUM(o.total_cost - COALESCE(o.discount_amount,0)),0) FROM order_ o WHERE o.customer_id = ? AND " + PAID, id);
      b.append("\nFez ").append(cnt).append(" pedido(s) e pagou ").append(brl(spent)).append(" no total.");
    }
    long disp = n("SELECT COUNT(*) FROM order_ WHERE (customer_id = ? OR professional_id = ?) AND dispute_status = 'OPEN'", id, id);
    if (disp > 0) b.append("\n⚠️ Tem ").append(disp).append(" disputa(s) aberta(s).");
    return reply(b.toString(), List.of(act("Abrir ficha", "user", id), act("Ver pedidos", "pedidos", "q:" + u.get("name"))));
  }

  private Map<String, Object> navigate(String t) {
    String[][] map = {{"financ", "fin", "Financeiro"}, {"usuario", "usuarios", "Usuários"}, {"pedido", "pedidos", "Pedidos"}, {"config", "config", "Configurações"}, {"equipe", "equipe", "Equipe"},
        {"rede social", "social", "Rede social"}, {"aviso", "avisos", "Avisos"}, {"golpe", "golpes", "Alertas de golpe"}, {"mapa", "demanda", "Mapa de demanda"}, {"inicio", "inicio", "Início"}};
    for (String[] x : map) if (t.contains(x[0])) return reply("Abrindo **" + x[2] + "**.", List.of(act("Abrir " + x[2], x[1], null)));
    return null;
  }

  private Map<String, Object> explain(String t) {
    int h = Math.max(1, settings.getInt(SettingsService.DISPUTE_TIMEOUT_HOURS));
    boolean refund = "true".equalsIgnoreCase(settings.get(SettingsService.DISPUTE_TIMEOUT_REFUND));
    if (has(t, " disputa", " 2h", " 2 h", " prazo")) return reply("Quando o cliente abre uma disputa, o dinheiro fica **travado por até " + h + " h**. Você decide em Pedidos (liberar ao profissional ou devolver ao cliente). Se ninguém decidir no prazo, o valor " + (refund ? "**volta para o cliente**" : "**vai para o profissional**") + ". Você muda isso em Configurações.", List.of(act("Abrir Configurações", "config", null)));
    if (has(t, " etapa")) return reply("No **pagamento por etapas**, o profissional divide um serviço grande (ex.: 30/40/30). O cliente paga tudo no cofre; a cada etapa o profissional marca \"Terminei\", o cliente aprova e só aquela parte vai para a carteira dele. A última parte sai com o código no final.", List.of());
    if (has(t, " cofre", " pagamento", " pago", " pix")) return reply("O cliente paga por PIX o serviço + 5% de taxa. O dinheiro fica **guardado no cofre da OPUS** até o cliente conferir o serviço e mostrar o código. Aí o valor do serviço vai para a carteira do profissional, que saca por PIX quando quiser. A taxa fica com a OPUS.", List.of(act("Abrir Financeiro", "fin", null)));
    if (has(t, " sos", " emergencia")) return reply("Quando alguém toca no **SOS**, o app avisa o contato de confiança, grava áudio, manda a localização e o painel toma a tela com uma sirene até alguém clicar em \"Estou acompanhando\". Enquanto o SOS estiver ativo, a liberação automática do pagamento daquele pedido fica pausada.", List.of(act("Abrir SOS", "sos", null)));
    if (has(t, " saque", " carteira")) return reply("O profissional vê o dinheiro liberado na aba **Ganhos** e saca por PIX na chave cadastrada. Se o banco recusar, o valor volta para a carteira e aparece um aviso aqui no sino.", List.of());
    if (has(t, " verific", " document")) return reply("O profissional manda frente e verso do documento e selfies. Você aprova ou recusa em **Verificações**. Só profissionais verificados aparecem com o selo azul.", List.of(act("Abrir Verificações", "verif", null)));
    if (has(t, " orcamento", " comparar")) return reply("O cliente pode pedir **orçamento para 2 ou 3 profissionais** de uma vez. Cada um responde no chat, o cliente compara preço, materiais, garantia e nota numa tela só e escolhe. Os outros pedidos são cancelados sem custo.", List.of());
    if (has(t, " ponto", " pontos")) return reply("Os clientes ganham **pontos** a cada serviço pago e trocam por desconto (cada 10 pontos = R$ 1). A reserva para cobrir os pontos aparece em Financeiro.", List.of(act("Abrir Financeiro", "fin", null)));
    if (has(t, " taxa")) return reply("A OPUS cobra **5% em cima do valor do serviço**, pagos pelo cliente. O profissional recebe 100% do que cobrou.", List.of());
    return null;
  }

  // ───────────── apoio às telas novas ─────────────

  @GetMapping("/orders/{id}/stages")
  public List<Map<String, Object>> orderStages(@PathVariable UUID id) {
    return q("SELECT s.idx, s.percent, s.label, s.status, ROUND(COALESCE(o.service_value, o.total_cost/1.05) * s.percent / 100.0, 2) AS value " +
        "FROM order_stage_ s JOIN order_ o ON o.id = s.order_id WHERE s.order_id = ? ORDER BY s.idx", id);
  }

  @GetMapping("/chat/audio/{id}")
  public org.springframework.http.ResponseEntity<byte[]> chatAudio(@PathVariable UUID id) {
    List<Map<String, Object>> r = q("SELECT content_type, data FROM chat_audio_ WHERE id = ?", id);
    if (r.isEmpty()) return org.springframework.http.ResponseEntity.notFound().build();
    return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.parseMediaType(Objects.toString(r.get(0).get("content_type"), "audio/m4a"))).body((byte[]) r.get(0).get("data"));
  }
}
