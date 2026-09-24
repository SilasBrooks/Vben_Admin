package com.vben.service.module.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.vben.service.common.R;
import com.vben.service.common.idempotent.Idempotent;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user-config")
@RequiredArgsConstructor
public class UserConfigController {
  private final UserConfigService service;

  @GetMapping
  public R<JsonNode> get(@RequestParam(required = false) String key) {
    return R.ok(service.get(key));
  }

  @PostMapping("/save")
  @Idempotent(name = "user:config", key = "#p0.key", releaseAfterCompletion = true)
  public R<Void> save(@RequestBody SaveRequest body) {
    service.save(body.key(), body.value());
    return R.ok();
  }

  public record SaveRequest(String key, JsonNode value) {}
}
