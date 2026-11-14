package br.com.personalfighters.controller;

import br.com.personalfighters.entity.User;
import br.com.personalfighters.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/** Linha do tempo do pedido (quando foi feito, quando o profissional saiu, chegou, terminou...). Só quem participa do pedido vê. */
@RestController
@RequiredArgsConstructor
public class OrderProgressController {

  private final JdbcTemplate jdbc;
  private final UserService userService;

  @GetMapping("/orders/{id}/progress")
  public ResponseEntity<?> progress(@PathVariable String id) {
    UUID oid;
    try { oid = UUID.fromString(id); } catch (Exception e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
    User me = userService.findAuthUser();
    List<Map<String, Object>> r = jdbc.queryForList(
        "SELECT status, customer_id, professional_id, COALESCE(pix_paid, FALSE) AS paid, created_at AS \"createdAt\", departed_at AS \"departedAt\", arrived_at AS \"arrivedAt\", " +
            "requested_completion_at AS \"finishedAt\", done_at AS \"doneAt\", cancelled_at AS \"cancelledAt\" FROM order_ WHERE id = ?", oid);
    if (r.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    Map<String, Object> o = new LinkedHashMap<>(r.get(0));
    boolean mine = me.getId().equals(num(o.get("customer_id"))) || me.getId().equals(num(o.get("professional_id")));
    if (!mine) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    o.remove("customer_id");
    o.remove("professional_id");
    return ResponseEntity.ok(o);
  }

  private static Long num(Object v) { return v == null ? null : ((Number) v).longValue(); }
}
