package br.com.personalfighters.service.impl;

import br.com.personalfighters.service.ImageService;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

@Service
public class ImageServiceImpl implements ImageService {

  // FilenameUtils faz parte da biblioteca commons-io que está no pom.xml
  @Override
  public BufferedImage getJpgImageFromFile(MultipartFile uploadedFile) {
    String ext = FilenameUtils.getExtension(uploadedFile.getOriginalFilename());

    if (!"png".equals(ext) && !"jpg".equals(ext)) {
      throw new RuntimeException("Somente imagens PNG e JPG são permitidas");
    }

    try {
      BufferedImage img = ImageIO.read(uploadedFile.getInputStream());

      if ("png".equals(ext)) {
        img = pngToJpg(img);
      }
      return img;

    } catch (IOException e) {
      throw new RuntimeException("Erro ao ler arquivo");
    }

  }

  @Override
  public BufferedImage pngToJpg(BufferedImage img) {
    BufferedImage jpgImage = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
    jpgImage.createGraphics().drawImage(img, 0, 0, Color.WHITE, null);

    return jpgImage;
  }

  @Override
  public InputStream getInputStream(BufferedImage img, String extension) {
    try {
      ByteArrayOutputStream os = new ByteArrayOutputStream();
      ImageIO.write(img, extension, os);
      return new ByteArrayInputStream(os.toByteArray());
    } catch (IOException e) {
      throw new RuntimeException("Erro ao ler arquivo");
    }
  }
}