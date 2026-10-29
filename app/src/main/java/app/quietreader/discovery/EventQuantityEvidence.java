package app.quietreader.discovery;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.*;

/** Veto only: an equal sequence of numerals cannot exchange explicit outcomes.
 * Missing roles, contextual snippets and unrecognised quantities supply no facts.
 * This does not relax the existing whole-title number/unit/admission checks. */
final class EventQuantityEvidence {
    private static final String NUMBER="(?<![0-9.+\\-])([0-9]{1,12}(?:\\.[0-9]{1,6})?)(万|亿)?";
    private static final String OUTCOME="死亡|遇难|身亡|丧生|受轻伤|受重伤|受伤|轻伤|重伤|获救|被救出|伤亡";
    // Consume complete clauses left-to-right. Independent before/after scans
    // would read the boundary in '10人死亡20人受伤' as a second death count.
    private static final Pattern CLAUSE=Pattern.compile("("+OUTCOME+")"+NUMBER+"(?:人|名)|"+NUMBER+"(?:人|名)("+OUTCOME+")|"+NUMBER+"(死|伤)(?![\\p{IsHan}])");
    private final Map<String,Set<String>> roles=new HashMap<>();
    private EventQuantityEvidence(){}
    static EventQuantityEvidence parse(String normalizedTitle){
        EventQuantityEvidence result=new EventQuantityEvidence();
        // Caller normalizes width/case once per candidate, not once per pair.
        String title=normalizedTitle.replaceAll("\\s+","");
        Matcher clause=CLAUSE.matcher(title);while(clause.find()){
            if(clause.group(1)!=null)result.add(clause.group(1),clause.group(2),clause.group(3));
            else if(clause.group(6)!=null)result.add(clause.group(6),clause.group(4),clause.group(5));
            else result.add(clause.group(9),clause.group(7),clause.group(8));
        }
        return result;
    }
    private void add(String outcome,String number,String scale){
        String role=switch(outcome){case "死亡","遇难","身亡","丧生","死"->"death";case "受轻伤","轻伤"->"mild-injury";case "受重伤","重伤"->"severe-injury";case "受伤","伤"->"injury";case "获救","被救出"->"rescued";default->"casualty";};
        String amount=new BigDecimal(number).stripTrailingZeros().toPlainString()+(scale==null?"":scale);
        roles.computeIfAbsent(role,ignored->new HashSet<>()).add(amount);
    }
    boolean conflicts(EventQuantityEvidence other){
        for(Map.Entry<String,Set<String>> role:roles.entrySet()){
            Set<String> value=other.roles.get(role.getKey());
            if(value!=null&&!role.getValue().equals(value))return true;
        }
        return false;
    }
}
