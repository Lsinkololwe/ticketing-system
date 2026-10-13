package com.pml.identity.account;

import com.pml.identity.domain.model.User;
import com.pml.identity.workflow.contactchange.ContactChangeProcess;
import com.pml.identity.workflow.contactchange.ContactChangeRules;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;

/**
 * What stops an account's contact being changed while a change is open, and what lets a change that died
 * stop stopping it.
 *
 * <p>While a contact change runs, the account carries a marker naming the execution that owns it. The
 * marker is what refuses a second change: two at once could each release a contact the other still needs.
 * A change that ends in the ordinary ways clears its own marker. One that is killed from outside
 * (terminated by an operator, timed out by the server, lost with its worker) cannot, and a marker nobody
 * clears would lock the person out of changing a contact for ever.</p>
 *
 * <p>So the marker is judged at the moment someone next tries to change that account's contact. It is
 * live when the server says the owning execution is still open and the marker has not passed its expiry;
 * otherwise it is a leftover. A leftover is reclaimed with whatever the dead change had half done (see
 * {@link ContactChangeSteps#reclaim}) in one conditional write, so of two attempts arriving together
 * exactly one takes it and the other is told a change is in progress. Nothing scans for leftovers: an
 * account nobody tries to change is left alone, and a stuck marker never affects signing in, which only
 * refuses an account that is being merged.</p>
 */
@Service
public class ContactChangeMarker {

    private final ContactChangeSteps steps;
    private final ContactChangeProcess process;
    private final ReactiveMongoTemplate template;
    private final Clock clock;

    public ContactChangeMarker(ContactChangeSteps steps, ContactChangeProcess process, ReactiveMongoTemplate template,
                               Clock clock) {
        this.steps = steps;
        this.process = process;
        this.template = template;
        this.clock = clock;
    }

    /**
     * The account with its CHANGING marker released, when the marker is a leftover and this call took it;
     * otherwise {@code CONTACT_CHANGE_IN_PROGRESS}.
     */
    public Mono<User> release(User account) {
        Mono<Boolean> leftover = ContactChangeRules.expired(account.getPendingExpiresAt(), account.getPendingSince(), clock.instant())
                ? Mono.just(true)
                : process.isOpen(account.getId(), account.getPendingOwnerRunId()).map(open -> !open);
        return leftover.flatMap(stale -> {
            if (!stale) {
                return Mono.<User>error(inProgress());
            }
            return steps.reclaim(account, true).flatMap(won -> won
                    ? template.findById(account.getId(), User.class)
                    : Mono.<User>error(inProgress()));
        });
    }

    private static TranslatedRefusal inProgress() {
        return new TranslatedRefusal(ErrorCode.CONTACT_CHANGE_IN_PROGRESS, "a change is open");
    }
}
