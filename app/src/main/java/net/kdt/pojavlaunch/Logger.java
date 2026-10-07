package net.kdt.pojavlaunch;

public class Logger {
    public Logger() {
    }

    public static native void begin(boolean isPrinting);

    public static native void appendToLog(String text);
}
