package app.quietreader.discovery;

import app.quietreader.Models.Source;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Cheap admission evidence for a future bounded event-verification queue.
 * CANDIDATE means a model may inspect the pair, never permission to merge it.
 * Missing facts are not inferred from similar wording or a high model score. */
public final class EventClaimEvidence {
    public static final String VERSION="claim-evidence-v21-quantity-roles";
    public enum Decision { ALREADY_GROUPED, CANDIDATE, INSUFFICIENT, CONFLICT }
    private static final long DAY=86_400_000L;
    private static final Pattern NUMBER=Pattern.compile("[+-]?[0-9]+(?:\\.[0-9]+)?(?:[%‰‱])?|(?:(?<=连续)|(?<=第))(?:[二三四五六七八九]十[一二三四五六七八九]?|十[一二三四五六七八九]?|[零〇一二两三四五六七八九])(?=年|届|次)");
    private static final Pattern ASCII=Pattern.compile("[a-z][a-z0-9]{2,}");
    private static final Pattern MODEL_CODE=Pattern.compile("(?<![a-z0-9])[a-z][a-z0-9]*[0-9]+(?:\\.[0-9]+)*(?![a-z0-9])");
    private static final Pattern WRAPPERS=Pattern.compile("如何看待|怎么看待|如何评价|如何理解|你怎么看|网友|近日|宣布|正式|消息称|据报道|这家公司|该公司|某公司|某企业|该企业|这款产品|新产品|新款|产品|当地|官方|相关部门");
    private static final Pattern COOPERATION_END=Pattern.compile("^([\\p{IsHan}a-z0-9·]{2,24}?)(?:[：:](?:[^：:，,。！？?!]{1,48}[，,])?)?(?:宣布|正式|已)?终止与([\\p{IsHan}a-z0-9·]{2,20}?)(?:的)?(?:全部)?合作(?=[,，。!！?？;；]|$)");
    private static final Pattern BROADCAST_STOP=Pattern.compile("^([\\p{IsHan}a-z0-9·]{2,24}?)(?:已经|已|宣布|全面|正式){0,2}叫停([^,，。！？?!；;]{2,48}播出)(?=[,，。!！?？;；]|$)");
    private static final Pattern INVOLVED_BROADCAST=Pattern.compile("^涉及(.{2,30}?)的(赛事和广告播出)$");
    private static final Pattern UNSAFE_STOP=Pattern.compile("某|该|这家|多家|多个|相关|传闻|网传|疑似|可能|拟|计划|即将|将要|是否|能否|如果|假如|否认|辟谣|未|没有|不再|不会|不能|赔偿|违约|解读|回应");
    private static final Pattern JUDGMENT=Pattern.compile("^受贿([0-9]+(?:\\.[0-9]+)?(?:万|亿)?余?元)[,，\\s]+([^,，。！？!?;；:：]{2,42}?)(一审|二审|再审|终审)?被判(死缓|死刑|无期徒刑)(?:[。！!])?$");
    private static final Pattern JUDGMENT_ROLE=Pattern.compile("^([\\p{IsHan}]{1,30}(?:委员|书记|厅长|局长|院长|部长|董事长|总经理|主任|行长|校长|队长))([\\p{IsHan}]{2,4})$");
    private static final Pattern JUDGMENT_PERSON=Pattern.compile("[\\p{IsHan}]{2,4}");
    private static final Pattern UNSAFE_JUDGMENT=Pattern.compile("拟|计划|预计|或将|即将|可能|疑似|网传|传闻|否认|辟谣|尚未|未被|没有|不会|不予|是否|能否|如何|[?？]");
    private static final Pattern UNSAFE_PERSON=Pattern.compile("某|该|这|人员|男子|女子|官员|干部|职工|员工|领导|负责人|多人|一人|公司|集团|企业|被告");
    private static final Pattern APOLOGY_EXPANSION=Pattern.compile("致以(?:诚挚的?)?歉意");
    private static final Pattern APOLOGY=Pattern.compile("^([\\p{IsHan}a-z0-9·]{2,24}?)(?:[：:]代表([\\p{IsHan}a-z0-9·]{2,24}))?向([\\p{IsHan}a-z0-9·]{2,24}?)(?:致歉|道歉|致以(?:诚挚的?)?歉意)[。！!]?$" );
    private static final Pattern UNSAFE_APOLOGY=Pattern.compile("某|该|这|相关|传闻|网传|疑似|可能|拟|计划|即将|将要|是否|能否|如果|假如|否认|辟谣|未|没有|不再|不会|不能|如何|因|由于|[?？]");
    private static final Pattern GENERIC_APOLOGY_ACTOR=Pattern.compile("官方|公司|企业|品牌|联盟|联赛|俱乐部|组织|机构|平台|我们|本人|有人|一名|网友|媒体");
    private static final Pattern WEALTH_RECORD=Pattern.compile("^([\\p{IsHan}·]{2,24}?)([0-9]+(?:\\.[0-9]+)?(?:万|亿)(?:人民币|美元|港元|元)?)?刷新([\\p{IsHan}]{2,24}财富纪录)[。！!]?$" );
    private static final Pattern UNSAFE_WEALTH_RECORD=Pattern.compile("某|该|这|疑似|网传|传闻|可能|拟|计划|预计|即将|将要|是否|能否|如果|假如|否认|辟谣|未|没有|不再|不会|不能|如何|[?？]");
    private static final Map<String,Pattern> PROTECTED=new LinkedHashMap<>(),ACTIONS=new LinkedHashMap<>(),POLARITY=new LinkedHashMap<>();
    static {
        PROTECTED.put("death",Pattern.compile("死亡|致死|身亡|遇难|丧生"));
        PROTECTED.put("injury",Pattern.compile("受伤|重伤|伤亡"));
        PROTECTED.put("response",Pattern.compile("回应|答复"));
        PROTECTED.put("denial",Pattern.compile("辟谣|否认|澄清"));
        PROTECTED.put("rescue",Pattern.compile("获救|救出|脱险"));
        PROTECTED.put("arrest",Pattern.compile("被捕|逮捕"));
        PROTECTED.put("sentence",Pattern.compile("判刑|获刑|判处"));
        PROTECTED.put("accompany",Pattern.compile("陪同"));
        PROTECTED.put("conditional",Pattern.compile("如果|假如|有望|或将|若(?!干)"));
        PROTECTED.put("unconfirmed",Pattern.compile("疑似|网传|传闻|据传|可能|或为|涉嫌"));
        PROTECTED.put("negated",Pattern.compile("尚未|并未|没有|并非|不是|未曾|不实|谣言|不成立"));
        PROTECTED.put("refused-recognition",Pattern.compile("拒绝承认|拒不承认|不承认"));
        for(String role:new String[]{"总导演","董事长","主教练","总经理","负责人"})PROTECTED.put("role:"+role,Pattern.compile(role));
        for(String category:new String[]{"男单","女单","男双","女双","混双","男足","女足","男子","女子"})PROTECTED.put(category,Pattern.compile(category));
        ACTIONS.put("discovery",Pattern.compile("发现|出土"));
        ACTIONS.put("visit",Pattern.compile("访问|到访"));
        ACTIONS.put("speech",Pattern.compile("发言|致辞"));
        ACTIONS.put("controversy",Pattern.compile("争议|热议|吐槽"));
        ACTIONS.put("loss",Pattern.compile("不敌|落败|负于"));
        ACTIONS.put("win",Pattern.compile("战胜|击败|赢下|取胜|获胜"));
        ACTIONS.put("release",Pattern.compile("发布"));
        ACTIONS.put("sale",Pattern.compile("开售|上市"));
        ACTIONS.put("record",Pattern.compile("破.{0,4}纪录"));
        ACTIONS.put("collision",Pattern.compile("相撞"));
        ACTIONS.put("finance",Pattern.compile("融资"));
        ACTIONS.put("apology",Pattern.compile("致歉|道歉|"+APOLOGY_EXPANSION.pattern()));
        ACTIONS.put("election-win",Pattern.compile("连任|当选|胜选"));
        ACTIONS.put("election-loss",Pattern.compile("败选"));
        ACTIONS.put("appointment",Pattern.compile("任命|出任|获任|就任|(?:总导演|主教练|董事长|总经理|负责人)(?:人选)?公布"));
        ACTIONS.put("cooperation-end",Pattern.compile("终止.{0,30}合作"));
        ACTIONS.put("broadcast-stop",Pattern.compile("叫停.{0,48}播出"));
        // Scope explicit qualifiers to their following predicate. A global
        // 'negative somewhere' flag cannot distinguish 'not released, on sale'
        // from 'released, not on sale'. This is admission protection, not NLP
        // inference: unrecognised paraphrases still require model verification.
        Map<String,String> prefixes=new LinkedHashMap<>();
        prefixes.put("negative","(?:未(?:能|曾|再|获)?|没(?:能|有)?|不(?:能|曾|再|会)?)");
        prefixes.put("planned","(?:计划|拟|预计|准备|即将|将要)");
        prefixes.put("questioned","(?:能否|是否|会否|会不会|能不能|可否)");
        // In an apology the explicit reason can separate a qualifier from its
        // predicate: 是否/未/计划 + 就某件事 + 道歉. Keep clause boundaries;
        // a later question about accepting an already completed apology is not
        // a qualifier on that apology. Do not strip qualifiers for model input.
        String apologyReason="(?:\\s*(?:就|为|因|针对)[^,，。！？?!；;:：]{1,32})?";
        for(Map.Entry<String,String> prefix:prefixes.entrySet())for(Map.Entry<String,Pattern> action:ACTIONS.entrySet()){
            String reason=action.getKey().equals("apology")?apologyReason:"";
            POLARITY.put(prefix.getKey()+":"+action.getKey(),Pattern.compile(prefix.getValue()+"(?:\\s*(?:成功|已经|能够|再次|继续|已|会|能)){0,2}"+reason+"\\s*(?:"+action.getValue().pattern()+")"));
        }
        // An explicit different relative year must not be supplied by a copied
        // current-event excerpt. Preserve which year, not just a generic past flag.
        for(String year:List.of("去年","前年"))POLARITY.put("year:"+year+":apology",Pattern.compile(year+"(?:\\s*(?:已经|曾经|曾|已)){0,2}"+apologyReason+"\\s*(?:"+ACTIONS.get("apology").pattern()+")"));
        // An unspecified former apology is not evidence of this current event.
        // Already-completed (已经/已) alone does not assert a different occasion.
        POLARITY.put("former:apology",Pattern.compile("曾(?:经)?(?:\\s*(?:已经|已))?"+apologyReason+"\\s*(?:"+ACTIONS.get("apology").pattern()+")"));
    }
    private EventClaimEvidence(){}
    private static String text(String value){return Normalizer.normalize(value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);}
    private static Set<String> markers(String text,Map<String,Pattern> patterns){Set<String> found=new HashSet<>();for(Map.Entry<String,Pattern> entry:patterns.entrySet())if(entry.getValue().matcher(text).find())found.add(entry.getKey());return found;}
    private static List<String> numbers(String text){
        List<String> result=new ArrayList<>();Matcher matcher=NUMBER.matcher(text);
        while(matcher.find()){
            String number=matcher.group();int first=smallChineseDigit(number.charAt(0));
            if(first>=0||number.charAt(0)=='十'){
                // Exact standard ordinal/streak counts 0..99 before 年/届/次,
                // immediately after 第/连续 only. Do not guess larger,
                // approximate or colloquial spans by taking a suffix.
                int ten=number.indexOf('十'),value=ten<0?first:(ten==0?1:first)*10;
                if(ten>=0&&ten+1<number.length())value+=smallChineseDigit(number.charAt(ten+1));
                number=Integer.toString(value);
            }
            result.add(number);
        }
        return result;
    }
    private static int smallChineseDigit(char value){if(value=='〇')return 0;if(value=='两')return 2;return "零一二三四五六七八九".indexOf(value);}
    private static List<String> countUnits(String text){
        List<String> result=new ArrayList<>();Matcher matcher=NUMBER.matcher(text);
        while(matcher.find()){int end=matcher.end();while(end<text.length()&&Character.isWhitespace(text.charAt(end)))end++;result.add(end<text.length()&&"年届次".indexOf(text.charAt(end))>=0?text.substring(end,end+1):"");}
        return result;
    }
    private static boolean chineseCount(String text){Matcher matcher=NUMBER.matcher(text);while(matcher.find()){char first=matcher.group().charAt(0);if(first=='十'||smallChineseDigit(first)>=0)return true;}return false;}
    private static boolean releaseEnrichment(Prepared a,Prepared b){
        if(!a.actions.contains("release")||!b.actions.contains("release"))return false;
        Prepared shorter=a.numbers.size()<=b.numbers.size()?a:b,longer=shorter==a?b:a;
        if(shorter.numbers.isEmpty()||shorter.numbers.size()==longer.numbers.size())return false;
        // Only a shared explicit model code can explain every number on the
        // shorter headline. Missing rates, scores or quantities are not guessed.
        if(!shorter.numbers.equals(shorter.modelNumbers))return false;
        if(!longer.modelCodes.containsAll(shorter.modelCodes))return false;
        int index=0;for(String number:longer.numbers)if(index<shorter.numbers.size()&&shorter.numbers.get(index).equals(number))index++;
        return index==shorter.numbers.size();
    }
    private static boolean timeEvidence(HotCandidate a,HotCandidate b){
        if(a.observedAt<=0||b.observedAt<=0||Math.abs(a.observedAt-b.observedAt)>DAY)return false;
        if(a.publishedAt!=null&&(a.publishedAt<=0||a.publishedAt>a.observedAt+600_000L||b.observedAt-a.publishedAt>36*3600_000L))return false;
        if(b.publishedAt!=null&&(b.publishedAt<=0||b.publishedAt>b.observedAt+600_000L||a.observedAt-b.publishedAt>36*3600_000L))return false;
        // A fetch time is never substituted for a missing publication date.
        return a.publishedAt==null||b.publishedAt==null||Math.floorDiv(a.publishedAt+8*3600_000L,DAY)==Math.floorDiv(b.publishedAt+8*3600_000L,DAY);
    }
    private static Set<String> anchors(String value){
        for(Pattern pattern:ACTIONS.values())value=pattern.matcher(value).replaceAll(" ");
        for(Pattern pattern:PROTECTED.values())value=pattern.matcher(value).replaceAll(" ");
        value=WRAPPERS.matcher(value).replaceAll(" ");
        Set<String> words=new HashSet<>();Matcher matcher=ASCII.matcher(value);while(matcher.find())words.add(matcher.group());
        // Keep token boundaries: removing an action must not manufacture a new
        // shared name by concatenating unrelated characters on either side.
        int[] points=value.codePoints().toArray();for(int n=0;n+2<points.length;n++)if(han(points[n])&&han(points[n+1])&&han(points[n+2]))words.add(new String(points,n,3));
        return words;
    }
    private static boolean han(int point){return Character.UnicodeScript.of(point)==Character.UnicodeScript.HAN;}
    /** Extra admission guard only: exact actor/object slots, never guessed brand aliases. */
    private static String stoppedClaim(String title,Set<String> actions){
        if(!actions.contains("cooperation-end")&&!actions.contains("broadcast-stop"))return "";
        String value=title.replaceAll("\\s+","");if(UNSAFE_STOP.matcher(value).find())return "";
        Matcher matcher=(actions.contains("cooperation-end")?COOPERATION_END:BROADCAST_STOP).matcher(value);
        if(!matcher.find())return "";
        String object=matcher.group(2);
        // Normalize this complete grammatical frame only. The actor, named
        // object and both broadcast categories remain literal; no aliases,
        // missing categories or number enrichment are inferred.
        if(actions.contains("broadcast-stop")){
            Matcher involved=INVOLVED_BROADCAST.matcher(object);
            if(involved.matches())object="涉"+involved.group(1)+involved.group(2);
        }
        return (actions.contains("cooperation-end")?"cooperation:":"broadcast:")+matcher.group(1)+"\n"+object;
    }
    /** Literal slots in an asserted corruption judgment, never a guessed case identity. */
    private static final class Judgment {
        final String amount,person,role,stage,outcome;
        Judgment(String amount,String person,String role,String stage,String outcome){this.amount=amount;this.person=person;this.role=role;this.stage=stage==null?"":stage;this.outcome=outcome;}
        static Judgment parse(String title){
            if(!title.startsWith("受贿")||UNSAFE_JUDGMENT.matcher(title).find())return null;
            Matcher match=JUDGMENT.matcher(title);if(!match.matches())return null;
            String person=match.group(2).trim(),role="";Matcher qualified=JUDGMENT_ROLE.matcher(person);
            if(qualified.matches()){role=qualified.group(1);person=qualified.group(2);}
            if(!JUDGMENT_PERSON.matcher(person).matches()||UNSAFE_PERSON.matcher(person).find())return null;
            return new Judgment(match.group(1),person,role,match.group(3),match.group(4));
        }
        boolean compatible(Judgment other){
            return amount.equals(other.amount)&&person.equals(other.person)&&outcome.equals(other.outcome)
                &&(stage.isEmpty()||other.stage.isEmpty()||stage.equals(other.stage))
                &&(role.isEmpty()||other.role.isEmpty()||role.equals(other.role));
        }
    }
    /** Complete asserted apology frame; never infer an actor, audience or cause. */
    private static final class Apology {
        final String actor,represented,recipient;
        Apology(String actor,String represented,String recipient){this.actor=actor;this.represented=represented==null?"":represented;this.recipient=recipient;}
        static Apology parse(String title){
            if(UNSAFE_APOLOGY.matcher(title).find())return null;
            Matcher match=APOLOGY.matcher(title.replaceAll("\\s+",""));
            if(!match.matches()||GENERIC_APOLOGY_ACTOR.matcher(match.group(1)).matches())return null;
            return new Apology(match.group(1),match.group(2),match.group(3));
        }
        boolean compatible(Apology other){return actor.equals(other.actor)&&recipient.equals(other.recipient)
            &&(represented.isEmpty()||other.represented.isEmpty()||represented.equals(other.represented));}
    }
    /** An exact wealth-record assertion. An omitted amount is extra detail,
     * never a guessed value; two supplied amounts/units must agree literally. */
    private static final class WealthRecord {
        final String actor,amount,scope;
        WealthRecord(String actor,String amount,String scope){this.actor=actor;this.amount=amount==null?"":amount;this.scope=scope;}
        static WealthRecord parse(String title){
            String value=title.replaceAll("\\s+","");if(UNSAFE_WEALTH_RECORD.matcher(value).find())return null;
            Matcher match=WEALTH_RECORD.matcher(value);return match.matches()?new WealthRecord(match.group(1),match.group(2),match.group(3)):null;
        }
        boolean compatible(WealthRecord other){return actor.equals(other.actor)&&scope.equals(other.scope)&&(amount.isEmpty()||other.amount.isEmpty()||amount.equals(other.amount));}
    }
    private static boolean wealthAmountEnrichment(Prepared a,Prepared b){return a.wealthRecord!=null&&b.wealthRecord!=null&&a.wealthRecord.compatible(b.wealthRecord)&&(a.wealthRecord.amount.isEmpty()!=b.wealthRecord.amount.isEmpty());}
    /** Exact short obituary assertion. A courtesy title never supplies a name,
     * role, cause or date. This only admits model comparison, not a merge. */
    private static final Pattern PASSING=Pattern.compile("^([\\p{IsHan}]{2,4}?)(?:同志)?逝世[。！!]?$" );
    private static final Pattern UNSAFE_PASSING=Pattern.compile("某|该|这|有人|一名|多人|男子|女子|老人|老人家|网友|同志|先生|女士|干部|官员|领导|教授|医生|老师|演员|父亲|母亲|父母|爷爷|奶奶|外公|外婆|爸爸|妈妈|丈夫|妻子|疑似|网传|传闻|可能|拟|将|否认|辟谣|未|没|不|是否|假如|如果");
    private static String passingPerson(String title){
        Matcher match=PASSING.matcher(title.replaceAll("\\s+",""));
        if(!match.matches()||UNSAFE_PASSING.matcher(match.group(1)).find())return "";
        return match.group(1);
    }
    /** Immutable per-window features avoid repeating regex/hash work per pair. */
    static final class Prepared {
        final HotCandidate candidate;final String key,stoppedClaim,passingPerson;final List<String> numbers,modelNumbers,countUnits;final boolean chineseCount,expandedApology;final Set<String> facts,actions,anchors,modelCodes,polarity;final AppointmentEventEvidence.Claim appointment;final Judgment judgment;final Apology apology;final WealthRecord wealthRecord;final ProductReleaseEvidence productRelease;final InstitutionStatementEvidence statement;final DirectRelationEvidence relation;final RateDecisionEvidence rateDecision;
        final EventQuantityEvidence quantities;
        Prepared(HotCandidate candidate){this(candidate,AppointmentEventEvidence.parse(candidate));}
        Prepared(HotCandidate candidate,AppointmentEventEvidence.Claim appointment){this.candidate=Objects.requireNonNull(candidate);this.appointment=appointment;key=EventGroups.key(candidate);String value=text(candidate.title);numbers=numbers(value);chineseCount=chineseCount(value);countUnits=countUnits(value);facts=markers(value,PROTECTED);actions=markers(value,ACTIONS);polarity=markers(value,POLARITY);anchors=anchors(value);
            quantities=EventQuantityEvidence.parse(value);
            passingPerson=passingPerson(value);if(!passingPerson.isEmpty())actions.add("person-passing");
            rateDecision=RateDecisionEvidence.parse(candidate.title);
            statement=InstitutionStatementEvidence.parse(candidate);
            relation=DirectRelationEvidence.parse(value);
            productRelease=actions.contains("release")?ProductReleaseEvidence.parse(value):null;
            wealthRecord=WealthRecord.parse(value);if(wealthRecord!=null)actions.add("wealth-record");
            judgment=Judgment.parse(value);if(judgment!=null)actions.add("corruption-judgment");
            expandedApology=APOLOGY_EXPANSION.matcher(value).find();apology=actions.contains("apology")?Apology.parse(value):null;
            stoppedClaim=stoppedClaim(value,actions);modelCodes=new HashSet<>();modelNumbers=new ArrayList<>();Matcher codes=MODEL_CODE.matcher(value);while(codes.find()){modelCodes.add(codes.group());modelNumbers.addAll(numbers(codes.group()));}}
    }
    public static Decision evaluate(HotCandidate a,HotCandidate b){
        return evaluate(new Prepared(a),new Prepared(b));
    }
    static Decision evaluate(Prepared left,Prepared right){
        HotCandidate a=left.candidate,b=right.candidate;
        if(left.key.equals(right.key))return Decision.ALREADY_GROUPED;
        if(a.source==Source.V2EX&&b.source==Source.V2EX)return Decision.CONFLICT;
        if(a.video||b.video||!timeEvidence(a,b))return Decision.INSUFFICIENT;
        if(left.quantities.conflicts(right.quantities))return Decision.CONFLICT;
        if(left.rateDecision!=null||right.rateDecision!=null){
            if(left.rateDecision==null||right.rateDecision==null)return Decision.INSUFFICIENT;
            return left.rateDecision.compatible(right.rateDecision)?Decision.CANDIDATE:Decision.CONFLICT;
        }
        if(left.statement!=null||right.statement!=null){
            if(left.statement==null||right.statement==null)return Decision.INSUFFICIENT;
            if(!left.numbers.equals(right.numbers)||!left.statement.sameCore(right.statement))return Decision.CONFLICT;
            return left.statement.supportedPair(right.statement)?Decision.CANDIDATE:Decision.INSUFFICIENT;
        }
        boolean productRelease=left.productRelease!=null&&right.productRelease!=null;
        // A fully asserted release must not borrow missing scope from an
        // unparsed report, planned release or unknown appended clause, even
        // when both titles have the same (or no) numbers.
        if(!productRelease&&(left.productRelease!=null||right.productRelease!=null))return Decision.INSUFFICIENT;
        if(productRelease&&!left.productRelease.compatible(right.productRelease))return Decision.CONFLICT;
        // Once a typed release is recognized, an unknown clause on the other
        // side cannot use the older model-code-only enrichment shortcut.
        if(!left.numbers.equals(right.numbers)&&!productRelease
            &&!(left.productRelease==null&&right.productRelease==null&&releaseEnrichment(left,right))
            &&!wealthAmountEnrichment(left,right))return Decision.CONFLICT;
        if((left.chineseCount||right.chineseCount)&&!left.countUnits.equals(right.countUnits))return Decision.CONFLICT;
        if(!left.facts.equals(right.facts)||!left.polarity.equals(right.polarity))return Decision.CONFLICT;
        if(left.relation!=null||right.relation!=null){
            if(left.relation==null||right.relation==null)return Decision.INSUFFICIENT;
            return left.relation.same(right.relation)?Decision.CANDIDATE:Decision.CONFLICT;
        }
        if(left.wealthRecord!=null||right.wealthRecord!=null){
            if(left.wealthRecord==null||right.wealthRecord==null)return Decision.INSUFFICIENT;
            if(!left.wealthRecord.compatible(right.wealthRecord))return Decision.CONFLICT;
        }
        if(left.judgment!=null||right.judgment!=null){
            if(left.judgment==null||right.judgment==null)return Decision.INSUFFICIENT;
            if(!left.judgment.compatible(right.judgment))return Decision.CONFLICT;
        }
        if(!left.passingPerson.isEmpty()||!right.passingPerson.isEmpty()){
            if(left.passingPerson.isEmpty()||right.passingPerson.isEmpty())return Decision.INSUFFICIENT;
            if(!left.passingPerson.equals(right.passingPerson))return Decision.CONFLICT;
        }
        Set<String> actionsA=left.actions,actionsB=right.actions;
        if(actionsA.isEmpty()||actionsB.isEmpty())return Decision.INSUFFICIENT;
        boolean apologyFrame=left.expandedApology||right.expandedApology;
        if(apologyFrame){
            if(left.apology==null||right.apology==null)return Decision.INSUFFICIENT;
            if(!left.apology.compatible(right.apology))return Decision.CONFLICT;
        }
        if(actionsA.contains("cooperation-end")||actionsB.contains("cooperation-end")||actionsA.contains("broadcast-stop")||actionsB.contains("broadcast-stop")){
            if(left.stoppedClaim.isEmpty()||right.stoppedClaim.isEmpty())return Decision.INSUFFICIENT;
            if(!left.stoppedClaim.equals(right.stoppedClaim))return Decision.CONFLICT;
        }
        if(actionsA.contains("win")&&actionsB.contains("loss")||actionsA.contains("loss")&&actionsB.contains("win"))return Decision.CONFLICT;
        if(actionsA.contains("election-win")&&actionsB.contains("election-loss")||actionsA.contains("election-loss")&&actionsB.contains("election-win"))return Decision.CONFLICT;
        if(Collections.disjoint(actionsA,actionsB)||Collections.disjoint(left.anchors,right.anchors)&&left.stoppedClaim.isEmpty()&&left.judgment==null&&left.passingPerson.isEmpty()&&!apologyFrame)return Decision.INSUFFICIENT;
        return Decision.CANDIDATE;
    }
}

