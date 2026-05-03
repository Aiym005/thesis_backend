package com.tms.thesissystem.microservices.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audits")
@RequiredArgsConstructor
public class AuditQueryController {
    private final AuditEventStore store;

    @GetMapping
    public List<AuditEventStore.AuditView> audits(@RequestParam(required = false) String entityType) {
        return store.findAll().stream()
                .filter(entry -> entityType == null || entityType.equalsIgnoreCase(entry.entityType()))
                .toList();
    }
}
