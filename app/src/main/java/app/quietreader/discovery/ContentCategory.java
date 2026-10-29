package app.quietreader.discovery;

import app.quietreader.Models.Source;
import java.util.*;

/** Conservative mapping of explicit source nodes/columns, never inferred from platform or title. */
public enum ContentCategory {
    ALL("全部"),TECH("科技"),SOCIETY("社会"),LIFE("生活"),SCIENCE("科学"),CULTURE("文化"),OTHER("其他");
    public final String label;
    ContentCategory(String label){this.label=label;}
    public static ContentCategory parse(String value){try{return valueOf(value);}catch(Exception ignored){return ALL;}}
    public static ContentCategory of(HotCandidate candidate){return of(candidate.source,candidate.sourceTag);}
    public static ContentCategory of(Source source,String tag){
        if(source==Source.THEPAPER){
            // These are exact source-owned columns verified against the public
            // hotNews nodeInfo, not title keywords or a platform-wide category.
            if("对齐Lab".equals(tag))return TECH;
            if("有戏".equals(tag))return CULTURE;
            if(Arrays.asList("教育家","澎湃防务","浦江头条","一号专案","大国外交","中国政库","自贸区连线","全球速报").contains(tag))return SOCIETY;
            // Commentary and other broad/unknown columns do not establish topic.
            return OTHER;
        }
        if(source!=Source.V2EX||tag==null||!tag.startsWith("node:"))return OTHER;
        String node=tag.substring(5);
        if(Arrays.asList("programmer","python","java","javascript","android","ios","apple","linux","devops","hardware","nas","database","cloud").contains(node))return TECH;
        if(Arrays.asList("travel","health","pet","parenting","coffee","cooking","fitness").contains(node))return LIFE;
        if(Arrays.asList("science","astronomy","physics","biology").contains(node))return SCIENCE;
        if(Arrays.asList("books","movies","music","photography").contains(node))return CULTURE;
        return OTHER;
    }
}
