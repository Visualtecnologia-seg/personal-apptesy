package br.com.personalfighters.controller.auth;

import br.com.personalfighters.entity.User;
import br.com.personalfighters.entity.auth.AuthRequest;
import br.com.personalfighters.entity.auth.AuthResponse;
import br.com.personalfighters.model.ForgotPassword;
import br.com.personalfighters.model.NewPasswordData;
import br.com.personalfighters.service.EmailService;
import br.com.personalfighters.service.UserService;
import br.com.personalfighters.service.impl.AuthenticationService;
import br.com.personalfighters.utils.PasswordGenerator;
import br.com.personalfighters.utils.Views;
import com.fasterxml.jackson.annotation.JsonView;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;

import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.Principal;

@RestController
@RequiredArgsConstructor
@CrossOrigin
@Api(tags = {"Authentication"})
@RequestMapping("/auth")
public class AuthenticationController {

  private final AuthenticationManager authenticationManager;
  private final UserDetailsService userDetailsService;
  private final AuthenticationService authenticationService;
  private final UserService userService;
  private final EmailService emailService;


  @PostMapping
  @ApiOperation(value = "Autentica o user e retorna o token")
  public ResponseEntity<?> createAuthenticationToken(@RequestBody AuthRequest authenticationRequest, HttpServletRequest request, HttpServletResponse response) throws Exception {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null) {
      new SecurityContextLogoutHandler().logout(request, response, auth);
    }
    authenticate(authenticationRequest.getUsername(), authenticationRequest.getPassword());
    final User user = (User) userDetailsService
        .loadUserByUsername(authenticationRequest.getUsername());
    final String token = authenticationService.addAuthentication(user);
    return ResponseEntity.ok(token);
  }

  @PreAuthorize("hasRole('ROLE_ADMIN')")
  @GetMapping("/dashboard")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Retorna o usuário autenticado no painel administrativo", notes = "Usar após receber o token do usuário.")
  @JsonView(Views.FirstLevel.class)
  public Object authUserDashboard() {
    return userService.findAuthUser();
  }

  @GetMapping("/mobile")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Retorna o usuário autenticado nos aplicativos", notes = "Usar após receber o token do usuário.")
  public Object authUserMobile() {
    return userService.findAuthUser();
  }

  @PutMapping("/new-password")
  @ApiOperation(value = "Atualiza a senha do user")
  @ResponseStatus(HttpStatus.OK)
  public Object updatePassword(@RequestBody NewPasswordData data, HttpServletRequest request) {
    Principal principal = request.getUserPrincipal();
    User user = userService.findByUsername(principal.getName());
    String newPassword = new BCryptPasswordEncoder().encode(data.getNewPassword());
    user.setPassword(newPassword);
    return userService.update(user) != null;
  }

  @PostMapping("/forgot-password")
  @ApiOperation(value = "Reseta a senha do user")
  @ResponseStatus(HttpStatus.OK)
  public Object forgotPassword(@RequestBody ForgotPassword forgotPassword) {
    User user = userService.findByUsername(forgotPassword.getUsername());
    if (user != null) {
      String newPassword = PasswordGenerator.GenerateWeakPassword(4);
      String passwordHash = new BCryptPasswordEncoder().encode(newPassword);
      user.setPassword(passwordHash);
      emailService.sendNewPassword(user.getEmail(), newPassword, user.getName());
      userService.update(user);
      return true;
    }
    return false;
  }

  @GetMapping("/check/{email}")
  @PermitAll
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Retorna o usuário pelo e-mail.")
  public Boolean checkIfExists(@PathVariable String email) {
    return userService.checkIfExists(email);
  }

  private void authenticate(String username, String password) throws Exception {
    try {
      authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
    } catch (DisabledException e) {
      throw new Exception("User disabled", e);
    } catch (BadCredentialsException e) {
      throw new Exception("Invalid credentials", e);
    } catch (LockedException e) {
      throw new Exception("User locked", e);
    }
  }
}
