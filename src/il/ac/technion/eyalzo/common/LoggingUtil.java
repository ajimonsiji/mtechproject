package il.ac.technion.eyalzo.common;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provide a log method that accepts several parameters.
 */
public class LoggingUtil {
	// a thread specific logger, useful in functions which are not associated
	// with a specialized logger
	public static ThreadLocal<Logger> localLog = new ThreadLocal<Logger>();

	/**
	 * Log a message using the provided logger, in the provided logging level
	 * <p>
	 * This method is intended for cases where there are a number of parameters
	 * (insertion strings) and we want to avoid creating our own array of
	 * objects.
	 * <p>
	 * Note 1: since Java will automatically create the array of objects out of
	 * the provided varying number of params, it is advisable to check if this
	 * level is loggable before calling this method. It's not crucial, though.
	 * <p>
	 * Note 2: there is no input checking. log must not be null!
	 * <p>
	 * Note 3: Do not call this method with an exception, use the native
	 * log.log(level, msg, throwable) instead
	 * 
	 * @param log
	 *            - logger to use. Never send null.
	 * @param level
	 *            - level to log with (usually one of: FINEST, FINER, FINE,
	 *            INFO, WARNING, SEVERE)
	 * @param msg
	 *            - main string message with room for insertion string i
	 *            represented as {i}
	 * @param params
	 *            - varying number of params (can also provide an array of
	 *            Object)
	 */
	public static void log(Logger log, Level level, String msg,
			Object... params) {
		if (log == null || !log.isLoggable(level)) {
			return;
		}
		StackTraceElement[] stack = Thread.currentThread().getStackTrace();
		//
		// We have to look back into the stack 2 positions since the stack will
		// now be:
		// 0 - Thread@getStackTrace
		// 1 - LoggingUtil.log (here)
		// 2 - Caller
		//
		int callerPos = Math.min(2, stack.length - 1);
		String callerClass = stack[callerPos].getClassName();
		String callerMethod = stack[callerPos].getMethodName();
		log.logp(level, callerClass, callerMethod, msg, params);
	}

	/**
	 * Get logger level recursively.
	 * <p>
	 * If the logger does not have a specific level defined looks recursively in
	 * its parents.
	 */
	static public Level getLoggerLevelExt(Logger logger) {
		Level level = null;
		Logger parentLog = logger;
		while (level == null && parentLog != null) {
			level = parentLog.getLevel();
			parentLog = parentLog.getParent();
		}
		return level;
	}
}