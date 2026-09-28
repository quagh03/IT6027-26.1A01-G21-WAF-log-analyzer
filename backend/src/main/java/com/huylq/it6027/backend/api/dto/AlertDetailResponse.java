package com.huylq.it6027.backend.api.dto;

import java.util.List;

public record AlertDetailResponse(
    AlertResponse alert,
    String method,
    String path,
    String query,
    Integer httpStatus,
    Integer eventScore,
    List<DetectionHitResponse> hits
) {
}
