package br.com.personalfighters.util;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Protege onde as pessoas moram nas telas públicas (busca, perfil, área de atendimento, chamado urgente).
 *
 * Antes a distância saía com precisão de 100 m a partir de um ponto escolhido por quem pergunta: com 3 consultas
 * de lugares diferentes dava para achar a casa do profissional (teste: 43 m de erro). Agora toda comparação com um
 * ponto de fora usa um ponto "embaralhado" de cada pessoa — deslocado entre 400 e 900 m, sempre o mesmo para ela e
 * secreto — e a distância mostrada é arredondada de 0,5 em 0,5 km. Triangular só leva ao ponto falso.
 *
 * O rastreio ao vivo do pedido (com o pedido pago, enquanto o profissional está a caminho) continua exato.
 */
@Component
public class GeoPrivacy {

  private static volatile byte[] key;
  private static final double MIN_M = 400, MAX_M = 900;

  public GeoPrivacy(JdbcTemplate jdbc) {
    try {
      jdbc.execute("CREATE TABLE IF NOT EXISTS app_secret_ (name VARCHAR(40) PRIMARY KEY, value TEXT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT NOW())");
      byte[] raw = new byte[32];
      new SecureRandom().nextBytes(raw);
      jdbc.update("INSERT INTO app_secret_ (name, value) VALUES ('geo', ?) ON CONFLICT (name) DO NOTHING", Base64.getEncoder().encodeToString(raw));
      key = Base64.getDecoder().decode(jdbc.queryForObject("SELECT value FROM app_secret_ WHERE name = 'geo'", String.class));
    } catch (Exception e) {
      byte[] raw = new byte[32];
      new SecureRandom().nextBytes(raw);
      key = raw;
    }
  }

  private static byte[] mac(String what) {
    try {
      byte[] k = key;
      if (k == null) { k = new byte[32]; new SecureRandom().nextBytes(k); key = k; }
      Mac m = Mac.getInstance("HmacSHA256");
      m.init(new SecretKeySpec(k, "HmacSHA256"));
      return m.doFinal(what.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** Ponto público (embaralhado) de uma pessoa ou de um lugar dela. {@code kind} separa casa, área, chamado etc. */
  public static double[] blur(String kind, Object id, double lat, double lng) {
    byte[] h = mac(kind + ":" + id);
    double u1 = ((h[0] & 0xff) << 8 | (h[1] & 0xff)) / 65535.0;
    double u2 = ((h[2] & 0xff) << 8 | (h[3] & 0xff)) / 65535.0;
    double angle = u1 * 2 * Math.PI;
    double meters = MIN_M + u2 * (MAX_M - MIN_M);
    double dLat = meters * Math.cos(angle) / 111_320.0;
    double dLng = meters * Math.sin(angle) / (111_320.0 * Math.max(0.2, Math.cos(Math.toRadians(lat))));
    return new double[]{lat + dLat, lng + dLng};
  }

  /** Distância (km, sem arredondar) entre um ponto de fora e o ponto embaralhado. Para filtros de raio. */
  public static Double km(String kind, Object id, Double viewerLat, Double viewerLng, Object lat, Object lng) {
    if (viewerLat == null || viewerLng == null || lat == null || lng == null) return null;
    try {
      double a = Double.parseDouble(lat.toString()), b = Double.parseDouble(lng.toString());
      double[] p = blur(kind, id, a, b);
      return haversine(viewerLat, viewerLng, p[0], p[1]);
    } catch (Exception e) {
      return null;
    }
  }

  /** Distância para mostrar na tela: arredondada de 0,5 em 0,5 km (mínimo 0,5). */
  public static Double shown(Double km) {
    if (km == null) return null;
    return Math.max(0.5, Math.round(km * 2) / 2.0);
  }

  public static double haversine(double lat1, double lng1, double lat2, double lng2) {
    double r = 6371.0, dLat = Math.toRadians(lat2 - lat1), dLng = Math.toRadians(lng2 - lng1);
    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
    return 2 * r * Math.asin(Math.min(1, Math.sqrt(a)));
  }
}
