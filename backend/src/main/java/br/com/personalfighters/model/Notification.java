package br.com.personalfighters.model;

import br.com.personalfighters.entity.User;
import lombok.Data;

@Data
public class Notification {

  private User from;
  private User to;
  private String body;
  private String data;
  private Role fromRole;
  private Role toRole;

}
