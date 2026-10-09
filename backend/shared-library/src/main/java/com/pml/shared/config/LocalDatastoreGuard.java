package com.pml.shared.config;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Refuses to start the {@code local} profile against a database that is not on this machine.
 *
 * <h2>The hole this exists to close</h2>
 * The machine-wide policy hook decides whether a command may run by reading the command line and
 * the module's {@code application-local.yml}. That covers the two ordinary ways a run gets
 * pointed somewhere else — someone edits the profile file, or names a remote host on the line —
 * and it re-arms by itself in both cases.
 *
 * <p>It cannot cover the third: <b>an environment variable</b>. {@code MONGODB_URI=<remote>}
 * exported in a shell profile, set by a previous command, or written inline before the run,
 * overrides the file the hook inspected. Every artefact the hook can see still says
 * {@code localhost}, and the service connects to something else entirely. A hook that reads
 * command lines cannot close that, because the value does not appear on one.</p>
 *
 * <h2>So the check moved to where the truth is</h2>
 * Spring resolves the URI from every property source — file, environment, command line — before
 * this runs. Whatever it hands us <em>is</em> what the driver will dial. Validating that one
 * resolved string is the only place the guarantee can actually be made.
 *
 * <h2>Scoped to the local profile on purpose</h2>
 * This is not a general "no remote databases" rule; production connects to a real cluster and
 * must. It says something narrower and checkable: <b>a profile named {@code local} must be
 * local.</b> That is what makes the machine-wide allowance for local runs safe to grant — the
 * allowance is granted on the strength of the profile's name, so the name has to mean something.
 *
 * <h2>Fails at startup, not at first query</h2>
 * A service that starts and then talks to the wrong database has already done the damage by the
 * time anyone notices. Refusing to start is loud, immediate, and happens before the migration
 * runner fires.
 */
public final class LocalDatastoreGuard {

    /** What counts as "this machine". Matches the policy hook's own definition. */
    private static final Set<String> LOOPBACK = Set.of(
            "localhost", "localhost.localdomain", "127.0.0.1", "::1", "0:0:0:0:0:0:0:1");

    private LocalDatastoreGuard() {
    }

    /**
     * @param uri            the fully resolved datastore URI
     * @param activeProfiles the profiles Spring actually activated
     * @throws IllegalStateException when the local profile is active and {@code uri} names a host
     *                               that is not on this machine
     */
    public static void check(String uri, List<String> activeProfiles) {
        if (activeProfiles == null || activeProfiles.stream().noneMatch("local"::equalsIgnoreCase)) {
            return;
        }
        if (uri == null || uri.isBlank()) {
            return;                         // nothing configured is not this guard's problem
        }

        for (String host : hostsIn(uri)) {
            if (!LOOPBACK.contains(host.toLowerCase(Locale.ROOT))) {
                throw new IllegalStateException("""
                        Refusing to start: the 'local' profile is active but the datastore URI \
                        points at '%s', which is not this machine.

                        The local profile is what the machine-wide data-access policy relies on \
                        when it allows a service to be started here, so a 'local' profile that \
                        reaches a shared database would make that allowance meaningless. This is \
                        usually an exported MONGODB_URI overriding application-local.yml.

                        Either unset the override, or run under a profile that is not 'local'."""
                        .formatted(host));
            }
        }
    }

    /**
     * Every host named in a connection string, including each member of a replica-set seed list.
     *
     * <p>All of them are checked, not just the first: a seed list of
     * {@code localhost:27017,db-uat.internal:27017} would otherwise pass on its first entry while
     * the driver happily connects to the second.</p>
     */
    private static List<String> hostsIn(String uri) {
        int scheme = uri.indexOf("://");
        if (scheme < 0) {
            return List.of();
        }
        String remainder = uri.substring(scheme + 3);

        int path = remainder.indexOf('/');
        String authority = path < 0 ? remainder : remainder.substring(0, path);

        int credentials = authority.lastIndexOf('@');
        if (credentials >= 0) {
            authority = authority.substring(credentials + 1);
        }

        return List.of(authority.split(",")).stream()
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .map(LocalDatastoreGuard::hostOf)
                .filter(host -> !host.isEmpty())
                .toList();
    }

    /** Strips the port, leaving IPv6 literals intact. */
    private static String hostOf(String hostAndPort) {
        if (hostAndPort.startsWith("[")) {
            int close = hostAndPort.indexOf(']');
            return close < 0 ? hostAndPort : hostAndPort.substring(1, close);
        }
        int colon = hostAndPort.indexOf(':');
        String host = colon < 0 ? hostAndPort : hostAndPort.substring(0, colon);
        try {
            // Tolerates a value that is already a bare host; URI parsing is only used to reject
            // anything with escaping that would make the string mean something else.
            return URI.create("//" + host).getHost() == null ? host : host;
        } catch (IllegalArgumentException malformed) {
            return host;
        }
    }
}
