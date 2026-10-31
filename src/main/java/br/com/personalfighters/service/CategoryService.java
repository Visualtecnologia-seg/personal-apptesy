package br.com.personalfighters.service;

import br.com.personalfighters.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CategoryService {

  Page<Category> findAll(Category category, Pageable pageable);

  Category save(Category category);

}
