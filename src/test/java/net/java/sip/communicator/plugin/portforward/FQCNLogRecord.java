package net.java.sip.communicator.plugin.portforward;

import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * LogRecord with overridable isLoggerImplFrame().
 */
public class FQCNLogRecord extends LogRecord {

  private static final long serialVersionUID = 1L;

  /**
   * @param level  a logging level value
   * @param msg  the raw non-localized logging message (may be null)
   * @param loggerImplClass logger wrapper class name
   */
  public FQCNLogRecord(Level level, String msg, String loggerImplClass) {
      super(level, msg);
      super.setSourceClassName(null); // sets super.needToInferCaller to false
      this.loggerImplClass = loggerImplClass;
      needToInferCaller = true;
  }

  @Override
  public String getSourceClassName() {
      if (needToInferCaller) {
          inferCaller();
      }
      return super.getSourceClassName();
  }
  
  @Override
  public void setSourceClassName(String sourceClassName) {
    super.setSourceClassName(sourceClassName);
    needToInferCaller = false;
  }

  @Override
  public String getSourceMethodName() {
      if (needToInferCaller) {
          inferCaller();
      }
      return super.getSourceMethodName();
  }

  @Override
  public void setSourceMethodName(String sourceMethodName) {
    super.setSourceMethodName(sourceMethodName);
    needToInferCaller = false;
}

  private void inferCaller() {
      needToInferCaller = false;
      Throwable throwable = new Throwable();
      StackTraceElement[] stackTrace = throwable.getStackTrace();
      int depth = stackTrace.length;

      boolean lookingForLogger = true;
      for (int ix = 0; ix < depth; ix++) {
          // Calling getStackTraceElement directly prevents the VM
          // from paying the cost of building the entire stack frame.
          StackTraceElement frame = stackTrace[ix];
          String cname = frame.getClassName();
          boolean isLoggerImpl = isLoggerImplFrame(cname);
          if (lookingForLogger) {
              // Skip all frames until we have found the first logger frame.
              if (isLoggerImpl) {
                  lookingForLogger = false;
              }
          } else {
              if (!isLoggerImpl) {
                  // skip reflection call
                  if (!cname.startsWith("java.lang.reflect.") && !cname.startsWith("sun.reflect.")) {
                     // We've found the relevant frame.
                     setSourceClassName(cname);
                     setSourceMethodName(frame.getMethodName());
                     return;
                  }
              }
          }
      }
      // We haven't found a suitable frame, so just punt.  This is
      // OK as we are only committed to making a "best effort" here.
  }

  /**
   * @param cname class name in stack frame
   * @return true if cname equals to loggerImplClass or cname equals to one of the standard java loggers
   */
  protected boolean isLoggerImplFrame(String cname) {
    // the log record could be created for a platform logger
    return cname.equals(loggerImplClass)
        //|| cname.equals(FQCNLogRecord.class.getName())
        || (cname.equals("java.util.logging.Logger") ||
            cname.startsWith("java.util.logging.LoggingProxyImpl") ||
            cname.startsWith("sun.util.logging."));
  }

  private final String loggerImplClass;

  private transient boolean needToInferCaller;

}
