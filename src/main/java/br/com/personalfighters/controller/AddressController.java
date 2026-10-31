package br.com.personalfighters.controller;

import br.com.personalfighters.entity.Address;
import br.com.personalfighters.service.AddressService;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;


@CrossOrigin
@RestController
@RequiredArgsConstructor
@Api(tags = {"Customers"})
@RequestMapping("/addresses")
public class AddressController {

  private final AddressService addressService;

  @GetMapping
  @ResponseStatus(HttpStatus.OK)
  public Page<Address> findAll(@RequestBody Address address, Pageable pageable) {
    return addressService.findAll(address, pageable);
  }

  @GetMapping("/{id}")
  @ResponseStatus(HttpStatus.OK)
  public Address findById(@PathVariable Long id) {
    return addressService.findById(id);
  }

  @GetMapping("/users/{id}")
  @ResponseStatus(HttpStatus.OK)
  public Page<Address> findAllByUser(@PathVariable Long id, Pageable pageable) {
    return addressService.findAllByUser(id, pageable);
  }

  @PostMapping("/users/{id}")
  @ResponseStatus(HttpStatus.OK)
  public void save(@PathVariable Long id, @RequestBody Address address) {
    addressService.save(id, address);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.OK)
  public void delete(@PathVariable Long id) throws Exception {
    addressService.delete(id);
  }

}
