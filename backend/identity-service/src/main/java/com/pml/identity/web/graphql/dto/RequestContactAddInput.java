package com.pml.identity.web.graphql.dto;

import com.pml.identity.domain.enums.ContactType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The {@code RequestContactAddInput} GraphQL type. {@code toString} hides the contact. */
public record RequestContactAddInput(@NotNull ContactType type, @NotBlank @Size(max = 254) String value,
                                     @Size(max = 8) String regionHint) {
    @Override
    public String toString() {
        return "RequestContactAddInput[" + type + "]";
    }
}
