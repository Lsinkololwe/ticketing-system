package com.pml.identity.migration;

import com.pml.identity.persistence.IdentityCollections;
import com.pml.shared.util.PhoneNumbers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Read-only report on what the account backfills will meet (ET-IDN-004). Writes nothing and
 * logs COUNTS only - never an email, phone number or id.
 *
 * <p>What it counts:</p>
 * <ul>
 *   <li>{@code placeholderEmails}: legacy phone-only accounts holding a made-up {@code @phone.local} address</li>
 *   <li>{@code caseCollidingEmailGroups}: sets of accounts whose emails differ only by case</li>
 *   <li>{@code phoneDerivedUsernameCollisions}: groups of different phone numbers sharing their
 *       last eight digits (the legacy {@code user_<last8>} username scheme gave them one name)</li>
 *   <li>{@code orphans}: accounts whose id is not a Keycloak (UUID) id</li>
 *   <li>{@code samePersonOnSeveralAccounts}: groups of accounts that share a normalised email or phone</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class AccountPreflightReportMigrationService {

    static final Pattern UUID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    static final Pattern PLACEHOLDER_EMAIL = Pattern.compile("(?i)^.*@phone\\.local$");

    private final ReactiveMongoTemplate mongo;

    /** Counts only. */
    public record Report(long users, long placeholderEmails, long caseCollidingEmailGroups,
                         long phoneDerivedUsernameCollisions, long orphans, long samePersonOnSeveralAccounts) {
        @Override
        public String toString() {
            return "users=%d placeholderEmails=%d caseCollidingEmailGroups=%d phoneDerivedUsernameCollisions=%d orphans=%d samePersonOnSeveralAccounts=%d"
                    .formatted(users, placeholderEmails, caseCollidingEmailGroups,
                            phoneDerivedUsernameCollisions, orphans, samePersonOnSeveralAccounts);
        }
    }

    public Mono<Report> migrate() {
        return mongo.getCollection(IdentityCollections.USERS)
                .flatMap(users -> Flux.from(users.find()
                                .projection(new Document("email", 1).append("phoneNumber", 1))
                                .batchSize(1000))
                        .reduce(new Accumulator(), Accumulator::add))
                .map(Accumulator::report)
                .doOnNext(report -> log.info("Account preflight (counts only): {}", report));
    }

    /** Streams over the users once, keeping hashed-free in-memory maps that are dropped afterwards. */
    private static final class Accumulator {
        long users;
        long placeholders;
        long orphans;
        final Map<String, Integer> emailGroups = new HashMap<>();
        final Map<String, Integer> phoneGroups = new HashMap<>();
        final Map<String, String> last8FirstPhone = new HashMap<>();
        final Set<String> last8Collided = new HashSet<>();

        Accumulator add(Document user) {
            users++;
            Object id = user.get("_id");
            if (!(id instanceof String s) || !UUID.matcher(s).matches()) {
                orphans++;
            }
            String email = user.get("email") instanceof String e ? e.trim() : null;
            if (email != null && !email.isEmpty()) {
                if (PLACEHOLDER_EMAIL.matcher(email).matches()) {
                    placeholders++;
                } else {
                    emailGroups.merge(email.toLowerCase(Locale.ROOT), 1, Integer::sum);
                }
            }
            if (user.get("phoneNumber") instanceof String phone && !phone.isBlank()) {
                String e164 = PhoneNumbers.parseMobile(phone, null).map(PhoneNumbers.Parsed::e164).orElse(phone.trim());
                phoneGroups.merge(e164, 1, Integer::sum);
                String digits = e164.replaceAll("\\D", "");
                String last8 = digits.length() > 8 ? digits.substring(digits.length() - 8) : digits;
                String first = last8FirstPhone.putIfAbsent(last8, e164);
                if (first != null && !first.equals(e164)) {
                    last8Collided.add(last8);
                }
            }
            return this;
        }

        Report report() {
            long emailCollisions = emailGroups.values().stream().filter(n -> n > 1).count();
            long phoneCollisions = phoneGroups.values().stream().filter(n -> n > 1).count();
            return new Report(users, placeholders, emailCollisions, last8Collided.size(), orphans,
                    emailCollisions + phoneCollisions);
        }
    }
}
