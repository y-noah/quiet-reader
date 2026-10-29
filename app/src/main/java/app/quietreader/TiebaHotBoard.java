package app.quietreader;

import org.json.*;
import org.jsoup.parser.Parser;
import java.util.*;
import static app.quietreader.Models.*;

/** Official bang_topic only; personal suggestions/homepage posts are not a hot list. */
final class TiebaHotBoard {
    static final String ENDPOINT="https://tieba.baidu.com/hottopic/browse/topicList";
    private TiebaHotBoard(){}
    static List<Item> parse(String raw)throws Exception{
        JSONObject response=new JSONObject(raw);
        if(response.optInt("errno",-1)!=0)throw new IllegalStateException("贴吧未返回有效热议榜");
        JSONArray rows=response.getJSONObject("data").getJSONObject("bang_topic").getJSONArray("topic_list");
        List<Item> items=new ArrayList<>();Set<Long> seen=new HashSet<>();
        for(int n=0;n<rows.length()&&items.size()<150;n++){
            JSONObject row=rows.optJSONObject(n);if(row==null)continue;
            long id=row.optLong("topic_id");String title=row.optString("topic_name").trim();
            String url=Parser.unescapeEntities(row.optString("topic_url"),true);
            if(id<=0||title.isEmpty()||seen.contains(id)||TiebaTopic.id(url)!=id)continue;
            long seconds=row.optLong("create_time",0);Long published=seconds>0&&seconds<=Long.MAX_VALUE/1000?seconds*1000:null;
            String snippet=row.optString("topic_desc");if(snippet.isEmpty())snippet=row.optString("abstract");
            String heat=row.has("discuss_num")?row.optString("discuss_num")+" 讨论":"热议 #"+(n+1);
            items.add(new Item(Source.TIEBA,title,url,heat,"1".equals(row.optString("is_video_topic")),snippet,published));seen.add(id);
        }
        if(items.isEmpty())throw new IllegalStateException("贴吧未返回有效热议榜");
        return items;
    }
}
