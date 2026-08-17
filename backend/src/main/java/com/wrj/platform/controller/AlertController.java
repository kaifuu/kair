package com.wrj.platform.controller;

import com.wrj.platform.common.ApiResponse;
import com.wrj.platform.dto.AlertDto;
import com.wrj.platform.service.AlertService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "false") boolean unhandled) {
        PageRequest pageable = PageRequest.of(Math.max(0, page - 1), size);
        Page<com.wrj.platform.entity.Alert> result = alertService.page(unhandled, pageable);
        List<AlertDto> items = result.getContent().stream().map(AlertService::toDto).toList();
        return ApiResponse.ok(Map.of(
                "items", items,
                "total", result.getTotalElements(),
                "page", page,
                "size", size
        ));
    }

    @GetMapping("/latest")
    public ApiResponse<List<AlertDto>> latest() {
        return ApiResponse.ok(alertService.latest(50).stream().map(AlertService::toDto).toList());
    }

    @PostMapping("/{id}/handle")
    public ApiResponse<AlertDto> handle(@PathVariable Long id,
                                        @RequestParam(required = false, defaultValue = "admin") String handler) {
        return ApiResponse.ok(AlertService.toDto(alertService.handle(id, handler)));
    }
}
