package br.com.personalfighters.service.impl;

import br.com.personalfighters.entity.User;
import br.com.personalfighters.model.Role;
import br.com.personalfighters.model.UserClaim;
import io.jsonwebtoken.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

  static final String TOKEN_PREFIX = "Bearer";
  static final String HEADER_STRING = "Authorization";
  static final String SECRET = "@blueshark-innovation";
  static long EXPIRATION = 1000L * 60 * 60 * 24 * 360; /* milliseconds * seconds * minutes * hours * days = 360 days */
  private final UserDetailsService userDetailsService;

  public static String getUsernameFromToken(String token) {
    return getClaimFromToken(token, Claims::getSubject);
  }

  public static <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
    final Claims claims = getAllClaimsFromToken(token);
    return claimsResolver.apply(claims);
  }

  private static Claims getAllClaimsFromToken(String token) {
    return Jwts.parser().setSigningKey(SECRET).parseClaimsJws(token).getBody();
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
    return Jwts.builder()
        .setClaims(claims)
        .setSubject(user.getUsername())
        .setExpiration(new Date(System.currentTimeMillis() + expiration))
        .signWith(SignatureAlgorithm.HS512, SECRET)
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
      try {
        username = Jwts.parser()
            .setSigningKey(SECRET)
            .parseClaimsJws(token.replace(TOKEN_PREFIX, ""))
            .getBody()
            .getSubject();
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
      UserDetails userDetails = userDetailsService.loadUserByUsername(username);

      if (userDetails != null) {
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
      }
    }
    return null;
  }

  public Date getExpirationDateFromToken(String token) {
    return getClaimFromToken(token, Claims::getExpiration);
  }
}
