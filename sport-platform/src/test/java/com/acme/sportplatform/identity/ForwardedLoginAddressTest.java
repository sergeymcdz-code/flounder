package com.acme.sportplatform.identity;

import java.util.concurrent.atomic.AtomicReference;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.RemoteIpValve;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Tomcat native forwarding defaults used by application-prod.yml behind Caddy. */
class ForwardedLoginAddressTest {
    private String remoteAddress(String peer, String forwarded) throws Exception {
        var coyote = new org.apache.coyote.Request();
        coyote.remoteAddr().setString(peer);
        coyote.remoteHost().setString(peer);
        coyote.getMimeHeaders().addValue("X-Forwarded-For").setString(forwarded);
        var request = new Request(new Connector());
        request.setCoyoteRequest(coyote);
        var resolved = new AtomicReference<String>();
        var next = mock(Valve.class);
        doAnswer(call -> { resolved.set(((Request) call.getArgument(0)).getRemoteAddr()); return null; })
                .when(next).invoke(any(), any());
        var valve = new RemoteIpValve();
        valve.setNext(next);
        valve.invoke(request, new Response());
        return resolved.get();
    }
    @Test void nativeForwardingUsesClientAddressFromInternalCaddyPeer() throws Exception {
        assertThat(remoteAddress("172.18.0.4", "203.0.113.42")).isEqualTo("203.0.113.42");
    }
    @Test void directUntrustedPeerCannotReplaceItsAddressWithForwardedHeader() throws Exception {
        assertThat(remoteAddress("203.0.113.42", "198.51.100.8")).isEqualTo("203.0.113.42");
    }
}
