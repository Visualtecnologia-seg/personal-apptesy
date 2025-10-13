package br.com.personalfighters.controller;

import br.com.personalfighters.entity.Photo;
import br.com.personalfighters.service.PhotoService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;

@CrossOrigin
@RequiredArgsConstructor
@RestController
@Api(tags = {"S3 Upload Images"})
@RequestMapping("/photos")
public class PhotoController {

  private final PhotoService photoService;

  @GetMapping("/users/{id}")
  @ResponseStatus(HttpStatus.OK)
  public List<Photo> findAll(@PathVariable Long id) {
    return photoService.findAllByUser(id);
  }

  @PostMapping("/upload")
  @ApiOperation(value = "Retorna a URL da imagem")
  @ResponseStatus(HttpStatus.OK)
  public URI upload(@RequestParam("file") MultipartFile file) {
    return photoService.upload(file);
  }

  @PostMapping("/avatar/users/{id}")
  @ResponseStatus(HttpStatus.OK)
  public void saveAvatar(@RequestParam("file") MultipartFile file, @PathVariable Long id) {
    photoService.saveAvatar(file, id);
  }

  @PostMapping("/photo/users/{id}")
  @ApiOperation(value = "Retorna a URL da imagem")
  @ResponseStatus(HttpStatus.OK)
  public void savePhoto(@RequestParam("file") MultipartFile file, @PathVariable Long id) {
    photoService.savePhoto(file, id);
  }

  @DeleteMapping("/{id}")
  @ApiOperation(value = "Retorna a URL da imagem")
  @ResponseStatus(HttpStatus.OK)
  public void delete(@PathVariable Long id) {
    photoService.delete(id);
  }
}