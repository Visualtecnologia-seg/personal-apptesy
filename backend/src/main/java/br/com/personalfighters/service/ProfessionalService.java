package br.com.personalfighters.service;


public interface ProfessionalService {

  void setActive(Long id);

  void setRating(Long id, double rating, Long count);
}
