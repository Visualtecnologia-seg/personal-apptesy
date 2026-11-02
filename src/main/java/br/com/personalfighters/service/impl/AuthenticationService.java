package br.com.personalfighters.service.impl;

import br.com.personalfighters.entity.User;
import br.com.personalfighters.model.Role;
import br.com.personalfighters.model.UserClaim;
import io.jsonwebtoken.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Slf4j
@Service
public class AuthenticationService {

  static final String TOKEN_PREFIX = "Bearer";
  static final String HEADER_STRING = "Authorization";
  /** Chave de assinatura dos tokens: vem da variável JWT_SECRET do servidor (nunca fica no código). */
  private static byte[] KEY;
  static long EXPIRATION = 1000L * 60 * 60 * 24 * 360; /* milliseconds * seconds * minutes * hours * days = 360 days */
  private final UserDetailsService userDetailsService;
  private final JdbcTemplate jdbc;
  /** Versão dos tokens de cada pessoa: "não fui eu" aumenta a versão e derruba todos os acessos antigos. */
  private static final Map<Long, long[]> VERSIONS = new ConcurrentHashMap<>();

  public AuthenticationService(UserDetailsService userDetailsService, JdbcTemplate jdbc, @Value("${jwt.secret}") String secret) {
    this.userDetailsService = userDetailsService;
    this.jdbc = jdbc;
    if (secret == null || secret.length() < 16) log.warn("JWT_SECRET está curto. Use uma chave longa (32+ caracteres) no servidor.");
    if (secret == null) secret = "";
    try { KEY = MessageDigest.getInstance("SHA-512").digest(("opus-jwt:" + secret).getBytes(StandardCharsets.UTF_8)); }
    catch (Exception e) { throw new IllegalStateException(e); }
  }

  private long tokenVersion(Long userId) {
    if (userId == null) return 0;
    long[] c = VERSIONS.get(userId);
    if (c != null && c[1] > System.currentTimeMillis()) return c[0];
    Long v = jdbc.query("SELECT COALESCE(token_version, 0) FROM user_ WHERE id = ?", rs -> rs.next() ? rs.getLong(1) : 0L, userId);
    long val = v == null ? 0 : v;
    VERSIONS.put(userId, new long[]{val, System.currentTimeMillis() + 30_000});
    return val;
  }

  /** Desconecta a pessoa de todos os aparelhos (os tokens antigos deixam de valer na hora). */
  public void revokeAll(Long userId) {
    jdbc.update("UPDATE user_ SET token_version = COALESCE(token_version, 0) + 1 WHERE id = ?", userId);
    VERSIONS.remove(userId);
  }

  public static String getUsernameFromToken(String token) {
    return getClaimFromToken(token, Claims::getSubject);
  }

  public static <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
    final Claims claims = getAllClaimsFromToken(token);
    return claimsResolver.apply(claims);
  }

  private static Claims getAllClaimsFromToken(String token) {
    return Jwts.parser().setSigningKey(KEY).parseClaimsJws(token).getBody();
  }

  private static Map<String, Object> generateUserClaims(User user) {
    Map<String, Object> claims = new HashMap<>();
    UserClaim userClaim = new UserClaim();
    userClaim.setUsername(user.getUsername());
    userClaim.setActive(user.isEnabled());
    userClaim.setRole(user.getRole());
    userClaim.setId(user.getId());
    claims.put("user", userClaim);
    return claims;
  }

  public String addAuthentication(User user) {
    long expiration = EXPIRATION;
    if (isAdmin(user)) {
      expiration = 1000L * 60 * 60 * 24 * 30; /* 30 days */
    }
    Map<String, Object> claims = generateUserClaims(user);
    claims.put("tv", tokenVersion(user.getId()));
    return Jwts.builder()
        .setClaims(claims)
        .setSubject(user.getUsername())
        .setExpiration(new Date(System.currentTimeMillis() + expiration))
        .signWith(SignatureAlgorithm.HS512, KEY)
        .compact();
  }

  private Boolean isAdmin(User user) {
    return user.getAuthorities()
        .stream()
        .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals(Role.ADMIN.getRole()));
  }

  public Authentication getAuthentication(HttpServletRequest request) {
    String token = request.getHeader(HEADER_STRING);

    if (token != null) {
      String username = null;
      Claims body = null;
      try {
        body = Jwts.parser()
            .setSigningKey(KEY)
            .parseClaimsJws(token.replace(TOKEN_PREFIX, "").trim())
            .getBody();
        username = body.getSubject();
      } catch (ExpiredJwtException e) {
        log.error("Expired token");
      } catch (UnsupportedJwtException e) {
        log.error("Unsupported token");
      } catch (MalformedJwtException e) {
        log.error("Malformed token");
      } catch (SignatureException e) {
        log.error("Token invalid singnature");
      } catch (IllegalArgumentException e) {
        log.error("Illegal token");
      }
      if (username == null) return null;
      UserDetails userDetails = userDetailsService.loadUserByUsername(username);

      if (userDetails != null) {
        // Token de antes do "não fui eu" / "sair de todos os aparelhos": não vale mais
        if (userDetails instanceof User) {
          Object tv = body.get("tv");
          long v = tv instanceof Number ? ((Number) tv).longValue() : 0;
          if (v != tokenVersion(((User) userDetails).getId())) return null;
        }
        // Só as contas do painel (login separado) têm poder de administrador
        java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> auths = userDetails.getAuthorities();
        if (username != null && !username.startsWith("painel:")) {
          auths = auths.stream().filter(a -> !"ROLE_ADMIN".equals(a.getAuthority())).collect(java.util.stream.Collectors.toList());
        }
        return new UsernamePasswordAuthenticationToken(userDetails, null, auths);
      }
    }
    return null;
  }

  public Date getExpirationDateFromToken(String token) {
    return getClaimFromToken(token, Claims::getExpiration);
  }
}
