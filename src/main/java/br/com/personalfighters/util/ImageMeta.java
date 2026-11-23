package br.com.personalfighters.util;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Tira os dados escondidos das fotos (localização GPS, modelo do celular, data, comentários)
 * antes de guardar. Sem isso, a foto de perfil tirada em casa entregava o endereço a qualquer um.
 * JPEG: remove APP1..APP15 (EXIF/XMP/IPTC) e comentários, mantém APP0 (JFIF) e APP14 (cores).
 * PNG: remove eXIf, tEXt, zTXt, iTXt, tIME. WebP: remove EXIF e XMP.
 * Se não reconhecer o formato, devolve como veio.
 */
public final class ImageMeta {
  private ImageMeta() { }

  public static byte[] strip(byte[] d) {
    try {
      if (d == null || d.length < 12) return d;
      if ((d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8) return jpeg(d);
      if ((d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') return png(d);
      if (d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F' && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') return webp(d);
    } catch (Exception ignored) { }
    return d;
  }

  private static byte[] jpeg(byte[] d) {
    ByteArrayOutputStream o = new ByteArrayOutputStream(d.length);
    o.write(0xFF); o.write(0xD8);
    int i = 2;
    while (i + 4 <= d.length) {
      if ((d[i] & 0xFF) != 0xFF) return d;              // formato inesperado: não mexe
      int m = d[i + 1] & 0xFF;
      if (m == 0xDA) { o.write(d, i, d.length - i); return o.toByteArray(); } // início da imagem: copia o resto
      if (m == 0xD9) { o.write(0xFF); o.write(0xD9); return o.toByteArray(); }
      if (m == 0x01 || (m >= 0xD0 && m <= 0xD7)) { o.write(d, i, 2); i += 2; continue; }
      int len = ((d[i + 2] & 0xFF) << 8) | (d[i + 3] & 0xFF);
      if (len < 2 || i + 2 + len > d.length) return d;
      boolean drop = (m >= 0xE1 && m <= 0xEF && m != 0xEE) || m == 0xFE;
      if (!drop) o.write(d, i, 2 + len);
      i += 2 + len;
    }
    return d;
  }

  private static byte[] png(byte[] d) {
    ByteArrayOutputStream o = new ByteArrayOutputStream(d.length);
    o.write(d, 0, 8);
    int i = 8;
    while (i + 12 <= d.length) {
      int len = ByteBuffer.wrap(d, i, 4).getInt();
      if (len < 0 || i + 12 + len > d.length) return d;
      String type = new String(d, i + 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
      boolean drop = type.equals("eXIf") || type.equals("tEXt") || type.equals("zTXt") || type.equals("iTXt") || type.equals("tIME");
      if (!drop) o.write(d, i, 12 + len);
      i += 12 + len;
      if (type.equals("IEND")) return o.toByteArray();
    }
    return d;
  }

  private static byte[] webp(byte[] d) {
    ByteArrayOutputStream o = new ByteArrayOutputStream(d.length);
    o.write(d, 0, 12);
    int i = 12;
    while (i + 8 <= d.length) {
      String type = new String(d, i, 4, java.nio.charset.StandardCharsets.US_ASCII);
      int len = ByteBuffer.wrap(d, i + 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
      int total = 8 + len + (len & 1);
      if (len < 0 || i + total > d.length) return d;
      if (!type.equals("EXIF") && !type.equals("XMP ")) o.write(d, i, total);
      i += total;
    }
    byte[] out = o.toByteArray();
    if (out.length == d.length) return d;
    // ajusta o tamanho do RIFF e tira os avisos de EXIF/XMP do cabeçalho VP8X
    ByteBuffer.wrap(out, 4, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(out.length - 8);
    if (out.length > 21 && out[12] == 'V' && out[13] == 'P' && out[14] == '8' && out[15] == 'X') out[20] = (byte) (out[20] & ~0x0C);
    return out;
  }
}
