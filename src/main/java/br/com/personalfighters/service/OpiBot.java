package br.com.personalfighters.service;

import br.com.personalfighters.controller.SearchController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Opi: primeiro atendimento do suporte, sem custo (dicionário de assuntos).
 * Os assuntos ficam em src/main/resources/support/opi-cliente.txt e opi-profissional.txt.
 */
@Slf4j
@Component
public class OpiBot {

  public static final class Topic {
    public final String id, kind; public final String[] keys; public final List<String> lines = new ArrayList<>(); public final List<String[]> buttons = new ArrayList<>();
    Topic(String id, String kind, String[] keys) { this.id = id; this.kind = kind; this.keys = keys; }
  }

  /** Resposta da Opi: texto, botões e se precisa chamar a equipe. */
  public static final class Reply {
    public final String topic, text; public final List<Map<String, String>> buttons; public final boolean escalate, priority;
    Reply(String topic, String text, List<Map<String, String>> buttons, boolean escalate, boolean priority) { this.topic = topic; this.text = text; this.buttons = buttons; this.escalate = escalate; this.priority = priority; }
  }

  private final List<Topic> client = load("/support/opi-cliente.txt");
  private final List<Topic> pro = load("/support/opi-profissional.txt");

  public List<Topic> topics(boolean professional) { return professional ? pro : client; }

  static List<Topic> load(String path) {
    List<Topic> out = new ArrayList<>();
    try (InputStream in = OpiBot.class.getResourceAsStream(path)) {
      if (in == null) return out;
      Topic cur = null;
      for (String raw : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\r?\\n")) {
        String l = raw.trim();
        if (l.isEmpty() || l.startsWith("#")) continue;
        if (l.startsWith("@")) { String[] h = l.substring(1).split("\\|"); cur = new Topic(h[0].trim(), h.length > 1 ? h[1].trim() : "normal", new String[0]); out.add(cur); continue; }
        if (cur == null) continue;
        if (l.startsWith("k:")) {
          String[] ks = Arrays.stream(l.substring(2).split(",")).map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
          Topic t = new Topic(cur.id, cur.kind, ks); t.lines.addAll(cur.lines); t.buttons.addAll(cur.buttons); out.set(out.size() - 1, t); cur = t;
        } else if (l.startsWith("r:")) cur.lines.add(l.substring(2).trim());
        else if (l.startsWith("b:")) { String[] b = l.substring(2).split("\\|", 2); if (b.length == 2) cur.buttons.add(new String[]{b[0].trim(), b[1].trim()}); }
      }
    } catch (Exception e) { log.error("Não consegui ler {}", path, e); }
    return out;
  }

  /** Pontua o assunto: frases inteiras valem mais; palavras longas aceitam começo e erro pequeno. */
  static int score(String q, Topic t) {
    int sc = 0;
    String[] toks = q.trim().split(" ");
    for (String k : t.keys) {
      String nk = SearchController.clean(SearchController.norm(k)).trim();
      if (nk.isEmpty()) continue;
      boolean hit = nk.length() < 5 ? q.contains(" " + nk + " ") : q.contains(" " + nk);
      if (hit) { sc += 3 + 3 * (nk.split(" ").length - 1); continue; }
      if (!nk.contains(" ") && nk.length() >= 6)
        for (String w : toks) if (w.length() >= 5 && Math.abs(w.length() - nk.length()) <= 1 && SearchController.lev(w, nk) <= 1) { sc += 2; break; }
    }
    return sc;
  }

  public Topic best(String text, boolean professional) {
    String q = SearchController.clean(SearchController.norm(text));
    Topic best = null; int bs = 0;
    for (Topic t : topics(professional)) { int s = score(q, t); if (s > bs) { bs = s; best = t; } }
    return bs >= 3 ? best : null;
  }

  public Topic byId(String id, boolean professional) { return topics(professional).stream().filter(t -> t.id.equals(id)).findFirst().orElse(null); }

  /** Monta a resposta para a pessoa. `misses` = quantas vezes seguidas a Opi não entendeu. */
  public Reply reply(String text, String action, String name, boolean professional, int misses) {
    String nome = name == null || name.isBlank() ? "" : name;
    if ("human".equals(action)) return render(byId("atendente", professional), nome);
    if ("ok".equals(action)) return render(byId("obrigado", professional), nome);
    Topic t = best(text, professional);
    if (t != null) return render(t, nome);
    if (misses >= 1) {
      return new Reply("nao-entendi", "Desculpa, " + nome + ", ainda não entendi. 🙏 Já chamei alguém da equipe OPUS para te ajudar. Pode continuar escrevendo aqui.",
          List.of(), true, false);
    }
    List<Map<String, String>> bs = new ArrayList<>();
    for (String[] b : professional
        ? new String[][]{{"Receber mais chamados", "send:Como recebo mais chamados?"}, {"Saque", "send:Como faço um saque?"}, {"Problema com um serviço", "send:Problema com um serviço"}}
        : new String[][]{{"Como achar um profissional", "send:Como acho um profissional?"}, {"Problema com um pedido", "send:Problema com um pedido"}, {"Pagamento", "send:Como funciona o pagamento?"}})
      bs.add(button(b[0], b[1]));
    bs.add(button("Falar com atendente", "humano"));
    return new Reply("nao-entendi", "Não tenho certeza se entendi, " + nome + ". 🤔 Pode me contar com outras palavras? Ou escolha um assunto:", bs, false, false);
  }

  Reply render(Topic t, String nome) {
    String text = t.lines.stream().map(l -> l.replace("{nome}", nome).replace(", !", "!").replace(" ,", ",")).collect(Collectors.joining("\n"));
    if (nome.isEmpty()) text = text.replace("Oi, !", "Oi!").replace(", ,", ",");
    List<Map<String, String>> bs = t.buttons.stream().map(b -> button(b[0], b[1])).collect(Collectors.toList());
    boolean escalate = !"normal".equals(t.kind);
    boolean chooser = bs.stream().anyMatch(b -> "send".equals(b.get("type")));
    if (!escalate && !t.id.equals("obrigado") && !t.id.equals("oi")) {
      if (!chooser) bs.add(button("Resolveu ✓", "ok"));
      bs.add(button("Falar com atendente", "humano"));
    }
    return new Reply(t.id, text, bs, escalate, "urgente".equals(t.kind));
  }

  static Map<String, String> button(String label, String target) {
    Map<String, String> b = new LinkedHashMap<>();
    b.put("label", label);
    if (target.equals("humano")) b.put("type", "human");
    else if (target.equals("ok")) b.put("type", "ok");
    else if (target.startsWith("send:")) { b.put("type", "send"); b.put("value", target.substring(5)); }
    else { b.put("type", "route"); b.put("value", target); }
    return b;
  }
}
