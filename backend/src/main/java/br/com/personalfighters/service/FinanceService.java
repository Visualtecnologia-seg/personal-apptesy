package br.com.personalfighters.service;

import br.com.personalfighters.entity.Finance;
import br.com.personalfighters.model.FinanceDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface FinanceService {

  Optional<Finance> find(Long id);

  Page<Finance> findAll(Finance finance, Pageable pageRequest);

  Page<FinanceDTO> findAllFinanceData(Pageable pageRequest);

  Finance save(Finance finance);

  void payUser(Long id);

  Finance findByUser(Long id);

}
