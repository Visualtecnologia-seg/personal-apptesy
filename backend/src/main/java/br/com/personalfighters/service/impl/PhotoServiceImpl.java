package br.com.personalfighters.service.impl;

import br.com.personalfighters.entity.Photo;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.repository.PhotoRepository;
import br.com.personalfighters.repository.UserRepository;
import br.com.personalfighters.service.PhotoService;
import br.com.personalfighters.service.S3Service;
import br.com.personalfighters.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;

@RequiredArgsConstructor
@Slf4j
@Service
public class PhotoServiceImpl implements PhotoService {

  private final PhotoRepository photoRepository;
  private final UserRepository userRepository;
  private final S3Service s3Service;
  private final UserService userService;

  @Override
  public URI upload(MultipartFile file) {
    return s3Service.upload(file);
  }

  @Override
  public void savePhoto(MultipartFile file, Long id) {
    URI url = upload(file);
    User user = userService.findById(id);
    Photo photo = new Photo();
    photo.setImageUrl(url.toString());
    photo.setUser(user);
    photoRepository.save(photo);
  }

  @Override
  public void saveAvatar(MultipartFile file, Long id) {
    URI url = upload(file);
    User user = userService.findById(id);
    user.setAvatarUrl(url.toString());
    userService.update(user);
  }

  @Override
  public void savePhoto(Photo photo) {
    photoRepository.save(photo);
  }

  @Override
  public Page<Photo> findAll(Photo photo, Pageable pageable) {
    Example<Photo> example = Example.of(photo,
        ExampleMatcher
            .matching()
            .withIgnoreCase()
            .withIgnoreNullValues()
            .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING));
    return photoRepository.findAll(example, pageable);
  }

  @Override
  public void delete(Long id) {
    photoRepository.deleteById(id);
  }

  @Override
  public List<Photo> findAllByUser(Long id) {
    return photoRepository.findByUserId(id);
  }
}
