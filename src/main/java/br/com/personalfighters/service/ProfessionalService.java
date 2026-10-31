package br.com.personalfighters.service;


import br.com.personalfighters.entity.Professional;

public interface ProfessionalService {

  void setActive(Long id);

  void setRating(Long id, double rating, Long count);

  Professional setOnlineStatus(Long id, boolean isOnline);
}
