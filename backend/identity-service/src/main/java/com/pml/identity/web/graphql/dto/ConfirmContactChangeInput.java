package com.pml.identity.web.graphql.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The {@code ConfirmContactChangeInput} GraphQL type. {@code toString} hides the codes. */
public record ConfirmContactChangeInput(@NotBlank @Size(max = 64) String changeId,
                                        @NotBlank @Size(max = 64) String newContactCode,
                                        @Size(max = 64) String currentContactCode) {
    @Override
    public String toString() {
        return "ConfirmContactChangeInput[" + changeId + "]";
    }
}
