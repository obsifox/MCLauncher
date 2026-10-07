package net.kdt.pojavlaunch.utils;

public class JREUtils {
    public static volatile String LD_LIBRARY_PATH;
    public static volatile String jvmLibraryPath;

    public JREUtils() {
    }

    public static native int chdir(String path);

    public static native boolean dlopen(String libPath);

    public static native void setLdLibraryPath(String ldLibraryPath);

    public static native void setupBridgeWindow(Object surface);

    public static native void releaseBridgeWindow();

    public static native void initializeHooks();

    public static native void setupExitMethod(android.content.Context context);
}
