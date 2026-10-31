package br.com.personalfighters.service.impl;

import br.com.personalfighters.entity.Finance;
import br.com.personalfighters.entity.Professional;
import br.com.personalfighters.model.IncomeDTO;
import br.com.personalfighters.repository.ProfessionalFinanceRepository;
import br.com.personalfighters.repository.ProfessionalRepository;
import br.com.personalfighters.repository.TransactionRepository;
import br.com.personalfighters.service.ProfessionalFinanceService;
import br.com.personalfighters.service.impl.exceptions.ObjectNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProfessionalFinanceServiceImpl implements ProfessionalFinanceService {

  private final ProfessionalFinanceRepository professionalFinanceRepository;
  private final ProfessionalRepository professionalRepository;
  private final TransactionRepository transactionRepository;

  @Override
  public Optional<Finance> find(Long id) {
    return professionalFinanceRepository.findById(id);
  }

  @Override
  public Finance save(Finance finance) {
    return professionalFinanceRepository.save(finance);
  }

  @Override
  public Finance update(Finance finance) {
    return professionalFinanceRepository.save(finance);
  }

  @Override
  public Finance findByProfessional(Long id) {
    Professional professional = professionalRepository.findById(id).orElseThrow(() -> new ObjectNotFoundException("Profissional não encontrado."));
    // return professionalFinanceRepository.findByProfessional(professional);
    return null;
  }

  @Override
  public BigDecimal findProfessionalIncomeByDate(IncomeDTO data) {
    Finance finance = findByProfessional(data.getId());
//        return transactionRepository.sumProfessionalValueByDateAndFinance(
//            finance.getId(),
//            data.getFirstDateOfWeek(),
//            data.getFirstDateOfWeek().plusDays(6)
//            );
    return null;
  }

  @Override
  public Object findProfessionalTransactionsByDate(IncomeDTO data) {
    Finance finance = findByProfessional(data.getId());
//        return transactionRepository.findTransactionsByBillingOrderDateAndOrderIsDoneAndProfessionalFinanceId(
//            data.getFirstDateOfWeek(),
//            true,
//            finance.getId()
//        );
    return null;
  }
}
