package app.quietreader;

/** Emulator-only bound service so WebView sees an actual assistive-technology service.
 * No speech, event storage, network, gestures or production component. */
public final class ReaderProbeAccessibilityService extends android.accessibilityservice.AccessibilityService {
    @Override public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {}
    @Override public void onInterrupt() {}
}
