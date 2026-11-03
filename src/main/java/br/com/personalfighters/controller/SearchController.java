package br.com.personalfighters.controller;

import br.com.personalfighters.entity.User;
import br.com.personalfighters.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Busca que entende o problema ("meu chuveiro queimou") e indica o serviço (Eletricista),
 * depois lista só profissionais daquele serviço, perto do cliente.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class SearchController {

  private static final ZoneId Z = ZoneId.of("America/Sao_Paulo");
  private final JdbcTemplate jdbc;
  private final UserService userService;
  private final ProDayController proDay;
  private final ProGrowthController growth;

  /** Serviço do catálogo + palavras e frases do dia a dia que levam até ele. */
  static final class Svc {
    final String product, label, sub, why, tip; final String[] words;
    Svc(String product, String label, String sub, String why, String tip, String... words) { this.product = product; this.label = label; this.sub = sub; this.why = why; this.tip = tip; this.words = words; }
    String product() { return product; } String label() { return label; } String sub() { return sub; } String why() { return why; } String tip() { return tip; } String[] words() { return words; }
  }

  /** Dicionário (src/main/resources/search/dicionario.txt): fácil de aumentar sem mexer no código. */
  static final List<Svc> DICT = loadDict();

  static List<Svc> loadDict() {
    List<Svc> out = new ArrayList<>();
    try (java.io.InputStream in = SearchController.class.getResourceAsStream("/search/dicionario.txt")) {
      if (in == null) return out;
      String[] head = null;
      for (String raw : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\\r?\\n")) {
        String line = raw.trim();
        if (line.isEmpty() || line.startsWith("#")) continue;
        if (line.startsWith("@")) { head = line.substring(1).split("\\|", -1); continue; }
        if (head == null) continue;
        String[] words = Arrays.stream(line.split(",")).map(String::trim).filter(w -> !w.isEmpty()).toArray(String[]::new);
        String tip = head.length > 4 && !head[4].isBlank() ? head[4].trim() : null;
        String prod = head[0].trim();
        Svc prev = out.stream().filter(x -> x.product.equals(prod)).findFirst().orElse(null);
        if (prev != null) { // mesmo serviço em dois lugares: junta as palavras
          out.set(out.indexOf(prev), new Svc(prev.product, prev.label, prev.sub, prev.why, prev.tip != null ? prev.tip : tip,
              java.util.stream.Stream.concat(Arrays.stream(prev.words), Arrays.stream(words)).toArray(String[]::new)));
        } else out.add(new Svc(prod, head[1].trim(), head[2].trim(), head[3].trim(), tip, words));
        head = null;
      }
    } catch (Exception e) { log.error("Não consegui ler o dicionário da busca", e); }
    return out;
  }


  public static String norm(String s) {
    String t = java.text.Normalizer.normalize(Objects.toString(s, "").toLowerCase(), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    return " " + t.replaceAll("[^a-z0-9]+", " ").trim() + " ";
  }

  public static int lev(String a, String b) {
    int[] p = new int[b.length() + 1], c = new int[b.length() + 1];
    for (int j = 0; j <= b.length(); j++) p[j] = j;
    for (int i = 1; i <= a.length(); i++) {
      c[0] = i;
      for (int j = 1; j <= b.length(); j++) c[j] = Math.min(Math.min(c[j - 1] + 1, p[j] + 1), p[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
      int[] t = p; p = c; c = t;
    }
    return p[b.length()];
  }

  /** Primeiras palavras genéricas de nomes de serviço (não servem para adivinhar por semelhança). */
  static final Set<String> GENERIC_LABEL = Set.of("instalacao", "instalador", "instalacoes", "conserto", "tecnico", "limpeza", "professor", "instrutor", "reformas", "aula", "cuidador", "preparador", "servicos", "designer", "consultor", "assistente", "especialista", "lavagem", "higienizacao", "energia");

  /** Pontua o quanto a frase combina com o serviço (aceita erros pequenos de digitação). */
  /** Palavras que não ajudam a entender ("o", "da", "meu"...): somem da frase e do dicionário. */
  static final Set<String> STOP = Set.of("o", "a", "os", "as", "de", "do", "da", "dos", "das", "d", "um", "uma", "uns", "umas", "meu", "minha", "meus", "minhas",
      "no", "na", "nos", "nas", "em", "pra", "pro", "para", "por", "com", "que", "e", "ta", "esta", "estao", "ficou", "fica", "muito", "bem", "tambem", "ja", "agora",
      "eu", "voce", "vc", "seu", "sua", "me", "mim", "preciso", "precisa", "precisando", "quero", "queria", "gostaria", "alguem", "algum", "alguma", "ele", "ela", "isso", "aqui", "la", "sei", "nao sei", "favor", "socorro", "urgente", "hoje", "amanha");
  public static String clean(String s) {
    StringBuilder b = new StringBuilder(" ");
    for (String w : s.trim().split(" ")) if (!w.isEmpty() && !STOP.contains(w)) b.append(w).append(' ');
    return b.length() == 1 ? s : b.toString();
  }
  /** Todas as palavras soltas do dicionário: se a pessoa escreveu uma delas certinho, não precisa adivinhar por semelhança. */
  static final Set<String> ALL_WORDS = DICT.stream().flatMap(x -> Arrays.stream(x.words)).map(w -> norm(w).trim()).filter(w -> !w.contains(" ")).collect(Collectors.toSet());

  static int score(String q0, Svc s) {
    String q = clean(q0);
    int sc = 0;
    String[] toks = q.trim().split(" ");
    for (String w : s.words()) {
      String nw = clean(norm(w)).trim();
      if (nw.isEmpty()) continue;
      boolean hit = nw.length() < 5 ? q.contains(" " + nw + " ") : (q.contains(" " + nw) || q.contains(nw + " "));
      if (hit) { sc += 3 + 3 * (nw.split(" ").length - 1); continue; }
      if (!nw.contains(" ") && nw.length() >= 5) {
        for (String t : toks) if (t.length() >= 4 && !ALL_WORDS.contains(t) && (t.startsWith(nw) || (Math.abs(t.length() - nw.length()) <= 2 && lev(t, nw) <= (nw.length() >= 8 ? 2 : 1)))) { sc += 2; break; }
      }
    }
    String name = norm(s.label()).trim(), prod = norm(s.product()).trim();
    if (q.contains(" " + name + " ") || q.trim().length() >= 4 && (name.startsWith(q.trim()) || prod.startsWith(q.trim()))) sc += 4;
    else for (String t : toks) for (String nm : new String[]{name.split(" ")[0], prod.split(" ")[0]})
      if (t.length() >= 6 && nm.length() >= 6 && !GENERIC_LABEL.contains(nm) && !ALL_WORDS.contains(t) && lev(t, nm) <= (nm.length() >= 9 ? 2 : 1)) { sc += 4; break; }
    return sc;
  }

  private static final String NAME_N = "TRIM(REGEXP_REPLACE(LOWER(TRANSLATE(name, 'áàâãéêíóôõúçÁÀÂÃÉÊÍÓÔÕÚÇ', 'aaaaeeiooouc' || 'aaaaeeiooouc')), '[^a-z0-9]+', ' ', 'g'))";
  private static final Set<String> GENERIC = Set.of("instal", "tecnic", "limpez", "profes", "instru", "design", "consul", "especi", "assist", "cuidad", "operad");

  /** Serviços do catálogo com esse nome (aceita acento e variações: Eletricista / Elétrica). */
  private List<Long> productIds(String productName) {
    String n = norm(productName).trim();
    List<Long> ids = jdbc.queryForList("SELECT id FROM product_ WHERE COALESCE(active, TRUE) = TRUE AND " + NAME_N + " LIKE ?", Long.class, n + "%");
    String stem = n.length() >= 6 ? n.substring(0, 6) : "";
    if (ids.isEmpty() && !stem.isEmpty() && !GENERIC.contains(stem))
      ids = jdbc.queryForList("SELECT id FROM product_ WHERE COALESCE(active, TRUE) = TRUE AND " + NAME_N + " LIKE ?", Long.class, stem + "%");
    return ids;
  }

  /** Entende o que a pessoa escreveu e indica o serviço. */
  @GetMapping("/search/understand")
  public Map<String, Object> understand(@RequestParam String q, @RequestParam(required = false) Double lat, @RequestParam(required = false) Double lng) {
    String t = norm(q);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("q", q);
    List<Map.Entry<Svc, Integer>> ranked = DICT.stream().map(s -> Map.entry(s, score(t, s))).filter(e -> e.getValue() > 0)
        .sorted((a, b) -> b.getValue() - a.getValue()).limit(4).collect(Collectors.toList());
    // Também procura direto no catálogo (serviços que não estão no dicionário)
    List<Map<String, Object>> catalog = new ArrayList<>();
    if (t.trim().length() >= 3) {
      catalog = jdbc.queryForList("SELECT p.id, p.name, p.about, c.name AS category FROM product_ p LEFT JOIN category_ c ON c.id = p.category_id " +
          "WHERE COALESCE(p.active, TRUE) = TRUE AND (LOWER(TRANSLATE(p.name, 'áàâãéêíóôõúçÁÀÂÃÉÊÍÓÔÕÚÇ', 'aaaaeeiooouc' || 'aaaaeeiooouc')) LIKE ? " +
          "OR LOWER(TRANSLATE(COALESCE(p.about,''), 'áàâãéêíóôõúçÁÀÂÃÉÊÍÓÔÕÚÇ', 'aaaaeeiooouc' || 'aaaaeeiooouc')) LIKE ?) LIMIT 4", "%" + t.trim() + "%", "% " + t.trim() + "%");
    }
    List<Map<String, Object>> options = new ArrayList<>();
    Set<Long> used = new HashSet<>();
    for (Map.Entry<Svc, Integer> e : ranked) {
      Svc s = e.getKey();
      List<Long> ids = productIds(s.product());
      if (ids.isEmpty() || !used.addAll(ids)) continue;
      options.add(option(ids, s.label(), s.sub(), s.why(), s.tip(), lat, lng));
    }
    for (Map<String, Object> c : catalog) {
      Long id = ((Number) c.get("id")).longValue();
      if (used.add(id)) options.add(option(List.of(id), Objects.toString(c.get("name")), Objects.toString(c.get("about"), ""), null, null, lat, lng));
    }
    out.put("understood", !options.isEmpty());
    if (options.isEmpty() && !ranked.isEmpty()) {
      Svc s = ranked.get(0).getKey();
      out.put("understood", false);
      out.put("missing", s.label());
      out.put("message", "Entendi que você precisa de " + s.label().toLowerCase() + ", mas ainda não temos esse serviço no app. Veja os mais procurados ou conte de outro jeito.");
      out.put("popular", popular());
    } else if (options.isEmpty()) {
      out.put("message", "Não entendi bem. Tente contar de outro jeito, por exemplo \"tomada sem energia\" ou \"quero aprender a dançar\", ou escolha um dos serviços abaixo.");
      out.put("popular", popular());
    } else {
      Map<String, Object> top = options.get(0);
      String why = (String) top.remove("why");
      out.put("message", why != null ? "Entendi! " + Character.toUpperCase(why.charAt(0)) + why.substring(1) + ". Quem resolve é: " + top.get("label") + "."
          : "Encontrei o serviço " + top.get("label") + ".");
      for (Map<String, Object> o : options) o.remove("why");
      out.put("top", top);
      out.put("alternatives", options.subList(1, Math.min(options.size(), 3)));
    }
    return out;
  }

  private Map<String, Object> option(List<Long> ids, String label, String sub, String why, String tip, Double lat, Double lng) {
    Map<String, Object> o = new LinkedHashMap<>();
    o.put("productIds", ids);
    o.put("label", label);
    o.put("sub", sub);
    o.put("why", why);
    o.put("tip", tip);
    String in = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    List<Map<String, Object>> pros = jdbc.queryForList(
        "SELECT DISTINCT u.id, u.name, u.latitude AS lat, u.longitude AS lng, COALESCE(p.is_online, FALSE) AS online, COALESCE(p.hourly_rate, p.price) AS rate " +
            "FROM product__professional pp JOIN user_ u ON u.id = pp.professional_id JOIN professional_ p ON p.id = u.professional_id " +
            "WHERE pp.product_id IN (" + in + ") AND COALESCE(u.active, TRUE) = TRUE AND COALESCE(u.blocked, FALSE) = FALSE");
    List<Map<String, Object>> near = new ArrayList<>();
    for (Map<String, Object> p : pros) {
      Double d = dist(lat, lng, p.get("lat"), p.get("lng"));
      if (d == null || d <= 25) near.add(p);
    }
    o.put("pros", near.size());
    o.put("online", near.stream().filter(p -> Boolean.TRUE.equals(p.get("online"))).count());
    List<Double> rates = near.stream().map(p -> p.get("rate") == null ? null : ((Number) p.get("rate")).doubleValue()).filter(Objects::nonNull).sorted().collect(Collectors.toList());
    if (!rates.isEmpty()) { o.put("priceMin", Math.round(rates.get(0) * 1.05)); o.put("priceMax", Math.round(rates.get(rates.size() - 1) * 1.05)); }
    o.put("faces", near.stream().limit(3).map(p -> Objects.toString(p.get("name"), "?").substring(0, 1).toUpperCase()).collect(Collectors.toList()));
    return o;
  }

  static Double dist(Double lat, Double lng, Object la, Object ln) {
    if (lat == null || lng == null || la == null || ln == null) return null;
    try {
      double a = Double.parseDouble(la.toString()), b = Double.parseDouble(ln.toString());
      return br.com.personalfighters.service.AreaService.km(lat, lng, a, b);
    } catch (Exception e) { return null; }
  }

  /** Serviços mais procurados (para a tela inicial da busca). */
  @GetMapping("/search/popular")
  public List<Map<String, Object>> popular() {
    List<Map<String, Object>> r = jdbc.queryForList(
        "SELECT p.id, p.name, COUNT(o.id) AS n FROM product_ p LEFT JOIN order_ o ON o.product_id = p.id AND o.created_at > NOW() - INTERVAL '90 days' " +
            "WHERE COALESCE(p.active, TRUE) = TRUE GROUP BY p.id, p.name ORDER BY n DESC, p.name LIMIT 12");
    return r;
  }

  /** Profissionais de um serviço, perto do cliente, com filtros e ordem. */
  @GetMapping("/search/pros")
  public List<Map<String, Object>> pros(@RequestParam(defaultValue = "") String productIds, @RequestParam(required = false) Long categoryId, @RequestParam(required = false) Double lat, @RequestParam(required = false) Double lng,
                                        @RequestParam(defaultValue = "false") boolean online, @RequestParam(defaultValue = "false") boolean verified,
                                        @RequestParam(defaultValue = "false") boolean today, @RequestParam(required = false) Double minRating,
                                        @RequestParam(required = false) Double maxPrice, @RequestParam(defaultValue = "near") String sort) {
    User me = userService.findAuthUser();
    String in = Arrays.stream(productIds.split(",")).map(String::trim).filter(s -> s.matches("\\d+")).collect(Collectors.joining(","));
    if (in.isEmpty() && categoryId != null)
      in = jdbc.queryForList("SELECT id FROM product_ WHERE category_id = ? AND COALESCE(active, TRUE) = TRUE", Long.class, categoryId).stream().map(String::valueOf).collect(Collectors.joining(","));
    if (in.isEmpty()) return List.of();
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT DISTINCT u.id, u.name, u.avatar_url AS \"avatarUrl\", u.latitude AS lat, u.longitude AS lng, COALESCE(u.verified, FALSE) AS verified, " +
            "COALESCE(p.is_online, FALSE) AS online, COALESCE(p.hourly_rate, p.price, 0) AS rate, p.specialties, p.years_experience AS \"years\", " +
            "(SELECT ROUND(AVG(r.rating)::numeric, 1) FROM review_ r WHERE r.professional_id = u.id AND r.rating IS NOT NULL) AS rating, " +
            "(SELECT COUNT(*) FROM review_ r WHERE r.professional_id = u.id AND r.rating IS NOT NULL) AS reviews, " +
            "(SELECT COUNT(*) FROM order_ o WHERE o.professional_id = u.id AND o.status IN ('DONE','REVIEWED')) AS done, " +
            "(SELECT MAX(warranty_days) FROM order_ o WHERE o.professional_id = u.id) AS warranty, " +
            "(SELECT CAST(ph.id AS VARCHAR) FROM post_ po JOIN post_photo_ ph ON ph.post_id = po.id WHERE po.professional_id = u.id " +
            " ORDER BY CASE WHEN po.kind = 'WORK' THEN 0 ELSE 1 END, po.created_at DESC, ph.position LIMIT 1) AS \"workPhoto\" " +
            "FROM product__professional pp JOIN user_ u ON u.id = pp.professional_id JOIN professional_ p ON p.id = u.professional_id " +
            "WHERE pp.product_id IN (" + in + ") AND COALESCE(u.active, TRUE) = TRUE AND COALESCE(u.blocked, FALSE) = FALSE AND u.id <> ? " +
            "AND NOT EXISTS (SELECT 1 FROM pro_blocked_client_ b WHERE b.professional_id = u.id AND b.client_id = ?)", me.getId(), me.getId());
    Set<Long> boosted = new HashSet<>(growth.boostedIds());
    LocalDate d0 = LocalDate.now(Z);
    List<Map<String, Object>> out = new ArrayList<>();
    for (Map<String, Object> r : rows) {
      Long id = ((Number) r.get("id")).longValue();
      Double km = dist(lat, lng, r.get("lat"), r.get("lng"));
      r.remove("lat"); r.remove("lng");
      if (km != null && km > 40) continue;
      double rate = ((Number) r.get("rate")).doubleValue() * 1.05;
      if (online && !Boolean.TRUE.equals(r.get("online"))) continue;
      if (verified && !Boolean.TRUE.equals(r.get("verified"))) continue;
      if (minRating != null && (r.get("rating") == null || ((Number) r.get("rating")).doubleValue() < minRating)) continue;
      if (maxPrice != null && rate > maxPrice) continue;
      r.put("distanceKm", km == null ? null : Math.round(km * 10) / 10.0);
      r.put("price", Math.round(rate));
      r.put("featured", boosted.contains(id));
      Map<String, Object> slot = nextSlot(id, d0);
      r.put("nextSlot", slot);
      if (today && (slot == null || !d0.toString().equals(slot.get("date")))) continue;
      out.add(r);
    }
    Comparator<Map<String, Object>> cmp;
    switch (sort) {
      case "rating": cmp = Comparator.comparing((Map<String, Object> r) -> r.get("rating") == null ? 0.0 : ((Number) r.get("rating")).doubleValue()).reversed(); break;
      case "price": cmp = Comparator.comparing(r -> ((Number) r.get("price")).doubleValue()); break;
      default: cmp = Comparator.comparing(r -> r.get("distanceKm") == null ? 999.0 : (Double) r.get("distanceKm"));
    }
    out.sort(Comparator.comparing((Map<String, Object> r) -> Boolean.TRUE.equals(r.get("featured")) ? 0 : 1).thenComparing(cmp));
    return out;
  }

  /** Próximo horário livre (hoje ou amanhã), usando a agenda que o profissional configurou. */
  @SuppressWarnings("unchecked")
  private Map<String, Object> nextSlot(Long proId, LocalDate d0) {
    try {
      for (int i = 0; i < 3; i++) {
        LocalDate d = d0.plusDays(i);
        ResponseEntity<?> r = proDay.freeSlots(proId, d.toString(), 1);
        Object body = r.getBody();
        if (body instanceof Map) {
          Object s = ((Map<String, Object>) body).get("slots");
          if (s instanceof List && !((List<?>) s).isEmpty()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", d.toString());
            m.put("time", String.valueOf(((List<?>) s).get(0)));
            m.put("label", (i == 0 ? "Hoje " : i == 1 ? "Amanhã " : d.getDayOfMonth() + "/" + d.getMonthValue() + " ") + String.valueOf(((List<?>) s).get(0)));
            return m;
          }
        }
      }
    } catch (Exception e) { log.debug("slot {}: {}", proId, e.getMessage()); }
    return null;
  }
}
