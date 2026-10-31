package br.com.personalfighters.controller;

import br.com.personalfighters.service.AuditService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@CrossOrigin
@RestController
@RequiredArgsConstructor
@Api(tags = {"Audit"})
@RequestMapping("/audit")
public class AuditLogController {

  private final AuditService auditService;

  @PreAuthorize("hasRole('ROLE_ADMIN')")
  @GetMapping
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Retorna os logs de auditoria (admin only).")
  public Object findAll(@PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
    return auditService.findAll(pageable);
  }
}
