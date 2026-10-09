package com.pml.catalog.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-CAT-004")
@DisplayName("ET-CAT-004-R11 · a keyset cursor names a position and nothing else")
class KeysetCursorTest {

    @Test
    @DisplayName("it round-trips a time and an id, whatever characters the id holds")
    void roundTrip() {
        Instant at = Instant.parse("2026-09-19T10:00:00.123Z");

        var position = KeysetCursor.decode(KeysetCursor.encode(at, "65a1b2c3d4e5f60718293a4c")).orElseThrow();

        assertThat(position.createdAt()).isEqualTo(at);
        assertThat(position.id()).isEqualTo("65a1b2c3d4e5f60718293a4c");
        assertThat(KeysetCursor.decode(KeysetCursor.encode(at, "a:b:c")).orElseThrow().id()).isEqualTo("a:b:c");
    }

    @Test
    @DisplayName("text the list did not issue is not a cursor")
    void foreign() {
        assertThat(KeysetCursor.decode(null)).isEmpty();
        assertThat(KeysetCursor.decode("")).isEmpty();
        assertThat(KeysetCursor.decode("not-base64!!")).isEmpty();
        assertThat(KeysetCursor.decode(Base64.getUrlEncoder().encodeToString("discover:5".getBytes()))).isEmpty();
        assertThat(KeysetCursor.decode(Base64.getUrlEncoder().encodeToString("keyset:abc:1".getBytes()))).isEmpty();
        assertThat(KeysetCursor.decode(Base64.getUrlEncoder().encodeToString("keyset:123".getBytes()))).isEmpty();
    }
}
