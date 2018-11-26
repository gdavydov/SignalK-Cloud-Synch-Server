package nz.co.fortytwo.signalk.artemis.intercept;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.script.ScriptContext;
import javax.script.ScriptException;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jdk.nashorn.api.scripting.NashornScriptEngine;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;


@SuppressWarnings("restriction")
public class JsBaseInterceptor extends BaseInterceptor {
	
	private static Logger logger = LogManager.getLogger(NMEAMsgInterceptor.class);
	
	protected static NashornScriptEngine engine;
	private static ScheduledExecutorService globalScheduledThreadPool = Executors.newScheduledThreadPool(20);
	
	static {
	
		NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
		// console.error
		engine = (NashornScriptEngine) factory.getScriptEngine(new String[] { "--language=es6" });

		// Injection of __NASHORN_POLYFILL_TIMER__ in ScriptContext
		engine.getContext().setAttribute("__NASHORN_POLYFILL_TIMER__", globalScheduledThreadPool,
				ScriptContext.ENGINE_SCOPE);
		
		try {
			engine.eval(IOUtils.toString(getIOStream("jsext/nashorn-polyfill.js")));
		} catch (ScriptException | IOException e) {
			logger.error(e, e);
		}
	}
	
	protected static InputStream getIOStream(String path) {

		if(logger.isDebugEnabled())logger.debug("Return resource {}", path);
		return Thread.currentThread().getContextClassLoader().getResourceAsStream(path);

	}
}
