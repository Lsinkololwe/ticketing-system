package com.pml.booking.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.enums.AccountType;
import com.pml.booking.domain.enums.JournalEntryStatus;
import com.pml.booking.domain.model.ChartOfAccountsEntry;
import com.pml.booking.domain.model.JournalEntry;
import com.pml.booking.domain.model.JournalLine;
import com.pml.booking.repository.ChartOfAccountsRepository;
import com.pml.booking.repository.JournalEntryRepository;
import com.pml.booking.web.graphql.dto.AccountBalanceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The trial balance — every account's debit and credit totals as at a date.
 * {@code trialBalance(asOf)}, for FINANCE.
 *
 * <h2>Why it exists</h2>
 * It is the report that says whether the books balance at all. A double-entry ledger with no trial balance is a
 * ledger nobody can check.
 *
 * <h2>POSTED only, and that is the definition rather than a filter</h2>
 * A DRAFT entry has not affected balances and a REVERSED one has had its effect nullified by its
 * reversal — {@code JournalEntryStatus.affectsBalances()} says so, and only POSTED does. Summing
 * anything else produces a number that looks like a trial balance and reconciles against nothing.
 *
 * <h2>Every account, including the ones with no movement</h2>
 * The rows come from the chart of accounts and not from the entries, so an account that has never
 * been posted to appears with zeroes. That is deliberate: a trial balance is read to find what is
 * <em>missing</em> as often as what is wrong, and an account silently absent because nothing
 * touched it is indistinguishable from an account somebody deleted.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class TrialBalanceQueryResolver {

    private final JournalEntryRepository journalEntries;
    private final ChartOfAccountsRepository chartOfAccounts;

    /**
     * @param asOf inclusive upper bound; {@code null} means every posted entry to date
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE', 'SUPER_ADMIN')")
    public Mono<List<AccountBalanceDto>> trialBalance(@InputArgument Instant asOf) {
        log.debug("GraphQL query: trialBalance(asOf={})", asOf);

        return postedEntries(asOf)
                .flatMapIterable(entry -> entry.getLines() == null ? List.<JournalLine>of() : entry.getLines())
                .reduce(new LinkedHashMap<String, BigDecimal[]>(), TrialBalanceQueryResolver::accumulate)
                .flatMap(totals -> chartOfAccounts.findAll()
                        .collectList()
                        .map(accounts -> rows(accounts, totals)));
    }

    private Flux<JournalEntry> postedEntries(Instant asOf) {
        Flux<JournalEntry> posted = journalEntries.findByStatus(JournalEntryStatus.POSTED);
        if (asOf == null) {
            return posted;
        }
        // Filtered in memory rather than by a derived query: `entryDate` is a LocalDate and the
        // argument is an Instant, so a repository-level range would need a timezone decision this
        // resolver has no business making. Revisit if the ledger outgrows one page of accounts.
        return posted.filter(entry -> entry.getPostedAt() == null || !entry.getPostedAt().isAfter(asOf));
    }

    private static LinkedHashMap<String, BigDecimal[]> accumulate(
            LinkedHashMap<String, BigDecimal[]> totals, JournalLine line) {

        BigDecimal[] sums = totals.computeIfAbsent(line.getAccountCode(),
                code -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        sums[0] = sums[0].add(line.getDebit() == null ? BigDecimal.ZERO : line.getDebit());
        sums[1] = sums[1].add(line.getCredit() == null ? BigDecimal.ZERO : line.getCredit());
        return totals;
    }

    private static List<AccountBalanceDto> rows(
            List<ChartOfAccountsEntry> accounts, Map<String, BigDecimal[]> totals) {

        List<AccountBalanceDto> rows = new ArrayList<>();
        for (ChartOfAccountsEntry account : accounts) {
            BigDecimal[] sums = totals.getOrDefault(account.getAccountCode(),
                    new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            rows.add(new AccountBalanceDto(
                    account.getAccountCode(),
                    account.getAccountName(),
                    account.getAccountType() == null ? null : account.getAccountType().name(),
                    sums[0],
                    sums[1],
                    net(account.getAccountType(), sums[0], sums[1])));
        }
        rows.sort(Comparator.comparing(AccountBalanceDto::accountCode));
        return rows;
    }

    /**
     * Net balance in the account's own normal direction, so every row reads as a positive number
     * when the account behaves as it should.
     *
     * <p>Assets and expenses are debit-normal; liabilities, equity and revenue are credit-normal.
     * Returning {@code debit - credit} for all five would show every liability as negative and
     * make a page of correct figures look like a page of errors.
     */
    private static BigDecimal net(AccountType type, BigDecimal debit, BigDecimal credit) {
        if (type == AccountType.ASSET || type == AccountType.EXPENSE) {
            return debit.subtract(credit);
        }
        return credit.subtract(debit);
    }
}
