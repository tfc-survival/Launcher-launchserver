package launcher.helper;

import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import launcher.LauncherAPI;

import javax.script.ScriptEngine;
import java.io.IOException;
import java.util.Locale;

public final class CommonHelper {

    public static final boolean dev = true;
    @LauncherAPI
    public static final int PROTOCOL_MAGIC = 0x724724_00 + 23;
    // Version info
    @LauncherAPI
    public static final String VERSION = "1.7.5.2";
    @LauncherAPI
    public static final String BUILD = readBuildNumber();

    private static final String[] SCRIPT_ENGINE_ARGS = {"-strict", "--language=es6", "--optimistic-types=false"};

    private CommonHelper() {
    }

    @LauncherAPI
    public static String low(String s) {
        return s.toLowerCase(Locale.US);
    }

    @LauncherAPI
    public static ScriptEngine newScriptEngine() {
        return new NashornScriptEngineFactory().getScriptEngine(SCRIPT_ENGINE_ARGS);
    }

    @LauncherAPI
    public static Thread newThread(String name, boolean daemon, Runnable runnable) {
        Thread thread = new Thread(runnable);
        thread.setDaemon(daemon);
        if (name != null) {
            thread.setName(name);
        }
        return thread;
    }

    @LauncherAPI
    public static String replace(String source, String... params) {
        for (int i = 0; i < params.length; i += 2) {
            source = source.replace('%' + params[i] + '%', params[i + 1]);
        }
        return source;
    }

    private static String readBuildNumber() {
        try {
            return IOHelper.request(IOHelper.getResourceURL("buildnumber"));
        } catch (IOException ignored) {
            return "dev"; // Maybe dev env?
        }
    }
}
