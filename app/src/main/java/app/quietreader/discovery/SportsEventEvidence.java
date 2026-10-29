package app.quietreader.discovery;

import app.quietreader.Models.Source;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Explicit Swiss-stage match evidence from source titles/excerpts, not similarity.
 * This initial parser deliberately recognizes only an explicit S-number season
 * and Swiss stage, ASCII team codes, and an actual result in the headline.
 * Unknown rounds/scores can join only one co-observed unambiguous full family. */
final class SportsEventEvidence {
    static final String VERSION="swiss-match-evidence-v1";
    private static final long DAY=86_400_000L,ZONE=8*3600_000L;
    private static final String TEAM="([a-z][a-z0-9]{1,11})";
    private static final Pattern COMPETITION=Pattern.compile("(?<![a-z0-9])s([0-9]{1,2})\\s*(?:全球总决赛\\s*)?瑞士轮");
    private static final Pattern ROUND=Pattern.compile("瑞士轮\\s*第([一二三四五六七八九十0-9]{1,3})轮");
    private static final Pattern SCORE=Pattern.compile("(?<![a-z0-9])"+TEAM+"\\s+([0-9]{1,2})\\s*[-:比]\\s*([0-9]{1,2})\\s*"+TEAM+"(?![a-z0-9])");
    private static final Pattern RESULT=Pattern.compile("(?<![a-z0-9])"+TEAM+"\\s*(战胜|击败|拿下|力克|速通|不敌|负于|斩|逮捕)\\s*"+TEAM+"(?![a-z0-9])");
    private static final Pattern NOT_RESULT=Pattern.compile("回应|答复|采访|表示|谈及|谈到|预测|前瞻|展望|有望|或将|即将|将会|如果|假如|若(?!干)|疑似|网传|传闻|否认|辟谣|尚未|并未|没有|未能|回顾|去年|往年|历史上|赛前|复盘|警方|被捕|拘留|涉嫌|假赛|调查|男单|女单|男双|女双|混双|男子|女子|第[一二三四五六七八九十0-9]+[局场]|首局|小局");
    private SportsEventEvidence(){}
    private static String text(String value){return Normalizer.normalize(value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);}
    private static long day(long time){return Math.floorDiv(time+ZONE,DAY);}
    private static final class Outcome {
        final String winner,loser,score;
        Outcome(String winner,String loser,String score){this.winner=winner;this.loser=loser;this.score=score;}
        boolean agrees(Outcome other){return winner.equals(other.winner)&&loser.equals(other.loser)&&(score==null||other.score==null||score.equals(other.score));}
    }
    private static final Outcome INVALID=new Outcome("!","!",null);
    private static Outcome outcome(String text){
        Outcome found=null;Matcher scores=SCORE.matcher(text);
        while(scores.find()){
            int left=Integer.parseInt(scores.group(2)),right=Integer.parseInt(scores.group(3));
            if(left==right||left>5||right>5||scores.group(1).equals(scores.group(4)))return INVALID;
            Outcome next=left>right?new Outcome(scores.group(1),scores.group(4),left+":"+right):new Outcome(scores.group(4),scores.group(1),right+":"+left);
            if(found!=null&&!found.agrees(next))return INVALID;found=next;
        }
        Matcher results=RESULT.matcher(text);
        while(results.find()){
            String first=results.group(1),second=results.group(3);if(first.equals(second))return INVALID;
            boolean lost=results.group(2).equals("不敌")||results.group(2).equals("负于");
            Outcome next=lost?new Outcome(second,first,null):new Outcome(first,second,null);
            if(found!=null&&!found.agrees(next))return INVALID;if(found==null)found=next;
        }
        return found;
    }
    private static String unique(Pattern pattern,String text){String value=null;Matcher matcher=pattern.matcher(text);while(matcher.find()){String next=matcher.group(1);if(value!=null&&!value.equals(next))return "!";value=next;}return value;}
    private static Integer round(String text){
        String value=unique(ROUND,text);if(value==null)return null;
        if(value.matches("[1-9]"))return Integer.parseInt(value);
        int index="一二三四五六七八九".indexOf(value);return value.length()==1&&index>=0?index+1:-1;
    }
    private static final class Claim {
        final HotCandidate candidate;final String season,winner,loser,score;final Integer round;final long day;
        Claim(HotCandidate c,String season,Outcome outcome,Integer round){candidate=c;this.season=season;winner=outcome.winner;loser=outcome.loser;score=outcome.score;this.round=round;day=day(c.observedAt);}
        String family(){String pair=winner.compareTo(loser)<0?winner+":"+loser:loser+":"+winner;return season+"/swiss/"+pair+"/"+day;}
    }
    private static Claim parse(HotCandidate c){
        if(c.video||c.source==Source.V2EX||c.observedAt<=0)return null;
        if(c.publishedAt!=null&&(c.publishedAt<=0||c.publishedAt>c.observedAt+600_000L||day(c.publishedAt)!=day(c.observedAt)))return null;
        String title=text(c.title),excerpt=text(c.snippet);
        if(NOT_RESULT.matcher(title).find()||NOT_RESULT.matcher(excerpt).find())return null;
        // Never turn a title without an actual match result into one using an
        // excerpt: previews, interviews and reaction articles remain separate.
        Outcome result=outcome(title);if(result==null||result==INVALID)return null;
        String season=unique(COMPETITION,title),extraSeason=unique(COMPETITION,excerpt);
        if("!".equals(season)||"!".equals(extraSeason)||season!=null&&extraSeason!=null&&!season.equals(extraSeason))return null;
        if(season==null)season=extraSeason;if(season==null)return null;
        Integer round=round(title),extraRound=round(excerpt);
        if(round!=null&&round<0||extraRound!=null&&extraRound<0||round!=null&&extraRound!=null&&!round.equals(extraRound))return null;
        if(round==null)round=extraRound;
        Outcome summary=outcome(excerpt);
        // Supplement context, never override an explicit opposing result.
        if(summary==INVALID||summary!=null&&!result.agrees(summary))return null;
        if(title.contains("逮捕")&&(summary==null||excerpt.contains("逮捕")||NOT_RESULT.matcher(excerpt).find()))return null;
        return new Claim(c,season,result,round);
    }
    static Map<String,String> resolve(List<FeedStore.Row> rows){
        Map<String,List<Claim>> families=new HashMap<>();
        for(FeedStore.Row row:rows){Claim claim=parse(row.candidate);if(claim!=null)families.computeIfAbsent(claim.family(),ignored->new ArrayList<>()).add(claim);}
        Map<String,String> resolved=new HashMap<>();
        for(List<Claim> family:families.values()){
            if(family.size()<2)continue;Set<String> winners=new HashSet<>(),scores=new HashSet<>();Set<Integer> rounds=new HashSet<>();boolean publication=false;
            for(Claim claim:family){winners.add(claim.winner);if(claim.score!=null)scores.add(claim.score);if(claim.round!=null)rounds.add(claim.round);publication|=claim.candidate.publishedAt!=null;}
            // A missing round or score never bridges two known alternatives.
            // At least one real source publication is required; fetch time is
            // only the co-observation window, never an invented event date.
            if(winners.size()!=1||scores.size()!=1||rounds.size()!=1||!publication)continue;
            Claim first=family.get(0);String identity="group-"+HotCandidate.hash(VERSION+"\n"+first.family()+"\n"+first.winner+"\n"+rounds.iterator().next()+"\n"+scores.iterator().next());
            for(Claim claim:family)resolved.put(claim.candidate.id,identity);
        }
        return resolved;
    }
}
