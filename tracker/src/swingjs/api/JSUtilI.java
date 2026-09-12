package swingjs.api;

import java.io.File;

/**
 * Desktop-build stub for the SwingJS bridge interface.
 *
 * <p>The real {@code swingjs.api.JSUtilI} is provided by the SwingJS runtime
 * when Tracker runs in a browser (J2S). On the desktop the app never enters
 * the {@code OSPRuntime.isJS} code paths that use it, so this minimal stub
 * lets the full source tree compile for a native Windows/Mac build without
 * pulling in the web-deployment toolchain.
 */
public interface JSUtilI {
    byte[] getBytes(File file);
    void setFileBytes(File file, byte[] bytes);
    void setUIEnabled(Object component, boolean enabled);
}
