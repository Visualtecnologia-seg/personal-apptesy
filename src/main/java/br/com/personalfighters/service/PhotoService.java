package br.com.personalfighters.service;

import br.com.personalfighters.entity.Photo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;

public interface PhotoService {

  URI upload(MultipartFile file);

  void savePhoto(MultipartFile file, Long id);

  void saveAvatar(MultipartFile file, Long id);

  void savePhoto(Photo photo);

  Page<Photo> findAll(Photo photo, Pageable pageable);

  void delete(Long id);

  List<Photo> findAllByUser(Long id);
}
