package com.pml.identity.web.rest;

import com.pml.identity.service.UserNotifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Other services ask here to message accounts they know only by id: booking resends a ticket, offers a
 * transfer, or writes to the holders of an event. Identity chooses the channel and owns the contacts.
 * Internal only.
 */
@RestController
@RequestMapping("/api/internal/notifications")
public class InternalUserNotificationController {

    private final UserNotifier notifier;

    public InternalUserNotificationController(UserNotifier notifier) {
        this.notifier = notifier;
    }

    /** Answers {@code 200} with the receipt, or {@code 400} for an unknown template or a malformed request. */
    @PostMapping("/users")
    @PreAuthorize("hasAnyAuthority('SCOPE_internal-write', 'ROLE_INTERNAL_SERVICE')")
    public Mono<UserNotifier.Receipt> notifyUser(@RequestBody UserNotice notice) {
        return notifier.notifyUser(notice.templateKey(), notice.discriminator(), notice.userId(), notice.params());
    }

    /** Answers {@code 200} with outcome counts only, or {@code 400} for more than 100 accounts. */
    @PostMapping("/users/batch")
    @PreAuthorize("hasAnyAuthority('SCOPE_internal-write', 'ROLE_INTERNAL_SERVICE')")
    public Mono<UserNotifier.BatchReceipt> notifyUsers(@RequestBody BatchNotice notice) {
        return notifier.notifyUsers(notice.templateKey(), notice.discriminator(), notice.userIds(), notice.params());
    }

    public record UserNotice(String templateKey, String discriminator, String userId, Map<String, Object> params) {
    }

    public record BatchNotice(String templateKey, String discriminator, List<String> userIds, Map<String, Object> params) {
    }
}
