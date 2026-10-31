package br.com.personalfighters.config;

import br.com.personalfighters.entity.User;
import br.com.personalfighters.model.Role;
import br.com.personalfighters.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Promove a administrador os usuários listados na variável de ambiente ADMIN_USERNAMES
 * (nomes de usuário ou e-mails separados por vírgula). O usuário precisa já ter se cadastrado
 * pelo app; a promoção acontece quando o servidor inicia.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

  private final UserRepository userRepository;

  @Value("${ADMIN_USERNAMES:}")
  private String adminUsernames;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (adminUsernames == null || adminUsernames.isBlank()) return;
    for (String raw : adminUsernames.split(",")) {
      String key = raw.trim();
      if (key.isEmpty()) continue;
      try {
        User u = userRepository.findByUsername(key);
        if (u == null) u = userRepository.findByEmail(key);
        if (u == null) {
          log.warn("ADMIN_USERNAMES: usuário '{}' ainda não existe", key);
          continue;
        }
        List<Role> roles = u.getRole() == null ? new ArrayList<>() : new ArrayList<>(u.getRole());
        if (!roles.contains(Role.ADMIN)) {
          roles.add(Role.ADMIN);
          u.setRole(roles);
          userRepository.save(u);
          log.info("ADMIN_USERNAMES: '{}' agora é administrador", key);
        }
      } catch (Exception e) {
        log.warn("ADMIN_USERNAMES: falha ao promover '{}': {}", key, e.getMessage());
      }
    }
  }
}
