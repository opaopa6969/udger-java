package org.udger.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.sql.SQLException;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FetchDeviceBrandStatementLeakTest {

    private static final String LUNA_UA =
            "Mozilla/5.0 (Linux; U; Android 4.0.4; sk-sk; Luna TAB474 Build/LunaTAB474) "
            + "AppleWebKit/534.30 (KHTML, like Gecko) Version/4.0 Safari/534.30";

    private UdgerParser parser;

    @Before
    public void initialize() throws SQLException {
        URL resource = this.getClass().getClassLoader().getResource("udgerdb_test_v3.dat");
        parser = new UdgerParser(resource.getFile());
    }

    @After
    public void close() throws IOException {
        parser.close();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getPreparedStmtMap() throws ReflectiveOperationException {
        Field f = UdgerParser.class.getDeclaredField("preparedStmtMap");
        f.setAccessible(true);
        return (Map<String, Object>) f.get(parser);
    }

    @Test
    public void fetchDeviceBrandRunsForLunaUa() throws Exception {
        parser.parseUa(LUNA_UA);
        Map<String, Object> map = getPreparedStmtMap();
        assertTrue("SQL_DEVICE_REGEX statement must be cached after parseUa with an Android UA",
                map.keySet().stream().anyMatch(k -> k.contains("udger_devicename_regex")));
    }

    @Test
    public void preparedStmtMapSizeDoesNotGrowAcrossManyParseCalls() throws Exception {
        parser.parseUa(LUNA_UA);
        Map<String, Object> mapAfterFirst = getPreparedStmtMap();
        int sizeAfterFirst = mapAfterFirst.size();

        for (int i = 0; i < 100; i++) {
            parser.parseUa(LUNA_UA);
        }
        Map<String, Object> mapAfterMany = getPreparedStmtMap();
        int sizeAfterMany = mapAfterMany.size();

        assertTrue("preparedStmtMap must contain SQL_DEVICE_REGEX entry after parseUa",
                mapAfterMany.keySet().stream().anyMatch(k -> k.contains("udger_devicename_regex")));
        assertEquals("preparedStmtMap size must not grow with parseUa calls (statement leak)",
                sizeAfterFirst, sizeAfterMany);
    }
}
