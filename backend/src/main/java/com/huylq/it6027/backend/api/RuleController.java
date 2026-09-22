package com.huylq.it6027.backend.api;

import com.huylq.it6027.backend.api.dto.RuleResponse;
import com.huylq.it6027.backend.entity.DetectionRule;
import com.huylq.it6027.backend.repository.DetectionRuleRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only rule listing (Week 2). CRUD write lands in Week 3.
 */
@RestController
@RequestMapping("/api/rules")
public class RuleController {

  private final DetectionRuleRepository detectionRuleRepository;

  public RuleController(DetectionRuleRepository detectionRuleRepository) {
    this.detectionRuleRepository = detectionRuleRepository;
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
    return rules.stream().map(RuleController::toResponse).toList();
  }

  private static RuleResponse toResponse(DetectionRule r) {
    return new RuleResponse(
        r.getId(),
        r.getCode(),
        r.getName(),
        r.getCategory(),
        r.getPattern(),
        r.getTargetField(),
        r.getWeight() != null ? r.getWeight() : 0,
        Boolean.TRUE.equals(r.getEnabled()),
        r.getDescription(),
        r.getSource(),
        r.getGeneratorRuleId(),
        r.getRuleVersion()
    );
  }
}
