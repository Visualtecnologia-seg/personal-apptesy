package br.com.personalfighters.service;

import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PayoutCodeService {

  private static final long CODE_EXPIRY_MS = 15L * 60 * 1000; // 15 minutes
  private final ConcurrentHashMap<Long, CodeInfo> codes = new ConcurrentHashMap<>();
  private final Random random = new Random();

  public String generateCode(Long financeId) {
    String code = String.format("%06d", random.nextInt(1_000_000));
    codes.put(financeId, new CodeInfo(code, System.currentTimeMillis()));
    return code;
  }

  public boolean validateAndConsume(Long financeId, String code) {
    CodeInfo info = codes.get(financeId);
    if (info == null) return false;
    if (System.currentTimeMillis() - info.createdAt > CODE_EXPIRY_MS) {
      codes.remove(financeId);
      return false;
    }
    boolean valid = info.code.equals(code);
    if (valid) codes.remove(financeId);
    return valid;
  }

  public boolean hasPendingCode(Long financeId) {
    CodeInfo info = codes.get(financeId);
    if (info == null) return false;
    if (System.currentTimeMillis() - info.createdAt > CODE_EXPIRY_MS) {
      codes.remove(financeId);
      return false;
    }
    return true;
  }

  static class CodeInfo {
    final String code;
    final long createdAt;
    CodeInfo(String c, long t) { code = c; createdAt = t; }
  }
}
