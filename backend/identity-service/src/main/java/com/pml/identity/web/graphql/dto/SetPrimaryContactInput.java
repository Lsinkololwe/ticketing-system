package com.pml.identity.web.graphql.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The {@code SetPrimaryContactInput} GraphQL type. {@code toString} hides the code. */
public record SetPrimaryContactInput(@NotBlank @Size(max = 64) String contactId, @NotBlank @Size(max = 64) String challengeId,
                                     @NotBlank @Size(max = 64) String code) {
    @Override
    public String toString() {
        return "SetPrimaryContactInput[" + contactId + "]";
    }
}
