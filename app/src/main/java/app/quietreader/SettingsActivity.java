package app.quietreader;

import android.app.*;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.view.*;
import android.widget.*;

/** Local reading preferences. No account, analytics, or network requests. */
public final class SettingsActivity extends Activity {
    private LinearLayout content;
    private SharedPreferences prefs;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle state){Theme.apply(this);super.onCreate(state);setResult(RESULT_OK);prefs=getSharedPreferences("appearance",0);draw();}
    private TextView label(String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(Theme.ink(this));v.setPadding(0,dp(12),0,dp(12));return v;}
    private void draw(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Theme.background(this));root.setFitsSystemWindows(true);root.setPadding(dp(18),0,dp(18),0);setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{root.setPadding(dp(18),insets.getSystemWindowInsetTop(),dp(18),insets.getSystemWindowInsetBottom());return insets;});
        TextView back=label("返回 · 阅读设置",18);back.setTextColor(Theme.accent(this));back.setMinHeight(dp(48));back.setOnClickListener(v->finish());root.addView(back);
        ScrollView scroll=new ScrollView(this);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        int font=getSharedPreferences("MainActivity",0).getInt("font",19);
        TextView size=label("阅读字号 · "+font,16);content.addView(size);
        SeekBar slider=new SeekBar(this);slider.setMax(12);slider.setProgress(Math.max(0,Math.min(12,font-16)));slider.setContentDescription("阅读字号，16 至 28");content.addView(slider,new LinearLayout.LayoutParams(-1,dp(48)));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int value,boolean user){size.setText("阅读字号 · "+(value+16));if(user)getSharedPreferences("MainActivity",0).edit().putInt("font",value+16).apply();}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){draw();}});
        choice("显示模式","mode",new String[]{"跟随系统","浅色","深色"},0,true);
        choice("阅读配色","palette",new String[]{"炭灰 / 素白","暖灰 / 米白","纯黑 / 纯白"},0,true);
        choice("链接与选中颜色","accent",new String[]{"蓝色","绿色","琥珀","紫色"},0,true);
        choice("正文字体","face",new String[]{"系统默认","衬线字体"},0,false);
        choice("正文行距","spacing",new String[]{"紧凑","标准","宽松"},1,false);
        TextView preview=label("阅读预览\n把注意力留给正文，按自己的习惯调整字号、行距和配色。",font);preview.setTypeface(prefs.getInt("face",0)==1?android.graphics.Typeface.SERIF:android.graphics.Typeface.DEFAULT);preview.setLineSpacing(0,Float.parseFloat(Theme.style(this).lineHeight()));content.addView(preview);
        TextView note=label("设置只保存在本机。登录状态不受影响。",12);content.addView(note);
        TextView reset=label("恢复默认阅读设置",15);reset.setTextColor(Theme.accent(this));reset.setOnClickListener(v->new AlertDialog.Builder(this).setMessage("恢复字号、字体、行距与配色？不会清除登录状态。").setNegativeButton("取消",null).setPositiveButton("恢复",(d,w)->{prefs.edit().remove("mode").remove("palette").remove("accent").remove("face").remove("spacing").apply();getSharedPreferences("MainActivity",0).edit().putInt("font",19).apply();recreate();}).show());content.addView(reset);
    }
    private void choice(String title,String key,String[] values,int fallback,boolean theme){
        int selected=Math.max(0,Math.min(values.length-1,prefs.getInt(key,fallback)));
        TextView row=label(title+" · "+values[selected],16);row.setMinHeight(dp(52));row.setContentDescription(title+"，"+values[selected]);content.addView(row);
        row.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(values,selected,(d,n)->{prefs.edit().putInt(key,n).apply();d.dismiss();if(theme)recreate();else draw();}).setNegativeButton("取消",null).show());
    }
}
