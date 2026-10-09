package com.pml.identity.account;

import com.pml.identity.domain.model.User;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/** Answers {@code GET /accounts/{id}/status} from MongoDB (CONTRACT 4.5). */
@Service
public class AccountStatusService implements AccountStatusLookup {

    private final ReactiveMongoTemplate template;

    public AccountStatusService(ReactiveMongoTemplate template) {
        this.template = template;
    }

    @Override
    public Mono<AccountStatusView> byAccountId(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return Mono.empty();
        }
        return template.findById(accountId, User.class)
                .map(account -> new AccountStatusView(account.getId(), AccountStates.of(account),
                        account.getKeycloakUserId()));
    }
}
