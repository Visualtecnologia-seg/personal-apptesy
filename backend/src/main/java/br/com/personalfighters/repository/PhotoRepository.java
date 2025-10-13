package br.com.personalfighters.repository;

import br.com.personalfighters.entity.Photo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PhotoRepository extends JpaRepository<Photo, Long> {
  List<Photo> findByUserId(Long id);
}
