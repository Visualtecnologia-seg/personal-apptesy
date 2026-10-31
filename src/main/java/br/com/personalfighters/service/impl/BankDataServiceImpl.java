package br.com.personalfighters.service.impl;

import br.com.personalfighters.entity.BankData;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.repository.BankDataRepository;
import br.com.personalfighters.service.BankDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class BankDataServiceImpl implements BankDataService {

  private final BankDataRepository bankDataRepository;

  @Override
  public void save(BankData bankData) {
    bankDataRepository.save(bankData);
  }

  @Override
  public BankData findByProfessionalId(Long id) {
    return bankDataRepository.findByProfessionalId(id);
  }
}
