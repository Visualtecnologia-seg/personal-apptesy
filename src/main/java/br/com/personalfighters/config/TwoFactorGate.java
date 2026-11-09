package br.com.personalfighters.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Set;

/**
 * Verificação em duas etapas OBRIGATÓRIA para os acessos do painel.
 * Enquanto o acesso não ligar o Google Authenticator, ele só consegue usar a tela para ligar.
 */
@Configuration
@RequiredArgsConstructor
public class TwoFactorGate implements WebMvcConfigurer, HandlerInterceptor {

  private final JdbcTemplate jdbc;
  private static final Set<String> ALLOWED = Set.of("/admin/me", "/admin/me/2fa", "/admin/me/2fa/setup", "/admin/me/2fa/enable");

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(this).addPathPatterns("/**").excludePathPatterns("/panel-auth/**", "/admin/ui/**", "/painel/**", "/error");
  }

  @Override
  public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
    Authentication a = SecurityContextHolder.getContext().getAuthentication();
    if (a == null || a.getName() == null || !a.getName().startsWith("painel:")) return true;
    if (ALLOWED.contains(req.getRequestURI())) return true;
    Boolean on = jdbc.query("SELECT enabled FROM panel_2fa_ pf JOIN user_ u ON u.id = pf.user_id WHERE u.username = ?",
        rs -> rs.next() ? rs.getBoolean(1) : Boolean.FALSE, a.getName());
    if (Boolean.TRUE.equals(on)) return true;
    res.setStatus(428);
    res.setContentType("application/json;charset=UTF-8");
    res.getWriter().write("{\"need2fa\":true,\"message\":\"Ligue a verificação em duas etapas para usar o painel.\"}");
    return false;
  }
}
