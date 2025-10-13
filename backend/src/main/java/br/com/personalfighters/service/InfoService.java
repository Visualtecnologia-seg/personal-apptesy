package br.com.personalfighters.service;

import br.com.personalfighters.entity.Info;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InfoService {
  Page<Info> findAll(Info info, Pageable pageable);

  Info findById(Long id);

  Info save(Info info);

  void delete(Long id) throws Exception;
}
