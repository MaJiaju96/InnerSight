package cn.innersight.health.controller;

import cn.innersight.health.service.HealthService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/health")
class HealthController {
    private final HealthService service;
    HealthController(HealthService service) { this.service = service; }

    @GetMapping("/live") Map<String, String> live() { return Map.of("service", "up"); }
    @GetMapping("/ready") ResponseEntity<Map<String, String>> ready() {
        boolean ready = service.ready();
        return ResponseEntity.status(ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("service", "up", "database", ready ? "up" : "down"));
    }
}
