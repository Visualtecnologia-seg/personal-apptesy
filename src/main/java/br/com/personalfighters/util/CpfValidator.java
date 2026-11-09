package br.com.personalfighters.util;

/** Confere se o CPF é válido (dígitos verificadores da Receita) e recusa sequências como 111.111.111-11. */
public final class CpfValidator {
  private CpfValidator() { }

  public static String digits(String cpf) {
    return cpf == null ? "" : cpf.replaceAll("\\D", "");
  }

  public static boolean isValid(String cpf) {
    String d = digits(cpf);
    if (d.length() != 11 || d.chars().distinct().count() == 1) return false;
    return check(d, 9) == d.charAt(9) - '0' && check(d, 10) == d.charAt(10) - '0';
  }

  private static int check(String d, int len) {
    int sum = 0;
    for (int i = 0; i < len; i++) sum += (d.charAt(i) - '0') * (len + 1 - i);
    int r = (sum * 10) % 11;
    return r == 10 ? 0 : r;
  }
}
