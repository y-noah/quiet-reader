package app.quietreader;

import android.app.*;
import android.os.*;
import android.database.*;
import android.database.sqlite.*;
import android.webkit.CookieManager;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Actual public BBC request on the authorized phone; never exports cookies or headers. */
final class ReleasePhoneBbcResponseProbe {
    private final Instrumentation test;
    ReleasePhoneBbcResponseProbe(Instrumentation test){this.test=test;}
    void run(){StringBuilder report=new StringBuilder();int failures=0;try{
        if(!test.getTargetContext().getPackageName().equals("app.quietreader")||!Build.MODEL.equals("PHY110"))throw new IllegalStateException("Phone scope mismatch");
        List<String> urls=new ArrayList<>();
        try(SQLiteDatabase db=SQLiteDatabase.openDatabase(test.getTargetContext().getDatabasePath("personalized-hot-topics-v1.db").getPath(),null,SQLiteDatabase.OPEN_READONLY);Cursor c=db.rawQuery("SELECT payload FROM candidates WHERE source='BBC' AND active=1 ORDER BY rank LIMIT 3",null)){
            while(c.moveToNext())urls.add(new JSONObject(c.getString(0)).getString("url"));
        }
        if(urls.isEmpty())throw new IllegalStateException("No retained BBC candidate");
        boolean[] web={false};test.runOnMainSync(()->web[0]=test.getTargetContext().getSharedPreferences("source-session",0).getBoolean("BBC-web",false));report.append("BBC preferWeb=").append(web[0]).append('\n');
        int n=0;for(String url:urls){
            if(!url.matches("https://www\\.bbc\\.com/zhongwen/articles/c[a-z0-9]+/(simp|trad)"))throw new IllegalStateException("Public article scope mismatch");
            String[] cookie={null};test.runOnMainSync(()->cookie[0]=CookieManager.getInstance().getCookie(url));long started=SystemClock.elapsedRealtime();
            HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();connection.setConnectTimeout(5000);connection.setReadTimeout(7000);connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36");connection.setRequestProperty("Accept","text/html,application/json,image/*;q=0.8,*/*;q=0.5");connection.setRequestProperty("Referer",url);if(cookie[0]!=null)connection.setRequestProperty("Cookie",cookie[0]);
            try{
                int code=connection.getResponseCode();report.append("BBC sample=").append(n).append(" status=").append(code);
                if(code==200){byte[] bytes;try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] block=new byte[8192];int count;while((count=in.read(block))!=-1){if(out.size()+count>4*1024*1024)throw new IOException("Response too large");out.write(block,0,count);}bytes=out.toByteArray();}
                    String html=new String(bytes,StandardCharsets.UTF_8);java.util.regex.Matcher state=java.util.regex.Pattern.compile("<script[^>]*id=[\"']__NEXT_DATA__[\"'][^>]*>(.*?)</script>",java.util.regex.Pattern.DOTALL).matcher(html);
                    report.append(" chars=").append(html.length()).append(" hasState=").append(state.find());
                    if(state.reset().find()){JSONObject props=new JSONObject(state.group(1)).getJSONObject("props").getJSONObject("pageProps");report.append(" pageType=").append(props.optString("pageType")).append(" service=").append(props.optString("service")).append(" dataStatus=").append(props.optInt("status"));
                        JSONObject saved=new JSONObject().put("props",new JSONObject().put("pageProps",props));Files.write(new File(test.getTargetContext().getExternalFilesDir(null),"phone-bbc-response-"+n+".json").toPath(),saved.toString().getBytes(StandardCharsets.UTF_8));
                    }
                }
            }catch(Exception error){report.append(" error=").append(error.getClass().getSimpleName());}
            finally{connection.disconnect();report.append(" elapsedMs=").append(SystemClock.elapsedRealtime()-started).append('\n');}n++;
        }
    }catch(Throwable error){failures++;report.append(android.util.Log.getStackTraceString(error));}
        Bundle result=new Bundle();result.putString("stream",report.toString()+"diagnosticFailures="+failures+"; request success does not imply production parse/cache success\n");test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
