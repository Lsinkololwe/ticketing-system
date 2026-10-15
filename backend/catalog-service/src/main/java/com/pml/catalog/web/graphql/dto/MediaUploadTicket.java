package com.pml.catalog.web.graphql.dto;

import java.time.Instant;
import java.util.List;

/** The {@code MediaUploadTicket} GraphQL type: where to send the file, what to send it with, and the token that completes it. */
public record MediaUploadTicket(String uploadToken, String uploadUrl, String method, List<Header> headers,
                                Instant expiresAt, int maxBytes) {

    /** One header the file's request must carry exactly as given. */
    public record Header(String name, String value) {
    }
}
