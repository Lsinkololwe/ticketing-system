package com.pml.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pml.identity.service.storage.DocumentKeys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("a document URL names a file in the organization's own folder or it names nothing")
class DocumentKeysTest {

    private static final String ORG = "org-kabwe";
    private static final String OWN = "organizations/org-kabwe/verification-documents/tax/abc-cert.pdf";

    @Test
    @DisplayName("the URL forms the platform issues resolve to the key")
    void ownUrlsResolve() {
        assertThat(DocumentKeys.ownedKey(ORG, "https://bucket.s3.eu-west-1.amazonaws.com/" + OWN + "?X-Amz-Signature=1"))
                .contains(OWN);
        assertThat(DocumentKeys.ownedKey(ORG, "http://localhost:8083/local-storage/" + OWN + "?exp=1&sig=2")).contains(OWN);
        assertThat(DocumentKeys.ownedKey(ORG, "https://s3.amazonaws.com/bucket/" + OWN)).contains(OWN);
        assertThat(DocumentKeys.ownedKey(ORG, OWN)).contains(OWN);
    }

    @ParameterizedTest
    @DisplayName("another organization's file, traversal, look-alikes and junk are refused")
    @ValueSource(strings = {
            "https://bucket.s3.amazonaws.com/organizations/org-victim/verification-documents/tax/a.pdf",
            "https://bucket.s3.amazonaws.com/organizations/org-kabwe-evil/verification-documents/tax/a.pdf",
            "https://bucket.s3.amazonaws.com/organizations/org-kabwe/verification-documents/../../org-victim/verification-documents/tax/a.pdf",
            "https://bucket.s3.amazonaws.com/organizations/org-kabwe/verification-documents/./x.pdf",
            "https://bucket.s3.amazonaws.com/xorganizations/org-kabwe/verification-documents/tax/a.pdf",
            "https://evil.example/x?p=organizations/org-kabwe/verification-documents/tax/a.pdf",
            "https://bucket.s3.amazonaws.com/organizations/org-kabwe/verification-documents/",
            "https://bucket.s3.amazonaws.com/organizations/org-kabwe/verification-documents//a.pdf",
            "https://bucket.s3.amazonaws.com/organizations\\org-kabwe\\verification-documents\\a.pdf",
            "not a url at all ::::",
            "   ",
    })
    void foreignOrMalformedRefused(String url) {
        assertThat(DocumentKeys.ownedKey(ORG, url)).isEmpty();
    }

    @Test
    @DisplayName("a missing organization or URL names nothing")
    void nulls() {
        assertThat(DocumentKeys.ownedKey(null, "https://x/" + OWN)).isEmpty();
        assertThat(DocumentKeys.ownedKey(ORG, null)).isEmpty();
    }
}
