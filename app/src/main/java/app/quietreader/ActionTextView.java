package app.quietreader;

import android.content.Context;
import android.widget.Button;
import android.widget.TextView;

/** Preserve text styling while exposing clickable text as a standard button. */
final class ActionTextView extends TextView {
    ActionTextView(Context context) { super(context); }

    @Override public CharSequence getAccessibilityClassName() {
        return isClickable() ? Button.class.getName() : super.getAccessibilityClassName();
    }
}
