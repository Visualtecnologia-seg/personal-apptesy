package br.com.personalfighters.entity.auth;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

import java.io.Serializable;

@Data
public class AuthResponse implements Serializable {

  private static final long serialVersionUID = -8091879091924046844L;

  @Setter(AccessLevel.NONE)
  private final String token;

}
