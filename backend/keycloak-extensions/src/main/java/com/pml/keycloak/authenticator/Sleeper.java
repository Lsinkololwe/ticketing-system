package com.pml.keycloak.authenticator;

import java.time.Duration;

/** Seam so the provisioning poll does not sleep in tests. */
@FunctionalInterface
public interface Sleeper {
    void sleep(Duration duration) throws InterruptedException;

    Sleeper REAL = d -> Thread.sleep(d.toMillis());
}
