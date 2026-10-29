package app.quietreader;

import android.content.Context;
import app.quietreader.discovery.*;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.json.JSONArray;

final class ReadInterestProbe {
    static void run(Context context,BiConsumer<Boolean,String> check)throws Exception{
        String database="test-read-"+UUID.randomUUID()+".db";
        HotCandidate item=new HotCandidate(Models.Source.ZHIHU,"https://www.zhihu.com/question/567","合成阅读兴趣","测试标题摘要","",false,1,null,1);
        try{
            try(FeedStore store=new FeedStore(context,database)){
                check.accept(store.readLearningEnabled(),"reading weak reference defaults enabled and is explicitly configurable");
                check.accept(store.recordRead(item,100)&&!store.recordRead(item,101),"qualified read records once per content version");
                JSONArray feedback=store.activeFeedback(102);String id=feedback.getJSONObject(0).getString("id");
                check.accept("READ".equals(feedback.getJSONObject(0).getString("action"))&&new SemanticOrder.Profile(feedback).samples.get(0).read,"read event feeds the weaker semantic sample type");
                store.undo(id,103);check.accept(!store.recordRead(item,104)&&store.activeFeedback(105).length()==0,"undo cannot silently revive on reopening the same version");
                HotCandidate changed=new HotCandidate(item.source,item.originalUrl,"更新的合成标题","摘要更新","",false,1,null,1);
                store.recordRead(changed,106);store.feedback("explicit","MORE","sample","explicit",107,null,item.modelText());app.quietreader.discovery.LegacySavedFixture.save(store,item.id,item,108);
                store.readLearningEnabled(false,109);
                check.accept(store.activeFeedback(110).length()==1&&app.quietreader.discovery.LegacySavedFixture.saved(store).size()==1&&!store.recordRead(item,111),"disabling retracts reading samples while preserving explicit interest and saved item");
                store.readLearningEnabled(true,120);
                HotCandidate later=new HotCandidate(item.source,item.originalUrl,"新一版合成标题","摘要","",false,1,null,1);
                check.accept(!store.recordRead(later,119)&&store.recordRead(later,121),"queued evidence before the toggle cutoff cannot reappear after re-enabling");
                check.accept(store.activeFeedback(121+7L*86_400_000).length()==1,"reading interest expires after seven days while explicit preference remains");
                check.accept(store.recordQuickExit(item,130)&&!store.recordQuickExit(item,131),"quick exit records once per content version");
                check.accept(store.activeFeedback(132).toString().contains("QUICK_EXIT"),"quick exit persists as distinct reversible weak evidence");
                store.readLearningEnabled(false,133);
                check.accept(!store.activeFeedback(134).toString().contains("QUICK_EXIT")&&!store.recordQuickExit(changed,135),"disabling learning retracts and rejects quick-exit evidence");
                store.resetProfile();check.accept(!store.recordRead(later,122)&&app.quietreader.discovery.LegacySavedFixture.saved(store).size()==1,"profile reset rejects queued older reads and preserves saved metadata");
                store.readLearningEnabled(false,System.currentTimeMillis());
            }
            try(FeedStore reopened=new FeedStore(context,database)){check.accept(!reopened.readLearningEnabled(),"reading learning off setting persists after reopening database");}
        }finally{context.deleteDatabase(database);}
    }
}
