package app.quietreader;

import android.content.Context;
import app.quietreader.discovery.*;
import java.util.*;
import java.util.function.BiConsumer;

final class InterestSetupProbe {
    static void run(Context context,BiConsumer<Boolean,String> check)throws Exception{
        String database="test-setup-"+UUID.randomUUID()+".db";
        HotCandidate first=new HotCandidate(Models.Source.ZHIHU,"https://www.zhihu.com/question/123","合成正样本","","",false,1,null,1),second=new HotCandidate(Models.Source.WEIBO,"https://s.weibo.com/weibo?q=test","合成负样本","","",false,1,null,1);
        try{
            try(FeedStore store=new FeedStore(context,database)){
                check.accept(store.needsInterestSetup(1),"empty profile offers optional interest setup");
                store.finishInterestSetup(Arrays.asList(first,second),Collections.emptyMap(),2);check.accept(!store.needsInterestSetup(3)&&store.activeFeedback(3).length()==0,"skip dismisses setup without treating unseen topics as dislikes");
                Map<String,Boolean> choices=new LinkedHashMap<>();choices.put(first.id,true);choices.put(second.id,false);store.finishInterestSetup(Arrays.asList(first,second),choices,4);
                SemanticOrder.Profile profile=new SemanticOrder.Profile(store.activeFeedback(5));check.accept(profile.samples.size()==2&&profile.samples.stream().anyMatch(s->s.text.equals(first.title)&&s.positive)&&profile.samples.stream().anyMatch(s->s.text.equals(second.title)&&!s.positive),"setup commits only explicit positive and negative sample choices");
                int before=store.activeFeedback(6).length();boolean rejected=false;try{store.finishInterestSetup(Collections.singletonList(first),Collections.singletonMap(second.id,true),7);}catch(IllegalArgumentException expected){rejected=true;}
                check.accept(rejected&&store.activeFeedback(8).length()==before,"unknown setup selection is rejected without partial feedback");
                String id=store.activeFeedback(9).getJSONObject(0).getString("id");store.undo(id,10);check.accept(store.activeFeedback(11).length()==1,"setup feedback uses normal reversible event history");
                store.resetProfile();check.accept(store.needsInterestSetup(System.currentTimeMillis()),"reset preference makes optional setup available again");
                store.finishInterestSetup(Collections.emptyList(),Collections.emptyMap(),System.currentTimeMillis());
            }
            try(FeedStore reopened=new FeedStore(context,database)){check.accept(!reopened.needsInterestSetup(System.currentTimeMillis()),"skip decision survives reopening");}
        }finally{context.deleteDatabase(database);}
    }
}
