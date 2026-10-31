package br.com.personalfighters.service.impl;

import br.com.personalfighters.service.ImageService;
import br.com.personalfighters.service.S3Service;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3ServiceImpl implements S3Service {

  private final AmazonS3 s3Client;
  private final ImageService imageService;

  @Value("${s3.bucket}")
  private String bucketName;

  public URI uploadFile(MultipartFile multiPartFile) {
    try {
      String fileName = multiPartFile.getOriginalFilename();
      InputStream is;
      is = multiPartFile.getInputStream();
      String contentType = multiPartFile.getContentType();
      return uploadFile(is, fileName, contentType);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      throw new RuntimeException("Erro de IO: " + e.getMessage());
    }

  }

  public URI uploadFile(InputStream is, String fileName, String contentType) {
    try {
      ObjectMetadata meta = new ObjectMetadata();
      meta.setContentType(contentType);
      s3Client.putObject(bucketName, fileName, is, meta);
      return s3Client.getUrl(bucketName, fileName).toURI();
    } catch (URISyntaxException e) {
      // TODO Auto-generated catch block
      throw new RuntimeException("Erro ao converter URL par URI");
    }
  }

  public void deleteFiles(List<String> files) {
    String[] keyFiles = new String[files.size()];
    files = files.stream().map(this::getFileKey).collect(Collectors.toList());
    files.toArray(keyFiles);
    DeleteObjectsRequest delObjReq = new DeleteObjectsRequest(bucketName)
        .withKeys(keyFiles);
    s3Client.deleteObjects(delObjReq);
  }

  @Override
  public URI upload(MultipartFile file) {
    BufferedImage jpgImage = imageService.getJpgImageFromFile(file);
    String fileName = UUID.randomUUID() + ".jpg";
    return uploadFile(imageService.getInputStream(jpgImage, "jpg"), fileName, "image");
  }

  private String getFileKey(String url) {
    String fileExtension = url.substring(url.length() - 4);
    return Arrays.stream(url.split("amazonaws.com/")).filter(s -> s.contains(fileExtension)).collect(Collectors.joining());
  }
}
