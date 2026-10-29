package app.quietreader.discovery;

import app.quietreader.Models.Source;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Explicit year/organization/role/person slots; no guessed names or model scores. */
final class AppointmentEventEvidence {
    static final String VERSION="explicit-appointment-v1";
    private static final String ROLE="(总导演|主教练|董事长|总经理|负责人)",SLOT="(20[0-9]{2}[\\p{IsHan}a-zA-Z0-9]{2,28}?)";
    private static final Pattern DIRECT=Pattern.compile("^([\\p{IsHan}]{2,4})(?:正式)?(?:出任|获任|就任)"+SLOT+ROLE+"(?=[,，。!！?？;；]|$)");
    private static final Pattern ANNOUNCED=Pattern.compile("^"+SLOT+ROLE+"(?:人选)?公布[,，:：]([\\p{IsHan}]{2,4})(?=连续|再次|出任|担任|执导|获任)");
    private static final Pattern UNCERTAIN=Pattern.compile("否认|辟谣|未能|尚未|并未|没有|未曾|不实|网传|传闻|传言|疑似|可能|或将|计划|拟任|即将|如果|能否|是否|会否|回应|采访|回顾|往年|去年|离职|辞任|辞职|撤任|被免|免职|卸任|解职");
    private static final Pattern UNKNOWN_PERSON=Pattern.compile("某|有人|网友|官方|媒体|记者|宣布|公布|计划|预计|近日|今天|昨日|本人|此次|确认|确定|正式|消息|新任|候任|原任|前任|现任|中国|央视");
    private AppointmentEventEvidence(){}
    static final class Claim {
        final String person,slot,role;
        Claim(String person,String slot,String role){this.person=person;this.slot=slot;this.role=role;}
        String identity(){return slot+"\n"+role+"\n"+person;}
    }
    static Claim parse(HotCandidate c){
        if(c.video||c.source==Source.V2EX||c.observedAt<=0||c.observedAt>Long.MAX_VALUE-8*3600_000L||c.title.length()>160)return null;
        if(!c.title.contains("公布")&&!c.title.contains("出任")&&!c.title.contains("获任")&&!c.title.contains("就任"))return null;
        // Only headline-only metadata or a literal repeated headline may use
        // this shortcut; substantive excerpts keep the existing verifier path.
        if(!c.snippet.isEmpty()&&!HeadlineIdentity.core(c.snippet).equals(HeadlineIdentity.core(c.title)))return null;
        String title=Normalizer.normalize(c.title,Normalizer.Form.NFKC).replaceAll("\\s+","");
        if(UNCERTAIN.matcher(title).find())return null;
        Matcher direct=DIRECT.matcher(title),announced=ANNOUNCED.matcher(title);Claim claim;
        if(direct.find())claim=new Claim(direct.group(1),direct.group(2),direct.group(3));
        else if(announced.find())claim=new Claim(announced.group(3),announced.group(1),announced.group(2));
        else return null;
        return UNKNOWN_PERSON.matcher(claim.person).find()||claim.slot.matches(".*(?:某|该|这|相关|当地|一家).*" )?null:claim;
    }
    /** Caller must also apply the ordinary fact, time, number and polarity gates. */
    static boolean same(EventClaimEvidence.Prepared a,EventClaimEvidence.Prepared b){
        return a.appointment!=null&&b.appointment!=null&&(a.candidate.publishedAt!=null||b.candidate.publishedAt!=null)
            &&Math.floorDiv(a.candidate.observedAt+8*3600_000L,86_400_000L)==Math.floorDiv(b.candidate.observedAt+8*3600_000L,86_400_000L)
            &&a.appointment.identity().equals(b.appointment.identity());
    }
    static Map<String,String> resolve(List<FeedStore.Row> rows){
        Map<String,List<EventClaimEvidence.Prepared>> families=new HashMap<>();
        for(FeedStore.Row row:rows){Claim claim=parse(row.candidate);if(claim!=null)families.computeIfAbsent(claim.identity(),ignored->new ArrayList<>()).add(new EventClaimEvidence.Prepared(row.candidate,claim));}
        Map<String,String> result=new HashMap<>();
        for(Map.Entry<String,List<EventClaimEvidence.Prepared>> family:families.entrySet()){
            List<EventClaimEvidence.Prepared> members=family.getValue();if(members.size()<2)continue;
            Set<Source> sources=EnumSet.noneOf(Source.class);boolean publication=false,valid=true;
            for(EventClaimEvidence.Prepared entry:members){valid&=sources.add(entry.candidate.source);publication|=entry.candidate.publishedAt!=null;}
            if(!valid||!publication)continue;
            for(int i=0;i<members.size()&&valid;i++)for(int j=i+1;j<members.size();j++){
                EventClaimEvidence.Decision decision=EventClaimEvidence.evaluate(members.get(i),members.get(j));
                if(decision!=EventClaimEvidence.Decision.CANDIDATE&&decision!=EventClaimEvidence.Decision.ALREADY_GROUPED){valid=false;break;}
            }
            if(!valid)continue;
            // Co-observed evidence only; captured member IDs retain feedback.
            long day=Math.floorDiv(members.get(0).candidate.observedAt+8*3600_000L,86_400_000L);
            for(EventClaimEvidence.Prepared entry:members)if(Math.floorDiv(entry.candidate.observedAt+8*3600_000L,86_400_000L)!=day){valid=false;break;}
            if(!valid)continue;String identity="group-"+HotCandidate.hash(VERSION+"\n"+family.getKey()+"\n"+day);
            for(EventClaimEvidence.Prepared entry:members)result.put(entry.candidate.id,identity);
        }
        return result;
    }
}
