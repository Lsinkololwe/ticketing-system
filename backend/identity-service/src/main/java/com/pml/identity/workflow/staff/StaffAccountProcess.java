package com.pml.identity.workflow.staff;

import com.pml.identity.account.StaffAccountCreator;
import com.pml.identity.account.StaffAccountPassword;
import com.pml.identity.account.TemporaryPasswordGenerator;
import com.pml.identity.account.StaffAccountRequest;
import com.pml.identity.account.StaffAccountSeal;
import com.pml.identity.account.StaffAccountSeal.Details;
import com.pml.identity.account.StaffAccountSteps;
import com.pml.identity.account.StaffRolePolicy;
import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.domain.model.User;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.identity.infrastructure.temporal.WorkflowIds;
import com.pml.identity.security.ContactHasher;
import com.pml.identity.workflow.staff.StaffAccountWorkflow.Created;
import com.pml.identity.workflow.staff.StaffAccountWorkflow.Request;
import com.pml.shared.constants.UserType;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.infrastructure.temporal.ProcessSearchAttributes;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import com.pml.shared.workflow.Refusals;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Service;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowStub;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * How the console's {@code createUser} reaches {@link StaffAccountWorkflow}.
 *
 * <p>Everything that can be refused is refused here, before an execution exists: a caller who is not a
 * super administrator, a role that is not a staff role, an address or number that does not parse, an
 * address another account already holds. Then the request is sealed and the workflow is started under
 * {@code staff-create/{staffKey}} with {@code USE_EXISTING}, so a repeated request meets the execution
 * already making that person. The call waits a bounded time for the account; past it the caller is told
 * to ask again ({@code STAFF_CREATION_IN_PROGRESS}) and the work carries on, so a half-made person is
 * never returned.</p>
 *
 * <p>The person's first password is generated here, one per call, and sealed into the request with the
 * rest. It is handed back to the caller only when the execution that applied it is the one this call
 * started.</p>
 */
@Service
public class StaffAccountProcess implements StaffAccountCreator {

    /** How long a request waits for the account before being told to ask again. */
    static final Duration DEFAULT_WAIT = Duration.ofSeconds(20);

    private final TemporalGateway temporal;
    private final ReactiveMongoTemplate template;
    private final ContactHasher hasher;
    private final StaffAccountSeal seal;
    private final StaffAccountSteps steps;
    private final TemporaryPasswordGenerator passwords;
    private final Duration wait;

    @Autowired
    public StaffAccountProcess(TemporalGateway temporal, ReactiveMongoTemplate template, ContactHasher hasher,
                               StaffAccountSeal seal, StaffAccountSteps steps, TemporaryPasswordGenerator passwords,
                               @Value("${identity.staff.create-wait:PT20S}") Duration wait) {
        this.temporal = temporal;
        this.template = template;
        this.hasher = hasher;
        this.seal = seal;
        this.steps = steps;
        this.passwords = passwords;
        this.wait = wait == null ? DEFAULT_WAIT : wait;
    }

    @Override
    public Mono<StaffAccountPassword> create(StaffAccountRequest request) {
        UserType role = request.role();
        if (role == null || !role.isStaffRole()) {
            return Mono.error(new TranslatedRefusal(ErrorCode.COMMAND_NOT_WELL_FORMED,
                    "a staff user is created with one of the staff roles"));
        }
        Optional<ContactHasher.Normalized> email = hasher.normalize(request.email(), ContactType.EMAIL, null, null);
        if (email.isEmpty()) {
            return Mono.error(new TranslatedRefusal(ErrorCode.CONTACT_INVALID, "a staff user needs a valid email"));
        }
        boolean hasPhone = request.phoneNumber() != null && !request.phoneNumber().isBlank();
        Optional<ContactHasher.Normalized> phone = hasPhone
                ? hasher.normalize(request.phoneNumber(), ContactType.WHATSAPP, null, null) : Optional.empty();
        if (hasPhone && phone.isEmpty()) {
            return Mono.error(new TranslatedRefusal(ErrorCode.PHONE_NUMBER_INVALID, "the staff member's number is not valid"));
        }
        // The password is made here, before anything starts, and travels only inside the sealed request.
        String temporaryPassword = passwords.generate();
        Details details = new Details(email.get().value(), request.firstName(), request.lastName(),
                phone.map(ContactHasher.Normalized::value).orElse(null),
                phone.map(ContactHasher.Normalized::region).orElse(null),
                role, request.actorId(), temporaryPassword);
        String staffKey = email.get().key();
        String attemptId = UUID.randomUUID().toString();

        // The caller is checked again here, at the one door to the process: the workflow runs with no caller.
        return StaffRolePolicy.requireSuperAdminToTouch(List.of(role))
                // An account this administrator already finished is returned as it is: its password was
                // shown when it was created, and a lost one is replaced with a reset.
                .then(Mono.defer(() -> steps.finishedAccount(details)))
                .map(finished -> new StaffAccountPassword(finished, null))
                .switchIfEmpty(Mono.defer(() -> steps.admit(details)
                        .then(Mono.defer(() -> seal.seal(details)))
                        .flatMap(sealed -> run(staffKey, new Request(StaffAccountSteps.creationId(staffKey), sealed, attemptId)))
                        .flatMap(created -> template.findById(created.accountId(), User.class)
                                // The password was applied by this call's own execution only when the execution
                                // carries this call's attempt id; one that met another call's run holds that
                                // call's password, and this call has none to show.
                                .map(account -> new StaffAccountPassword(account,
                                        attemptId.equals(created.attemptId()) ? temporaryPassword : null)))));
    }

    private Mono<Created> run(String staffKey, Request request) {
        return temporal.call(() -> {
                    StaffAccountWorkflow workflow = temporal.newWorkflow(StaffAccountWorkflow.class,
                            WorkflowIds.staffAccountCreate(staffKey), TaskQueues.ONBOARDING,
                            WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING,
                            WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE,
                            ProcessSearchAttributes.of("StaffAccountCreate", request.creationId()).build());
                    WorkflowClient.start(workflow::run, request);
                    try {
                        return WorkflowStub.fromTyped(workflow).getResult(wait.toMillis(), TimeUnit.MILLISECONDS, Created.class);
                    } catch (TimeoutException stillRunning) {
                        throw new TranslatedRefusal(ErrorCode.STAFF_CREATION_IN_PROGRESS,
                                "the staff account is still being created; ask again to receive it");
                    }
                })
                .onErrorMap(error -> Refusals.fromTemporal(error, ErrorCode.STAFF_CREATION_IN_PROGRESS));
    }
}
