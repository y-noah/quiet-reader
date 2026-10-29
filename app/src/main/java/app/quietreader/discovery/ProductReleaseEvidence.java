package app.quietreader.discovery;

import java.util.*;
import java.util.regex.*;

/** Literal slots for a narrow asserted product release. Unknown clauses are
 * rejected; omitted specifications never acquire an inferred value. */
final class ProductReleaseEvidence {
    private static final String DECIMAL="[0-9]+(?:\\.[0-9]+)?";
    private static final String ACTOR="(?<actor>[\\p{IsHan}·]{2,20}?)";
    private static final String MODEL="(?<model>[a-z][a-z0-9]*(?:\\s+(?:[a-z][a-z0-9]*|"+DECIMAL+")){0,5})";
    private static final String KIND="(?:\\s*(?<kind>平板|手机|笔记本|耳机|手表))?";
    private static final String INTERACTION="(?:(?<first>首款)?(?<interaction>触控屏|触屏)\\s*)?";
    private static final Pattern PREFIX_PRICE=Pattern.compile("^("+DECIMAL+")\\s*元起\\s*[:：]\\s*");
    private static final Pattern BEFORE=Pattern.compile("^"+ACTOR+"\\s*(?:正式)?发布\\s*"+INTERACTION+MODEL+KIND+"$");
    private static final Pattern AFTER=Pattern.compile("^"+ACTOR+"\\s*"+INTERACTION+MODEL+KIND+"\\s*(?:正式)?发布$");
    private static final Pattern UNSAFE_ACTOR=Pattern.compile("某|该|相关|拟|计划|预计|将|有望|准备|考虑|打算|欲|可能|疑似|网传|传闻|据称|未|没有|否认|辟谣|是否|能否");
    private static final Pattern GENERIC_ACTOR=Pattern.compile("公司|企业|厂商|品牌|官方|一家|多家");
    private static final Pattern SCREEN=Pattern.compile("^("+DECIMAL+")\\s*(?:英寸|寸)\\s*("+DECIMAL+")\\s*hz\\s*(oled|lcd|mini-led)\\s*(?:屏幕|屏)$");
    private static final Pattern SCREEN_TYPE=Pattern.compile("^(?:首搭|搭载)?\\s*(?:(单层|双层)串联\\s*)?(oled|lcd|mini-led)\\s*(?:屏幕|屏)?$");
    private static final Pattern CHIP=Pattern.compile("^([a-z][a-z0-9]*(?:\\s+(?:pro|max|ultra|plus)){0,2})\\s*芯片$");
    private static final Pattern RATING=Pattern.compile("^支持\\s*(ip[0-9]{2})\\s*防尘防水$");
    private static final Pattern PRICE=Pattern.compile("^(?:起售价\\s*("+DECIMAL+")\\s*元(?:起)?|("+DECIMAL+")\\s*元起)$");
    private static final Pattern GROUPED_PRICE=Pattern.compile("(?<![0-9.])(?<![0-9],)([1-9][0-9]{0,2}(?:,[0-9]{3})+(?:\\.[0-9]+)?)(\\s*元(?:起)?)");
    private static final Pattern MIN_WEIGHT=Pattern.compile("^低至\\s*("+DECIMAL+")\\s*千克(?:重量)?$");
    private static final Set<String> GENERIC_MODEL_WORDS=Set.of("new","product","products","model","device","phone","tablet","laptop","computer","watch","earbuds","headphones","screen","pro","max","ultra","plus","mini","air");
    private static final Pattern COMMENT=Pattern.compile("^还有哪些亮点值得关注[?？]?$");
    final String actor,model;
    private final Map<String,String> facts=new LinkedHashMap<>();
    private ProductReleaseEvidence(String actor,String model){this.actor=actor;this.model=model.replaceAll("\\s+"," ");}
    private boolean fact(String key,String value){String old=facts.putIfAbsent(key,value);return old==null||old.equals(value);}
    private static boolean numbered(String value){for(int n=0;n<value.length();n++){char c=value.charAt(n);if(c>='0'&&c<='9')return true;}return false;}
    private static boolean identifiedModel(String value){
        if(numbered(value))return true;
        for(String word:value.split("\\s+"))if(word.length()>=4&&!GENERIC_MODEL_WORDS.contains(word))return true;
        return false;
    }
    private static String currencyGroups(String value){
        Matcher amounts=GROUPED_PRICE.matcher(value);StringBuffer out=new StringBuffer();
        while(amounts.find())amounts.appendReplacement(out,Matcher.quoteReplacement(amounts.group(1).replace(",","")+amounts.group(2)));
        amounts.appendTail(out);return out.toString();
    }
    static ProductReleaseEvidence parse(String title){
        // EventClaimEvidence has already applied NFKC and lower case once.
        // Only well-formed CNY amounts lose grouping commas, before splitting
        // clauses. Scores, model numbers and arbitrary comma lists are untouched.
        String value=currencyGroups(EventQuestionFrame.withoutGenericTail(title).trim()),price=null;Matcher prefix=PREFIX_PRICE.matcher(value);
        if(prefix.find()){price=prefix.group(1);value=value.substring(prefix.end());}
        String[] clauses=value.split("[:：,，、]",-1);Matcher header=BEFORE.matcher(clauses[0].trim());
        if(!header.matches()){header=AFTER.matcher(clauses[0].trim());if(!header.matches())return null;}
        String actor=header.group("actor"),model=header.group("model");
        if(UNSAFE_ACTOR.matcher(actor).find()||GENERIC_ACTOR.matcher(actor).matches()||!identifiedModel(model))return null;
        ProductReleaseEvidence result=new ProductReleaseEvidence(actor,model);
        if(price!=null)result.fact("base-price-cny",price);
        if(header.group("kind")!=null)result.fact("kind",header.group("kind"));
        if(header.group("first")!=null)result.fact("release-order","first");
        if(header.group("interaction")!=null)result.fact("interaction","touchscreen");
        for(int n=1;n<clauses.length;n++){
            String clause=clauses[n].trim();Matcher match=SCREEN.matcher(clause);
            if(match.matches()){
                if(!result.fact("screen-inch",match.group(1))||!result.fact("screen-hz",match.group(2))||!result.fact("screen-type",match.group(3)))return null;
                continue;
            }
            match=SCREEN_TYPE.matcher(clause);if(match.matches()){
                if(!result.fact("screen-type",match.group(2))||match.group(1)!=null&&!result.fact("screen-stack",match.group(1)))return null;
                continue;
            }
            match=CHIP.matcher(clause);if(match.matches()&&numbered(match.group(1))){if(!result.fact("chip",match.group(1).replaceAll("\\s+"," ")))return null;continue;}
            match=RATING.matcher(clause);if(match.matches()){if(!result.fact("ingress",match.group(1)))return null;continue;}
            match=PRICE.matcher(clause);if(match.matches()){if(!result.fact("base-price-cny",match.group(1)!=null?match.group(1):match.group(2)))return null;continue;}
            match=MIN_WEIGHT.matcher(clause);if(match.matches()){if(!result.fact("min-weight-kg",match.group(1)))return null;continue;}
            if(COMMENT.matcher(clause).matches())continue;
            return null;
        }
        return result;
    }
    boolean compatible(ProductReleaseEvidence other){
        if(!actor.equals(other.actor)||!model.equals(other.model))return false;
        for(Map.Entry<String,String> fact:facts.entrySet()){
            String supplied=other.facts.get(fact.getKey());if(supplied!=null&&!supplied.equals(fact.getValue()))return false;
        }
        return true;
    }
}
