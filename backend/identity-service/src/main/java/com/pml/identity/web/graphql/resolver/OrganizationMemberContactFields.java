package com.pml.identity.web.graphql.resolver;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.identity.account.ContactService;
import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.domain.model.Contact;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.service.OrganizationMemberService;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * A team member's email and WhatsApp number, masked, for the organization's owners and admins and
 * for platform administrators. Everyone else, including the other members of the team, sees null:
 * a member list is not a directory of contact details.
 *
 * <p>The masked form ({@code j***@gmail.com}, {@code +260 97* ***123}) is the only one the schema
 * can return; the stored value is encrypted and never leaves the contact service.
 */
@DgsComponent
@RequiredArgsConstructor
public class OrganizationMemberContactFields {

    private final ContactService contacts;
    private final OrganizationMemberService members;

    @DgsData(parentType = "OrganizationMember", field = "contactEmailMasked")
    public Mono<String> email(DgsDataFetchingEnvironment env) {
        return masked(env.getSource(), ContactType.EMAIL);
    }

    @DgsData(parentType = "OrganizationMember", field = "contactPhoneMasked")
    public Mono<String> phone(DgsDataFetchingEnvironment env) {
        return masked(env.getSource(), ContactType.WHATSAPP);
    }

    private Mono<String> masked(OrganizationMember member, ContactType type) {
        if (member == null) {
            return Mono.empty();
        }
        return mayView(member.getOrganizationId())
                .filter(allowed -> allowed)
                .flatMap(allowed -> contacts.contactsOf(member.getUserId())
                        .filter(contact -> contact.getType() == type && contact.getVerifiedAt() != null)
                        .sort((a, b) -> Boolean.compare(b.isPrimary(), a.isPrimary()))
                        .map(Contact::getValueMasked)
                        .next());
    }

    /** Owners and admins of the member's organization, and platform administrators. */
    private Mono<Boolean> mayView(String organizationId) {
        return SecurityContextUtils.getAuthenticationContext().flatMap(context -> {
            if (context.isAdmin()) {
                return Mono.just(true);
            }
            if (context.getUserId() == null) {
                return Mono.just(false);
            }
            return members.findByUserAndOrganization(context.getUserId(), organizationId)
                    .map(viewer -> viewer.isActive()
                            && (viewer.getRole() == OrganizationRole.OWNER || viewer.getRole() == OrganizationRole.ADMIN))
                    .defaultIfEmpty(false);
        }).defaultIfEmpty(false);
    }
}
