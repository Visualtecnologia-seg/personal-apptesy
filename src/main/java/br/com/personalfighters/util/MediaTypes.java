package br.com.personalfighters.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Tipos de arquivo aceitos nos envios. Qualquer outro (ex.: SVG, HTML) é recusado ou trocado,
 * porque alguns tipos de "imagem" carregam código que roda no navegador.
 */
public final class MediaTypes {
  private MediaTypes() { }

  private static final Set<String> IMAGE = new HashSet<>(Arrays.asList(
      "image/jpeg", "image/jpg", "image/png", "image/webp", "image/heic", "image/heif"));
  private static final Set<String> VIDEO = new HashSet<>(Arrays.asList(
      "video/mp4", "video/quicktime", "video/3gpp", "video/webm", "video/x-m4v"));
  private static final Set<String> AUDIO = new HashSet<>(Arrays.asList(
      "audio/mp4", "audio/m4a", "audio/x-m4a", "audio/aac", "audio/mpeg", "audio/webm", "audio/3gpp", "audio/ogg", "audio/wav", "audio/x-wav"));

  private static String norm(String t) {
    if (t == null) return "";
    int i = t.indexOf(';');
    return (i >= 0 ? t.substring(0, i) : t).trim().toLowerCase(Locale.ROOT);
  }

  public static boolean isImage(String t) { return IMAGE.contains(norm(t)); }
  public static boolean isVideo(String t) { return VIDEO.contains(norm(t)); }
  public static boolean isAudio(String t) { return AUDIO.contains(norm(t)); }

  /** Devolve o tipo se for seguro, senão o padrão indicado. */
  public static String imageOr(String t, String def) { return isImage(t) ? norm(t) : def; }
  public static String videoOr(String t, String def) { return isVideo(t) ? norm(t) : def; }
  public static String audioOr(String t, String def) { return isAudio(t) ? norm(t) : def; }
}
