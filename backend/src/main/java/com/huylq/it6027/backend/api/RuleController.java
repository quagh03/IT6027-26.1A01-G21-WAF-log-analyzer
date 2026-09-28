package com.huylq.it6027.backend.api;

import com.huylq.it6027.backend.api.dto.RuleCreateRequest;
import com.huylq.it6027.backend.api.dto.RuleResponse;
import com.huylq.it6027.backend.api.dto.RuleUpdateRequest;
import com.huylq.it6027.backend.entity.DetectionRule;
import com.huylq.it6027.backend.repository.DetectionRuleRepository;
import com.huylq.it6027.backend.rules.RuleAdminService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rules")
public class RuleController {

  private final DetectionRuleRepository detectionRuleRepository;
  private final RuleAdminService ruleAdminService;

  public RuleController(
      DetectionRuleRepository detectionRuleRepository,
      RuleAdminService ruleAdminService
  ) {
    this.detectionRuleRepository = detectionRuleRepository;
    this.ruleAdminService = ruleAdminService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public RuleResponse create(@RequestBody RuleCreateRequest request) {
    return ruleAdminService.create(request);
  }

  @PatchMapping("/{id}")
  public RuleResponse update(@PathVariable Long id, @RequestBody RuleUpdateRequest request) {
    return ruleAdminService.update(id, request);
  }

  @GetMapping
  public List<RuleResponse> list(
      @RequestParam(required = false) Boolean enabled
  ) {
    List<DetectionRule> rules = enabled == null
        ? detectionRuleRepository.findAll()
        : enabled
            ? detectionRuleRepository.findByEnabledTrue()
            : detectionRuleRepository.findAll().stream()
                .filter(r -> r.getEnabled() == null || !r.getEnabled())
                .toList();
    return rules.stream().map(SocResponses::rule).toList();
  }
}
