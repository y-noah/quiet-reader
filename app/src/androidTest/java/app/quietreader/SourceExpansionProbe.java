package app.quietreader;

import android.app.Instrumentation;
import app.quietreader.discovery.*;
import java.util.*;
import java.util.function.BiConsumer;
import static app.quietreader.Models.*;

/** Actual schema4/5/6 upgrades with new enum rows absent, not fresh-db assertions. */
final class SourceExpansionProbe {
    static void run(Instrumentation test,BiConsumer<Boolean,String> check)throws Exception{
        for(int oldVersion:new int[]{4,5,6})upgrade(test,check,oldVersion);
    }
    private static void upgrade(Instrumentation test,BiConsumer<Boolean,String> check,int oldVersion)throws Exception{
        String name="test-source-expansion-"+UUID.randomUUID()+".db";HotCandidate saved=new HotCandidate(Source.ZHIHU,"https://www.zhihu.com/question/990022","升级保留的中文收藏","","",false,1,null,100);
        try{
            try(FeedStore store=new FeedStore(test.getTargetContext(),name)){
                store.enable(Source.WEIBO,false);store.enable(Source.DOUYIN,true);app.quietreader.discovery.LegacySavedFixture.saveWithInterest(store,saved.id,saved,100);store.feedback("keep-more","MORE","sample",CandidateOrder.versionTarget(saved),101,null,saved.modelText(),saved.source);
                if(oldVersion==4)store.getWritableDatabase().delete("sources","id=?",new String[]{Source.ITHOME.name()});
                if(oldVersion<6)store.getWritableDatabase().delete("sources","id=?",new String[]{Source.THEPAPER.name()});
                store.getWritableDatabase().delete("sources","id=?",new String[]{Source.BBC.name()});store.getWritableDatabase().execSQL("DROP TABLE feed_exposure");store.getWritableDatabase().execSQL("DROP TABLE article_progress");store.getWritableDatabase().setVersion(oldVersion);
            }
            try(FeedStore store=new FeedStore(test.getTargetContext(),name)){
                check.accept(store.getWritableDatabase().getVersion()==9&&store.sourceState(Source.ITHOME).getBoolean("enabled")&&store.sourceState(Source.THEPAPER).getBoolean("enabled")&&store.sourceState(Source.BBC).getBoolean("enabled"),"schema"+oldVersion+" upgrade inserts absent text source rows without resetting the database");
                check.accept(!store.sourceState(Source.WEIBO).getBoolean("enabled")&&store.sourceState(Source.DOUYIN).getBoolean("enabled")&&app.quietreader.discovery.LegacySavedFixture.saved(store).size()==1&&store.activeFeedback(102).length()==2,"source expansion preserves old toggles, bookmarks and both saved/explicit feedback");
                store.enable(Source.BBC,false);
                store.enable(Source.ITHOME,false);
                store.enable(Source.THEPAPER,false);
            }
            try(FeedStore store=new FeedStore(test.getTargetContext(),name)){check.accept(!store.sourceState(Source.ITHOME).getBoolean("enabled")&&!store.sourceState(Source.THEPAPER).getBoolean("enabled")&&!store.sourceState(Source.BBC).getBoolean("enabled"),"reopening does not re-enable newly disabled text sources after schema"+oldVersion+" upgrade");}
        }finally{test.getTargetContext().deleteDatabase(name);}
    }
}
