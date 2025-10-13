package br.com.personalfighters.service;

import br.com.personalfighters.entity.Finance;
import br.com.personalfighters.model.IncomeDTO;

import java.math.BigDecimal;
import java.util.Optional;

public interface ProfessionalFinanceService {

  Optional<Finance> find(Long id);

  Finance save(Finance finance);

  Finance update(Finance finance);

  Finance findByProfessional(Long id);

  BigDecimal findProfessionalIncomeByDate(IncomeDTO data);

  Object findProfessionalTransactionsByDate(IncomeDTO data);
}
