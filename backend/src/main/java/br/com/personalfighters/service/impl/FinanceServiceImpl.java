package br.com.personalfighters.service.impl;

import br.com.personalfighters.entity.Finance;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.model.FinanceDTO;
import br.com.personalfighters.repository.FinanceRepository;
import br.com.personalfighters.service.FinanceService;
import br.com.personalfighters.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class FinanceServiceImpl implements FinanceService {

  private final FinanceRepository financeRepository;
  private final UserService userService;

  @Override
  public Optional<Finance> find(Long id) {
    return financeRepository.findById(id);
  }

  @Override
  public Page<Finance> findAll(Finance finance, Pageable pageRequest) {
    Example<Finance> example = Example.of(finance,
        ExampleMatcher
            .matching()
            .withIgnoreCase()
            .withIgnoreNullValues()
            .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING));
    return financeRepository.findAll(example, pageRequest);
  }

  @Override
  public Page<FinanceDTO> findAllFinanceData(Pageable pageRequest) {
    return financeRepository.findAllFinanceData(pageRequest);
  }

  @Override
  public Finance save(Finance finance) {
    return financeRepository.save(finance);
  }

  @Override
  public void payUser(Long id) {
    Optional<Finance> finance = financeRepository.findById(id);
    if (finance.isPresent()) {
      finance.get().setProfessionalBalance(BigDecimal.ZERO);
      financeRepository.save(finance.get());
    }
  }

  @Override
  public Finance findByUser(Long id) {
    User user = userService.findById(id);
    return financeRepository.findByUser(user);
  }

}
