package jp.bunkaich.sukashimotion;

/** Model eligibility is separate from runtime capability discovery and physical validation. */
final class DeviceSupport {
    private DeviceSupport() {}
    static boolean eligible(String model) {
        return "SM-F9760".equals(model) || "SM-F966Z".equals(model);
    }
    // Native remapping on One UI 9 blanks both panels. The selected production
    // setup uses Folduo Home with a fixed mapping and display-local navigation.
    // Keep the native path disabled until seamless remapping is actually available.
    static boolean nativeEndpoints(String model) { return false; }
    static boolean homeReady(android.content.Context context) {
        if(!"SM-F9760".equals(android.os.Build.MODEL))return true;
        android.content.ComponentName home=new android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_HOME).resolveActivity(context.getPackageManager());
        return new android.content.ComponentName(context,HomeActivity.class).equals(home);
    }
}
