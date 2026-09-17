package jp.bunkaich.sukashimotion;
import android.os.Bundle;
import android.view.SurfaceControl;
import jp.bunkaich.sukashimotion.IAngleSink;
interface IShellBridge {
 Bundle inspect() = 0;
 Bundle capture(int displayId) = 1;
 Bundle hold(boolean innerPrimary, int previousOwner) = 2;
 void release() = 3;
 oneway void heartbeat() = 4;
 void startAngles(IAngleSink sink) = 5;
 void stopAngles() = 6;
 Bundle captureBehind(int displayId, in SurfaceControl[] exclude) = 7;
 Bundle windowState(int displayId) = 8;
 Bundle moveApp(int sourceDisplayId, int targetDisplayId, boolean idle) = 9;
 Bundle statusIcons(boolean hidden) = 10;
 Bundle navigate(int displayId, int action, int taskId) = 11;
 Bundle launchApp(int displayId, String component) = 12;
 Bundle taskWindowState(int displayId, int taskId, boolean requireWallpaper) = 13;
 void destroy() = 16777114;
}
