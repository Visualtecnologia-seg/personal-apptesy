package br.com.personalfighters.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Entrega o código do painel só para quem entrou no painel (não fica público na internet). */
@RestController
public class AdminUiController {

  private static final Set<String> FILES = Set.of("app", "verif", "central");

  @GetMapping("/admin/ui/{name}.js")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  public ResponseEntity<String> script(@PathVariable String name) throws Exception {
    if (!FILES.contains(name)) return ResponseEntity.notFound().build();
    String js = new String(new ClassPathResource("painel-privado/" + name + ".js").getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Robots-Tag", "noindex")
        .contentType(new MediaType("application", "javascript", StandardCharsets.UTF_8)).body(js);
  }
}
