package com.pml.identity.account;

import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.domain.model.Contact;
import com.pml.identity.domain.model.User;
import com.pml.identity.security.ContactHasher;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reads an account's contacts, and finds the account a contact belongs to.
 *
 * <p>Adding, changing and removing a contact are verified processes: see {@link ContactChangeService}
 * and {@code ContactChangeWorkflow}. This class only reads.</p>
 */
@Service
public class ContactService {

    private final ReactiveMongoTemplate template;
    private final ContactHasher hasher;

    public ContactService(ReactiveMongoTemplate template, ContactHasher hasher) {
        this.template = template;
        this.hasher = hasher;
    }

    /** The contacts the account holds now (released ones are history). */
    public Flux<Contact> contactsOf(String accountId) {
        return template.find(Query.query(Criteria.where("accountId").is(accountId).and("releasedAt").is(null))
                .with(org.springframework.data.domain.Sort.by("createdAt")), Contact.class);
    }

    /** The account that owns a verified contact, found by its key; the raw value is only normalised and hashed. */
    public Mono<User> accountByContact(String raw, ContactType hint) {
        return hasher.normalize(raw, hint, null, null)
                .map(normalized -> template.findOne(Query.query(Criteria.where("type").is(normalized.type())
                                .and("valueHash").is(normalized.key())
                                .and("verifiedAt").exists(true)
                                .and("releasedAt").is(null)), Contact.class)
                        .flatMap(owner -> template.findById(owner.getAccountId(), User.class)))
                .orElse(Mono.empty());
    }
}
