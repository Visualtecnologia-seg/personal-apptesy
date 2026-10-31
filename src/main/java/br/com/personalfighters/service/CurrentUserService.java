package br.com.personalfighters.service;

import br.com.personalfighters.entity.User;

import javax.servlet.http.HttpServletRequest;

public interface CurrentUserService {
  User getCurrentUser(HttpServletRequest request);
}
