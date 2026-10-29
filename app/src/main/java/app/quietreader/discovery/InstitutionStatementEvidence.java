package app.quietreader.discovery;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** A narrow direct institutional stance, not a general quotation extractor.
 * Exact speaker/stance/object slots plus cross-supported conditional detail
 * only admit model verification. Related excerpts never supply the main claim. */
final class InstitutionStatementEvidence {
    static final String VERSION="direct-institution-stance-v1";
    private static final Pattern HEADER=Pattern.compile("^([\\p{IsHan}a-z0-9·]{2,40}?(?:中心|委员会|政府|大学|协会|研究院|办公室|公司|集团|俱乐部))(?:发言人)?:(.+)$");
    private static final Pattern STANCE=Pattern.compile("^(不认可|认可|不支持|支持|反对|不赞同|赞同)([\\p{IsHan}a-z0-9·]{2,24})的(表态|声明|说法|决定)$");
    private static final Pattern CONDITION=Pattern.compile("^在[^:：,，。;；!?！？]{4,40}之前[^:：,，。;；!?！？]{2,20}$");
    private static final Pattern UNSAFE=Pattern.compile("某|该|相关|网友|网传|传闻|据传|据说|疑似|假冒|冒充|伪造|虚构|断章取义|不实|辟谣|澄清|否认|未提及|没有提及|不包含|不代表|并非|不是|去年|前年|上个月|上周|昨日|昨天|此前|曾经|昔日|撤回|改口|不再|如果|假如|是否|能否|可能|或许|拟|计划|即将|将要|尚未|暂未|[?？]");
    private static final Pattern GENERIC=Pattern.compile("公司|集团|俱乐部|中心|委员会|政府|大学|协会|研究院|办公室|当地政府|有关部门|官方|我们");
    // Exact department alias observed in the paired public THEPAPER/CLS reports.
    // This is NOT a global 央视=总台 rule and does not alias other departments.
    private static final Map<String,String> ACTORS=Map.of("央视体育青少中心","总台体育青少中心");
    final String actor,stance,object,kind,extra,modelTitle;
    private final Set<String> supportingConditions;
    private final String condition;
    private InstitutionStatementEvidence(String actor,String stance,String object,String kind,String extra,String modelTitle,String snippet){
        this.actor=actor;this.stance=stance;this.object=object;this.kind=kind;this.extra=extra;this.modelTitle=modelTitle;
        condition=conditionKey(extra,object);
        Set<String> conditions=new HashSet<>();if(!condition.isEmpty())conditions.add(condition);
        String excerpt=normalized(snippet);
        if(!UNSAFE.matcher(excerpt).find())for(String sentence:excerpt.split("[。!?;]",-1)){
            String whole=sentence.replaceAll("[,，]","");
            // A substring inside a quotation, disclaimer, longer qualification
            // or changed consequence cannot supply the complete assertion.
            if(CONDITION.matcher(whole).matches())conditions.add(conditionKey(whole,object));
        }
        supportingConditions=Collections.unmodifiableSet(conditions);
    }
    private static String normalized(String value){return Normalizer.normalize(value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("\\s+","");}
    private static String conditionKey(String clause,String object){String prefix="在"+object+"的";return clause.startsWith(prefix)?"在"+clause.substring(prefix.length()):clause;}
    static InstitutionStatementEvidence parse(HotCandidate candidate){return parse(candidate.title,candidate.snippet);}
    private static InstitutionStatementEvidence parse(String title,String snippet){
        if(title.length()>512)return null;
        String value=normalized(title);if(UNSAFE.matcher(value).find())return null;
        Matcher header=HEADER.matcher(value);if(!header.matches()||GENERIC.matcher(header.group(1)).matches())return null;
        String actor=ACTORS.getOrDefault(header.group(1),header.group(1));
        String[] clauses=header.group(2).split("[,，]",-1);if(clauses.length>2)return null;
        Matcher claim=STANCE.matcher(clauses[0]);if(!claim.matches()||GENERIC.matcher(claim.group(2)).matches())return null;
        String extra=clauses.length==1?"":clauses[1];
        if(clauses.length==2&&!CONDITION.matcher(extra).matches())return null;
        // Keep every statement clause verbatim in model input. Only the explicit
        // actor prefix/reporting role is canonicalized; no excerpt is appended.
        int colon=title.indexOf('：');if(colon<0)colon=title.indexOf(':');if(colon<0)return null;
        return new InstitutionStatementEvidence(actor,claim.group(1),claim.group(2),claim.group(3),extra,actor+"："+title.substring(colon+1),snippet);
    }
    private boolean supportedBy(InstitutionStatementEvidence other){
        if(extra.isEmpty())return true;
        // Full sentence equality or the other parsed title's full condition.
        // The only optional subject is this exact, already matched object.
        return other.supportingConditions.contains(condition);
    }
    boolean sameCore(InstitutionStatementEvidence other){return actor.equals(other.actor)&&stance.equals(other.stance)&&object.equals(other.object)&&kind.equals(other.kind);}
    boolean supportedPair(InstitutionStatementEvidence other){return supportedBy(other)&&other.supportedBy(this);}
    static String modelTitle(HotCandidate candidate){InstitutionStatementEvidence value=parse(candidate.title,"");return value==null?candidate.title:value.modelTitle;}
}
