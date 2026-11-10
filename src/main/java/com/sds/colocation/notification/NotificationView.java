package com.sds.colocation.notification;

import java.time.LocalDateTime;

public record NotificationView(
        Long id,
        Long customerId,
        String customerName,
        String recipient,
        String title,
        String message,
        LocalDateTime sentAt
) {

    public static NotificationView from(Notification notification) {
        return new NotificationView(notification.getId(), notification.getCustomerId(),
                notification.getCustomerName(), notification.getRecipient(), notification.getTitle(),
                notification.getMessage(), notification.getSentAt());
    }
}
