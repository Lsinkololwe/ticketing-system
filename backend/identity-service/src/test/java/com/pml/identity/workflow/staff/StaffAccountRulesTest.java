package com.pml.identity.workflow.staff;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pml.identity.account.AccountFixtures;
import com.pml.identity.account.StaffAccountRequest;
import com.pml.identity.account.StaffAccountSeal;
import com.pml.identity.account.StaffAccountSteps;
import com.pml.identity.domain.enums.ContactType;
import com.pml.shared.constants.UserType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-IDN-001")
@DisplayName("ET-IDN-001 · staff creation: the record id is derived, the sealed request is ciphertext, secrets are never printed")
class StaffAccountRulesTest {

    @Test
    @DisplayName("the record id of a creation is the same for every attempt on one address and differs between addresses")
    void creationIdIsDerived() {
        String a = AccountFixtures.HASHER.hash(ContactType.EMAIL, "a@example.org");
        String b = AccountFixtures.HASHER.hash(ContactType.EMAIL, "b@example.org");

        assertThat(StaffAccountSteps.creationId(a)).isEqualTo(StaffAccountSteps.creationId(a)).isNotEqualTo(StaffAccountSteps.creationId(b));
        assertThat(StaffAccountSteps.creationId(a)).doesNotContain("@");
    }

    @Test
    @DisplayName("the steps are versioned from the first release and a failing step is attempted a bounded number of times")
    void versionedAndBounded() {
        assertThat(StaffAccountRules.STEPS).isEqualTo("staff-account-steps");
        assertThat(StaffAccountRules.ATTEMPTS).isBetween(3, 10);
    }

    @Test
    @DisplayName("the sealed request opens to what was sealed and holds no readable address, name, number or password")
    void sealedRequestIsCiphertext() {
        StaffAccountSeal seal = new StaffAccountSeal(new ObjectMapper(), AccountFixtures.CRYPTO);
        StaffAccountSeal.Details details = new StaffAccountSeal.Details("ops@example.org", "Chileshe", "Mwansa", "+260971234567",
                "ZM", UserType.FINANCE, "root-1", "Temp-Passw0rd!");

        String sealed = seal.seal(details).block();

        assertThat(sealed).doesNotContain("ops@example.org").doesNotContain("Chileshe").doesNotContain("Temp-Passw0rd!")
                .doesNotContain("260971234567");
        assertThat(seal.open(sealed).block()).isEqualTo(details);
    }

    @Test
    @DisplayName("neither a request nor its sealed details print the password or the person in a log line or a failure message")
    void secretsAreNotPrinted() {
        StaffAccountRequest request = new StaffAccountRequest("ops@example.org", "Chileshe", "Mwansa",
                "+260971234567", UserType.ADMIN, "root-1");
        StaffAccountSeal.Details details = new StaffAccountSeal.Details("ops@example.org", "Chileshe", "Mwansa",
                "+260971234567", "ZM", UserType.ADMIN, "root-1", "Temp-Passw0rd!");
        StaffAccountWorkflow.Request workflowRequest = new StaffAccountWorkflow.Request("id", "sealed-ciphertext", "attempt-1");

        for (String printed : new String[]{request.toString(), details.toString(), workflowRequest.toString()}) {
            assertThat(printed).doesNotContain("Temp-Passw0rd!").doesNotContain("ops@example.org").doesNotContain("Chileshe");
        }
    }
}
