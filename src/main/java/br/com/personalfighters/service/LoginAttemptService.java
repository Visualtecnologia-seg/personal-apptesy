package br.com.personalfighters.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginAttemptService {

  private static final int MAX_ATTEMPTS = 5;
  private static final long BLOCK_DURATION_MS = 60_000L; // 60 seconds

  private final ConcurrentHashMap<String, AttemptInfo> cache = new ConcurrentHashMap<>();

  public boolean isBlocked(String email) {
    AttemptInfo info = cache.get(normalize(email));
    if (info == null || info.count < MAX_ATTEMPTS) return false;
    long elapsed = System.currentTimeMillis() - info.blockedAt;
    if (elapsed >= BLOCK_DURATION_MS) {
      cache.remove(normalize(email));
      return false;
    }
    return true;
  }

  public int getSecondsRemaining(String email) {
    AttemptInfo info = cache.get(normalize(email));
    if (info == null || info.count < MAX_ATTEMPTS) return 0;
    long elapsed = System.currentTimeMillis() - info.blockedAt;
    long remaining = (BLOCK_DURATION_MS - elapsed) / 1000L;
    return remaining > 0 ? (int) remaining : 0;
  }

  public void loginFailed(String email) {
    String key = normalize(email);
    AttemptInfo info = cache.getOrDefault(key, new AttemptInfo());
    info.count++;
    if (info.count >= MAX_ATTEMPTS && info.blockedAt == 0) {
      info.blockedAt = System.currentTimeMillis();
    }
    cache.put(key, info);
  }

  public void loginSucceeded(String email) {
    cache.remove(normalize(email));
  }

  public int getAttemptCount(String email) {
    AttemptInfo info = cache.get(normalize(email));
    return info == null ? 0 : info.count;
  }

  private String normalize(String email) {
    return email == null ? "" : email.toLowerCase().trim();
  }

  static class AttemptInfo {
    int count = 0;
    long blockedAt = 0;
  }
}
