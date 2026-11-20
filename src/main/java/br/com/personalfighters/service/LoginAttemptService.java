package br.com.personalfighters.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Bloqueio de login por senha errada, cada vez mais longo:
 * 5 erros = 1 min; de novo = 5 min; de novo = 15 min; daí em diante = 60 min.
 * O "nível" só zera depois de 24 h sem erro ou num login certo.
 */
@Component
public class LoginAttemptService {

  private static final int MAX_ATTEMPTS = 5;
  private static final long[] BLOCK_MS = {60_000L, 5 * 60_000L, 15 * 60_000L, 60 * 60_000L};
  private static final long FORGET_MS = 24 * 60 * 60_000L;

  private final ConcurrentHashMap<String, AttemptInfo> cache = new ConcurrentHashMap<>();

  public boolean isBlocked(String email) {
    AttemptInfo info = cache.get(normalize(email));
    if (info == null || info.blockedAt == 0) return false;
    long elapsed = System.currentTimeMillis() - info.blockedAt;
    if (elapsed >= duration(info)) {
      // bloqueio acabou: libera novas tentativas, mas lembra o nível
      info.count = 0;
      info.blockedAt = 0;
      return false;
    }
    return true;
  }

  public int getSecondsRemaining(String email) {
    AttemptInfo info = cache.get(normalize(email));
    if (info == null || info.blockedAt == 0) return 0;
    long remaining = (duration(info) - (System.currentTimeMillis() - info.blockedAt)) / 1000L;
    return remaining > 0 ? (int) remaining : 0;
  }

  /** Registra um erro. Devolve o nível do bloqueio que começou agora (0 = não bloqueou). */
  public synchronized int loginFailed(String email) {
    String key = normalize(email);
    AttemptInfo info = cache.computeIfAbsent(key, k -> new AttemptInfo());
    long now = System.currentTimeMillis();
    if (info.lastFail > 0 && now - info.lastFail > FORGET_MS) { info.level = 0; info.count = 0; }
    info.lastFail = now;
    if (info.blockedAt != 0) return 0;
    info.count++;
    if (info.count >= MAX_ATTEMPTS) {
      info.blockedAt = now;
      info.level = Math.min(info.level + 1, BLOCK_MS.length);
      return info.level;
    }
    return 0;
  }

  public void loginSucceeded(String email) {
    cache.remove(normalize(email));
  }

  public int getAttemptCount(String email) {
    AttemptInfo info = cache.get(normalize(email));
    return info == null ? 0 : info.count;
  }

  private static long duration(AttemptInfo i) { return BLOCK_MS[Math.max(0, Math.min(i.level, BLOCK_MS.length) - 1)]; }

  private String normalize(String email) {
    return email == null ? "" : email.toLowerCase().trim();
  }

  // ── Trava da conta inteira (somando todos os aparelhos/redes) ─────────────────
  // Mais folgada que a do aparelho: segura ataque espalhado em muitas redes, mas
  // não deixa um estranho trancar a conta do dono (o celular já conhecido da conta passa).
  private static final int WIDE_MAX = 20;
  private static final long WIDE_WINDOW_MS = 60 * 60_000L, WIDE_BLOCK_MS = 15 * 60_000L;
  private final ConcurrentHashMap<String, long[]> wide = new ConcurrentHashMap<>(); // {início, erros, travadoAté}
  private final ConcurrentHashMap<String, Long> lastAlert = new ConcurrentHashMap<>();

  /** Erro somado na conta. Devolve true se a conta acabou de ser travada agora. */
  public synchronized boolean wideFailed(String account) {
    long now = System.currentTimeMillis();
    long[] w = wide.computeIfAbsent(normalize(account), k -> new long[]{now, 0, 0});
    if (now - w[0] > WIDE_WINDOW_MS) { w[0] = now; w[1] = 0; }
    if (w[2] > now) return false;
    if (++w[1] >= WIDE_MAX) { w[2] = now + WIDE_BLOCK_MS; w[0] = now; w[1] = 0; return true; }
    return false;
  }

  public boolean wideBlocked(String account) {
    long[] w = wide.get(normalize(account));
    return w != null && w[2] > System.currentTimeMillis();
  }

  public int wideSeconds(String account) {
    long[] w = wide.get(normalize(account));
    return w == null ? 0 : (int) Math.max(0, (w[2] - System.currentTimeMillis()) / 1000L);
  }

  public void wideReset(String account) { wide.remove(normalize(account)); }

  /** E-mail de alerta de senha errada: no máximo 1 por dia para a mesma conta. */
  public boolean alertAllowed(String account) {
    long now = System.currentTimeMillis();
    Long last = lastAlert.get(normalize(account));
    if (last != null && now - last < FORGET_MS) return false;
    lastAlert.put(normalize(account), now);
    return true;
  }

  /** Limpa contadores velhos a cada hora. */
  @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 3_600_000)
  public void cleanup() {
    long now = System.currentTimeMillis();
    cache.entrySet().removeIf(e -> e.getValue().blockedAt == 0 && now - e.getValue().lastFail > FORGET_MS);
    wide.entrySet().removeIf(e -> e.getValue()[2] < now && now - e.getValue()[0] > WIDE_WINDOW_MS);
    lastAlert.entrySet().removeIf(e -> now - e.getValue() > FORGET_MS);
  }

  static class AttemptInfo {
    int count = 0;
    long blockedAt = 0;
    int level = 0;
    long lastFail = 0;
  }
}
