package br.com.personalfighters.service.impl;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public class AuditorAwareImpl implements AuditorAware<String> {

  @Override
  public Optional<String> getCurrentAuditor() {
    String author = "system";
    if (SecurityContextHolder.getContext().getAuthentication() != null) {
      author = SecurityContextHolder.getContext().getAuthentication().getName();
    }
    return Optional.of(author);
  }
}
