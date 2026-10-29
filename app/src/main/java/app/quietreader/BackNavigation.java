package app.quietreader;

import android.app.Activity;
import android.os.Build;

/** Android16 targets use the system back dispatcher; older devices keep the override. */
final class BackNavigation {
    private BackNavigation(){}
    static void install(Activity activity,Runnable action){if(Build.VERSION.SDK_INT>=33)Api33.install(activity,action);}
    @androidx.annotation.RequiresApi(33)
    private static final class Api33 {
        static void install(Activity activity,Runnable action){activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,action::run);}
    }
}
