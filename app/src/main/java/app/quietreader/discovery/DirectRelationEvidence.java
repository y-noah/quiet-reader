package app.quietreader.discovery;

import java.util.regex.*;

/** Literal asserted relations missing from the broad action vocabulary.
 * No named-entity inference, source aliases or equivalence decision. */
final class DirectRelationEvidence {
    private static final Pattern AFFILIATION=Pattern.compile("^([\\p{IsHan}a-z0-9·“”\"「」]{2,48})加盟([\\p{IsHan}a-z0-9·]{1,30}(?:大学|学院|俱乐部|球队|公司|集团|研究院|研究所|实验室))[。！!]?$" );
    private static final Pattern AIRSPACE=Pattern.compile("^([\\p{IsHan}]{2,24})对([\\p{IsHan}]{2,24}?)(?:地区)?航司关闭领空[。！!]?$" );
    private static final Pattern UNSAFE=Pattern.compile("某|该|这|相关|有关|一名|多人|多家|网友|未|没|不|计划|拟|预计|将|或|有望|准备|考虑|打算|欲|可能|疑似|网传|传闻|据传|据称|据说|是否|能否|如果|假如|去年|前年|昨日|昨天|此前|曾经|曾|撤回|改口|否认|辟谣|回应|报道|声称|表示|宣布|质疑|反对|支持|发布|致歉|道歉|[?？,，:：;；]");
    private static final Pattern GENERIC=Pattern.compile("工程师|学者|教授|研究员|球员|选手|大学|公司|集团|俱乐部|球队|航司|航空公司|政府|国家|当局");
    private final String kind,actor,object;
    private DirectRelationEvidence(String kind,String actor,String object){this.kind=kind;this.actor=actor;this.object=object;}
    static DirectRelationEvidence parse(String normalizedTitle){
        String value=normalizedTitle.replaceAll("\\s+","");
        if(UNSAFE.matcher(value).find())return null;
        Matcher match=AFFILIATION.matcher(value);
        if(match.matches()&&value.indexOf("加盟")==value.lastIndexOf("加盟")&&!GENERIC.matcher(match.group(1)).matches())
            return new DirectRelationEvidence("affiliation",match.group(1),match.group(2));
        match=AIRSPACE.matcher(value);
        if(match.matches()&&!GENERIC.matcher(match.group(1)).matches()&&!GENERIC.matcher(match.group(2)).matches())
            // 地区 is optional grammar around the same literal airline region,
            // not a global place-name alias or inferred country relationship.
            return new DirectRelationEvidence("airspace",match.group(1),match.group(2));
        return null;
    }
    boolean same(DirectRelationEvidence other){return kind.equals(other.kind)&&actor.equals(other.actor)&&object.equals(other.object);}
}
