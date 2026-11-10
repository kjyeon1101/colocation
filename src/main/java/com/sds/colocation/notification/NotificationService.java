package com.sds.colocation.notification;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificationView> findAll() {
        return notificationRepository.findAllByOrderBySentAtDescIdDesc().stream()
                .map(NotificationView::from)
                .toList();
    }
}
