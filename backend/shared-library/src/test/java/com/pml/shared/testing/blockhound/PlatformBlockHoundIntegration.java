package com.pml.shared.testing.blockhound;

import reactor.blockhound.BlockHound;
import reactor.blockhound.integration.BlockHoundIntegration;

/**
 * The platform's BlockHound configuration, applied wherever BlockHound is installed.
 *
 * <h2>What BlockHound adds that a grep cannot</h2>
 * The no-blocking rule can be checked statically for the calls we know to look for — {@code .block()},
 * {@code .blockFirst()}, {@code Thread.sleep}. It cannot catch a blocking call three frames
 * down inside a driver, a JSON parser or a library we did not write. BlockHound instruments
 * the JVM and fails at the moment such a call executes on a thread Reactor has marked
 * non-blocking, which is the only definition of the harm that actually matters.
 *
 * <h2>Every allowance is a hole</h2>
 * This class starts with none, deliberately. An allowance says "blocking here is acceptable",
 * and once a few accumulate nobody can say what the guarantee still covers. Add one only with
 * the reason written next to it, and prefer moving the call to
 * {@code Schedulers.boundedElastic()} — the platform's escape hatch — over allowing it in
 * place.
 *
 * <p>Registered through {@code META-INF/services}, so it applies automatically to every
 * module that installs BlockHound rather than each repeating the configuration.
 */
public final class PlatformBlockHoundIntegration implements BlockHoundIntegration {

    @Override
    public void applyTo(BlockHound.Builder builder) {
        // No allowances yet.
        //
        // When one becomes genuinely necessary it goes here, in this shape, with its
        // justification — never as a blanket package allowance:
        //
        //   builder.allowBlockingCallsInside(
        //           "com.pml.shared.infrastructure.SomeBlockingSdkAdapter", "call");
        //
        // Genuinely blocking third-party SDKs are wrapped once, in
        // `infrastructure/`, on Schedulers.boundedElastic(). Containing the blocking to one
        // adapter class is the point — a boundedElastic call scattered through a service
        // layer has not fixed anything, and an allowance scattered through this file is the
        // same mistake spelled differently.
    }
}
