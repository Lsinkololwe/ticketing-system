package com.pml.identity.web.graphql.dto;

import jakarta.validation.constraints.Size;

/**
 * What a user may change about their own profile.
 *
 * <p>Replaces the {@code JSON!} map the previous {@code updateProfile} mutation took. An untyped
 * input on a profile mutation validates nothing and documents nothing: every key is optional and
 * unchecked, and the set of writable fields is whatever the resolver body happens to read that
 * day. This record is the contract - a field absent from it cannot be written through this
 * mutation, and adding one is a schema change somebody reviews.
 *
 * <p>There is deliberately no phone number or email here: a contact is claimed by proving control
 * of it, never by typing it into a profile.
 *
 * <p>All fields are optional; a null means "leave it alone" rather than "clear it". A supplied
 * value is constrained - an unconstrained input type validates vacuously: {@code @Valid} runs,
 * finds nothing to check, and the mutation accepts anything the caller sends.
 *
 * @param firstName   given name
 * @param lastName    family name
 * @param displayName how the person is greeted
 * @param gender      self-declared, free text
 */
public record UpdateUserInput(
    @Size(min = 1, max = 100, message = "firstName must be 1-100 characters")
    String firstName,

    @Size(min = 1, max = 100, message = "lastName must be 1-100 characters")
    String lastName,

    @Size(min = 1, max = 200, message = "displayName must be 1-200 characters")
    String displayName,

    @Size(max = 40, message = "gender must be at most 40 characters")
    String gender
) {}
