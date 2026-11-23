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
  /** Login do app vale 30 dias (antes 360). O app entra de novo com a digital / "lembrar login". */
  static long EXPIRATION = 1000L * 60 * 60 * 24 * 30;
  private final UserDetailsService userDetailsService;
  private final JdbcTemplate jdbc;
  /** Versão dos tokens de cada pessoa: "não fui eu" aumenta a versão e derruba todos os acessos antigos. */
  private static final Map<Long, long[]> VERSIONS = new ConcurrentHashMap<>();

  public AuthenticationService(UserDetailsService userDetailsService, JdbcTemplate jdbc, @Value("${jwt.secret}") String secret) {
    this.userDetailsService = userDetailsService;
    this.jdbc = jdbc;
    // Chave fraca, curta ou que já apareceu em código público: quem souber fabricaria um login de administrador.
    // Nesse caso o servidor usa uma chave forte e aleatória, criada uma vez e guardada no banco.
    if (weak(secret)) {
      log.warn("JWT_SECRET fraca ou ausente: usando a chave forte guardada no banco. Troque a JWT_SECRET no Render por 64+ caracteres aleatórios.");
      secret = storedSecret(jdbc);
    }
    try { KEY = MessageDigest.getInstance("SHA-512").digest(("opus-jwt:" + secret).getBytes(StandardCharsets.UTF_8)); }
    catch (Exception e) { throw new IllegalStateException(e); }
  }

  /** Menos de 32 caracteres, pouca variedade, ou palavras conhecidas do código antigo/de exemplo. */
  static boolean weak(String s) {
    if (s == null || s.trim().length() < 32) return true;
    String l = s.toLowerCase();
    for (String bad : new String[]{"blueshark", "personal", "fighter", "secret", "segredo", "changeme", "opus-jwt", "teste", "test", "123456", "password", "senha"})
      if (l.contains(bad) && s.length() < 48) return true;
    return s.chars().distinct().count() < 12;
  }

  private static String storedSecret(JdbcTemplate jdbc) {
    try {
      jdbc.execute("CREATE TABLE IF NOT EXISTS app_secret_ (name VARCHAR(40) PRIMARY KEY, value TEXT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT NOW())");
      byte[] raw = new byte[64];
      new java.security.SecureRandom().nextBytes(raw);
      jdbc.update("INSERT INTO app_secret_ (name, value) VALUES ('jwt', ?) ON CONFLICT (name) DO NOTHING", java.util.Base64.getEncoder().encodeToString(raw));
      return jdbc.queryForObject("SELECT value FROM app_secret_ WHERE name = 'jwt'", String.class);
    } catch (Exception e) {
      // sem banco ainda: chave aleatória só desta execução (todos entram de novo depois de reiniciar)
      byte[] raw = new byte[64];
      new java.security.SecureRandom().nextBytes(raw);
      return java.util.Base64.getEncoder().encodeToString(raw);
    }
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
    jdbc.update("DELETE FROM bio_token_ WHERE user_id = ?", userId); // digital salva nos celulares também deixa de valer
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
      expiration = 1000L * 60 * 60 * 12; /* painel: 12 horas */
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

      // Conta bloqueada ou desativada perde o acesso na hora (antes o login antigo continuava valendo)
      if (userDetails != null && !userDetails.isEnabled()) return null;
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
