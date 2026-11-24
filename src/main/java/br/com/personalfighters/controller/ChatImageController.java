package br.com.personalfighters.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Foto do chat por link assinado: a tela de conversa mostra a imagem sem baixar todas de novo a cada atualização. */
@RestController
@RequiredArgsConstructor
public class ChatImageController {

  private final JdbcTemplate jdbc;
  private final MessageController messages;

  @GetMapping("/chat/image/{id}")
  public ResponseEntity<byte[]> image(@PathVariable UUID id, @RequestParam(value = "k", required = false) String k) {
    if (k == null || !MessageDigest.isEqual(messages.imageKey(id).getBytes(StandardCharsets.UTF_8), k.getBytes(StandardCharsets.UTF_8)))
      return ResponseEntity.status(404).build();
    List<String> r = jdbc.queryForList("SELECT image_url FROM message_ WHERE id = ? AND image_url LIKE 'data:image/%'", String.class, id);
    if (r.isEmpty()) return ResponseEntity.status(404).build();
    String d = r.get(0);
    int comma = d.indexOf(',');
    String type = d.substring(5, d.indexOf(';'));
    if (!type.matches("image/(jpeg|jpg|png|webp)")) return ResponseEntity.status(404).build();
    byte[] raw;
    try { raw = Base64.getDecoder().decode(d.substring(comma + 1)); } catch (IllegalArgumentException e) { return ResponseEntity.status(404).build(); }
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("image/jpg".equals(type) ? "image/jpeg" : type))
        .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePrivate())
        .header("X-Content-Type-Options", "nosniff")
        .body(raw);
  }
}
