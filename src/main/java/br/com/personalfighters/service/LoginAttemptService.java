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

  static class AttemptInfo {
    int count = 0;
    long blockedAt = 0;
    int level = 0;
    long lastFail = 0;
  }
}
