package com.pml.shared.graphql;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The public-contract derivation on small schemas, so each thing it reports is seen reported —
 * the real subgraphs are clean, and a check only ever run against clean input proves nothing.
 */
@Tag("L1")
@Tag("ET-PLT-004")
@DisplayName("ET-PLT-004-R3 · the public contract removes what is tagged, and reports what that breaks")
class PublicContractDerivationTest {

    private static PublicContract of(String... subgraphs) {
        Map<String, String> byName = new java.util.LinkedHashMap<>();
        for (int i = 0; i < subgraphs.length; i++) {
            byName.put("s" + i, subgraphs[i]);
        }
        return PublicContract.derive(byName);
    }

    @Test
    @DisplayName("a field tagged admin or internal leaves the contract; an untagged or other-tagged one stays")
    void taggedFieldsLeave() {
        PublicContract contract = of("""
                type Query {
                    events: [String!]!
                    auditLog: [String!]! @tag(name: "admin")
                    reconcile: Boolean @tag(name: "internal")
                    myTickets: [String!]! @tag(name: "mobile")
                }
                """);

        assertThat(contract.exposes("Query", "events")).isTrue();
        assertThat(contract.exposes("Query", "myTickets")).isTrue();
        assertThat(contract.exposes("Query", "auditLog")).isFalse();
        assertThat(contract.exposes("Query", "reconcile")).isFalse();
    }

    @Test
    @DisplayName("a type tagged in one subgraph is excluded in all of them, extensions included")
    void typeTagIsGlobal() {
        PublicContract contract = of(
                "type Ledger @tag(name: \"admin\") { id: ID! }",
                "extend type Ledger { balance: String }\ntype Query { a: Int }");

        assertThat(contract.excludedTypes()).contains("Ledger");
        assertThat(contract.exposes("Ledger", "balance")).isFalse();
    }

    @Test
    @DisplayName("a public field or argument that uses an excluded type is reported as dangling")
    void danglingReferencesAreReported() {
        PublicContract contract = of("""
                type Ledger @tag(name: "admin") { id: ID! }
                input LedgerFilter @tag(name: "admin") { id: ID }
                type Query {
                    ledger: Ledger
                    entries(filter: LedgerFilter): [String!]!
                    hidden(filter: LedgerFilter): Ledger @tag(name: "admin")
                }
                """);

        assertThat(contract.dangling())
                .containsExactlyInAnyOrder("Query.ledger → Ledger", "Query.entries(filter) → LedgerFilter");
    }

    @Test
    @DisplayName("a public type whose every field is tagged is reported as emptied")
    void emptiedTypesAreReported() {
        PublicContract contract = of("""
                type Query { a: Int }
                type Revenue { total: String @tag(name: "admin") }
                """);

        assertThat(contract.emptied()).containsExactly("Revenue");
    }

    @Test
    @DisplayName("an untagged field with an admin name is reported; the same field tagged, or an organizer's own action, is not")
    void adminVocabularyIsCaught() {
        PublicContract leaking = of("""
                type Query {
                    stuckTransactions: [String!]!
                    systemHealth: String
                    platformRevenueAccount: String
                }
                type Mutation {
                    suspendOrganization(id: ID!): Boolean
                    resolvePaymentIssue(id: ID!): Boolean
                    suspended: Boolean
                    suspendMember(id: ID!): Boolean
                }
                """);
        PublicContract tagged = of("""
                type Query { stuckTransactions: [String!]! @tag(name: "admin") events: Int }
                """);

        assertThat(leaking.adminVocabulary()).containsExactlyInAnyOrder(
                "Query.stuckTransactions", "Query.systemHealth", "Query.platformRevenueAccount",
                "Mutation.suspendOrganization", "Mutation.resolvePaymentIssue");
        assertThat(tagged.adminVocabulary()).isEmpty();
    }
}
