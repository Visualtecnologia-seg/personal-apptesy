package br.com.personalfighters.controller;

import br.com.personalfighters.util.MailTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Página pública do profissional (/p/{id}), usada no "Compartilhar".
 * No WhatsApp/Instagram o link vira um cartão com foto, nome, nota e serviços.
 * Mostra só o que já é público no app (nada de contato, documento ou endereço).
 */
@RestController
@RequiredArgsConstructor
public class PublicProfileController {

  private final JdbcTemplate jdbc;

  @Value("${opus.download.cliente:https://expo.dev/artifacts/eas/y-z3b3YW34wIzoyxnrngeULPGsvRhrVAmGENc2PSQ1M.apk}")
  private String downloadUrl;

  @GetMapping("/p/{id}")
  public ResponseEntity<String> page(@PathVariable String id) {
    long uid;
    try { uid = Long.parseLong(id); } catch (Exception e) { return notFound(); }
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT u.id, u.name, u.surname, u.avatar_url, COALESCE(u.verified, FALSE) AS verified, p.about, p.years_experience, " +
            "(SELECT AVG(r.rating) FROM review_ r WHERE r.professional_id = u.id AND COALESCE(r.type,'CLIENT_TO_PRO') = 'CLIENT_TO_PRO') AS rating, " +
            "(SELECT COUNT(*) FROM review_ r WHERE r.professional_id = u.id AND COALESCE(r.type,'CLIENT_TO_PRO') = 'CLIENT_TO_PRO') AS reviews, " +
            "(SELECT COUNT(*) FROM order_ o WHERE o.professional_id = u.id AND o.status IN ('DONE','REVIEWED')) AS done " +
            "FROM user_ u JOIN professional_ p ON p.id = u.professional_id " +
            "WHERE u.id = ? AND COALESCE(u.active, TRUE) AND u.deleted_at IS NULL AND NOT COALESCE(u.blocked, FALSE) " +
            "AND COALESCE(u.signup_app, 'PRO') <> 'CLIENT' AND EXISTS (SELECT 1 FROM user_role r WHERE r.user_id = u.id AND r.role = 'PROFESSIONAL')", uid);
    if (rows.isEmpty()) return notFound();
    Map<String, Object> r = rows.get(0);
    List<String> services = jdbc.queryForList(
        "SELECT p.name FROM product__professional pp JOIN product_ p ON p.id = pp.product_id WHERE pp.professional_id = ? AND COALESCE(p.active, TRUE) ORDER BY p.name LIMIT 6",
        String.class, uid);
    List<Map<String, Object>> praise = ReviewController.topTags(jdbc, uid, 5);

    String base = MailTemplate.base();
    String first = Objects.toString(r.get("name"), "").trim();
    String sur = Objects.toString(r.get("surname"), "").trim();
    String name = (first + (sur.isEmpty() ? "" : " " + sur)).trim();
    if (name.isEmpty()) name = "Profissional";
    boolean hasPhoto = r.get("avatar_url") != null && !Objects.toString(r.get("avatar_url")).isBlank();
    String img = hasPhoto ? base + "/users/" + uid + "/avatar" : base + "/email/logo.png";
    long reviews = ((Number) r.get("reviews")).longValue();
    long done = ((Number) r.get("done")).longValue();
    String rating = r.get("rating") == null ? null : String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.1f", ((Number) r.get("rating")).doubleValue());
    String svc = services.isEmpty() ? "Profissional no OPUS" : String.join(" · ", services.subList(0, Math.min(3, services.size())));
    String desc = (rating != null ? "★ " + rating + " (" + reviews + " avaliaç" + (reviews == 1 ? "ão" : "ões") + ") · " : "") + svc + ". Contrate pelo app OPUS com pagamento protegido.";
    String deep = "opus-cliente://professionals/" + uid;
    String url = base + "/p/" + uid;
    boolean verified = Boolean.TRUE.equals(r.get("verified"));
    String about = Objects.toString(r.get("about"), "").trim();
    if (about.length() > 220) about = about.substring(0, 217) + "…";

    StringBuilder chips = new StringBuilder();
    for (String s : services) chips.append("<span class=\"chip\">").append(MailTemplate.esc(s)).append("</span>");
    StringBuilder pr = new StringBuilder();
    for (Map<String, Object> t : praise)
      pr.append("<span class=\"pz\">").append(MailTemplate.esc(Objects.toString(t.get("tag")))).append(" <b>").append(t.get("n")).append("</b></span>");

    String initials = name.substring(0, 1).toUpperCase();
    String html = "<!DOCTYPE html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
        "<title>" + MailTemplate.esc(name) + " no OPUS</title>" +
        "<meta name=\"description\" content=\"" + MailTemplate.esc(desc) + "\">" +
        "<meta property=\"og:type\" content=\"profile\"><meta property=\"og:site_name\" content=\"OPUS\">" +
        "<meta property=\"og:title\" content=\"" + MailTemplate.esc(name + " no OPUS") + "\">" +
        "<meta property=\"og:description\" content=\"" + MailTemplate.esc(desc) + "\">" +
        "<meta property=\"og:image\" content=\"" + MailTemplate.esc(img) + "\"><meta property=\"og:url\" content=\"" + MailTemplate.esc(url) + "\">" +
        "<meta name=\"twitter:card\" content=\"summary\">" +
        "<style>body{margin:0;background:#EEF1F6;font-family:-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#1F2937}" +
        ".top{background:linear-gradient(135deg,#0B1F4D,#1D4ED8 60%,#0891B2);height:120px}" +
        ".c{max-width:460px;margin:-60px auto 30px;padding:0 16px}" +
        ".card{background:#fff;border-radius:22px;padding:24px 22px;box-shadow:0 10px 30px rgba(11,21,48,.12);text-align:center}" +
        ".av{width:104px;height:104px;border-radius:52px;border:4px solid #fff;margin:-74px auto 10px;background:#DBEAFE;object-fit:cover;display:grid;place-items:center;font-size:40px;font-weight:800;color:#1D4ED8;box-shadow:0 6px 18px rgba(0,0,0,.15)}" +
        "h1{margin:6px 0 4px;font-size:24px;color:#0B1530}.ver{display:inline-block;background:#DCFCE7;color:#047857;font-size:12px;font-weight:700;padding:4px 10px;border-radius:99px;margin-bottom:8px}" +
        ".stats{display:flex;justify-content:center;gap:22px;margin:14px 0 6px}.st b{display:block;font-size:20px;color:#0B1530}.st span{font-size:12px;color:#64748B}" +
        ".chip{display:inline-block;background:#EFF4FF;color:#1D4ED8;font-weight:700;font-size:13px;padding:7px 12px;border-radius:99px;margin:4px}" +
        ".pz{display:inline-block;background:#FEF3C7;color:#92400E;font-weight:700;font-size:13px;padding:7px 12px;border-radius:99px;margin:4px}.pz b{color:#B45309}" +
        "h2{font-size:13px;letter-spacing:1px;color:#64748B;margin:18px 0 6px;text-transform:uppercase}p{line-height:1.55;color:#334155;margin:6px 0}" +
        "a.btn{display:block;margin-top:12px;padding:15px;border-radius:14px;background:#0B1530;color:#fff;font-weight:800;font-size:16px;text-decoration:none}" +
        "a.sec{background:#fff;color:#0B1530;border:1.5px solid #CBD3E1}.safe{margin-top:16px;font-size:13px;color:#475569;background:#F1F5F9;border-radius:14px;padding:12px}" +
        ".logo{display:flex;align-items:center;justify-content:center;gap:8px;margin-top:18px;color:#94A3B8;font-size:12px;letter-spacing:3px;font-weight:700}.logo img{width:20px}</style></head><body>" +
        "<div class=\"top\"></div><div class=\"c\"><div class=\"card\">" +
        (hasPhoto ? "<img class=\"av\" src=\"/users/" + uid + "/avatar\" alt=\"\">" : "<div class=\"av\">" + MailTemplate.esc(initials) + "</div>") +
        "<h1>" + MailTemplate.esc(name) + "</h1>" +
        (verified ? "<div class=\"ver\">✓ Identidade verificada</div>" : "") +
        "<div class=\"stats\">" +
        "<div class=\"st\"><b>" + (rating != null ? "★ " + rating : "Novo") + "</b><span>" + (rating != null ? reviews + " avaliaç" + (reviews == 1 ? "ão" : "ões") : "no OPUS") + "</span></div>" +
        "<div class=\"st\"><b>" + done + "</b><span>atendimento" + (done == 1 ? "" : "s") + "</span></div>" +
        (r.get("years_experience") != null ? "<div class=\"st\"><b>" + r.get("years_experience") + "</b><span>anos de experiência</span></div>" : "") +
        "</div>" +
        (chips.length() > 0 ? "<h2>Serviços</h2><div>" + chips + "</div>" : "") +
        (pr.length() > 0 ? "<h2>Elogios dos clientes</h2><div>" + pr + "</div>" : "") +
        (about.isEmpty() ? "" : "<h2>Sobre</h2><p>" + MailTemplate.esc(about) + "</p>") +
        "<a class=\"btn\" href=\"" + MailTemplate.esc(deep) + "\">Ver no app e contratar</a>" +
        "<a class=\"btn sec\" href=\"" + MailTemplate.esc(downloadUrl) + "\">Ainda não tenho o app · Baixar</a>" +
        "<div class=\"safe\">🔒 Pelo OPUS o pagamento fica guardado e só vai para o profissional depois que você aprova o serviço.</div>" +
        "</div><div class=\"logo\"><img src=\"/email/logo.png\" alt=\"\">OPUS</div></div></body></html>";
    return ResponseEntity.ok().contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8))
        .header("Cache-Control", "public, max-age=600").body(html);
  }

  private static ResponseEntity<String> notFound() {
    String html = "<!DOCTYPE html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>OPUS</title></head>" +
        "<body style=\"font-family:sans-serif;background:#EEF1F6;text-align:center;padding:60px 20px;color:#0B1530\"><h1>Perfil não encontrado</h1><p>Este profissional não está mais disponível no OPUS.</p></body></html>";
    return ResponseEntity.status(404).contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8)).body(html);
  }
}
