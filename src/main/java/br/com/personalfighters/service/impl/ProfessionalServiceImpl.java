package br.com.personalfighters.service.impl;

import br.com.personalfighters.entity.Professional;
import br.com.personalfighters.repository.ProfessionalRepository;
import br.com.personalfighters.service.ProfessionalService;
import br.com.personalfighters.service.impl.exceptions.ObjectNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfessionalServiceImpl implements ProfessionalService {

  private final ProfessionalRepository professionalRepository;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  @Override
  public void setActive(Long id) {
    Professional professional = professionalRepository
        .findById(id)
        .orElseThrow(() -> new ObjectNotFoundException("User not found"));
    // TODO remover o usuário da lista do product professionals
    professional.setActive(false);
    professionalRepository.save(professional);
  }

  @Override
  public void setRating(Long id, double rating, Long count) {
    Professional professional = professionalRepository
        .findById(id)
        .orElseThrow(() -> new ObjectNotFoundException("User not found"));
    professional.setRating(rating);
    professional.setCount(count);
    professionalRepository.save(professional);
  }

  @Override
  public Professional setOnlineStatus(Long id, boolean isOnline) {
    Professional professional = professionalRepository
        .findById(id)
        .orElseThrow(() -> new ObjectNotFoundException("Profissional não encontrado: " + id));
    professional.setIsOnline(isOnline);
    Professional saved = professionalRepository.save(professional);
    if (isOnline) jdbc.update("UPDATE professional_ SET online_at = NOW() WHERE id = ?", id);
    return saved;
  }
}
