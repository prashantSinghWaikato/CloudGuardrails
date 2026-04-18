package com.cloud.guardrails.controller;

import com.cloud.guardrails.dto.NotificationResponse;
import com.cloud.guardrails.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationResponse> getNotifications(@RequestParam(defaultValue = "false") boolean unreadOnly) {
        return notificationService.getNotifications(unreadOnly);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", notificationService.getUnreadCount());
    }

    @PutMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable Long id) {
        return notificationService.markRead(id);
    }

    @PutMapping("/read-all")
    public Map<String, String> markAllRead() {
        notificationService.markAllRead();
        return Map.of("message", "All notifications marked as read");
    }
}
