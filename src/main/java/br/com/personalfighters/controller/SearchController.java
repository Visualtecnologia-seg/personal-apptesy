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

  static final List<Svc> DICT = List.of(
      new Svc("Eletricista", "Eletricista", "Tomadas, fiação, disjuntor, chuveiro", "parece um problema na parte elétrica", "Desligue o disjuntor daquele circuito até o profissional chegar.",
          "eletric", "chuveiro", "resistencia", "tomada", "fio", "fiacao", "disjuntor", "curto", "choque", "luz", "lampada", "interruptor", "quadro de luz", "energia", "queimou", "nao esquenta", "piscando", "faisca", "ventilador de teto", "lustre", "padrao de luz", "relogio de luz"),
      new Svc("Encanador", "Encanador", "Vazamentos, canos, torneiras, descarga", "parece um problema de água ou encanamento", "Feche o registro de água se estiver vazando.",
          "encan", "vazament", "vazando", "cano", "torneira", "descarga", "privada", "vaso sanitario", "registro", "infiltra", "pinga", "pingando", "goteira", "caixa d agua", "boia", "sifao", "agua quente", "pressao da agua", "hidraulic"),
      new Svc("Desentupidor", "Desentupidor", "Pia, ralo, vaso e esgoto entupidos", "parece entupimento", "Não use soda cáustica antes do profissional ver.",
          "entup", "ralo", "esgoto", "fossa", "caixa de gordura", "nao desce", "transbord", "mau cheiro no ralo"),
      new Svc("Técnico em Refrigeração", "Técnico em Refrigeração", "Geladeira, freezer, ar-condicionado", "parece algo na refrigeração", "Se a geladeira parou, mantenha a porta fechada.",
          "geladeira", "freezer", "refrigera", "nao gela", "parou de gelar", "gas da geladeira", "ar condicionado pingando", "ar nao gela", "frigobar"),
      new Svc("Instalador de Ar-condicionado", "Instalação de ar-condicionado", "Instalar, limpar e consertar ar", "é serviço de ar-condicionado", null,
          "ar condicionado", "ar-condicionado", "split", "instalar ar", "limpeza do ar", "higienizacao do ar"),
      new Svc("Chaveiro", "Chaveiro", "Abrir porta, trocar fechadura, cópia", "parece serviço de chaveiro", "Se for urgente, use o botão Urgente na tela inicial.",
          "chave", "chaveiro", "fechadura", "trancad", "tranquei", "porta emperrada", "perdi a chave", "segredo", "cadeado", "fechadura digital"),
      new Svc("Pintor", "Pintor", "Paredes, portas, grades e fachadas", "é serviço de pintura", null,
          "pint", "parede manchada", "mofo", "descascando", "textura", "grafiato", "verniz", "massa corrida", "repintar"),
      new Svc("Pedreiro", "Pedreiro", "Obras, reboco, muro, contrapiso", "é serviço de pedreiro", null,
          "pedreiro", "obra", "reboco", "muro", "rachadura", "trinca", "contrapiso", "alvenaria", "tijolo", "concreto", "laje", "calcada"),
      new Svc("Reformas em Geral", "Reformas em geral", "Reformas completas de casa e apartamento", "parece uma reforma", null,
          "reforma", "reformar", "renovar", "banheiro novo", "cozinha nova"),
      new Svc("Azulejista", "Azulejista", "Azulejo, porcelanato, rejunte", "é serviço de revestimento", null,
          "azulejo", "porcelanato", "rejunte", "ceramica", "revestimento", "piso solto"),
      new Svc("Instalador de Piso", "Instalação de piso", "Piso laminado, vinílico e porcelanato", "é instalação de piso", null,
          "piso", "laminado", "vinilico", "taco", "rodape"),
      new Svc("Gesseiro", "Gesseiro", "Gesso, sanca, drywall, forro", "é serviço de gesso", null,
          "gesso", "sanca", "drywall", "forro", "teto caindo", "moldura de gesso"),
      new Svc("Impermeabilizador", "Impermeabilização", "Laje, infiltração e umidade", "parece infiltração/umidade", null,
          "impermeabiliz", "umidade", "laje vazando", "infiltracao na parede"),
      new Svc("Instalador de Telhado", "Telhado", "Telhas, calhas e goteiras", "parece problema no telhado", null,
          "telhado", "telha", "calha", "rufo", "chovendo dentro"),
      new Svc("Vidraceiro", "Vidraceiro", "Vidros, box, espelhos, janelas", "é serviço de vidraçaria", null,
          "vidro", "box do banheiro", "espelho", "janela quebrada", "vidraca", "vidraceiro"),
      new Svc("Serralheiro", "Serralheiro", "Portões, grades e estruturas de metal", "é serviço de serralheria", null,
          "portao", "grade", "serralh", "solda", "ferro", "metalon", "corrimao"),
      new Svc("Marceneiro", "Marceneiro", "Móveis sob medida e reparos em madeira", "é serviço de marcenaria", null,
          "marcen", "movel sob medida", "armario", "madeira", "porta de madeira", "dobradica", "gaveta"),
      new Svc("Montador de Móveis", "Montador de móveis", "Montar e desmontar móveis", "é montagem de móveis", null,
          "montar", "montagem", "desmontar", "guarda roupa", "guarda-roupa", "movel novo", "estante", "rack"),
      new Svc("Limpeza Residencial", "Faxina / limpeza", "Faxina completa da casa", "é serviço de limpeza", null,
          "faxina", "limpeza", "limpar", "diarista", "faxineira", "casa suja", "passar roupa", "arrumar a casa"),
      new Svc("Limpeza Pós-Obra", "Limpeza pós-obra", "Limpeza pesada depois de obra", "é limpeza pós-obra", null,
          "pos obra", "pos-obra", "limpeza pesada", "sujeira de obra"),
      new Svc("Higienização de Estofados", "Higienização de estofados", "Sofá, colchão, tapete e cadeiras", "é limpeza de estofados", null,
          "sofa", "colchao", "estofado", "tapete", "carpete", "cadeira manchada"),
      new Svc("Limpeza de Piscina", "Limpeza de piscina", "Tratamento e limpeza de piscina", "é limpeza de piscina", null, "piscina", "agua verde"),
      new Svc("Dedetizador", "Dedetização", "Baratas, cupins, ratos e formigas", "parece caso de pragas", null,
          "barata", "cupim", "rato", "formiga", "pulga", "dedetiz", "praga", "inseto", "escorpiao", "percevejo"),
      new Svc("Jardineiro", "Jardineiro", "Jardim, grama, poda e plantas", "é serviço de jardinagem", null,
          "jardim", "grama", "gramado", "planta", "poda", "jardinag", "horta", "paisagismo"),
      new Svc("Lenhador / Poda de Árvores", "Poda de árvores", "Corte e poda de árvores", "é poda de árvore", null, "arvore", "galho", "cortar arvore"),
      new Svc("Instalador de Câmeras", "Câmeras de segurança", "Instalação de câmeras e CFTV", "é instalação de câmeras", null, "camera", "cftv", "monitoramento", "dvr"),
      new Svc("Técnico em Alarme", "Alarme e segurança", "Alarme, cerca elétrica, interfone", "é serviço de segurança eletrônica", null, "alarme", "cerca eletrica", "interfone", "portao eletronico", "motor do portao"),
      new Svc("Técnico em Energia Solar", "Energia solar", "Placas solares", "é serviço de energia solar", null, "solar", "placa solar", "painel solar", "fotovoltaic"),
      new Svc("Suporte em Informática", "Informática", "Computador, notebook, internet e Wi-Fi", "parece problema de informática", null,
          "computador", "notebook", "pc ", "lento", "virus", "formatar", "internet", "wifi", "wi-fi", "roteador", "impressora", "celular travando"),
      new Svc("Personal Trainer", "Personal trainer", "Treino em casa, emagrecer, ganhar massa", "você quer treinar com acompanhamento", null,
          "personal", "treino", "treinar", "academia", "emagrec", "perder peso", "barriga", "musculacao", "hipertrofia", "condicionamento", "funcional", "ganhar massa"),
      new Svc("Instrutor de Dança", "Aula de dança", "Samba, forró, zouk, ballet e mais", "você quer aprender a dançar", null,
          "danca", "dancar", "forro", "samba", "zouk", "salsa", "bachata", "ballet", "bale", "valsa", "danca de salao", "hip hop", "funk", "sertanejo"),
      new Svc("Instrutor de Yoga", "Yoga", "Aulas de yoga e meditação", "você procura yoga", null, "yoga", "ioga", "meditacao", "respiracao"),
      new Svc("Instrutor de Pilates", "Pilates", "Pilates em casa ou no estúdio", "você procura pilates", null, "pilates", "postura", "dor nas costas"),
      new Svc("Instrutor de Natação", "Natação", "Aulas de natação", "você procura natação", null, "natacao", "nadar", "aprender a nadar"),
      new Svc("Instrutor de Muay Thai", "Lutas", "Muay thai, boxe, jiu-jitsu", "você procura aula de luta", null, "luta", "muay", "boxe", "jiu", "karate", "mma", "defesa pessoal"),
      new Svc("Fisioterapeuta", "Fisioterapia", "Dores, lesões e reabilitação", "parece caso de fisioterapia", "Se a dor for forte ou começou de repente, procure um médico.",
          "fisio", "lesao", "torci", "reabilita", "dor no joelho", "dor no ombro", "coluna", "lombar"),
      new Svc("Massoterapeuta", "Massagem", "Massagem relaxante e terapêutica", "você procura massagem", null, "massagem", "relaxar", "drenagem", "tensao"),
      new Svc("Nutricionista", "Nutricionista", "Dieta e reeducação alimentar", "você procura um nutricionista", null, "dieta", "nutri", "alimentacao", "cardapio"),
      new Svc("Cuidador de Idosos", "Cuidador de idosos", "Acompanhante para idosos", "você procura um cuidador", null, "idoso", "cuidador", "acompanhante", "vovo", "vovó"),
      new Svc("Enfermeiro(a)", "Enfermagem", "Curativos, injeções e cuidados", "é serviço de enfermagem", null, "curativo", "injecao", "enferm", "soro", "aferir pressao"),
      new Svc("Psicólogo(a)", "Psicólogo", "Terapia e acompanhamento", "você procura um psicólogo", null, "psicolog", "terapia", "ansiedade", "depressao"),
      new Svc("Cabeleireiro(a)", "Cabeleireiro", "Corte, escova, química", "é serviço de cabelo", null, "cabelo", "corte de cabelo", "escova", "progressiva", "luzes", "tintura"),
      new Svc("Barbeiro", "Barbeiro", "Corte masculino e barba", "é serviço de barbearia", null, "barba", "barbeiro", "degrade"),
      new Svc("Manicure e Pedicure", "Manicure e pedicure", "Unhas das mãos e dos pés", "é serviço de unhas", null, "unha", "manicure", "pedicure", "esmalte", "alongamento de unha"),
      new Svc("Maquiador(a)", "Maquiagem", "Maquiagem para eventos", "é serviço de maquiagem", null, "maquiag", "make"),
      new Svc("Designer de Sobrancelhas", "Sobrancelhas", "Design e henna", "é serviço de sobrancelha", null, "sobrancelha", "henna"),
      new Svc("Banho e Tosa", "Banho e tosa", "Banho e tosa no seu endereço", "é cuidado para o seu pet", null, "banho no cachorro", "tosa", "banho e tosa"),
      new Svc("Dog Walker", "Passeador de cães", "Passeios com o seu cachorro", "você procura passeio para o pet", null, "passear com o cachorro", "passeador", "dog walker"),
      new Svc("Pet Sitter", "Pet sitter", "Cuidar do pet enquanto você viaja", "você procura quem cuide do pet", null, "pet sitter", "cuidar do gato", "cuidar do cachorro", "viajar e deixar o pet"),
      new Svc("Adestrador de Cães", "Adestrador", "Educação e comportamento", "você procura um adestrador", null, "adestra", "cachorro late", "cachorro morde", "comportamento do cachorro"),
      new Svc("Veterinário(a)", "Veterinário", "Consulta e vacinas em casa", "é cuidado veterinário", "Se o animal estiver muito mal, vá a uma emergência veterinária.", "veterin", "vacina do cachorro", "pet doente", "gato doente", "cachorro doente"),
      new Svc("Fotógrafo", "Fotógrafo", "Ensaios, eventos e produtos", "você procura um fotógrafo", null, "foto", "fotograf", "ensaio", "book"),
      new Svc("DJ para Festas", "DJ", "Som para festas", "você procura um DJ", null, "dj", "som para festa", "festa"),
      new Svc("Chef a Domicílio", "Chef em casa", "Jantar e eventos na sua casa", "você procura um chef", null, "chef", "cozinheiro", "jantar em casa", "churrasqueiro", "churrasco"),
      new Svc("Confeiteiro(a)", "Confeitaria", "Bolos e doces", "você procura confeitaria", null, "bolo", "doce", "confeit", "brigadeiro"),
      new Svc("Professor Particular", "Professor particular", "Reforço escolar e aulas", "você procura um professor", null, "reforco", "professor", "aula particular", "matematica", "vestibular", "enem", "dever de casa"),
      new Svc("Professor de Inglês", "Professor de inglês", "Aulas de inglês", "você quer aprender inglês", null, "ingles", "english"),
      new Svc("Motoboy", "Motoboy / entregas", "Entregas rápidas", "é serviço de entrega", null, "entrega", "motoboy", "levar um documento"),
      new Svc("Coletor de Entulho", "Fretes e entulho", "Retirada de entulho e móveis", "é transporte/retirada", null, "entulho", "frete", "mudanca", "carreto", "levar movel"),
      new Svc("Personal Organizer", "Personal organizer", "Organização de armários e casa", "você procura organização", null, "organizar", "organizacao", "bagunca"),
      new Svc("Programador(a)", "Programador", "Sites e sistemas", "é serviço de tecnologia", null, "site", "aplicativo", "sistema", "programa"),
      new Svc("Social Media", "Social media", "Instagram e redes sociais", "é serviço de redes sociais", null, "instagram", "redes sociais", "social media", "postagens"),
      new Svc("Advogado(a)", "Advogado", "Orientação jurídica", "é um assunto jurídico", null, "advogado", "processo", "juridic", "contrato"),
      new Svc("Contador(a)", "Contador", "Impostos e MEI", "é assunto de contabilidade", null, "imposto", "mei", "contador", "declaracao", "irpf")
  );

  static String norm(String s) {
    String t = java.text.Normalizer.normalize(Objects.toString(s, "").toLowerCase(), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    return " " + t.replaceAll("[^a-z0-9]+", " ").trim() + " ";
  }

  static int lev(String a, String b) {
    int[] p = new int[b.length() + 1], c = new int[b.length() + 1];
    for (int j = 0; j <= b.length(); j++) p[j] = j;
    for (int i = 1; i <= a.length(); i++) {
      c[0] = i;
      for (int j = 1; j <= b.length(); j++) c[j] = Math.min(Math.min(c[j - 1] + 1, p[j] + 1), p[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
      int[] t = p; p = c; c = t;
    }
    return p[b.length()];
  }

  /** Pontua o quanto a frase combina com o serviço (aceita erros pequenos de digitação). */
  static int score(String q, Svc s) {
    int sc = 0;
    String[] toks = q.trim().split(" ");
    for (String w : s.words()) {
      String nw = norm(w).trim();
      if (nw.isEmpty()) continue;
      if (q.contains(" " + nw) || q.contains(nw + " ") && nw.length() >= 5) { sc += nw.contains(" ") ? 4 : 3; continue; }
      if (!nw.contains(" ") && nw.length() >= 5) {
        for (String t : toks) if (t.length() >= 4 && (t.startsWith(nw) || (Math.abs(t.length() - nw.length()) <= 2 && lev(t, nw) <= (nw.length() >= 8 ? 2 : 1)))) { sc += 2; break; }
      }
    }
    String name = norm(s.label()).trim(), prod = norm(s.product()).trim();
    if (q.contains(name) || q.trim().length() >= 4 && (name.startsWith(q.trim()) || prod.startsWith(q.trim()))) sc += 6;
    else for (String t : toks) for (String nm : new String[]{name.split(" ")[0], prod.split(" ")[0]})
      if (t.length() >= 6 && nm.length() >= 6 && lev(t, nm) <= 2) { sc += 5; break; }
    return sc;
  }

  private static final String NAME_N = "LOWER(TRANSLATE(name, 'áàâãéêíóôõúçÁÀÂÃÉÊÍÓÔÕÚÇ', 'aaaaeeiooouc' || 'aaaaeeiooouc'))";

  /** Serviços do catálogo com esse nome (aceita acento e variações: Eletricista / Elétrica). */
  private List<Long> productIds(String productName) {
    String n = norm(productName).trim();
    List<Long> ids = jdbc.queryForList("SELECT id FROM product_ WHERE COALESCE(active, TRUE) = TRUE AND " + NAME_N + " LIKE ?", Long.class, n + "%");
    if (ids.isEmpty() && n.length() >= 6)
      ids = jdbc.queryForList("SELECT id FROM product_ WHERE COALESCE(active, TRUE) = TRUE AND " + NAME_N + " LIKE ?", Long.class, "%" + n.substring(0, 6) + "%");
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
    if (options.isEmpty()) {
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
            "(SELECT MAX(warranty_days) FROM order_ o WHERE o.professional_id = u.id) AS warranty " +
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
