package br.com.personalfighters.controller;

import br.com.personalfighters.entity.User;
import br.com.personalfighters.entity.UserRecords;
import br.com.personalfighters.service.UserRecordsService;
import br.com.personalfighters.service.UserService;
import br.com.personalfighters.utils.Views;
import com.fasterxml.jackson.annotation.JsonView;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@CrossOrigin
@RestController
@RequiredArgsConstructor
@Api(tags = {"Users"})
@RequestMapping("/users")
public class UserController {

  private final UserService userService;
  private final UserRecordsService userRecordsService;

  @GetMapping
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Retorna a lista paginada dos usuários.")
  public Object findAll(User user, Pageable pageRequest) {
    return userService.findAll(user, pageRequest);
  }

  @GetMapping("/{id}")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Retorna o usuário pelo ID.")
  public Object findById(@PathVariable Long id) {
    return userService.findById(id);
  }

  @GetMapping("/professionals/{ref}")
  @ResponseStatus(HttpStatus.OK)
  public Object findByProfessionalReference(@PathVariable String ref) {
    return userService.findByReference(ref);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Salva um usuário novo.")
  public Object save(@RequestBody User user) {
    return userService.save(user);
  }

  @PutMapping
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Atualiza um usuário.")
  public User update(@RequestBody User user) {
    return userService.update(user);
  }

  @PostMapping("/records")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Salva um usuário novo.")
  public void saveRecords(@RequestBody UserRecords userRecords) {
    userRecordsService.save(userRecords);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Inativa o usuário pelo ID")
  public void setActive(@PathVariable Long id) {
    userService.delete(id);
  }

  @GetMapping("/email/{email}")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Retorna o usuário pelo e-mail.")
  @JsonView(Views.FirstLevel.class)
  public Object findByEmail(@PathVariable String email) {
    return userService.findByEmail(email);
  }

  @PreAuthorize("hasRole('ROLE_ADMIN')")
  @PutMapping("/admin")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Dá o acesso de ADMIN para um usuário.")
  public Object setUserToAdmin(@RequestBody User user) {
    return userService.update(user);
  }

}
