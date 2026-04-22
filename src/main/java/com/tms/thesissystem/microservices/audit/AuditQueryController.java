package com.tms.thesissystem.microservices.audit;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audits")
public class AuditQueryController {
    private final AuditEventStore store;

    public AuditQueryController(AuditEventStore store) {
        this.store = store;
    }

    @GetMapping
    public List<AuditEventStore.AuditView> audits(@RequestParam(required = false) String entityType) {
        return store.findAll().stream()
                .filter(entry -> entityType == null || entityType.equalsIgnoreCase(entry.entityType()))
                .toList();
    }
}
