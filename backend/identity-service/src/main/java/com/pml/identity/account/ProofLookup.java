package com.pml.identity.account;

import reactor.core.publisher.Mono;

/** Reads a proof of contact control (CONTRACT 12). Implemented by the auth engine over Redis. */
public interface ProofLookup {

    /** Empty when the proof is unknown or expired. */
    Mono<ProofRecord> find(String proofId);
}
