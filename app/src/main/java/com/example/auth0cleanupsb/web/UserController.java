package com.example.auth0cleanupsb.web;

import com.example.auth0cleanupsb.service.CleanupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/users")
public class UserController {
  private final CleanupService service;
  public UserController(CleanupService service){ this.service = service; }

  @DeleteMapping("/by-ssoid/{ssoid}")
  public ResponseEntity<Map<String,Object>> deleteBySsoid(
      @PathVariable String ssoid,
      @RequestParam(defaultValue = "false") boolean dryRun) throws Exception {

    String deletedBy = "spring-boot-app";
    var result = service.deleteBySsoid(ssoid, dryRun, deletedBy);
    return ResponseEntity.ok(result);
  }
}
