package br.com.personalfighters.service.impl;

import br.com.personalfighters.entity.Category;
import br.com.personalfighters.repository.CategoryRepository;
import br.com.personalfighters.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

  private final CategoryRepository categoryRepository;

  @Override
  public Page<Category> findAll(Category category, Pageable pageable) {
    Example<Category> example = Example.of(category,
        ExampleMatcher
            .matching()
            .withIgnoreCase()
            .withIgnoreNullValues()
            .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING)
    );
    return categoryRepository.findAll(example, pageable);
  }

  @Override
  public Category save(Category category) {
    return categoryRepository.save(category);
  }

}
