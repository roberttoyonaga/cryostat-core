package com.redhat.rhjmc.containerjfr.core.net;

import javax.management.remote.JMXServiceURL;

import com.redhat.rhjmc.containerjfr.core.sys.Clock;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import org.openjdk.jmc.rjmx.IConnectionHandle;
import org.openjdk.jmc.rjmx.IConnectionListener;
import org.openjdk.jmc.rjmx.internal.DefaultConnectionHandle;
import org.openjdk.jmc.rjmx.internal.JMXConnectionDescriptor;
import org.openjdk.jmc.rjmx.internal.RJMXConnection;
import org.openjdk.jmc.rjmx.internal.ServerDescriptor;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.internal.FlightRecorderServiceFactory;
import org.openjdk.jmc.ui.common.security.InMemoryCredentials;

public class JFRConnection implements AutoCloseable {

    static final String URL_FORMAT = "service:jmx:rmi:///jndi/rmi://%s:%d/jmxrmi";
    public static final int DEFAULT_PORT = 9091;

    private final ClientWriter cw;
    private final Clock clock;
    private final JMXServiceURL url;
    private final RJMXConnection rjmxConnection;
    private final IConnectionHandle handle;
    private final IFlightRecorderService service;

    JFRConnection(ClientWriter cw, Clock clock, String host, int port) throws Exception {
        this(cw, clock, new JMXServiceURL(String.format(URL_FORMAT, host, port)));
    }

    JFRConnection(ClientWriter cw, Clock clock, JMXServiceURL url) throws Exception {
        this.cw = cw;
        this.clock = clock;
        this.url = url;
        this.rjmxConnection = attemptConnect(url, 0);
        this.handle = new DefaultConnectionHandle(rjmxConnection, "RJMX Connection", new IConnectionListener[0]);
        this.service = new FlightRecorderServiceFactory().getServiceInstance(handle);
    }

    public IConnectionHandle getHandle() {
        return this.handle;
    }

    public IFlightRecorderService getService() {
        return this.service;
    }

    public long getApproximateServerTime(Clock clock) {
        return rjmxConnection.getApproximateServerTime(clock.getWallTime());
    }

    public String getHost() {
        return this.url.getHost();
    }

    public int getPort() {
        return this.url.getPort();
    }

    public void disconnect() {
        this.rjmxConnection.close();
    }

    @Override
    public void close() {
        this.disconnect();
    }

    private RJMXConnection attemptConnect(JMXServiceURL url, int maxRetry) throws Exception {
        JMXConnectionDescriptor cd = new JMXConnectionDescriptor(
                url,
                new InMemoryCredentials(null, null));
        ServerDescriptor sd = new ServerDescriptor(null, "Container", null);

        int attempts = 0;
        while (true) {
            try {
                RJMXConnection conn = new RJMXConnection(cd, sd, JFRConnection::failConnection);
                if (!conn.connect()) {
                    failConnection();
                }
                return conn;
            } catch (Exception e) {
                attempts++;
                cw.println(String.format("Connection attempt %d failed.", attempts));
                if (attempts >= maxRetry) {
                    cw.println("Too many failed connections. Aborting.");
                    throw e;
                } else {
                    cw.println(e);
                }
                clock.sleep(500);
            }
        }
    }

    private static void failConnection() {
        throw new RuntimeException("Connection Failed");
    }
}
