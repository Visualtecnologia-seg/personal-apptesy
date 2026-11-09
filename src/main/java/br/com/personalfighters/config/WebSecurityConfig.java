package br.com.personalfighters.config;

import br.com.personalfighters.config.jwt.JwtRequestFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Collections;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

  private static final String[] PUBLIC_MATCHERS_GET = {"/users/*/avatar", "/orders/photos/*", "/platform/config", "/posts/photos/*", "/posts/videos/*", "/public/pro/*", "/public/export/*", "/panel-auth/status"};
  private static final String[] PUBLIC_MATCHERS_POST = {"/asaas/webhook", "/auth", "/auth/forgot-password", "/auth/reset-password", "/auth/phone/send", "/auth/phone/verify", "/auth/phone/login", "/auth/email/send", "/auth/email/verify", "/auth/google", "/auth/apple", "/users", "/partners/lead", "/panel-auth/login", "/panel-auth/2fa", "/panel-auth/setup"};
  private static final String[] WHITELIST = {
      // Documentação da API (swagger) e /actuator saíram daqui: agora só administrador.
      "/actuator/health",


      // -- App Cliente web (SPA estático)
      "/app",
      "/app/**",
      "/app/_expo/**",
      "/app/assets_expo/**",

      // -- Painel de verificação (página estática; as APIs continuam protegidas)
      "/painel",
      "/painel/**",

      // -- Página da chamada (WebRTC dentro do app; as APIs continuam protegidas)
      "/call",
      "/call/**",
  };
  private final UserDetailsService authUserDetailsService;
  private final JwtRequestFilter jwtAuthenticationFilter;

  @Autowired
  public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
    auth.userDetailsService(authUserDetailsService).passwordEncoder(passwordEncoder());
  }

  @Override
  protected void configure(HttpSecurity httpSecurity) throws Exception {
    httpSecurity
        .cors()
        .and()
        .csrf()
        .disable()
        // o painel abre Verificações e Suporte dentro dele mesmo (mesmo site)
        .headers().frameOptions().sameOrigin()
        .and()
        .authorizeRequests()
        .antMatchers("/").permitAll()
        .antMatchers("/static/**").permitAll()
        .antMatchers(WHITELIST).permitAll()
        .antMatchers(HttpMethod.POST, PUBLIC_MATCHERS_POST).permitAll()
        .antMatchers(HttpMethod.GET, PUBLIC_MATCHERS_GET).permitAll()
        .mvcMatchers("/system/**").hasRole("ADMIN") // geração de dados de teste: só administrador
        // Endpoints antigos que os apps não usam e que não conferiam o dono dos dados: só administrador
        .mvcMatchers("/bank/**", "/credit-cards/**", "/finances/**", "/professionalfinance/**", "/addresses/**", "/infos/**",
            "/customers/**", "/payments/**", "/auth/history/**", "/favorites/users/**", "/points/users/**", "/agendas/**").hasRole("ADMIN")
        .mvcMatchers(HttpMethod.DELETE, "/professionals/*", "/reviews/*", "/photos/*").hasRole("ADMIN")
        .mvcMatchers(HttpMethod.POST, "/specialty-history/**", "/products/users/**", "/photos/avatar/users/*", "/photos/photo/users/*").hasRole("ADMIN")
        .mvcMatchers(HttpMethod.PUT, "/products/*", "/products", "/categories").hasRole("ADMIN")
        .mvcMatchers(HttpMethod.POST, "/products", "/categories", "/notifications", "/photos/upload").hasRole("ADMIN")
        .mvcMatchers("/favorites/**", "/favorites").hasRole("ADMIN")
        .mvcMatchers("/swagger-ui/**", "/swagger-ui.html", "/swagger-resources/**", "/v2/api-docs", "/v3/api-docs/**").hasRole("ADMIN")
        .mvcMatchers("/actuator/**", "/webjars/**", "/configuration/**").hasRole("ADMIN")
        .anyRequest().authenticated()
        .and()
        .logout()
        .logoutUrl("/auth/logout") // TODO Não funcionando o logout por endpoint. É realmente necessário?
        .invalidateHttpSession(true)
        .deleteCookies("JSESSIONID")
        .and()
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .sessionManagement() // TODO a sessão é invalidada apenas pelo postman. Verificar. Atualização: Aparentemente em produção funcionou.
        .maximumSessions(10)
        .expiredSessionStrategy(event -> event.getResponse().sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalidated session"));
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  @Override
  public AuthenticationManager authenticationManagerBean() throws Exception {
    return super.authenticationManagerBean();
  }

  @Bean
  HttpSessionEventPublisher httpSessionEventPublisher() {
    return new HttpSessionEventPublisher();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    // Só o próprio servidor (painel) e testes locais podem chamar a API pelo navegador.
    // Os apps de celular não usam CORS, então continuam funcionando normalmente.
    configuration.setAllowedOriginPatterns(Arrays.asList("https://opus-backend-o8kw.onrender.com", "http://localhost:*", "http://127.0.0.1:*"));
    configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(Arrays.asList("authorization", "content-type", "x-auth-token"));
    configuration.setExposedHeaders(Collections.singletonList("x-auth-token"));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
