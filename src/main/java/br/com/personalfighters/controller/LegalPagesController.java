package br.com.personalfighters.controller;

import br.com.personalfighters.service.AccountEraseService;
import br.com.personalfighters.service.EmailService;
import br.com.personalfighters.util.MailTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Páginas públicas exigidas pelas lojas: Política de Privacidade, Termos de Uso
 * e o pedido de exclusão de conta pelo site (para quem não tem mais o app).
 * A exclusão só acontece depois de a pessoa confirmar pelo link enviado ao e-mail da conta.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class LegalPagesController {

  private static final ZoneId Z = ZoneId.of("America/Sao_Paulo");
  private final JdbcTemplate jdbc;
  private final EmailService emailService;
  private final AccountEraseService eraser;

  @Value("${opus.contato.email:}")
  private String contactEmail;

  private JsonNode legal;

  private JsonNode legal() {
    if (legal == null) {
      try { legal = new ObjectMapper().readTree(new ClassPathResource("legal/texts.json").getInputStream()); }
      catch (Exception e) { throw new IllegalStateException("Textos legais não encontrados", e); }
    }
    return legal;
  }

  @GetMapping("/privacidade")
  public ResponseEntity<String> privacy() { return doc("PRIVACY"); }

  @GetMapping("/termos")
  public ResponseEntity<String> terms() { return doc("TERMS"); }

  private ResponseEntity<String> doc(String key) {
    JsonNode d = legal().get(key);
    StringBuilder b = new StringBuilder();
    b.append("<h1>").append(esc(d.get("title").asText())).append("</h1><p class=\"up\">Atualizado em ").append(esc(d.get("updated").asText())).append("</p>");
    for (JsonNode s : d.get("sections")) {
      b.append("<h2>").append(esc(s.get("h").asText())).append("</h2><p>").append(esc(s.get("p").asText())).append("</p>");
    }
    if ("PRIVACY".equals(key)) {
      b.append("<h2>Como apagar sua conta</h2><p>No app: Perfil → Meus dados → Apagar minha conta. Sem o app: <a href=\"/excluir-conta\">peça a exclusão por aqui</a>.</p>");
    }
    b.append(contactBlock());
    return html(d.get("title").asText() + " · OPUS", b.toString());
  }

  // ───────────── Exclusão de conta pelo site ─────────────

  @GetMapping("/excluir-conta")
  public ResponseEntity<String> deleteForm() {
    return html("Apagar minha conta · OPUS",
        "<h1>Apagar minha conta</h1>" +
        "<p>Vale para o app <b>Cliente OPUS</b> e para o app <b>Profissional OPUS</b>.</p>" +
        "<div class=\"box\"><b>Pelo app (mais rápido)</b><br>Perfil → Meus dados → Apagar minha conta.</div>" +
        "<h2>Não tem mais o app?</h2><p>Digite o e-mail da sua conta. Vamos mandar um link para você confirmar. Ele vale por 1 hora.</p>" +
        "<form method=\"post\" action=\"/excluir-conta\"><input type=\"email\" name=\"email\" required maxlength=\"120\" placeholder=\"seu e-mail\" autocomplete=\"email\">" +
        "<button type=\"submit\">Enviar link de confirmação</button></form>" +
        whatHappens() + contactBlock());
  }

  @PostMapping(value = "/excluir-conta", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  public ResponseEntity<String> deleteRequest(@RequestParam(value = "email", required = false) String email) {
    String e = email == null ? "" : email.trim().toLowerCase();
    if (e.length() > 3 && e.length() <= 120 && e.contains("@")) {
      try {
        List<Map<String, Object>> u = jdbc.queryForList(
            "SELECT id, name, email FROM user_ WHERE LOWER(email) = ? AND deleted_at IS NULL AND username NOT LIKE 'painel:%' LIMIT 1", e);
        if (!u.isEmpty()) {
          long id = ((Number) u.get(0).get("id")).longValue();
          // No máximo 1 pedido a cada 10 minutos por conta (antes dava para lotar o e-mail de qualquer pessoa)
          Integer recent = jdbc.queryForObject("SELECT COUNT(*) FROM account_delete_token_ WHERE user_id = ? AND expires_at > ?", Integer.class, id, Timestamp.valueOf(LocalDateTime.now(Z).plusMinutes(50)));
          if (recent != null && recent > 0) throw new IllegalStateException("pedido repetido em menos de 10 minutos");
          byte[] raw = new byte[32];
          new SecureRandom().nextBytes(raw);
          String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
          jdbc.update("DELETE FROM account_delete_token_ WHERE user_id = ? OR expires_at < ?", id, now());
          jdbc.update("INSERT INTO account_delete_token_ (token_hash, user_id, expires_at) VALUES (?, ?, ?)", sha(token), id, Timestamp.valueOf(LocalDateTime.now(Z).plusHours(1)));
          String first = String.valueOf(u.get(0).get("name") == null ? "" : u.get(0).get("name")).trim().split(" ")[0];
          MailTemplate t = MailTemplate.client()
              .preheader("Confirme se você quer mesmo apagar sua conta no OPUS.")
              .kicker("Pedido de exclusão").title("Quer mesmo apagar sua conta?")
              .p("Olá" + (first.isEmpty() ? "" : ", " + first) + "! Recebemos um pedido para apagar a sua conta no OPUS pelo site.")
              .button("Confirmar a exclusão", MailTemplate.base() + "/excluir-conta/confirmar?t=" + token)
              .small("O link vale por 1 hora. Não foi você? Ignore este e-mail: nada vai acontecer com a sua conta.");
          emailService.send(String.valueOf(u.get(0).get("email")), "Confirme a exclusão da sua conta OPUS", t);
          log.info("LGPD: link de exclusão enviado para a conta {}", id);
        }
      } catch (Exception ex) { log.warn("Pedido de exclusão pelo site falhou: {}", ex.getMessage()); }
    }
    // Mesma resposta sempre: ninguém descobre por aqui se um e-mail tem conta
    return html("Confira seu e-mail · OPUS",
        "<h1>Confira seu e-mail</h1><p>Se existir uma conta com esse e-mail, enviamos um link para confirmar a exclusão. Ele vale por 1 hora.</p>" +
        "<p class=\"up\">Não chegou? Olhe a caixa de spam ou <a href=\"/excluir-conta\">tente de novo</a>.</p>" + contactBlock());
  }

  @GetMapping("/excluir-conta/confirmar")
  public ResponseEntity<String> deleteConfirmPage(@RequestParam(value = "t", required = false) String t) {
    // Só mostra o botão: programas de e-mail abrem links sozinhos, então a exclusão exige um clique de verdade
    if (valid(t) == null) return html("Link vencido · OPUS", "<h1>Este link não vale mais</h1><p>Ele venceu ou já foi usado. <a href=\"/excluir-conta\">Peça um novo</a>.</p>" + contactBlock());
    return html("Confirmar exclusão · OPUS",
        "<h1>Apagar sua conta de vez?</h1>" + whatHappens() +
        "<form method=\"post\" action=\"/excluir-conta/confirmar\"><input type=\"hidden\" name=\"t\" value=\"" + esc(t) + "\">" +
        "<button type=\"submit\" class=\"danger\">Sim, apagar minha conta</button></form>" +
        "<p class=\"up\">Mudou de ideia? É só fechar esta página.</p>");
  }

  @PostMapping(value = "/excluir-conta/confirmar", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  public ResponseEntity<String> deleteConfirm(@RequestParam(value = "t", required = false) String t) {
    Long id = valid(t);
    if (id == null) return html("Link vencido · OPUS", "<h1>Este link não vale mais</h1><p>Ele venceu ou já foi usado. <a href=\"/excluir-conta\">Peça um novo</a>.</p>" + contactBlock());
    String err = eraser.erase(id, "A pessoa apagou a conta pelo site (link do e-mail)");
    if (err != null) return html("Ainda não dá para apagar · OPUS", "<h1>Ainda não dá para apagar</h1><div class=\"box warn\">" + esc(err) + "</div><p>Depois de resolver, use o mesmo link de novo (ele vale por 1 hora) ou peça um novo.</p>" + contactBlock());
    jdbc.update("UPDATE account_delete_token_ SET used_at = ? WHERE token_hash = ?", now(), sha(t));
    return html("Conta apagada · OPUS", "<h1>Sua conta foi apagada</h1><p>Seus dados pessoais foram removidos. Os registros de pagamento ficam guardados sem os seus dados pessoais, como a lei exige.</p>");
  }

  private Long valid(String t) {
    if (t == null || t.length() < 20 || t.length() > 100) return null;
    List<Long> r = jdbc.queryForList("SELECT user_id FROM account_delete_token_ WHERE token_hash = ? AND used_at IS NULL AND expires_at > ?", Long.class, sha(t), now());
    return r.isEmpty() ? null : r.get(0);
  }

  private String whatHappens() {
    return "<h2>O que acontece</h2><ul>" +
        "<li>Seu nome, CPF, e-mail, celular, fotos, endereços, documentos e publicações são apagados.</li>" +
        "<li>Os registros de pedidos e pagamentos ficam guardados <b>sem os seus dados pessoais</b>, pelo prazo que a lei exige.</li>" +
        "<li>Antes, é preciso terminar ou cancelar pedidos em andamento e sacar o saldo, se tiver.</li>" +
        "<li>Não dá para desfazer.</li></ul>";
  }

  private String contactBlock() {
    String mail = contactEmail == null ? "" : contactEmail.trim();
    return "<p class=\"up\">Dúvidas: fale com o suporte pelo app (Perfil → Suporte)" +
        (mail.isEmpty() ? "" : " ou pelo e-mail <a href=\"mailto:" + esc(mail) + "\">" + esc(mail) + "</a>") + ".</p>";
  }

  private ResponseEntity<String> html(String title, String body) {
    String page = "<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
        "<meta name=\"robots\" content=\"index,follow\"><title>" + esc(title) + "</title><style>" +
        "body{margin:0;background:#F3F5F9;color:#0B1530;font-family:-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;line-height:1.55}" +
        ".top{background:linear-gradient(135deg,#0B1F4D,#1D4ED8);padding:18px 16px}.top a{color:#fff;font-weight:800;font-size:20px;text-decoration:none;letter-spacing:.5px}" +
        "main{max-width:720px;margin:0 auto;padding:20px 16px 48px}h1{font-size:26px;margin:8px 0 4px}h2{font-size:17px;margin:24px 0 4px}p,li{font-size:15px;color:#334155}" +
        ".up{color:#64748B;font-size:13.5px}a{color:#1D4ED8}.box{background:#fff;border:1px solid #E5E7EB;border-radius:14px;padding:14px;margin:14px 0;font-size:15px}" +
        ".warn{background:#FFFBEB;border-color:#FDE68A;color:#78350F}" +
        "form{display:flex;flex-direction:column;gap:10px;margin:12px 0}input{height:48px;border-radius:12px;border:1px solid #CBD5E1;padding:0 12px;font-size:16px}" +
        "button{height:50px;border:0;border-radius:14px;background:#0B1530;color:#fff;font-size:16px;font-weight:800;cursor:pointer}.danger{background:#B91C1C}" +
        "nav{margin-top:32px;font-size:13.5px;color:#64748B}nav a{margin-right:14px}" +
        "</style></head><body><div class=\"top\"><a href=\"/privacidade\">OPUS</a></div><main>" + body +
        "<nav><a href=\"/privacidade\">Privacidade</a><a href=\"/termos\">Termos de Uso</a><a href=\"/excluir-conta\">Apagar minha conta</a></nav></main></body></html>";
    return ResponseEntity.ok().contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
        .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'")
        .header("Referrer-Policy", "no-referrer")
        .body(page);
  }

  private static Timestamp now() { return Timestamp.valueOf(LocalDateTime.now(Z)); }

  private static String sha(String s) {
    try {
      byte[] h = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
      StringBuilder b = new StringBuilder();
      for (byte x : h) b.append(String.format("%02x", x));
      return b.toString();
    } catch (Exception e) { throw new IllegalStateException(e); }
  }

  private static String esc(String s) { return MailTemplate.esc(s); }
}
