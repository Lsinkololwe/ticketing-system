package com.pml.identity.auth;

import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** Test helper: run a Mono that must be refused and return the refusal. */
public final class Refusals {

    private Refusals() {
    }

    public static TranslatedRefusal of(Mono<?> mono) {
        Throwable thrown = catchThrowable(mono::block);
        assertThat(thrown).as("expected a refusal").isInstanceOf(TranslatedRefusal.class);
        return (TranslatedRefusal) thrown;
    }

    public static TranslatedRefusal of(ErrorCode expected, Mono<?> mono) {
        TranslatedRefusal refusal = of(mono);
        assertThat(refusal.errorCode()).isEqualTo(expected);
        return refusal;
    }
}
