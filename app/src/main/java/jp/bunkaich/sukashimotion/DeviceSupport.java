package jp.bunkaich.sukashimotion;

/** Model eligibility is separate from runtime capability discovery and physical validation. */
final class DeviceSupport {
    private DeviceSupport() {}
    static boolean eligible(String model) {
        return "SM-F9760".equals(model) || "SM-F966Z".equals(model);
    }
}
