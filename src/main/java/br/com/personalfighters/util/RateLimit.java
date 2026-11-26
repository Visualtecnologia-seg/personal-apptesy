package br.com.personalfighters.util;

import java.util.concurrent.ConcurrentHashMap;

/** Limite simples de "quantas vezes por período" (em memória). Usado para segurar inundação de chat e de pedidos. */
public final class RateLimit {

  private RateLimit() {}

  private static final class Window {
    long start;
    int count;
  }

  private static final ConcurrentHashMap<String, Window> MAP = new ConcurrentHashMap<>();
  private static volatile long lastClean = System.currentTimeMillis();

  /** true se ainda cabe mais uma vez dentro de {@code seconds}; conta a vez. */
  public static boolean allow(String key, int max, int seconds) {
    long now = System.currentTimeMillis();
    if (now - lastClean > 600_000 || MAP.size() > 200_000) clean(now);
    Window w = MAP.computeIfAbsent(key, k -> new Window());
    synchronized (w) {
      if (now - w.start > seconds * 1000L) { w.start = now; w.count = 0; }
      if (w.count >= max) return false;
      w.count++;
      return true;
    }
  }

  private static synchronized void clean(long now) {
    lastClean = now;
    MAP.entrySet().removeIf(e -> now - e.getValue().start > 86_400_000L);
  }
}
