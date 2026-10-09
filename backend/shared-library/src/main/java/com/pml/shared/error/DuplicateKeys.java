package com.pml.shared.error;

import org.springframework.dao.DuplicateKeyException;

/**
 * Recognises a duplicate-key failure whichever layer reports it.
 *
 * <h2>Why the driver's own exceptions have to be handled too</h2>
 * Spring's {@link DuplicateKeyException} is what the DAO translation layer
 * produces, and it is not what always arrives. A reactive write can surface
 * {@code MongoWriteException} straight from the driver, untranslated — so a
 * translator matching only the Spring type sees nothing, the failure falls
 * through to the defect path, and a reused idempotency key is reported as
 * {@code INTERNAL_ERROR} with {@code retryable: true}.
 *
 * <p>That combination is the worst one available on a payment path: the client
 * is told an internal error occurred and that retrying may help, so it retries —
 * which is precisely the double charge the idempotency key exists to prevent.
 * The failure is silent, because an internal error is a plausible thing to see.</p>
 *
 * <h2>Error 11000 is the whole test</h2>
 * A {@code MongoWriteException} covers every write failure, so the code is
 * checked rather than the type. Treating all of them as duplicates would
 * classify a document-validation rejection as a uniqueness conflict and offer a
 * retry that fails identically forever.
 */
public final class DuplicateKeys {

    /** MongoDB's duplicate-key error code. */
    private static final int DUPLICATE_KEY = 11000;

    private DuplicateKeys() {
    }

    /**
     * Whether the MongoDB driver is on the classpath. Services without a
     * database (the api-gateway) must still be able to translate errors: naming
     * a driver class directly in this class would throw NoClassDefFoundError
     * from inside the error handler and leave the request hanging.
     */
    private static final boolean DRIVER_PRESENT = isPresent("com.mongodb.MongoWriteException");

    private static boolean isPresent(String name) {
        try {
            Class.forName(name, false, DuplicateKeys.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError absent) {
            return false;
        }
    }

    /** Whether this throwable is a duplicate-key rejection, from any layer. */
    public static boolean isDuplicateKey(Throwable exception) {
        if (exception instanceof DuplicateKeyException) {
            return true;
        }
        return DRIVER_PRESENT && Driver.isDuplicateKey(exception);
    }

    /**
     * The text naming the offending index, or empty.
     *
     * <p>The index name is the only signal distinguishing an idempotency replay
     * from any other uniqueness conflict, and it exists only inside the message.
     * {@code MongoBulkWriteException} keeps it on the individual write error
     * rather than on the exception, so both have to be read.</p>
     */
    public static String describe(Throwable exception) {
        if (exception == null) {
            return "";
        }
        return DRIVER_PRESENT ? Driver.describe(exception) : String.valueOf(exception.getMessage());
    }

    /** Driver-typed checks, loaded only when the driver is present. */
    private static final class Driver {
        private Driver() {
        }

        static boolean isDuplicateKey(Throwable exception) {
            return switch (exception) {
                case com.mongodb.MongoWriteException write -> write.getError().getCode() == DUPLICATE_KEY;
                case com.mongodb.MongoBulkWriteException bulk -> bulk.getWriteErrors().stream()
                        .anyMatch(error -> error.getCode() == DUPLICATE_KEY);
                case null, default -> false;
            };
        }

        static String describe(Throwable exception) {
            return switch (exception) {
                case com.mongodb.MongoWriteException write -> String.valueOf(write.getError().getMessage());
                case com.mongodb.MongoBulkWriteException bulk -> bulk.getWriteErrors().stream()
                        .map(com.mongodb.bulk.BulkWriteError::getMessage)
                        .reduce("", (left, right) -> left + " " + right);
                default -> String.valueOf(exception.getMessage());
            };
        }
    }
}
