package app.quietreader;

import org.json.*;
import java.net.URI;
import static app.quietreader.Models.*;

/** Same-question paging only. Returned HTML still passes the normal article sanitizer. */
final class ZhihuAnswers {
    static String first(Item item){
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("/question/(\\d+)(?:/answer/\\d+)?/?").matcher(URI.create(item.url).getPath());
        return m.matches()?"https://www.zhihu.com/api/v4/questions/"+m.group(1)+"/answers?include=data%5B%2A%5D.content&limit=10&offset=0&sort_by=default":"";
    }
    static String next(Item item,String candidate){
        try{URI uri=URI.create(candidate),first=URI.create(first(item));
            return "https".equals(uri.getScheme())&&"www.zhihu.com".equals(uri.getHost())&&uri.getUserInfo()==null&&(uri.getPort()==-1||uri.getPort()==443)&&uri.getFragment()==null&&first.getPath().equals(uri.getPath())?candidate:"";
        }catch(Exception invalid){return "";}
    }
    static Document parse(Item item,JSONObject page)throws Exception{
        JSONArray rows=page.getJSONArray("data");StringBuilder html=new StringBuilder();
        for(int n=0;n<rows.length();n++){
            JSONObject answer=rows.getJSONObject(n);String id=answer.optString("id");
            if(!id.matches("[1-9]\\d*"))continue;
            JSONObject question=answer.optJSONObject("question");
            if(question!=null&&!first(item).contains("/questions/"+question.optString("id")+"/"))continue;
            JSONObject author=answer.optJSONObject("author");
            html.append("<div class='AnswerItem' id='").append(id).append("'");
            if(VideoPolicy.metadata(answer))html.append(" data-content-type='video'");
            html.append("><span class='AuthorInfo-name'>").append(ReaderHtml.escape(author==null?"":author.optString("name"))).append("</span><div class='RichContent-inner'><div class='RichText'>").append(answer.optString("content")).append("</div></div></div>");
        }
        return SourceParser.article(Source.ZHIHU,html.toString(),item.url);
    }
}
