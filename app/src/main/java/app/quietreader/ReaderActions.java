package app.quietreader;

import android.app.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/** Reader controls share the page palette and remain scrollable with large system text. */
final class ReaderActions {
    private final Activity owner;
    private final Dialog dialog;
    private final LinearLayout rows;
    private int dp(float value){return Math.round(value*owner.getResources().getDisplayMetrics().density);}
    ReaderActions(Activity owner){
        this.owner=owner;dialog=new Dialog(owner);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        rows=new LinearLayout(owner);rows.setOrientation(LinearLayout.VERTICAL);rows.setPadding(dp(18),dp(8),dp(18),dp(12));
        GradientDrawable background=new GradientDrawable();background.setColor(Theme.background(owner));background.setCornerRadii(new float[]{dp(16),dp(16),dp(16),dp(16),0,0,0,0});rows.setBackground(background);
        ScrollView scroll=new ScrollView(owner){@Override protected void onMeasure(int width,int height){super.onMeasure(width,View.MeasureSpec.makeMeasureSpec(Math.round(owner.getResources().getDisplayMetrics().heightPixels*.72f),View.MeasureSpec.AT_MOST));}};
        scroll.setFillViewport(false);scroll.addView(rows);dialog.setContentView(scroll);dialog.setCanceledOnTouchOutside(true);
        rows.setOnApplyWindowInsetsListener((view,insets)->{rows.setPadding(dp(18)+insets.getSystemWindowInsetLeft(),dp(8),dp(18)+insets.getSystemWindowInsetRight(),dp(12)+insets.getSystemWindowInsetBottom());return insets;});
    }
    ReaderActions add(String label,Runnable action){
        TextView row=new ActionTextView(owner);row.setText(label);row.setTextSize(16);row.setTextColor(Theme.ink(owner));row.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);row.setPadding(dp(12),dp(14),dp(12),dp(14));row.setMinHeight(dp(52));row.setFocusable(true);row.setContentDescription(label);
        android.util.TypedValue ripple=new android.util.TypedValue();owner.getTheme().resolveAttribute(android.R.attr.selectableItemBackground,ripple,true);row.setBackgroundResource(ripple.resourceId);
        row.setOnClickListener(view->{dialog.dismiss();action.run();});rows.addView(row,new LinearLayout.LayoutParams(-1,-2));return this;
    }
    Dialog show(){
        Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.3f);window.setGravity(Gravity.BOTTOM);window.setNavigationBarColor(Theme.background(owner));}
        dialog.show();if(window!=null){window.setLayout(-1,-2);window.getDecorView().requestApplyInsets();}return dialog;
    }
}
