package br.com.personalfighters.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URI;
import java.util.List;

public interface S3Service {

  URI uploadFile(MultipartFile multiPartFile);

  URI uploadFile(InputStream is, String fileName, String contentType);

  void deleteFiles(List<String> files);

  URI upload(MultipartFile file);
}
