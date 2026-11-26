package br.com.personalfighters.util;

/** Célula de planilha segura: texto que começa com = + - @ vira texto (o Excel não executa como fórmula). */
public final class CsvCell {
  private CsvCell() {}

  public static String of(Object v) {
    String s = v == null ? "" : v.toString().replace(';', ',').replace('\r', ' ').replace('\n', ' ');
    if (!s.isEmpty() && "=+-@\t".indexOf(s.charAt(0)) >= 0 && !s.matches("-?[0-9.,]+")) s = "'" + s;
    return s;
  }
}
