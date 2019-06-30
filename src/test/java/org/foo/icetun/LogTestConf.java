package org.foo.icetun;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.LogManager;

public final class LogTestConf {

    private LogTestConf() {
    }

    private static boolean inited;

    public static synchronized void init() {
        if (!inited) {
            inited = true;
            try (InputStream is = LogTestConf.class.getResourceAsStream("logger.properties")) {
                if (is != null) {
                    LogManager.getLogManager().readConfiguration(is);
                }
            } catch (IOException e) {
                //
            }
        }
    }

}
