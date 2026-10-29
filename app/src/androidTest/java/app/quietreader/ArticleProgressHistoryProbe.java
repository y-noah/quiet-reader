package app.quietreader;

import android.app.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import app.quietreader.discovery.*;
import java.util.*;
import static app.quietreader.Models.*;
import static app.quietreader.ArticleProgressEvidence.*;

/** Actual SQLite lifecycle/rollback/limits. Synthetic body history, no real accounts. */
final class ArticleProgressHistoryProbe {
    private final Instrumentation test;private int checks,failures;private final StringBuilder report=new StringBuilder();
    private static final String SCOPE=HotCandidate.hash("history-scope"),URL="https://www.ithome.com/0/990/101.htm";
    ArticleProgressHistoryProbe(Instrumentation test){this.test=test;}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    private long number(SQLiteDatabase db,String sql){try(Cursor c=db.rawQuery(sql,null)){return c.moveToFirst()?c.getLong(0):0;}}
    private String prose(int seed){Random random=new Random(seed);StringBuilder text=new StringBuilder();for(int n=0;n<300;n++)text.append((char)(0x4e00+random.nextInt(3000)));return text.toString();}
    private Document document(long now,boolean update){Document doc=new Document();doc.url=URL;doc.cachedAt=now-(update?1000:6000);doc.sourceModifiedAt=now-(update?2000:8000);doc.sourceUpdateEvidence=ArticleUpdateEvidence.VERSION;Section section=new Section("body","正文",false);for(int n=0;n<6;n++)section.blocks.add(new Block("text",prose(n)));if(update){section.blocks.add(new Block("text","【更新】"+prose(90)));section.blocks.add(new Block("text",prose(91)));}doc.sections.add(section);return doc;}
    private Snapshot body(long now,boolean update){return capture(Source.ITHOME,document(now,update),SCOPE,now);}
    void run(){String name="test-progress-history-"+UUID.randomUUID()+".db";long now=System.currentTimeMillis(),shown=now-5000;Snapshot old=body(now,false),fresh=body(now,true);long active=0;
        try{
            try(FeedStore store=new FeedStore(test.getTargetContext(),name)){
                HotCandidate candidate=new HotCandidate(Source.ITHOME,URL,"进展存储合成中文话题","","",false,1,null,now);store.upsertSnapshot(Source.ITHOME,store.beginRefresh(Source.ITHOME),List.of(candidate),now);
                active=store.commitRanking(store.version("sources"),store.profile(now).getLong("version"),now,"cold","all",List.of(new FeedStore.Row("shown",candidate,"")));store.activateSnapshot(active);store.recordExposed(active,List.of("shown"),shown);
                store.getWritableDatabase().execSQL("DROP TABLE article_progress");store.getWritableDatabase().setVersion(8);
            }
            try(FeedStore store=new FeedStore(test.getTargetContext(),name)){
                SQLiteDatabase db=store.getWritableDatabase();ArticleProgressHistory history=new ArticleProgressHistory(db);long source=store.version("sources"),profile=store.version("profile");
                check(db.getVersion()==9&&number(db,"SELECT COUNT(*) FROM article_progress")==0,"actual synthetic schema8 upgrades to9 with empty body progress table");
                check(store.activeSnapshot()==active&&store.readSnapshot(active,10,0).size()==1&&store.recentExposure(store.readSnapshot(active,10,0),now).get("shown").shownAt==shown,"upgrade preserves accepted list and actual exposure facts");
                history.observe(old,now);history.observe(fresh,now);check(history.progress(old.identity,SCOPE,shown,now)==0,"fetching old then new bodies without presentation never fabricates a baseline");
                db.delete("article_progress",null,null);history.observe(old,now);history.presented(old.identity,SCOPE,shown,now);history.observe(fresh,now);
                check(history.progress(old.identity,SCOPE,shown,now)==fresh.modifiedAt,"actual presentation plus later substantial marked body yields persisted progress");
                check(history.potential(List.of(old.identity,HotCandidate.hash("absent-article")),now).equals(Set.of(old.identity)),"worker prefilter returns only requested identities with live persisted revision evidence");
                check(history.progress(old.identity,HotCandidate.hash("another-session"),shown,now)==0,"different account/cache scope cannot read another scope's proof");
                check(history.progress(old.identity,SCOPE,now-1000,now)==0,"newer whole-event exposure/read time blocks an older member proof");
                check(!history.observe(old,now)&&number(db,"SELECT latest_at FROM article_progress")==fresh.acquiredAt,"late older body callback cannot rewind newest content or proof");
                history.presented(old.identity,SCOPE,now-500,now);check(history.progress(old.identity,SCOPE,now-500,now)==0&&!history.presented(old.identity,SCOPE,shown,now),"new actual display consumes progress and late display timestamps never rewind it");
                check(history.potential(List.of(old.identity),now).isEmpty(),"consumed revision evidence no longer schedules per-URL account capture");
                long before=number(db,"SELECT presented_at FROM article_progress");db.execSQL("CREATE TRIGGER reject_progress BEFORE INSERT ON article_progress BEGIN SELECT RAISE(ABORT,'controlled progress failure'); END");boolean failed=false;
                try{history.presented(old.identity,SCOPE,now,now);}catch(RuntimeException expected){failed=true;}finally{db.execSQL("DROP TRIGGER reject_progress");}
                check(failed&&number(db,"SELECT COUNT(*) FROM article_progress")==1&&number(db,"SELECT presented_at FROM article_progress")==before,"mid-replace failure restores deleted baseline row and timestamps atomically");
                db.delete("article_progress",null,null);history.observe(old,now);history.observe(fresh,now);history.consumed(old,shown,now);
                check(history.progress(old.identity,SCOPE,shown,now)==fresh.modifiedAt,"qualified read stores the actual older displayed body despite a newer prefetch");
                history.consumed(old,now,now);check(history.progress(old.identity,SCOPE,now,now)==0,"an update already predating the most recent read cannot be promoted repeatedly");
                db.delete("article_progress",null,null);history.presented(old.identity,SCOPE,shown,now);history.observe(old,now);history.observe(fresh,now);
                check(history.progress(old.identity,SCOPE,shown,now)==fresh.modifiedAt,"delayed storage of an acquisition predating real display can safely establish its baseline");
                db.delete("article_progress",null,null);history.presented(old.identity,SCOPE,old.acquiredAt-1,now);history.observe(old,now);history.observe(fresh,now);
                check(history.progress(old.identity,SCOPE,old.acquiredAt-1,now)==0,"a body acquired only after display cannot stand in for an unseen old body");
                boolean rejected=false;try{history.presented(old.identity,SCOPE,now+1,now);}catch(IllegalArgumentException expected){rejected=true;}
                check(rejected,"future exposure timestamps fail before any baseline mutation");
                history.maintain(old.acquiredAt-1);check(number(db,"SELECT COUNT(*) FROM article_progress")==0,"clock rollback drops future derivative proof rather than resurfacing it");
                Snapshot expired=new Snapshot(old.identity,SCOPE,old.acquiredAt-8L*24*60*60_000,old.modifiedAt-8L*24*60*60_000,old.body,old.marked);history.observe(expired,now);check(number(db,"SELECT COUNT(*) FROM article_progress")==0,"seven-day retention prunes expired acquisition evidence without timers");
                for(int n=0;n<260;n++){Snapshot value=new Snapshot(HotCandidate.hash("row-limit-"+n),SCOPE,now-1000+n,now-2000,old.body,Set.of());history.observe(value,now);}
                check(number(db,"SELECT COUNT(*) FROM article_progress")==256,"production default retains at most256 scoped evidence rows");
                db.delete("article_progress",null,null);Set<Long> large=new HashSet<>();for(int n=0;n<MAX_SHINGLES;n++)large.add(0x1000000000000000L+n);
                for(int n=0;n<22;n++){Snapshot value=new Snapshot(HotCandidate.hash("byte-limit-"+n),SCOPE,now-1000+2*n,now-2000,large,Set.of());history.observe(value,now);history.presented(value.identity,SCOPE,value.acquiredAt+1,now);}
                check(number(db,"SELECT SUM(bytes) FROM article_progress")<=ArticleProgressHistory.MAX_BYTES&&number(db,"SELECT COUNT(*) FROM article_progress")<22,"production8MiB logical payload budget evicts real hash payloads under pressure");
                check(number(db,"SELECT SUM(bytes) FROM article_progress")==number(db,"SELECT SUM(length(latest)+length(baseline)) FROM article_progress"),"stored byte accounting matches complete ASCII baseline and latest payloads");
                check(store.version("sources")==source&&store.version("profile")==profile&&store.activeFeedback(now).length()==0,"body history never changes preference/source versions or invents feedback");
                store.clearDerivedCache();check(number(db,"SELECT COUNT(*) FROM article_progress")==0,"explicit derived-cache cleanup removes derivative body signatures");
                history.observe(old,now);history.presented(old.identity,SCOPE,shown,now);history.observe(fresh,now);
            }
            try(FeedStore reopened=new FeedStore(test.getTargetContext(),name)){check(new ArticleProgressHistory(reopened.getWritableDatabase()).progress(old.identity,SCOPE,shown,now)==fresh.modifiedAt,"scoped proof survives database close and reopen");}
            try(FeedStore store=new FeedStore(test.getTargetContext(),name)){
                SQLiteDatabase db=store.getWritableDatabase();db.delete("article_progress",null,null);
                HotCandidate candidate=new HotCandidate(Source.ITHOME,URL,"绑定测试中文标题","","",false,1,null,now);store.upsertSnapshot(Source.ITHOME,store.beginRefresh(Source.ITHOME),List.of(candidate),System.currentTimeMillis());
                Document doc=document(System.currentTimeMillis(),false);check(new ArticleSession(test.getTargetContext(),candidate.readerItem()).stamp(doc),"real session stamps metadata and acquisition scope");
                ArticleProgressBinding binding=ArticleProgressBinding.capture(test.getTargetContext(),candidate,doc);check(binding!=null,"matching real stamped document captures bounded progress input");
                if(binding!=null){
                    binding.observe(store,()->false);check(number(db,"SELECT COUNT(*) FROM article_progress")==0,"cancelled foreground epoch admits no derivative evidence");
                    doc.sections.clear();binding.observe(store,()->true);check(number(db,"SELECT COUNT(*) FROM article_progress")==1,"queued observation uses frozen body despite caller mutation");
                    long presented=number(db,"SELECT presented_at FROM article_progress");check(presented==0&&store.activeFeedback(System.currentTimeMillis()).length()==0,"background acquisition creates neither seen/read facts nor preference feedback");
                    db.delete("article_progress",null,null);android.content.SharedPreferences prefs=test.getTargetContext().getSharedPreferences("article-cache",0);String prior=prefs.getString("namespace","initial");
                    try{prefs.edit().putString("namespace","probe-"+UUID.randomUUID()).commit();binding.observe(store,()->true);binding.consumed(store,System.currentTimeMillis());check(number(db,"SELECT COUNT(*) FROM article_progress")==0,"changed cache/account namespace rejects queued observation and qualified-read baseline");}finally{prefs.edit().putString("namespace",prior).commit();}
                    store.enable(Source.ITHOME,false);binding.observe(store,()->true);check(number(db,"SELECT COUNT(*) FROM article_progress")==0,"disabled source rejects previously captured candidate evidence");
                }
                store.enable(Source.ITHOME,true);
                HotCandidate other=new HotCandidate(Source.ITHOME,"https://www.ithome.com/0/990/102.htm","另一个同质量中文话题","","",false,2,null,now);
                store.upsertSnapshot(Source.ITHOME,store.beginRefresh(Source.ITHOME),List.of(candidate,other),System.currentTimeMillis());
                List<FeedStore.Row> ranked=List.of(new FeedStore.Row("body-event",candidate,"",List.of(candidate),0),new FeedStore.Row("unseen-event",other,"",List.of(other),0));
                long id=store.commitRanking(store.version("sources"),store.profile(now).getLong("version"),now,"personal","all",ranked);store.activateSnapshot(id);
                ArticleProgressHistory history=new ArticleProgressHistory(db);history.observe(old,now);
                List<HotCandidate> accepted=store.recordExposedMembers(id,List.of("body-event","absent-event"),shown);
                check(accepted.size()==1&&accepted.get(0).id.equals(candidate.id),"presentation baseline receives only exact committed snapshot members");
                for(HotCandidate member:accepted)history.presented(member.id,SCOPE,shown,now);
                FeedStore.ProgressLookup lookup=(row,recent,at)->history.progress(row.candidate.id,SCOPE,Math.max(recent.shownAt,recent.readAt),at);
                long cooled=store.refreshSnapshot(id,"all",now,lookup);check(store.readSnapshot(cooled,10,0).get(0).eventId.equals("unseen-event"),"real stored exposure and unchanged body keep the seen event cooled");
                history.observe(fresh,now);long returned=store.refreshSnapshot(cooled,"all",now,lookup);
                check(store.readSnapshot(returned,10,0).get(0).eventId.equals("body-event"),"actual persisted marked body revision flows through validated SQLite refresh and restores same-band relevance");
                store.recordExposed(returned,List.of("body-event"),now);history.presented(candidate.id,SCOPE,now,now);
                long consumed=store.refreshSnapshot(returned,"all",now,lookup);check(store.readSnapshot(consumed,10,0).get(0).eventId.equals("unseen-event"),"a later real presentation consumes progress and prevents repeated resurrection");
            }
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{test.getTargetContext().deleteDatabase(name);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
