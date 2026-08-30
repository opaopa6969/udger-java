package org.udger.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.URL;
import java.net.UnknownHostException;
import java.sql.SQLException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Regression tests for the SSRF-like behaviour of {@link UdgerParser#parseIp}.
 *
 * <p>Before the fix, {@code parseIp} forwarded its argument to
 * {@link java.net.InetAddress#getByName(String)}, which resolves hostnames via
 * DNS. A caller that passes an attacker-controlled hostname (e.g. a value
 * coming from an HTTP header that was meant to carry the client IP) could
 * trigger an outbound DNS query from the server.
 *
 * <p>After the fix, {@code parseIp} only accepts IPv4 / IPv6 literals and throws
 * {@link UnknownHostException} for anything else, without performing any
 * network lookup.
 */
public class ParseIpRejectsHostnameTest {

    private UdgerParser parser;

    @Before
    public void initialize() throws SQLException {
        URL resource = this.getClass().getClassLoader().getResource("udgerdb_test_v3.dat");
        parser = new UdgerParser(resource.getFile());
    }

    @After
    public void close() throws IOException {
        if (parser != null) {
            parser.close();
        }
    }

    @Test(expected = UnknownHostException.class)
    public void parseIpRejectsLocalhostHostname() throws SQLException, UnknownHostException {
        parser.parseIp("localhost");
    }

    @Test(expected = UnknownHostException.class)
    public void parseIpRejectsDomainName() throws SQLException, UnknownHostException {
        parser.parseIp("example.com");
    }

    @Test(expected = UnknownHostException.class)
    public void parseIpRejectsArbitraryHostname() throws SQLException, UnknownHostException {
        parser.parseIp("evil.attacker.example");
    }

    @Test(expected = UnknownHostException.class)
    public void parseIpRejectsNull() throws SQLException, UnknownHostException {
        parser.parseIp(null);
    }

    @Test(expected = UnknownHostException.class)
    public void parseIpRejectsEmpty() throws SQLException, UnknownHostException {
        parser.parseIp("");
    }

    @Test
    public void parseIpStillAcceptsIpv4Literal() throws SQLException, UnknownHostException {
        UdgerIpResult ret = parser.parseIp("203.0.113.42");
        assertEquals("IPv4 literal must still be parsed and echoed",
                "203.0.113.42", ret.getIp());
        assertEquals("IPv4 literal must set ip_ver to 4",
                4, ret.getIpVer());
    }

    @Test
    public void parseIpStillAcceptsIpv6Literal() throws SQLException, UnknownHostException {
        String ipv6 = "2a02:598:a::78:161";
        UdgerIpResult ret = parser.parseIp(ipv6);
        assertEquals("IPv6 literal must set ip_ver to 6",
                6, ret.getIpVer());
    }

    @Test
    public void parseIpStillAcceptsBracketedIpv6() throws SQLException, UnknownHostException {
        UdgerIpResult ret = parser.parseIp("[::1]");
        assertEquals("bracketed IPv6 literal must set ip_ver to 6",
                6, ret.getIpVer());
    }
}
