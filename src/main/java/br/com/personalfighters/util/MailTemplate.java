package br.com.personalfighters.util;

import java.util.ArrayList;
import java.util.List;

/**
 * E-mails com a cara do OPUS: topo azul-marinho com a marca, foto, título, texto, código em destaque,
 * números, lista, botão e rodapé. Tabelas e estilos em linha (é o que os programas de e-mail entendem).
 * Também gera a versão só texto, para quem não abre HTML.
 */
public class MailTemplate {

  public static String base() {
    String b = System.getenv("PUBLIC_URL");
    if (b == null || b.isBlank()) b = "https://opus-backend-o8kw.onrender.com";
    return b.replaceAll("/+$", "");
  }

  private static final String NAVY = "#0B1530";
  private static final String TEXT = "#1F2937";
  private static final String MUTED = "#6B7280";
  private static final String FONT = "font-family:-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;";

  private final boolean pro;
  private String preheader = "";
  private String hero;
  private String kicker;
  private String title = "";
  private String unsubscribe;
  private final StringBuilder body = new StringBuilder();
  private final StringBuilder text = new StringBuilder();

  private MailTemplate(boolean pro) { this.pro = pro; }

  public static MailTemplate client() { return new MailTemplate(false); }
  public static MailTemplate pro() { return new MailTemplate(true); }

  private String accent() { return pro ? "#0F7A4F" : "#1D4ED8"; }
  private String button() { return pro ? "#0F5132" : NAVY; }

  public static String esc(String s) {
    if (s == null) return "";
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
  }

  /** Linha curta que aparece na caixa de entrada, ao lado do assunto. */
  public MailTemplate preheader(String s) { preheader = s; return this; }
  /** Foto do topo (arquivo em /email/). */
  public MailTemplate hero(String file) { hero = file; return this; }
  public MailTemplate kicker(String s) { kicker = s; return this; }

  public MailTemplate title(String s) {
    title = s;
    text.append(s).append("\n\n");
    return this;
  }

  public MailTemplate p(String s) {
    body.append("<p style=\"margin:0 0 16px;").append(FONT).append("font-size:16px;line-height:25px;color:").append(TEXT).append(";\">")
        .append(esc(s).replace("\n", "<br>")).append("</p>");
    text.append(s).append("\n\n");
    return this;
  }

  /** Parágrafo com trechos em negrito: use **assim**. */
  public MailTemplate rich(String s) {
    String h = esc(s).replaceAll("\\*\\*(.+?)\\*\\*", "<strong style=\"color:" + NAVY + ";\">$1</strong>").replace("\n", "<br>");
    body.append("<p style=\"margin:0 0 16px;").append(FONT).append("font-size:16px;line-height:25px;color:").append(TEXT).append(";\">").append(h).append("</p>");
    text.append(s.replace("**", "")).append("\n\n");
    return this;
  }

  /** Código de 6 números em destaque. */
  public MailTemplate code(String code, String note) {
    StringBuilder digits = new StringBuilder();
    for (char c : code.toCharArray()) {
      digits.append("<td style=\"padding:0 4px;\"><div style=\"width:44px;height:56px;line-height:56px;border-radius:10px;background:#F1F4F9;border:1px solid #DCE2EC;")
          .append(FONT).append("font-size:28px;font-weight:700;color:").append(NAVY).append(";text-align:center;\">").append(esc(String.valueOf(c))).append("</div></td>");
    }
    body.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" align=\"center\" style=\"margin:8px auto 14px;\"><tr>").append(digits).append("</tr></table>");
    if (note != null) body.append("<p style=\"margin:0 0 20px;").append(FONT).append("font-size:13px;color:").append(MUTED).append(";text-align:center;\">").append(esc(note)).append("</p>");
    text.append("Código: ").append(code).append("\n").append(note == null ? "" : note + "\n").append("\n");
    return this;
  }

  /** Caixa de destaque (alerta de segurança, aviso importante). */
  public MailTemplate alert(String s, boolean danger) {
    String border = danger ? "#DC2626" : accent(), bg = danger ? "#FEF2F2" : (pro ? "#ECFDF5" : "#EFF4FF");
    body.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:4px 0 20px;\"><tr><td style=\"background:").append(bg)
        .append(";border-left:4px solid ").append(border).append(";border-radius:8px;padding:14px 16px;").append(FONT).append("font-size:15px;line-height:23px;color:").append(TEXT).append(";\">")
        .append(esc(s).replace("\n", "<br>")).append("</td></tr></table>");
    text.append(s).append("\n\n");
    return this;
  }

  /** Números lado a lado (até 3 por linha). */
  public MailTemplate stats(List<String[]> items) {
    body.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:4px 0 20px;\">");
    for (int i = 0; i < items.size(); i += 3) {
      body.append("<tr>");
      for (int j = i; j < i + 3; j++) {
        if (j < items.size()) {
          String[] it = items.get(j);
          int fs = it[1].length() > 9 ? 17 : it[1].length() > 6 ? 19 : 22;
          body.append("<td width=\"33%\" valign=\"top\" style=\"padding:4px;\"><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr>")
              .append("<td height=\"78\" valign=\"middle\" style=\"height:78px;background:#F6F8FB;border:1px solid #E5E9F0;border-radius:12px;padding:8px 6px;text-align:center;\">")
              .append("<div style=\"").append(FONT).append("font-size:").append(fs).append("px;font-weight:700;white-space:nowrap;color:").append(NAVY).append(";\">").append(esc(it[1])).append("</div>")
              .append("<div style=\"").append(FONT).append("font-size:12px;line-height:15px;color:").append(MUTED).append(";margin-top:4px;\">").append(esc(it[0])).append("</div></td></tr></table></td>");
          text.append("- ").append(it[0]).append(": ").append(it[1]).append("\n");
        } else body.append("<td width=\"33%\"></td>");
      }
      body.append("</tr>");
    }
    body.append("</table>");
    text.append("\n");
    return this;
  }

  /** Lista com marcadores (✓). */
  public MailTemplate checklist(List<String> items) {
    body.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 0 20px;\">");
    int n = 1;
    for (String it : items) {
      body.append("<tr><td valign=\"top\" style=\"padding:6px 12px 6px 0;\"><div style=\"width:26px;height:26px;line-height:26px;border-radius:13px;background:").append(accent())
          .append(";color:#fff;text-align:center;").append(FONT).append("font-size:13px;font-weight:700;\">").append(n++).append("</div></td>")
          .append("<td style=\"padding:6px 0;").append(FONT).append("font-size:15px;line-height:23px;color:").append(TEXT).append(";\">")
          .append(esc(it).replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>")).append("</td></tr>");
      text.append(n - 1).append(". ").append(it.replace("**", "")).append("\n");
    }
    body.append("</table>");
    text.append("\n");
    return this;
  }

  /** Linhas "rótulo — valor" (detalhes de um pedido). */
  public MailTemplate details(List<String[]> rows) {
    body.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 0 20px;border:1px solid #E5E9F0;border-radius:12px;\">");
    boolean first = true;
    for (String[] r : rows) {
      body.append("<tr><td style=\"padding:12px 16px;").append(first ? "" : "border-top:1px solid #EEF1F5;").append(FONT).append("font-size:14px;color:").append(MUTED).append(";\">").append(esc(r[0]))
          .append("</td><td align=\"right\" style=\"padding:12px 16px;").append(first ? "" : "border-top:1px solid #EEF1F5;").append(FONT).append("font-size:14px;font-weight:600;color:").append(TEXT).append(";\">").append(esc(r[1])).append("</td></tr>");
      text.append(r[0]).append(": ").append(r[1]).append("\n");
      first = false;
    }
    body.append("</table>");
    text.append("\n");
    return this;
  }

  public MailTemplate button(String label, String url) {
    body.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:6px 0 22px;\"><tr><td style=\"border-radius:12px;background:").append(button()).append(";\">")
        .append("<a href=\"").append(esc(url)).append("\" style=\"display:inline-block;padding:15px 28px;").append(FONT)
        .append("font-size:16px;font-weight:700;color:#ffffff;text-decoration:none;border-radius:12px;\">").append(esc(label)).append("</a></td></tr></table>");
    text.append(label).append(": ").append(url).append("\n\n");
    return this;
  }

  public MailTemplate small(String s) {
    body.append("<p style=\"margin:0 0 12px;").append(FONT).append("font-size:13px;line-height:20px;color:").append(MUTED).append(";\">").append(esc(s)).append("</p>");
    text.append(s).append("\n\n");
    return this;
  }

  /** E-mails de novidade/lembrete levam o link para parar de receber. */
  public MailTemplate unsubscribe(String url) { unsubscribe = url; return this; }

  /** Link que abre o app (ou explica como abrir, se for no computador). */
  public static String openApp(boolean pro, String path) {
    return base() + "/abrir/" + (pro ? "profissional" : "cliente") + (path == null || path.isBlank() ? "" : "?p=" + path);
  }

  public String text() {
    StringBuilder t = new StringBuilder(text);
    t.append("Equipe OPUS\n");
    if (unsubscribe != null) t.append("\nNão quer mais receber estes e-mails? ").append(unsubscribe).append("\n");
    return t.toString();
  }

  public String html() {
    String b = base();
    StringBuilder h = new StringBuilder();
    h.append("<!DOCTYPE html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        .append("<meta name=\"color-scheme\" content=\"light\"><title>").append(esc(title)).append("</title></head>")
        .append("<body style=\"margin:0;padding:0;background:#EEF1F6;\">")
        .append("<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;\">").append(esc(preheader)).append("&#8199;&#65279;&#847;</div>")
        .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#EEF1F6;\"><tr><td align=\"center\" style=\"padding:24px 12px;\">")
        .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:600px;background:#ffffff;border-radius:18px;overflow:hidden;\">")
        // topo
        .append("<tr><td style=\"background:").append(pro ? "#0A1F1A" : NAVY).append(";padding:20px 28px;\"><table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\"><tr>")
        .append("<td style=\"background:#ffffff;border-radius:10px;width:40px;height:40px;text-align:center;vertical-align:middle;\"><img src=\"").append(b).append("/email/logo.png\" width=\"30\" height=\"30\" alt=\"OPUS\" style=\"display:block;margin:5px auto;\"></td>")
        .append("<td style=\"padding-left:12px;").append(FONT).append("font-size:15px;font-weight:700;letter-spacing:4px;color:#ffffff;\">").append(pro ? "OPUS PRO" : "OPUS").append("</td>")
        .append("</tr></table></td></tr>");
    if (hero != null) {
      h.append("<tr><td><img src=\"").append(b).append("/email/").append(esc(hero)).append("\" width=\"600\" alt=\"\" style=\"display:block;width:100%;max-width:600px;height:auto;\"></td></tr>");
    }
    h.append("<tr><td style=\"padding:32px 28px 12px;\">");
    if (kicker != null) h.append("<div style=\"").append(FONT).append("font-size:12px;font-weight:700;letter-spacing:2px;color:").append(accent()).append(";margin-bottom:10px;\">").append(esc(kicker.toUpperCase())).append("</div>");
    h.append("<h1 style=\"margin:0 0 18px;").append(FONT).append("font-size:26px;line-height:32px;font-weight:700;color:").append(NAVY).append(";\">").append(esc(title)).append("</h1>")
        .append(body)
        .append("<p style=\"margin:8px 0 0;").append(FONT).append("font-size:15px;color:").append(TEXT).append(";\">Equipe OPUS</p>")
        .append("</td></tr>")
        // rodapé
        .append("<tr><td style=\"padding:22px 28px 28px;border-top:1px solid #EEF1F5;\">")
        .append("<p style=\"margin:0 0 6px;").append(FONT).append("font-size:12px;line-height:18px;color:#9CA3AF;\">Pagamento protegido · Profissionais com identidade verificada</p>")
        .append("<p style=\"margin:0;").append(FONT).append("font-size:12px;line-height:18px;color:#9CA3AF;\">Você recebeu este e-mail porque tem uma conta no OPUS. Nunca pedimos sua senha por e-mail.");
    if (unsubscribe != null) h.append(" <a href=\"").append(esc(unsubscribe)).append("\" style=\"color:#9CA3AF;text-decoration:underline;\">Não quero mais receber estes e-mails</a>.");
    h.append("</p></td></tr></table></td></tr></table></body></html>");
    return h.toString();
  }

  public static List<String[]> rows(String... kv) {
    List<String[]> l = new ArrayList<>();
    for (int i = 0; i + 1 < kv.length; i += 2) l.add(new String[]{kv[i], kv[i + 1]});
    return l;
  }
}
