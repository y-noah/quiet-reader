package app.quietreader;

import app.quietreader.discovery.HotCandidate;
import org.json.*;
import java.util.*;
import static app.quietreader.Models.*;
import static app.quietreader.ArticleProgressEvidence.*;

/** Conservative marked new-reply evidence; source creation time is mandatory.
 * No raw prose, author, URL, cookie or interest feedback is persisted. */
final class DiscussionProgressEvidence {
    static final String VERSION="marked-new-reply-v1";
    static final String TIEBA_VERSION="marked-tieba-child-v1";
    static final int MAX_SECTIONS=128,MAX_HASHES=4000;
    private DiscussionProgressEvidence(){}
    static boolean supported(Source source){return source==Source.V2EX||source==Source.HUPU||source==Source.TIEBA;}
    static final class Reply {
        final String id;final long at;final Set<Long> body,marked;
        Reply(String id,long at,Set<Long> body,Set<Long> marked){this.id=id;this.at=at;this.body=Collections.unmodifiableSet(new HashSet<>(body));this.marked=Collections.unmodifiableSet(new HashSet<>(marked));}
    }
    static final class State {
        final Set<Long> main;final Map<String,Reply> replies;final String thread;
        State(Set<Long> main,Map<String,Reply> replies){this(main,replies,"");}
        State(Set<Long> main,Map<String,Reply> replies,String thread){this.main=Collections.unmodifiableSet(new HashSet<>(main));this.replies=Collections.unmodifiableMap(new TreeMap<>(replies));this.thread=thread;}
        JSONObject json()throws JSONException{
            JSONArray entries=new JSONArray();for(Reply reply:replies.values())entries.put(new JSONObject().put("id",reply.id).put("at",reply.at).put("body",Snapshot.array(reply.body)).put("marked",Snapshot.array(reply.marked)));
            JSONObject value=new JSONObject().put("version",thread.isEmpty()?VERSION:TIEBA_VERSION).put("main",Snapshot.array(main)).put("replies",entries);if(!thread.isEmpty())value.put("thread",thread);return value;
        }
        static State read(JSONObject value,long modifiedAt,Set<Long> body,Set<Long> marked,long acquiredAt)throws JSONException{
            String version=value.getString("version");if(!VERSION.equals(version)&&!TIEBA_VERSION.equals(version)||TIEBA_VERSION.equals(version)!=value.has("thread"))return null;
            String thread="";if(value.has("thread")){Object raw=value.get("thread");if(!(raw instanceof String)||!((String)raw).matches("[0-9a-f]{64}"))return null;thread=(String)raw;}
            Set<Long> main=Snapshot.set(value.getJSONArray("main"));JSONArray entries=value.getJSONArray("replies");
            if(main==null||main.size()<32||entries.length()>MAX_SECTIONS-1)return null;
            Set<Long> union=new HashSet<>(main),marks=new HashSet<>();Map<String,Reply> replies=new HashMap<>();long latest=0;int hashes=main.size();
            for(int n=0;n<entries.length();n++){
                JSONObject entry=entries.getJSONObject(n);Object stamp=entry.get("at");if(!(stamp instanceof Long||stamp instanceof Integer))return null;
                long at=((Number)stamp).longValue();String id=entry.getString("id");Set<Long> text=Snapshot.set(entry.getJSONArray("body")),updates=Snapshot.set(entry.getJSONArray("marked"));
                if(!id.matches("[0-9a-f]{64}")||replies.containsKey(id)||at!=0&&(at<946684800000L||at>acquiredAt)||text==null||updates==null||!text.containsAll(updates)||at==0&&!updates.isEmpty())return null;
                hashes+=text.size();if(hashes>MAX_HASHES)return null;
                replies.put(id,new Reply(id,at,text,updates));union.addAll(text);marks.addAll(updates);latest=Math.max(latest,at);
            }
            return modifiedAt==latest&&union.equals(body)&&marks.equals(marked)?new State(main,replies,thread):null;
        }
    }
    static final class Part {
        final String id;final long at;final boolean main;final List<String> text;
        Part(String id,long at,boolean main,List<String> text){this.id=id;this.at=at;this.main=main;this.text=Collections.unmodifiableList(text);}
    }
    static final class Input {
        final Source source;final String url,scope;final long acquiredAt;private final List<Part> parts;private final String thread;
        Input(Source source,String url,String scope,long acquiredAt,List<Part> parts){this(source,url,scope,acquiredAt,parts,"");}
        Input(Source source,String url,String scope,long acquiredAt,List<Part> parts,String thread){this.source=source;this.url=url;this.scope=scope;this.acquiredAt=acquiredAt;this.parts=Collections.unmodifiableList(parts);this.thread=thread;}
        Snapshot finish(long now){
            if(acquiredAt>now)return null;
            Set<Long> main=new HashSet<>(),body=new HashSet<>(),marked=new HashSet<>();Map<String,Reply> replies=new HashMap<>();long latest=0;int hashes=0,han=0;
            for(Part part:parts){
                Text text=ArticleProgressEvidence.text(Collections.singletonList(part.text));if(text==null)return null;
                hashes+=text.body.size();han+=text.han;if(hashes>MAX_HASHES)return null;
                body.addAll(text.body);
                if(part.main){main.addAll(text.body);continue;}
                Set<Long> update=part.at>0?text.marked:Collections.emptySet();marked.addAll(update);latest=Math.max(latest,part.at);
                String id=HotCandidate.hash(part.id);if(replies.put(id,new Reply(id,part.at,text.body,update))!=null)return null;
            }
            if(main.size()<32||body.size()<100||han<240)return null;
            return new Snapshot(HotCandidate.hash(source.name()+"\n"+HotCandidate.canonical(url)),scope,acquiredAt,latest,body,marked,new State(main,replies,thread));
        }
    }
    /** UI capture is bounded string/scalar copying; hash/JSON work stays on worker. */
    static Input freeze(Source source,Document doc,String scope,long now){
        if(source==Source.TIEBA)return TiebaDiscussionProgress.freeze(doc,scope,now);
        if(doc==null||scope==null||!scope.matches("[0-9a-f]{64}")||!supported(source)||!UrlPolicy.belongs(source,doc.url)||!DiscussionPages.supports(source,doc.url)||doc.cachedAt<=0||doc.cachedAt>now||doc.loginRequired||doc.sourceUnavailable||doc.filteredVideo||doc.videoTopic||doc.virtualDiscussion||doc.topic!=null||!doc.related.isEmpty()||!(doc.notice.isEmpty()||source==Source.HUPU&&HupuDiscussion.PAGE_NOTICE.equals(doc.notice))||doc.sections.isEmpty()||doc.sections.size()>MAX_SECTIONS)return null;
        if(source==Source.HUPU&&(doc.hupuBodyRevision!=HupuDiscussion.BODY_REVISION||HupuDiscussion.page(doc.url)!=1))return null;
        List<Part> parts=new ArrayList<>();Set<String> ids=new HashSet<>();int chars=0,blocks=0;boolean main=false;
        for(Section section:doc.sections){
            boolean primary=source==Source.V2EX?section.id.equals("v2ex-main"):section.id.equals("hupu-main-"+HupuDiscussion.threadId(doc.url));
            boolean original=primary||source==Source.V2EX&&section.id.matches("v2ex-supplement-[1-9][0-9]*");
            if(!original&&!(source==Source.V2EX?section.id.matches("v2ex-r_[1-9][0-9]{0,19}"):section.id.matches("hupu-reply-[1-9][0-9]{0,31}"))||!ids.add(section.id)||section.answer||section.nestedReply||!section.continuationUrl.isEmpty()||section.blocks.size()>512-blocks)return null;
            blocks+=section.blocks.size();List<String> text=new ArrayList<>();
            for(Block block:section.blocks)if(block.type.equals("text")){if(block.value.length()>MAX_CHARS-chars)return null;chars+=block.value.length();text.add(block.value);}
            long at=0;
            if(!original&&DiscussionReplyEvidence.valid(section.sourceReplyEvidence,doc.url,section,section.sourceReplyAt,now)){
                at=section.sourceReplyAt;if(at>doc.cachedAt)return null;
            }
            main|=primary;parts.add(new Part(section.id,at,original,text));
        }
        return main?new Input(source,doc.url,scope,doc.cachedAt,parts):null;
    }
    static long verified(Snapshot before,Snapshot after,long lastPresentation){
        if(before.discussion==null||after.discussion==null||!before.discussion.thread.equals(after.discussion.thread))return 0;
        int shared=0;for(long hash:before.discussion.main)if(after.discussion.main.contains(hash))shared++;
        if(shared*5L<before.discussion.main.size()*4L)return 0;
        long proof=0;
        for(Reply reply:after.discussion.replies.values()){
            if(reply.at<=Math.max(lastPresentation,before.acquiredAt)||before.discussion.replies.containsKey(reply.id))continue;
            int novel=0,updates=0;for(long hash:reply.body)if(!before.body.contains(hash)){novel++;if(reply.marked.contains(hash))updates++;}
            if(novel>=96&&updates>=64)proof=Math.max(proof,reply.at);
        }
        return proof;
    }
}
