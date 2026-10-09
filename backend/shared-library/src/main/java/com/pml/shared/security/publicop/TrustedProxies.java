package com.pml.shared.security.publicop;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The network peers allowed to tell a service who the real client is, through {@code X-Forwarded-For}.
 *
 * <p>The header is only evidence when the machine that delivered it is one we run: the Apollo Router, the
 * gateway, a load balancer. From anyone else it is a string the caller typed, and counting requests against
 * it would let a direct caller mint a fresh rate-limit bucket per request. So the filter asks this class
 * whether the immediate peer is trusted before it reads the header, and ignores the header otherwise.
 *
 * <p>Configured as a comma-separated list of addresses and CIDR blocks (IPv4 or IPv6), e.g.
 * {@code 127.0.0.1,::1,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16}. The default, an empty list, trusts nobody.
 */
public final class TrustedProxies {

    /** Trusts nobody: the peer address is always the client. */
    public static final TrustedProxies NONE = new TrustedProxies(List.of());

    private static final Pattern LITERAL = Pattern.compile("^[0-9a-fA-F:.]{2,45}$");

    private record Block(byte[] network, int prefixBits) {
        boolean contains(byte[] address) {
            if (address.length != network.length) {
                return false;
            }
            int whole = prefixBits / 8;
            for (int i = 0; i < whole; i++) {
                if (address[i] != network[i]) {
                    return false;
                }
            }
            int rest = prefixBits % 8;
            if (rest == 0) {
                return true;
            }
            int mask = (0xFF << (8 - rest)) & 0xFF;
            return (address[whole] & mask) == (network[whole] & mask);
        }
    }

    private final List<Block> blocks;

    private TrustedProxies(List<Block> blocks) {
        this.blocks = List.copyOf(blocks);
    }

    /** Parses a comma-separated list of addresses and CIDR blocks; blank means none. A bad entry is a startup error. */
    public static TrustedProxies parse(String csv) {
        if (csv == null || csv.isBlank()) {
            return NONE;
        }
        List<Block> blocks = new ArrayList<>();
        for (String raw : csv.split(",")) {
            String entry = raw.trim();
            if (entry.isEmpty()) {
                continue;
            }
            String host = entry;
            Integer bits = null;
            int slash = entry.indexOf('/');
            if (slash >= 0) {
                host = entry.substring(0, slash);
                try {
                    bits = Integer.parseInt(entry.substring(slash + 1));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("not a CIDR block: " + entry);
                }
            }
            byte[] address = literal(host);
            if (address == null) {
                throw new IllegalArgumentException("not an IP address or CIDR block: " + entry);
            }
            int max = address.length * 8;
            if (bits == null) {
                bits = max;
            }
            if (bits < 0 || bits > max) {
                throw new IllegalArgumentException("prefix length out of range: " + entry);
            }
            blocks.add(new Block(address, bits));
        }
        return new TrustedProxies(blocks);
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    /** True when the peer is one of the configured proxies. */
    public boolean trusts(InetAddress peer) {
        return peer != null && trusts(peer.getAddress());
    }

    /** True when the textual address is an IP literal inside a configured block. */
    public boolean trusts(String address) {
        byte[] bytes = literal(address);
        return bytes != null && trusts(bytes);
    }

    private boolean trusts(byte[] address) {
        byte[] normal = unmapped(address);
        return blocks.stream().anyMatch(block -> block.contains(normal));
    }

    /** The bytes of an IP literal, or null for anything else (a name is never resolved here). */
    static byte[] literal(String text) {
        if (text == null) {
            return null;
        }
        String candidate = text.trim();
        if (!LITERAL.matcher(candidate).matches() || (candidate.indexOf(':') < 0 && candidate.indexOf('.') < 0)) {
            return null;
        }
        try {
            // Only digits, hex, ':' and '.' reach here, so this parses a literal and never queries DNS.
            return unmapped(InetAddress.getByName(candidate).getAddress());
        } catch (UnknownHostException e) {
            return null;
        }
    }

    /** An IPv4-mapped IPv6 address ({@code ::ffff:a.b.c.d}) is the IPv4 address. */
    private static byte[] unmapped(byte[] address) {
        if (address.length == 16) {
            for (int i = 0; i < 10; i++) {
                if (address[i] != 0) {
                    return address;
                }
            }
            if (address[10] == (byte) 0xFF && address[11] == (byte) 0xFF) {
                return new byte[]{address[12], address[13], address[14], address[15]};
            }
        }
        return address;
    }
}
