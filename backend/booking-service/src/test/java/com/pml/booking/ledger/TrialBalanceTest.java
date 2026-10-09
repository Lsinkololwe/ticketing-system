package com.pml.booking.ledger;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.booking.domain.enums.AccountType;
import com.pml.booking.domain.enums.JournalEntryStatus;
import com.pml.booking.domain.model.ChartOfAccountsEntry;
import com.pml.booking.domain.model.JournalEntry;
import com.pml.booking.domain.model.JournalLine;
import com.pml.booking.repository.ChartOfAccountsRepository;
import com.pml.booking.repository.JournalEntryRepository;
import com.pml.booking.web.graphql.dto.AccountBalanceDto;
import com.pml.booking.web.graphql.query.TrialBalanceQueryResolver;
import com.pml.shared.testing.MongoReplicaSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code trialBalance(asOf)} answers, and the answer balances.
 *
 * <h2>What this proves beyond "the resolver exists"</h2>
 * {@code trialBalance} was declared in booking's SDL with no resolver behind it: advertised by
 * two specs, offered by every generated client, answered by nothing. Binding it is the easy half.
 * The half worth testing is that the figures are a trial balance rather than a sum — which means
 * three properties a naive implementation gets wrong:
 *
 * <ul>
 *   <li><b>Only POSTED entries count.</b> A DRAFT has not affected balances and a REVERSED one
 *       had its effect nullified. Summing them produces a number that looks right and reconciles
 *       against nothing.</li>
 *   <li><b>Every account appears</b>, including those with no movement. A trial balance is read
 *       to find what is missing at least as often as what is wrong, and an account absent because
 *       nothing touched it looks exactly like an account somebody deleted.</li>
 *   <li><b>The net runs in the account's own normal direction.</b> Debit minus credit for all five
 *       types shows every liability and every revenue account as negative, and a page of correct
 *       figures reads as a page of errors.</li>
 * </ul>
 *
 * <h2>Against a replica set</h2>
 * The entries are real documents with embedded {@code JournalLine} lists, read back through the
 * real repository. Embedded-collection mapping is precisely what a mocked repository would not
 * exercise, and the lines are where every figure here comes from.
 */
@Tag("L2")
@Tag("ET-FIN-001")
@DisplayName("ET-FIN-001 · the trial balance counts posted entries only, and balances")
class TrialBalanceTest {

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static TrialBalanceQueryResolver resolver;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(client, "booking_trial_balance"));
        ReactiveMongoRepositoryFactory factory = new ReactiveMongoRepositoryFactory(template);
        resolver = new TrialBalanceQueryResolver(
                factory.getRepository(JournalEntryRepository.class),
                factory.getRepository(ChartOfAccountsRepository.class));
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seedALedger() {
        template.remove(new Query(), JournalEntry.class).block();
        template.remove(new Query(), ChartOfAccountsEntry.class).block();

        account("1010", "Bank", AccountType.ASSET);
        account("2020", "Commission Payable", AccountType.LIABILITY);
        account("4010", "Ticket Revenue", AccountType.REVENUE);
        account("5010", "Provider Fees", AccountType.EXPENSE);
        // Deliberately never posted to — the row that must still appear.
        account("3010", "Retained Earnings", AccountType.EQUITY);

        // A ticket sale: money in, revenue and commission recognised.
        posted("JE-1", Instant.parse("2026-08-01T10:00:00Z"),
                line("1010", "500.00", "0"),
                line("4010", "0", "450.00"),
                line("2020", "0", "50.00"));

        // A later sale, after the asOf cut used below.
        posted("JE-2", Instant.parse("2026-10-01T10:00:00Z"),
                line("1010", "300.00", "0"),
                line("4010", "0", "300.00"));

        // Neither of these may be counted.
        entry("JE-DRAFT", JournalEntryStatus.DRAFT, Instant.parse("2026-08-02T10:00:00Z"),
                line("1010", "9999.00", "0"), line("4010", "0", "9999.00"));
        entry("JE-REVERSED", JournalEntryStatus.REVERSED, Instant.parse("2026-08-03T10:00:00Z"),
                line("5010", "7777.00", "0"), line("1010", "0", "7777.00"));
    }

    @Test
    @DisplayName("ET-FIN-001 · debits equal credits across the whole ledger")
    void theBooksBalance() {
        List<AccountBalanceDto> rows = trialBalance(null);

        BigDecimal debits = rows.stream().map(AccountBalanceDto::debitBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = rows.stream().map(AccountBalanceDto::creditBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(debits)
                .as("a trial balance whose two columns disagree is the one thing this report exists to catch")
                .isEqualByComparingTo(credits)
                .isEqualByComparingTo(new BigDecimal("800.00"));
    }

    @Test
    @DisplayName("ET-FIN-001 · DRAFT and REVERSED entries are not counted")
    void onlyPostedEntriesCount() {
        // Both unposted entries are far larger than every posted figure, so if either leaked in
        // the totals could not be mistaken for correct.
        Map<String, AccountBalanceDto> byCode = byCode(trialBalance(null));

        assertThat(byCode.get("1010").debitBalance())
                .as("the DRAFT entry's 9999.00 debit must not appear")
                .isEqualByComparingTo(new BigDecimal("800.00"));
        assertThat(byCode.get("5010").debitBalance())
                .as("the REVERSED entry's 7777.00 debit must not appear")
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("ET-FIN-001 · an account with no movement still appears, at zero")
    void untouchedAccountsAppear() {
        Map<String, AccountBalanceDto> byCode = byCode(trialBalance(null));

        assertThat(byCode).containsKey("3010");
        assertThat(byCode.get("3010").netBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(byCode.get("3010").accountName()).isEqualTo("Retained Earnings");
    }

    @Test
    @DisplayName("ET-FIN-001 · the net runs in each account's normal direction")
    void netRunsInTheNormalDirection() {
        Map<String, AccountBalanceDto> byCode = byCode(trialBalance(null));

        // Debit-normal: assets and expenses.
        assertThat(byCode.get("1010").netBalance())
                .as("the bank holds money, so its net is positive")
                .isEqualByComparingTo(new BigDecimal("800.00"));
        // Credit-normal: liabilities, equity, revenue. `debit - credit` would make both negative.
        assertThat(byCode.get("4010").netBalance())
                .as("revenue earned is positive revenue, not negative")
                .isEqualByComparingTo(new BigDecimal("750.00"));
        assertThat(byCode.get("2020").netBalance())
                .as("commission owed is a positive liability")
                .isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    @DisplayName("ET-FIN-001 · asOf excludes entries posted after it, and still balances")
    void asOfCutsTheLedger() {
        List<AccountBalanceDto> rows = trialBalance(Instant.parse("2026-09-01T00:00:00Z"));
        Map<String, AccountBalanceDto> byCode = byCode(rows);

        assertThat(byCode.get("1010").debitBalance())
                .as("JE-2 was posted in October and must be excluded")
                .isEqualByComparingTo(new BigDecimal("500.00"));

        BigDecimal debits = rows.stream().map(AccountBalanceDto::debitBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = rows.stream().map(AccountBalanceDto::creditBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(debits)
                .as("a cut-off that splits an entry's own lines would unbalance the report")
                .isEqualByComparingTo(credits);
    }

    // ── fixture ─────────────────────────────────────────────────────────────

    private static List<AccountBalanceDto> trialBalance(Instant asOf) {
        return resolver.trialBalance(asOf).block();
    }

    private static Map<String, AccountBalanceDto> byCode(List<AccountBalanceDto> rows) {
        return rows.stream().collect(Collectors.toMap(
                AccountBalanceDto::accountCode, Function.identity()));
    }

    private static void account(String code, String name, AccountType type) {
        ChartOfAccountsEntry entry = new ChartOfAccountsEntry();
        entry.setId("coa-" + code);
        entry.setAccountCode(code);
        entry.setAccountName(name);
        entry.setAccountType(type);
        template.save(entry).block();
    }

    private static void posted(String id, Instant at, JournalLine... lines) {
        entry(id, JournalEntryStatus.POSTED, at, lines);
    }

    private static void entry(String id, JournalEntryStatus status, Instant at, JournalLine... lines) {
        JournalEntry entry = new JournalEntry();
        entry.setId(id);
        entry.setEntryNumber(id);
        entry.setStatus(status);
        entry.setPostedAt(at);
        entry.setLines(List.of(lines));
        template.save(entry).block();
    }

    private static JournalLine line(String code, String debit, String credit) {
        JournalLine line = new JournalLine();
        line.setAccountCode(code);
        line.setDebit(new BigDecimal(debit));
        line.setCredit(new BigDecimal(credit));
        return line;
    }
}
