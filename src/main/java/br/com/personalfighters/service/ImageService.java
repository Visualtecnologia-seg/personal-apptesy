package br.com.personalfighters.service;

import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.InputStream;

public interface ImageService {

  BufferedImage getJpgImageFromFile(MultipartFile uploadedFile);

  BufferedImage pngToJpg(BufferedImage img);

  InputStream getInputStream(BufferedImage img, String extension);

}
