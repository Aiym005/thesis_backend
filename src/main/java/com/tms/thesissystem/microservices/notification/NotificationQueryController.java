package com.tms.thesissystem.microservices.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationQueryController {
    private final NotificationEventStore store;

    @GetMapping
    public List<NotificationEventStore.NotificationView> notifications(@RequestParam(required = false) Long userId) {
        return store.findAll().stream()
                .filter(notification -> userId == null || userId.equals(notification.userId()))
                .toList();
    }
}
