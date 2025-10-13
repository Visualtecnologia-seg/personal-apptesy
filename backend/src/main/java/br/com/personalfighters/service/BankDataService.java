package br.com.personalfighters.service;

import br.com.personalfighters.entity.BankData;
import br.com.personalfighters.entity.User;

public interface BankDataService {

  void save(BankData bankData);

  BankData findByProfessionalId(Long id);
}
