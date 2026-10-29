package app.quietreader;

import app.quietreader.discovery.HotCandidate;
import org.json.*;
import java.util.*;
import java.util.regex.Pattern;
import static app.quietreader.Models.*;

/** Conservative source-marked body revision eligibility; not a fact/truth classifier.
 * Only local text shingles/opaque reply identities are retained, never raw prose or cookies. */
final class ArticleProgressEvidence {
    static final String VERSION="marked-body-revision-v1";
    static final int MAX_CHARS=24000,MAX_SHINGLES=12000;
    private static final String LABEL="(?:最新[进進]展|更新|[补補]充[报報]道|[后後][续續][报報]道)";
    private static final Pattern MARKER=Pattern.compile("^(?:【"+LABEL+"】|"+LABEL+"[：:]|"+LABEL+"$)");
    private ArticleProgressEvidence(){}
    static final class Snapshot {
        final String identity,scope;final long acquiredAt,modifiedAt;final Set<Long> body,marked;final DiscussionProgressEvidence.State discussion;
        Snapshot(String identity,String scope,long acquiredAt,long modifiedAt,Set<Long> body,Set<Long> marked){this(identity,scope,acquiredAt,modifiedAt,body,marked,null);}
        Snapshot(String identity,String scope,long acquiredAt,long modifiedAt,Set<Long> body,Set<Long> marked,DiscussionProgressEvidence.State discussion){this.identity=identity;this.scope=scope;this.acquiredAt=acquiredAt;this.modifiedAt=modifiedAt;this.body=Collections.unmodifiableSet(new HashSet<>(body));this.marked=Collections.unmodifiableSet(new HashSet<>(marked));this.discussion=discussion;}
        JSONObject json()throws JSONException{JSONObject value=new JSONObject().put("version",discussion==null?VERSION:discussion.thread.isEmpty()?DiscussionProgressEvidence.VERSION:DiscussionProgressEvidence.TIEBA_VERSION).put("identity",identity).put("scope",scope).put("acquiredAt",acquiredAt).put("modifiedAt",modifiedAt).put("body",array(body)).put("marked",array(marked));if(discussion!=null)value.put("discussion",discussion.json());return value;}
        static JSONArray array(Set<Long> values){List<Long> sorted=new ArrayList<>(values);Collections.sort(sorted);JSONArray array=new JSONArray();for(long value:sorted)array.put(Long.toHexString(value));return array;}
        static Snapshot read(JSONObject value,long now){
            try{String version=value.getString("version");boolean isDiscussion=DiscussionProgressEvidence.VERSION.equals(version)||DiscussionProgressEvidence.TIEBA_VERSION.equals(version);if(!VERSION.equals(version)&&!isDiscussion||isDiscussion!=value.has("discussion"))return null;String identity=value.getString("identity"),scope=value.getString("scope");Object acquired=value.get("acquiredAt"),modified=value.get("modifiedAt");if(!(acquired instanceof Long||acquired instanceof Integer)||!(modified instanceof Long||modified instanceof Integer))return null;
                long at=((Number)acquired).longValue(),time=((Number)modified).longValue();Set<Long> body=set(value.getJSONArray("body")),marked=set(value.getJSONArray("marked"));
                if(!identity.matches("[0-9a-f]{64}")||!scope.matches("[0-9a-f]{64}")||at<946684800000L||at<time||at>now||body==null||marked==null||body.size()<100||!body.containsAll(marked))return null;
                DiscussionProgressEvidence.State discussion=null;
                if(value.has("discussion")){JSONObject state=value.getJSONObject("discussion");if(!version.equals(state.getString("version")))return null;discussion=DiscussionProgressEvidence.State.read(state,time,body,marked,at);if(discussion==null)return null;}
                else if(time<946684800000L)return null;
                return new Snapshot(identity,scope,at,time,body,marked,discussion);
            }catch(JSONException|NumberFormatException malformed){return null;}
        }
        static Set<Long> set(JSONArray array)throws JSONException{if(array.length()>MAX_SHINGLES)return null;Set<Long> result=new HashSet<>();for(int n=0;n<array.length();n++){Object value=array.get(n);if(!(value instanceof String)||!((String)value).matches("[0-9a-f]{1,16}"))return null;long hash=Long.parseUnsignedLong((String)value,16);if(!result.add(hash))return null;}return result;}
    }
    /** UI-safe bounded copy of immutable strings/scalars; no hashing, JSON or IO. */
    static final class Input {
        final Source source;final String url,scope;final long acquiredAt,modifiedAt;final List<List<String>> sections;
        private final DiscussionProgressEvidence.Input discussion;
        private Input(Source source,String url,String scope,long acquiredAt,long modifiedAt,List<List<String>> sections){this.source=source;this.url=url;this.scope=scope;this.acquiredAt=acquiredAt;this.modifiedAt=modifiedAt;this.sections=Collections.unmodifiableList(sections);this.discussion=null;}
        private Input(DiscussionProgressEvidence.Input discussion){this.source=discussion.source;this.url=discussion.url;this.scope=discussion.scope;this.acquiredAt=discussion.acquiredAt;this.modifiedAt=0;this.sections=Collections.emptyList();this.discussion=discussion;}
        Snapshot finish(long now){return discussion==null?fingerprint(this,now):discussion.finish(now);}
    }
    static Input freeze(Source source,Document doc,String scope,long now){
        if(DiscussionProgressEvidence.supported(source)){DiscussionProgressEvidence.Input discussion=DiscussionProgressEvidence.freeze(source,doc,scope,now);return discussion==null?null:new Input(discussion);}
        if(doc==null||scope==null||!ArticleUpdateEvidence.supported(source)||!UrlPolicy.belongs(source,doc.url)||!scope.matches("[0-9a-f]{64}")||!ArticleUpdateEvidence.VERSION.equals(doc.sourceUpdateEvidence)||doc.sourceModifiedAt<946684800000L||doc.cachedAt<doc.sourceModifiedAt||doc.cachedAt>now||doc.loginRequired||doc.sourceUnavailable||doc.filteredVideo||doc.videoTopic||doc.virtualDiscussion||doc.discussionPage>0||doc.topic!=null||!doc.nextUrl.isEmpty()||!doc.related.isEmpty()||!doc.notice.isEmpty())return null;
        if(doc.sections.isEmpty()||doc.sections.size()>16)return null;
        List<List<String>> sections=new ArrayList<>();int scanned=0,blocks=0;
        for(Section section:doc.sections){
            if(section.blocks.size()>256-blocks)return null;blocks+=section.blocks.size();
            if(section.answer||section.nestedReply||!section.continuationUrl.isEmpty()||!(section.label.equals("正文")||section.label.startsWith("正文 · ")))return null;
            List<String> text=new ArrayList<>();for(Block block:section.blocks)if(block.type.equals("text")){if(block.value.length()>MAX_CHARS-scanned)return null;scanned+=block.value.length();text.add(block.value);}sections.add(Collections.unmodifiableList(text));
        }
        return new Input(source,doc.url,scope,doc.cachedAt,doc.sourceModifiedAt,sections);
    }
    static Snapshot capture(Source source,Document doc,String scope,long now){Input input=freeze(source,doc,scope,now);return input==null?null:input.finish(now);}
    private static Snapshot fingerprint(Input input,long now){
        if(input.acquiredAt>now)return null;
        Text text=text(input.sections);
        if(text==null||text.han<240||text.body.size()<100)return null;
        return new Snapshot(HotCandidate.hash(input.source.name()+"\n"+HotCandidate.canonical(input.url)),input.scope,input.acquiredAt,input.modifiedAt,text.body,text.marked);
    }
    static final class Text {final Set<Long> body,marked;final int han;Text(Set<Long> body,Set<Long> marked,int han){this.body=body;this.marked=marked;this.han=han;}}
    static Text text(List<List<String>> sections){
        Set<Long> body=new HashSet<>(),marked=new HashSet<>();int han=0;
        for(List<String> section:sections){boolean update=false;
            for(String paragraph:section){String text=paragraph.trim();java.util.regex.Matcher marker=MARKER.matcher(text);if(marker.find()){update=true;text=text.substring(marker.end());}
                StringBuilder normalized=new StringBuilder();int chinese=0;
                for(int offset=0;offset<text.length();){int cp=text.codePointAt(offset);offset+=Character.charCount(cp);if(Character.UnicodeScript.of(cp)==Character.UnicodeScript.HAN){chinese++;han++;}if(Character.isLetterOrDigit(cp))normalized.appendCodePoint(Character.toLowerCase(cp));}
                if(chinese<12)continue;int[] points=normalized.toString().codePoints().toArray();
                // Sample by content hash, never by character position. Inserting
                // one character must not flip every later shingle's eligibility.
                for(int n=0;n+8<=points.length;n++){long hash=0xcbf29ce484222325L;int count=0;for(int j=0;j<8;j++){int cp=points[n+j];if(Character.UnicodeScript.of(cp)==Character.UnicodeScript.HAN)count++;hash=(hash^cp)*0x100000001b3L;}if(count<6||(hash&1L)!=0)continue;body.add(hash);if(update)marked.add(hash);if(body.size()>MAX_SHINGLES)return null;}
            }
        }
        return new Text(body,marked,han);
    }
    /** Source timestamp and substantial new explicitly marked text are both needed.
     * A body acquired before the latest exposure/read cannot undo that cooldown. */
    static long verifiedProgress(Snapshot before,Snapshot after,long lastPresentation,long now){
        if(before==null||after==null||lastPresentation<=0||before.acquiredAt>lastPresentation||!before.identity.equals(after.identity)||!before.scope.equals(after.scope)||before.acquiredAt>=after.acquiredAt||after.acquiredAt>now||after.modifiedAt<=before.modifiedAt||after.modifiedAt<=before.acquiredAt||after.modifiedAt<=lastPresentation||after.modifiedAt>after.acquiredAt)return 0;
        if(before.discussion!=null||after.discussion!=null)return DiscussionProgressEvidence.verified(before,after,lastPresentation);
        return substantialRevision(before,after)?after.modifiedAt:0;
    }
    /** Caller must prove accepted event membership and validate BOTH current scopes.
     * Different URLs alone provide no evidence: retain the original report and add
     * substantial explicitly marked prose after its actual presentation. */
    static long verifiedFollowup(Snapshot before,Snapshot after,long lastPresentation,long now){
        if(before==null||after==null||before.discussion!=null||after.discussion!=null||before.identity.equals(after.identity)||lastPresentation<=0||before.acquiredAt>lastPresentation||before.acquiredAt>=after.acquiredAt||after.acquiredAt>now||after.modifiedAt<=before.modifiedAt||after.modifiedAt<=lastPresentation||after.modifiedAt>after.acquiredAt)return 0;
        return substantialRevision(before,after)?after.modifiedAt:0;
    }
    private static boolean substantialRevision(Snapshot before,Snapshot after){
        int shared=0,novel=0,markedNovel=0;for(long hash:after.body)if(before.body.contains(hash))shared++;else{novel++;if(after.marked.contains(hash))markedNovel++;}
        return shared*5L>=before.body.size()*4L&&shared*2L>=after.body.size()&&novel>=96&&markedNovel>=64;
    }
}
