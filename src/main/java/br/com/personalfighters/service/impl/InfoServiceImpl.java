package br.com.personalfighters.service.impl;

import br.com.personalfighters.entity.Info;
import br.com.personalfighters.repository.InfoRepository;
import br.com.personalfighters.service.InfoService;
import br.com.personalfighters.service.impl.exceptions.ObjectNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class InfoServiceImpl implements InfoService {

  private final InfoRepository infoRepository;

  @Override
  public Page<Info> findAll(Info info, Pageable pageable) {
    Example<Info> infos = Example.of(info,
        ExampleMatcher
            .matching()
            .withIgnoreCase()
            .withIgnoreNullValues()
            .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING)
    );
    return infoRepository.findAll(infos, pageable);
  }

  @Override
  public Info save(Info info) {

    //TODO enviar notificação para os profissionais
    return infoRepository.save(info);
  }

  @Override
  public Info findById(Long id) {
    return infoRepository.findById(id).orElseThrow(() -> new ObjectNotFoundException("Object not found."));
  }

  @Override
  public void delete(Long id) throws Exception {
    Info info = findById(id);
    if (info.getActive()) {
      throw new Exception("Could not delete object");
    }
    info.setDeleted(true);
    infoRepository.save(info);
  }

}