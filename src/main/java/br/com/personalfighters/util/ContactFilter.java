package br.com.personalfighters.util;

import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Acha tentativa de passar contato ou combinar pagamento por fora no chat.
 * Pega também as formas disfarçadas: dígitos separados por espaço/ponto, números por extenso,
 * dígitos "largos" (２１９...), e-mail escrito como "arroba"/"ponto com", links de Telegram/WhatsApp.
 */
public final class ContactFilter {

  private ContactFilter() {}

  private static final List<Pattern> WORDS = List.of(
      Pattern.compile("\\b(whats ?app|whats|wpp|zap ?zap|zap|insta|instagram|telegram|tiktok|facebook|face|messenger|signal|discord|skype)\\b"),
      Pattern.compile("(wa\\.me|t\\.me|fb\\.com|instagram\\.com|ig\\.me|https?://|www\\.)"),
      Pattern.compile("@|\\barroba\\b|\\b(gmail|hotmail|outlook|yahoo|icloud|bol|uol)\\b|ponto com|\\.com\\b"),
      Pattern.compile("\\b(meu|minha|seu|sua|teu|tua) (telefone|celular|contato|zap|whats|whatsapp|wpp|email|e-mail|insta|chave pix)\\b"),
      Pattern.compile("\\b(meu|seu|teu) numero( de (telefone|celular|whats|whatsapp|zap|contato))?\\b(?!\\s*(do|da|de|dos|das)\\b)"),
      Pattern.compile("\\b((me )?(passa|manda|envia) (o |a )?(seu|sua|teu|tua) (numero|telefone|celular|contato|zap|whats|whatsapp|wpp|email|e-mail|insta)|chama no|fala comigo no|me add|me adiciona)\\b"),
      Pattern.compile("\\b(por fora|fora do app|fora da plataforma|sem o app|sem a plataforma|direto comigo|chave pix)\\b")
  );

  private static final Map<String, String> NUM = Map.ofEntries(
      Map.entry("zero", "0"), Map.entry("um", "1"), Map.entry("uma", "1"), Map.entry("dois", "2"), Map.entry("duas", "2"),
      Map.entry("tres", "3"), Map.entry("quatro", "4"), Map.entry("cinco", "5"), Map.entry("seis", "6"), Map.entry("meia", "6"),
      Map.entry("sete", "7"), Map.entry("oito", "8"), Map.entry("nove", "9"));

  private static final Pattern WORD = Pattern.compile("\\p{L}+");
  /** Separadores que as pessoas põem entre os dígitos do telefone (vírgula não: preço "1.250,00" não vira telefone). */
  private static final Pattern GLUE = Pattern.compile("(?<=\\d)[\\s.\\-()/+_*|]{1,3}(?=\\d)");
  private static final Pattern PHONE = Pattern.compile("\\d{10,13}|(?<!\\d)9\\d{8}(?!\\d)");

  /** Motivo do bloqueio, ou null se a mensagem pode passar. */
  public static String violation(String text) {
    if (text == null || text.isBlank()) return null;
    String s = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(java.util.Locale.ROOT);
    s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    for (Pattern p : WORDS) {
      Matcher m = p.matcher(s);
      if (m.find()) return "palavra:" + m.group();
    }
    // números por extenso viram dígitos ("nove oito sete..." → "987...")
    StringBuilder sb = new StringBuilder();
    Matcher w = WORD.matcher(s);
    int last = 0;
    while (w.find()) {
      sb.append(s, last, w.start());
      String d = NUM.get(w.group());
      sb.append(d != null ? d : w.group());
      last = w.end();
    }
    sb.append(s.substring(last));
    String digits = GLUE.matcher(sb.toString()).replaceAll("");
    digits = GLUE.matcher(digits).replaceAll("");
    if (PHONE.matcher(digits).find()) return "telefone";
    return null;
  }
}
