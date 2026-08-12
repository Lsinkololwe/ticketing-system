package com.pml.identity.revocation.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A TCP hop placed in front of a container so a test can cut it mid-run.
 *
 * <p>The revocation design turns on what happens when a store becomes unreachable, which cannot
 * be asserted against a container that is simply up. Stopping the shared container is not an
 * option either — the suite reuses one Redis and one MongoDB across every test. Proxying the
 * connection gives per-test control over reachability without touching the containers.</p>
 *
 * <p>Modelled on {@code FlowBIntegrationSupport}'s {@code FaultInjectingProxy}, which does the
 * same job for the HTTP hop between Keycloak and the Identity Service.</p>
 *
 * <p>Both Lettuce and the MongoDB driver reconnect on their own, so {@link #resume()} restores
 * service without a restart.</p>
 */
public final class ControllableTcpProxy implements AutoCloseable {

    public enum Mode {
        /** Forward bytes in both directions. */
        PASS_THROUGH,
        /** Close every accepted connection immediately — models "the server is not there". */
        REFUSE,
        /** Accept and hold the connection open without forwarding — models a hung server. */
        BLACKHOLE
    }

    private final ServerSocket listener;
    private final String targetHost;
    private final int targetPort;
    private final ExecutorService pumps = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "tcp-proxy");
        thread.setDaemon(true);
        return thread;
    });
    private final Set<Socket> open = Collections.synchronizedSet(new HashSet<>());
    private final AtomicInteger accepted = new AtomicInteger();

    private volatile Mode mode = Mode.PASS_THROUGH;
    private volatile boolean running = true;

    private ControllableTcpProxy(ServerSocket listener, String targetHost, int targetPort) {
        this.listener = listener;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
    }

    /** Binds on an ephemeral loopback port and begins forwarding to {@code host:port}. */
    public static ControllableTcpProxy forwardingTo(String host, int port) {
        try {
            ServerSocket listener = new ServerSocket();
            listener.setReuseAddress(true);
            listener.bind(new InetSocketAddress("127.0.0.1", 0));

            ControllableTcpProxy proxy = new ControllableTcpProxy(listener, host, port);
            proxy.pumps.submit(proxy::acceptLoop);
            return proxy;
        } catch (IOException e) {
            throw new IllegalStateException("Could not start a TCP proxy to " + host + ":" + port, e);
        }
    }

    public int getPort() {
        return listener.getLocalPort();
    }

    public String getHost() {
        return "127.0.0.1";
    }

    /** Number of connections the proxy has accepted, in any mode. */
    public int acceptedConnections() {
        return accepted.get();
    }

    public Mode getMode() {
        return mode;
    }

    /**
     * Makes the backing store unreachable and drops connections already established, so the
     * client observes the outage immediately rather than on its next reconnect.
     */
    public void cut() {
        this.mode = Mode.REFUSE;
        closeOpenConnections();
    }

    /** Accepts connections but never answers — the client sees timeouts rather than resets. */
    public void hang() {
        this.mode = Mode.BLACKHOLE;
        closeOpenConnections();
    }

    /** Restores forwarding. Clients reconnect on their own. */
    public void resume() {
        this.mode = Mode.PASS_THROUGH;
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket client = listener.accept();
                accepted.incrementAndGet();

                switch (mode) {
                    case REFUSE -> closeQuietly(client);
                    case BLACKHOLE -> open.add(client);   // held, never serviced
                    case PASS_THROUGH -> pumps.submit(() -> relay(client));
                }
            } catch (IOException e) {
                if (running) {
                    // A transient accept failure should not silently kill the proxy thread.
                    continue;
                }
                return;
            }
        }
    }

    private void relay(Socket client) {
        Socket upstream = null;
        try {
            upstream = new Socket(targetHost, targetPort);
            open.add(client);
            open.add(upstream);

            Socket finalUpstream = upstream;
            pumps.submit(() -> pump(client, finalUpstream));
            pump(upstream, client);
        } catch (IOException e) {
            // Expected whenever a side is cut mid-transfer.
        } finally {
            closeQuietly(client);
            closeQuietly(upstream);
        }
    }

    private void pump(Socket from, Socket to) {
        byte[] buffer = new byte[8192];
        try (InputStream in = from.getInputStream()) {
            OutputStream out = to.getOutputStream();
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                out.flush();
            }
        } catch (IOException e) {
            // Normal on close or cut.
        } finally {
            closeQuietly(from);
            closeQuietly(to);
        }
    }

    private void closeOpenConnections() {
        synchronized (open) {
            open.forEach(ControllableTcpProxy::closeQuietly);
            open.clear();
        }
    }

    private static void closeQuietly(Socket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
            // nothing useful to do
        }
    }

    @Override
    public void close() {
        running = false;
        closeOpenConnections();
        try {
            listener.close();
        } catch (IOException ignored) {
            // nothing useful to do
        }
        pumps.shutdownNow();
    }
}
