package com.tms.thesissystem.microservices.notification;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationQueryController {
    private final NotificationEventStore store;

    public NotificationQueryController(NotificationEventStore store) {
        this.store = store;
    }

    @GetMapping
    public List<NotificationEventStore.NotificationView> notifications(@RequestParam(required = false) Long userId) {
        return store.findAll().stream()
                .filter(notification -> userId == null || userId.equals(notification.userId()))
                .toList();
    }
}
