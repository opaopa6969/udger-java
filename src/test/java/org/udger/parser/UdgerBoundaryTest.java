package org.udger.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.io.IOException;
import java.net.URL;
import java.net.UnknownHostException;
import java.sql.SQLException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Boundary and edge-case coverage for the public {@link UdgerParser} API.
 *
 * <p>These tests exercise behaviours that are documented or implied by the
 * public API but were not previously covered by the surefire-run test suite:
 * <ul>
 *   <li>LRU cache hit returns the cached instance (regression-prone, backed by README perf claims)</li>
 *   <li>cacheCapacity == 0 disables caching and parseUa still works</li>
 *   <li>parseIp on an unrecognised IPv4 yields Unrecognized with ip_ver == 4</li>
 *   <li>setOsParserEnabled(false) leaves OS fields empty and suppresses device-brand detection</li>
 *   <li>parseIp normalises an IPv6 address with a zero-run (::) before DB lookup</li>
 * </ul>
 *
 * Only tests are added; no production code is changed. A failure here indicates
 * a bug to be tracked separately rather than patched in this change.
 */
public class UdgerBoundaryTest {

    private static final String FIREFOX_UA =
            "Mozilla/5.0 (Windows NT 10.0; WOW64; rv:40.0) Gecko/20100101 Firefox/40.0";

    private URL dbUrl;

    private UdgerParser parser;

    @Before
    public void initialize() throws SQLException {
        dbUrl = this.getClass().getClassLoader().getResource("udgerdb_test_v3.dat");
        parser = new UdgerParser(dbUrl.getFile());
    }

    @After
    public void close() throws IOException {
        if (parser != null) {
            parser.close();
        }
    }

    @Test
    public void parseUaReturnsCachedInstanceOnSecondCall() throws SQLException {
        UdgerUaResult first = parser.parseUa(FIREFOX_UA);
        assertNotNull("first parseUa must produce a non-null result", first);
        UdgerUaResult second = parser.parseUa(FIREFOX_UA);
        assertNotNull("second parseUa must produce a non-null result", second);
        assertSame("LRU cache hit must return the exact instance cached by the first call",
                first, second);
        assertEquals("cached Firefox UA must retain family",
                "Firefox", second.getUaFamily());
    }

    @Test
    public void parseUaWorksWithZeroCacheCapacity() throws SQLException, IOException {
        UdgerParser noCache = new UdgerParser(dbUrl.getFile(), 0);
        try {
            UdgerUaResult ret = noCacheParser(noCache, FIREFOX_UA);
            assertEquals("parser with cacheCapacity == 0 must still classify Firefox as Browser",
                    "Browser", ret.getUaClass());
            assertEquals("parser with cacheCapacity == 0 must still detect uaFamily Firefox",
                    "Firefox", ret.getUaFamily());
        } finally {
            noCache.close();
        }
    }

    @Test
    public void parseIpUnrecognisedIpv4SetsIpVer4() throws SQLException, UnknownHostException {
        UdgerIpResult ret = parser.parseIp("203.0.113.42");
        assertEquals("an unrecognised IPv4 must keep default classification Unrecognized",
                "Unrecognized", ret.getIpClassification());
        assertEquals("an unrecognised IPv4 must keep default classification code",
                "unrecognized", ret.getIpClassificationCode());
        assertEquals("an unrecognised IPv4 must set ip_ver to 4",
                4, ret.getIpVer());
        assertEquals("an unrecognised IPv4 must echo the input ip string",
                "203.0.113.42", ret.getIp());
        assertEquals("an unrecognised IPv4 must have empty datacenter name",
                "", ret.getDataCenterName());
        assertNull("an unrecognised IPv4 must have null crawler family code",
                retNullIfEmpty(ret.getCrawlerFamilyCode()));
    }

    @Test
    public void disablingOsParserLeavesOsEmptyAndSuppressesDeviceBrand() throws SQLException {
        parser.setOsParserEnabled(false);
        UdgerUaResult ret = parser.parseUa(FIREFOX_UA);
        assertEquals("OS parser disabled: ua must still be classified as Browser",
                "Browser", ret.getUaClass());
        assertEquals("OS parser disabled: uaFamily must still be Firefox",
                "Firefox", ret.getUaFamily());
        assertEquals("OS parser disabled: os family must stay empty",
                "", ret.getOsFamily());
        assertEquals("OS parser disabled: os family code must stay empty",
                "", ret.getOsFamilyCode());
        assertEquals("OS parser disabled: os must stay empty",
                "", ret.getOs());
        assertEquals("OS parser disabled: device brand must stay empty (osFamilyCode gate)",
                "", ret.getDeviceBrand());
        assertEquals("OS parser disabled: device brand code must stay empty",
                "", ret.getDeviceBrandCode());
    }

    @Test
    public void parseIpNormalisesCompressedIpv6BeforeLookup() throws SQLException, UnknownHostException {
        String compressed = "2a02:598:a::78:161";
        String expanded = "2a02:598:a:0:0:0:78:161";
        UdgerIpResult ret = parser.parseIp(compressed);
        assertEquals("IPv6 parse must set ip_ver to 6",
                6, ret.getIpVer());
        assertEquals("IPv6 parse must echo the input ip string",
                compressed, ret.getIp());
        assertNotNull("IPv6 parse must produce a non-null classification",
                ret.getIpClassification());
        UdgerIpResult retExpanded = parser.parseIp(expanded);
        assertEquals("compressed and expanded IPv6 forms must resolve to the same classification code",
                ret.getIpClassificationCode(), retExpanded.getIpClassificationCode());
        assertEquals("compressed and expanded IPv6 forms must resolve to the same country code",
                ret.getIpCountryCode(), retExpanded.getIpCountryCode());
    }

    private UdgerUaResult noCacheParser(UdgerParser p, String ua) throws SQLException {
        return p.parseUa(ua);
    }

    private static String retNullIfEmpty(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
