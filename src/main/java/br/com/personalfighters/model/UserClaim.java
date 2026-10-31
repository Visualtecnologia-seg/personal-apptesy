package br.com.personalfighters.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UserClaim {
  private Long id;
  private String username;
  private boolean active;
  private List<Role> role;
}