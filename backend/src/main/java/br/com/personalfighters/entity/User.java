package br.com.personalfighters.entity;

import br.com.personalfighters.model.Gender;
import br.com.personalfighters.model.Role;
import br.com.personalfighters.utils.Views;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_")
@JsonView(Views.User.Basic.class)
public class User implements UserDetails, Serializable {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(unique = true, nullable = false)
  @JsonView(Views.User.Security.class)
  private String username;

  @JsonView(Views.User.Security.class)
  private String password;

  private String name;

  private String surname;

  private LocalDate birthday;

  @Column(nullable = false)
  private String cpf;

  @Enumerated(EnumType.STRING)
  private Gender gender;

  private String phoneNumber;

  @Column(unique = true, nullable = false)
  private String email;

  private String avatarUrl;

  @OneToOne(cascade = CascadeType.ALL)
  private Professional professional;

  @OneToOne(cascade = CascadeType.ALL)
  private Customer customer;

  private Boolean active;

  @Enumerated(EnumType.STRING)
  @ElementCollection(fetch = FetchType.EAGER)
  private List<Role> role;

  @JsonIgnore
  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return role.stream().map(role -> new SimpleGrantedAuthority(role.getRole())).collect(Collectors.toList());
  }

  @JsonIgnore
  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @JsonIgnore
  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return active;
  }

  @PostPersist
  private void newProfessional() {
    this.active = true;
  }
}
