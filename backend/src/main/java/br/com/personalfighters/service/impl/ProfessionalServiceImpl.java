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
}
