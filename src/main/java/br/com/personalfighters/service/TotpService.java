package br.com.personalfighters.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

/**
 * Verificação em duas etapas (TOTP, o mesmo padrão do Google Authenticator / Microsoft Authenticator).
 * O segredo fica guardado criptografado no banco (AES-GCM com chave derivada do JWT_SECRET).
 */
@Service
public class TotpService {

  private static final String B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  private static final SecureRandom RND = new SecureRandom();
  private final byte[] key;

  public TotpService(@Value("${jwt.secret}") String jwtSecret) {
    try { key = Arrays.copyOf(MessageDigest.getInstance("SHA-256").digest(("opus-2fa:" + jwtSecret).getBytes(StandardCharsets.UTF_8)), 32); }
    catch (Exception e) { throw new IllegalStateException(e); }
  }

  /** Novo segredo (20 bytes, em base32). */
  public String newSecret() {
    byte[] b = new byte[20]; RND.nextBytes(b);
    return base32(b);
  }

  public String otpauthUri(String account, String secret) {
    String label = "OPUS Painel:" + account;
    return "otpauth://totp/" + url(label) + "?secret=" + secret + "&issuer=" + url("OPUS Painel") + "&algorithm=SHA1&digits=6&period=30";
  }

  /** Confere o código (aceita 30 s antes/depois por diferença de relógio). Devolve o passo usado, ou -1. */
  public long verify(String secret, String code, long lastStep) {
    String c = code == null ? "" : code.replaceAll("\\D", "");
    if (c.length() != 6) return -1;
    long step = System.currentTimeMillis() / 1000 / 30;
    for (long s = step - 1; s <= step + 1; s++) {
      if (s <= lastStep) continue; // o mesmo código não vale duas vezes
      if (MessageDigest.isEqual(code(secret, s).getBytes(), c.getBytes())) return s;
    }
    return -1;
  }

  String code(String secret, long step) {
    try {
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(unbase32(secret), "HmacSHA1"));
      byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
      int o = h[h.length - 1] & 0xf;
      int bin = ((h[o] & 0x7f) << 24) | ((h[o + 1] & 0xff) << 16) | ((h[o + 2] & 0xff) << 8) | (h[o + 3] & 0xff);
      return String.format("%06d", bin % 1_000_000);
    } catch (Exception e) { throw new IllegalStateException(e); }
  }

  /** Códigos de emergência (uso único), no formato 1234-5678. */
  public List<String> recoveryCodes(int n) {
    List<String> out = new ArrayList<>();
    for (int i = 0; i < n; i++) out.add(String.format("%04d-%04d", RND.nextInt(10000), RND.nextInt(10000)));
    return out;
  }

  public String hash(String s) {
    try { return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(("opus-rc:" + s.replaceAll("\\D", "")).getBytes(StandardCharsets.UTF_8))); }
    catch (Exception e) { throw new IllegalStateException(e); }
  }

  public String encrypt(String plain) {
    try {
      byte[] iv = new byte[12]; RND.nextBytes(iv);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
      byte[] enc = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + enc.length).put(iv).put(enc).array());
    } catch (Exception e) { throw new IllegalStateException(e); }
  }

  public String decrypt(String stored) {
    try {
      byte[] all = Base64.getDecoder().decode(stored);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, all, 0, 12));
      return new String(c.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
    } catch (Exception e) { throw new IllegalStateException(e); }
  }

  static String base32(byte[] data) {
    StringBuilder sb = new StringBuilder(); int buf = 0, bits = 0;
    for (byte b : data) { buf = (buf << 8) | (b & 0xff); bits += 8; while (bits >= 5) { sb.append(B32.charAt((buf >> (bits - 5)) & 31)); bits -= 5; } }
    if (bits > 0) sb.append(B32.charAt((buf << (5 - bits)) & 31));
    return sb.toString();
  }

  static byte[] unbase32(String s) {
    String t = s.toUpperCase().replaceAll("[^A-Z2-7]", "");
    ByteBuffer out = ByteBuffer.allocate(t.length() * 5 / 8); int buf = 0, bits = 0;
    for (char ch : t.toCharArray()) { buf = (buf << 5) | B32.indexOf(ch); bits += 5; if (bits >= 8) { out.put((byte) (buf >> (bits - 8))); bits -= 8; } }
    return Arrays.copyOf(out.array(), out.position());
  }

  private static String url(String s) { return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"); }
}
