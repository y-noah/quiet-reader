package app.quietreader;

/** Visible identity for the temporary Android test APK, never included in the app. */
public final class TestDriverInfoActivity extends android.app.Activity {
    @Override public void onCreate(android.os.Bundle state){
        super.onCreate(state);android.widget.TextView info=new android.widget.TextView(this);
        int padding=Math.round(24*getResources().getDisplayMetrics().density);info.setPadding(padding,padding,padding,padding);info.setTextSize(17);
        info.setText("New 测试工具\n\n仅用于本轮 New 界面与缓存验证。测试由电脑发起，本页不在后台启动任务。\n\n测试结束后会卸载此工具，保留 New 主应用和数据。");setContentView(info);
    }
}
