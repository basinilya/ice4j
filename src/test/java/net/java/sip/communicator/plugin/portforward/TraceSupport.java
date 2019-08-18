package net.java.sip.communicator.plugin.portforward;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class TraceSupport {

    private final Object self;
    private final Logger logger;

    public TraceSupport(Object self, Logger logger) {
        this.self = self;
        this.logger = logger;
    }

    public boolean entering(String sourceMethod, Object... params)
    {
    	if ("".length() == 10) {
    		logger.entering(self.getClass().getName(), sourceMethod, PortForwardUtils.prepend(params, self));
    	}
        StringBuilder sb = new StringBuilder(">");
        for (int i = 0; i < params.length; i++) {
            sb.append(" {").append(i).append("}");
        }
        log(null, Level.FINER, sb.toString(), params);
        return false;
    }

    public void exiting(String sourceMethod, Object res, boolean ok)
    {
    	if ("".length() == 10) {
	        if (ok) {
	            logger.exiting(self.getClass().getName(), sourceMethod, res);
	        } else {
	            logger.exiting(self.getClass().getName(), sourceMethod);
	        }
    	}
    	String fmt = ok ? "< {0}" : "<";
    	log(null, Level.FINER, fmt, res);
    }


    private void log(Throwable thrown, Level level, String msg, Object param1) {
        if (!logger.isLoggable(level)) {
            return;
        }
        log(thrown, level, msg, new Object[] { param1 });
    }

    private void log(Throwable thrown, Level level, String msg, Object[] params) {
        if (!logger.isLoggable(level)) {
            return;
        }
        LogRecord lr = new FQCNLogRecord(level, msg, TraceSupport.class.getName());
        lr.setThrown(thrown);
        lr.setParameters(params);
        logger.log(lr);
    }
}
