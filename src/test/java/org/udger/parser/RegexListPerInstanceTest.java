package org.udger.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RegexListPerInstanceTest {

    private static final String FIREFOX_UA =
            "Mozilla/5.0 (Windows NT 10.0; WOW64; rv:40.0) Gecko/20100101 Firefox/40.0";

    private UdgerParser parserOriginal;
    private UdgerParser parserNoClientRegex;
    private Path modifiedDb;

    @Before
    public void initialize() throws Exception {
        URL original = this.getClass().getClassLoader().getResource("udgerdb_test_v3.dat");
        parserOriginal = new UdgerParser(original.getFile());

        modifiedDb = Files.createTempFile("udger_per_instance_test_", ".dat");
        try (InputStream in = original.openStream()) {
            Files.copy(in, modifiedDb,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        Class.forName("org.sqlite.JDBC");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + modifiedDb);
             Statement s = c.createStatement()) {
            s.executeUpdate("DELETE FROM udger_client_regex");
        }
        parserNoClientRegex = new UdgerParser(modifiedDb.toString());
    }

    @After
    public void close() throws IOException {
        parserOriginal.close();
        parserNoClientRegex.close();
        try {
            Files.deleteIfExists(modifiedDb);
        } catch (IOException e) {
        }
    }

    @Test
    public void differentDbProducesDifferentResult() throws Exception {
        UdgerUaResult r1 = parserOriginal.parseUa(FIREFOX_UA);
        UdgerUaResult r2 = parserNoClientRegex.parseUa(FIREFOX_UA);

        assertEquals("parserOriginal must classify Firefox UA as Browser",
                "Browser", r1.getUaClass());
        assertEquals("parserOriginal must detect uaFamily Firefox",
                "Firefox", r1.getUaFamily());

        assertEquals("parserNoClientRegex must classify as Unrecognized (client regex table empty)",
                "Unrecognized", r2.getUaClass());
        assertEquals("parserNoClientRegex must not detect a uaFamily",
                "", r2.getUaFamily());

        assertNotEquals("two instances with different DBs must produce different uaClass",
                r1.getUaClass(), r2.getUaClass());
    }

    @Test
    public void eachInstanceHasItsOwnClientRegstringList() throws Exception {
        parserOriginal.parseUa(FIREFOX_UA);
        parserNoClientRegex.parseUa(FIREFOX_UA);

        List<?> listA = getClientRegstringList(parserOriginal);
        List<?> listB = getClientRegstringList(parserNoClientRegex);

        assertNotEquals("each instance must own a distinct clientRegstringList reference",
                System.identityHashCode(listA), System.identityHashCode(listB));
        assertNotEquals("original DB must load non-empty regex list; modified DB must load empty list",
                listA.size(), listB.size());
        assertEquals("modified DB (regex table emptied) must yield empty clientRegstringList",
                0, listB.size());
    }

    private List<?> getClientRegstringList(UdgerParser p) throws ReflectiveOperationException {
        Field f = UdgerParser.class.getDeclaredField("clientRegstringList");
        f.setAccessible(true);
        return (List<?>) f.get(p);
    }
}
