package br.com.personalfighters.util;

/** Regra de senha para contas novas e senhas trocadas: 8+ caracteres, com letra e número. */
public final class PasswordRules {
  private PasswordRules() { }

  /** Devolve a mensagem do problema, ou null quando a senha serve. */
  public static String check(String p) {
    if (p == null || p.length() < 8) return "A senha precisa ter pelo menos 8 caracteres.";
    if (p.length() > 100) return "A senha está grande demais.";
    if (!p.matches(".*[A-Za-zÀ-ÿ].*") || !p.matches(".*\\d.*")) return "Use letras e números na senha.";
    return null;
  }
}
