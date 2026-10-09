package com.pml.catalog.error;

import com.pml.catalog.exception.EventNotFoundException;
import com.pml.catalog.exception.InvalidEventStateException;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.RefusalTranslator;
import com.pml.shared.error.TranslatedRefusal;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Every exception catalog-service declares, mapped to its shared error-registry code.
 *
 * <h2>Discovery is public, so "not found" carries weight here</h2>
 * Catalog is the one service a caller reaches without a session. An unpublished
 * or cancelled event and an event id that was never issued must be
 * indistinguishable from outside: both are {@code EVENT_UNKNOWN}, with no
 * detail explaining which. Anything finer lets an anonymous caller enumerate
 * draft events by id and watch the answer change on the day one is published.
 *
 * <p>The exception classes carry {@code eventId} and {@code reason} fields.
 * Neither is copied into {@code details} — the caller supplied the id, and the
 * reason is written for an operator.</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@Component
public class CatalogRefusalTranslator implements RefusalTranslator {

    @Override
    public Optional<DomainRefusal> translate(Throwable exception) {
        return Optional.ofNullable(switch (exception) {

            case EventNotFoundException ignored -> new TranslatedRefusal(
                    ErrorCode.EVENT_UNKNOWN, "event not found or not visible to this caller");

            case InvalidEventStateException ignored -> new TranslatedRefusal(
                    // The transition was refused, not the event's existence. A
                    // caller reaching this already holds a reference to the
                    // event, so the code discloses nothing further.
                    ErrorCode.EVENT_STATE_INVALID, "event is not in a state permitting this transition");

            default -> null;
        });
    }
}
