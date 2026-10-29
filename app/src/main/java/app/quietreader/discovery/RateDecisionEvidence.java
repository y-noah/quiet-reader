package app.quietreader.discovery;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Complete current hold-rate decision plus explicitly attributed dissent.
 * Never infers an actual decision from a forecast or uses a dissent as the decision. */
final class RateDecisionEvidence {
    static final String VERSION="hold-rate-decision-v1";
    private static final String DECIMAL="(?:0|[1-9][0-9]{0,2})(?:\\.[0-9]{1,3})?";
    private static final Pattern CORE=Pattern.compile("^(美联储|欧洲央行|英国央行|日本央行|中国人民银行)(?:宣布|决定)?(?:如期)?维持(基准|政策)?利率不变(.*)$");
    private static final Pattern DISSENT=Pattern.compile("^([一二两三四五六七八九]|[1-9][0-9]?)名票委主张(加息|降息)("+DECIMAL+")个基点$");
    private static final Pattern RANGE=Pattern.compile("^目标区间为("+DECIMAL+")%至("+DECIMAL+")%$");
    private final String bank;
    private final Map<String,String> facts;
    private RateDecisionEvidence(String bank,Map<String,String> facts){this.bank=bank;this.facts=Map.copyOf(facts);}
    private static String number(String value){return new BigDecimal(value).stripTrailingZeros().toPlainString();}
    static RateDecisionEvidence parse(String original){
        String title=Normalizer.normalize(original,Normalizer.Form.NFKC).replaceAll("\\s+","").replaceFirst("[。！!]$","");
        Matcher core=CORE.matcher(title);if(!core.matches())return null;
        Map<String,String> facts=new HashMap<>();if(core.group(2)!=null)facts.put("kind",core.group(2));
        String tail=core.group(3);if(tail.startsWith(",")||tail.startsWith(";"))tail=tail.substring(1);
        if(!tail.isEmpty())for(String clause:tail.split("[,;]",-1)){
            Matcher dissent=DISSENT.matcher(clause),range=RANGE.matcher(clause);
            if(dissent.matches()){
                if(facts.containsKey("voters"))return null;
                String voters=dissent.group(1);if(voters.equals("两"))voters="2";else{int digit="零一二三四五六七八九".indexOf(voters);if(digit>0)voters=Integer.toString(digit);}
                String bps=number(dissent.group(3));if(new BigDecimal(bps).signum()<=0)return null;
                facts.put("voters",voters);facts.put("direction",dissent.group(2));facts.put("bps",bps);
            }else if(range.matches()){
                if(facts.containsKey("low"))return null;
                BigDecimal low=new BigDecimal(range.group(1)),high=new BigDecimal(range.group(2));
                if(low.compareTo(high)>=0||high.compareTo(BigDecimal.valueOf(100))>0)return null;
                facts.put("low",number(range.group(1)));facts.put("high",number(range.group(2)));
            }else return null; // Unknown factual or qualifying clauses cannot be erased.
        }
        // A dangling separator is not a complete assertion.
        if(!core.group(3).isEmpty()&&tail.isEmpty())return null;
        return new RateDecisionEvidence(core.group(1),facts);
    }
    boolean compatible(RateDecisionEvidence other){
        if(!bank.equals(other.bank))return false;
        for(Map.Entry<String,String> fact:facts.entrySet())if(other.facts.containsKey(fact.getKey())&&!fact.getValue().equals(other.facts.get(fact.getKey())))return false;
        return true;
    }
    static String modelTitle(HotCandidate candidate){RateDecisionEvidence claim=parse(candidate.title);return claim==null?candidate.title:claim.bank+"维持利率不变";}
}
