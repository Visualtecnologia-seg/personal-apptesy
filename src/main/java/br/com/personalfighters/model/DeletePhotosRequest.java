package br.com.personalfighters.model;

import br.com.personalfighters.entity.Photo;
import lombok.Data;

import java.util.List;

@Data
public class DeletePhotosRequest {
  List<Photo> photos;
}
