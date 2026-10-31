package br.com.personalfighters.util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.*;

/**
 * Checagem automática (sem IA, sem custo) da qualidade de uma foto:
 * tamanho, luz, reflexo e nitidez (variância do Laplaciano).
 */
public final class ImageQuality {

  private ImageQuality() {}

  public static Map<String, Object> check(String dataUrl, String kind) {
    Map<String, Object> out = new LinkedHashMap<>();
    List<String> issues = new ArrayList<>();
    try {
      String raw = dataUrl == null ? "" : dataUrl;
      int comma = raw.indexOf(',');
      if (raw.startsWith("data:") && comma > 0) raw = raw.substring(comma + 1);
      byte[] bytes = Base64.getMimeDecoder().decode(raw);
      BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
      if (img == null) {
        out.put("ok", false);
        out.put("issues", List.of("Não conseguimos abrir a foto. Tire outra."));
        return out;
      }
      int w = img.getWidth(), h = img.getHeight();
      boolean doc = !"SELFIE".equalsIgnoreCase(kind);

      // Reduz para no máximo 800 px no lado maior (rápido e estável)
      double scale = Math.min(1.0, 800.0 / Math.max(w, h));
      int sw = Math.max(1, (int) (w * scale)), sh = Math.max(1, (int) (h * scale));
      int[][] g = new int[sh][sw];
      long sum = 0;
      int bright = 0, dark = 0;
      for (int y = 0; y < sh; y++) {
        for (int x = 0; x < sw; x++) {
          int rgb = img.getRGB(Math.min(w - 1, (int) (x / scale)), Math.min(h - 1, (int) (y / scale)));
          int r = (rgb >> 16) & 255, gg = (rgb >> 8) & 255, b = rgb & 255;
          int lum = (r * 299 + gg * 587 + b * 114) / 1000;
          g[y][x] = lum;
          sum += lum;
          if (lum >= 250) bright++;
          if (lum <= 20) dark++;
        }
      }
      int n = sw * sh;
      double mean = sum / (double) n;
      double glare = bright / (double) n;
      double shadow = dark / (double) n;

      // Nitidez: variância do Laplaciano
      double ls = 0, ls2 = 0;
      int cnt = 0;
      for (int y = 1; y < sh - 1; y++) {
        for (int x = 1; x < sw - 1; x++) {
          int lap = g[y - 1][x] + g[y + 1][x] + g[y][x - 1] + g[y][x + 1] - 4 * g[y][x];
          ls += lap;
          ls2 += (double) lap * lap;
          cnt++;
        }
      }
      double variance = cnt == 0 ? 0 : (ls2 / cnt) - Math.pow(ls / cnt, 2);

      int minSide = Math.min(w, h);
      if (minSide < (doc ? 600 : 400)) issues.add("A foto está muito pequena. Chegue mais perto.");
      if (mean < 55 || shadow > 0.35) issues.add("A foto está escura. Procure um lugar com mais luz.");
      if (mean > 225) issues.add("A foto está clara demais. Evite luz forte direto.");
      if (doc && glare > 0.06) issues.add("Tem reflexo de luz em cima do documento. Mude o ângulo ou desligue o flash.");
      double minSharp = doc ? 90 : 45;
      if (variance < minSharp) issues.add(doc ? "A foto está borrada e o texto não dá para ler. Segure firme e tire de novo." : "A foto está borrada. Segure firme e tire de novo.");

      int score = 100;
      score -= issues.size() * 30;
      if (variance < minSharp * 2) score -= 10;
      out.put("ok", issues.isEmpty());
      out.put("issues", issues);
      out.put("score", Math.max(0, score));
      out.put("width", w);
      out.put("height", h);
      out.put("brightness", Math.round(mean));
      out.put("sharpness", Math.round(variance));
      out.put("glare", Math.round(glare * 1000) / 10.0);
    } catch (Exception e) {
      out.put("ok", true);
      out.put("issues", List.of());
      out.put("error", "não analisada");
    }
    return out;
  }
}
