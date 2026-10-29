package app.quietreader;

import org.json.*;
import java.util.*;
import static app.quietreader.Models.*;

/** Optional source metadata. Missing times never delay or authorize readable content. */
final class TiebaReplyTimes {
    static final String VERSION="tieba-child-created-v1";
    private TiebaReplyTimes(){}
    static boolean valid(String url,Section section,long at,long now){
        return DiscussionPages.supports(Source.TIEBA,url)&&section.nestedReply&&!section.answer
                &&section.id.matches("tieba-post-[0-9a-f]{24}")&&section.parentSectionId.matches("tieba-post-[0-9a-f]{24}")
                &&at>=946684800000L&&at<=now;
    }
    static void attach(Document doc,JSONObject payload,long now){
        if(payload==null)return;
        String kind=payload.optString("kind");boolean rendered=TiebaRenderedReplies.KIND.equals(kind);
        // A continuation gate cannot hide metadata already bound to visible prose.
        // It still prevents requests and discussion-progress promotion.
        if(!kind.isEmpty()&&!rendered||doc.loginRequired||doc.sourceUnavailable||doc.moreStatus.equals("error")||doc.moreStatus.equals("login")&&!rendered||!DiscussionPages.samePage(Source.TIEBA,doc.sourceUrl(),payload.optString("url")))return;
        try{
            JSONArray rows=payload.getJSONArray("rows");if(rows.length()>128)return;
            Map<String,Section> sections=new HashMap<>();Set<String> duplicates=new HashSet<>();
            for(Section section:doc.sections)if(sections.put(section.id,section)!=null)duplicates.add(section.id);
            Map<String,Long> times=new HashMap<>();Set<String> seen=new HashSet<>();
            for(int n=0;n<rows.length();n++){
                JSONArray row=rows.getJSONArray(n);if(row.length()!=3)return;
                Object parentValue=row.get(0),childValue=row.get(1),time=row.get(2);
                if(!(parentValue instanceof String)||!(childValue instanceof String)||!(time instanceof Long||time instanceof Integer))return;
                String parent=(String)parentValue,child=(String)childValue;if(!parent.matches("[1-9][0-9]{0,19}")||!child.matches("[1-9][0-9]{0,19}")||!seen.add(child))return;
                long seconds=((Number)time).longValue();if(seconds<946684800L||seconds>now/1000)return;
                String parentId=id("post_content_"+parent),childId=id("lzl:"+child);Section section=sections.get(childId),floor=sections.get(parentId);
                if(section==null||floor==null||duplicates.contains(childId)||duplicates.contains(parentId)||!parent.equals(floor.sourcePostId)||floor.nestedReply||!parentId.equals(section.parentSectionId))continue;
                long at=seconds*1000;if(valid(doc.sourceUrl(),section,at,now))times.put(childId,at);
            }
            for(Map.Entry<String,Long> time:times.entrySet()){Section section=sections.get(time.getKey());section.sourceReplyAt=time.getValue();section.sourceReplyEvidence=VERSION;}
        }catch(JSONException invalid){/* Optional malformed metadata cannot affect prose. */}
    }
    private static String id(String key){return "tieba-post-"+app.quietreader.discovery.HotCandidate.hash(key).substring(0,24);}
    /** A second virtual-window sample may complete optional metadata for an existing child.
     * Clone only that section: the previously displayed document remains unchanged. */
    static void mergeTimes(Document result,Document incoming){
        if(!DiscussionPages.samePage(Source.TIEBA,result.sourceUrl(),incoming.sourceUrl())||incoming.loginRequired||incoming.sourceUnavailable||Set.of("login","error").contains(incoming.moreStatus))return;
        Map<String,Section> additions=new HashMap<>();Set<String> duplicates=new HashSet<>();
        for(Section section:incoming.sections)if(additions.put(section.id,section)!=null)duplicates.add(section.id);
        long now=System.currentTimeMillis();
        for(int n=0;n<result.sections.size();n++){
            Section old=result.sections.get(n),fresh=additions.get(old.id);
            if(fresh==null||duplicates.contains(old.id)||old.sourceReplyAt!=0||!old.nestedReply||!old.parentSectionId.equals(fresh.parentSectionId)||!VERSION.equals(fresh.sourceReplyEvidence)||!valid(result.sourceUrl(),fresh,fresh.sourceReplyAt,now))continue;
            Section copy=new Section(old.id,old.label,old.answer);copy.continuationUrl=old.continuationUrl;copy.sourcePostId=old.sourcePostId;copy.sourceChildId=old.sourceChildId;copy.nestedReply=old.nestedReply;copy.parentSectionId=old.parentSectionId;copy.replyToAuthor=old.replyToAuthor;copy.blocks.addAll(old.blocks);copy.sourceReplyAt=fresh.sourceReplyAt;copy.sourceReplyEvidence=VERSION;
            result.sections.set(n,copy);
        }
    }
    /** One bounded same-origin request per source page/5min, only while snapshots are requested.
     * Destroying the source WebView cancels the page; timeout is never a retry timer. */
    static String script(){return """
        var replyTimes=typeof renderedReplyTimes==='undefined'?null:renderedReplyTimes;
        try {
          const path=location.pathname.match(new RegExp('^/p/([1-9][0-9]{0,19})/?$')),params=new URLSearchParams(location.search),pages=params.getAll('pn');
          const page=pages.length===0?'1':pages.length===1&&/^[1-9][0-9]{0,5}$/.test(pages[0])?pages[0]:null;
          const children=document.querySelectorAll('.pb-lzl-item[data-id],.lzl_single_post[data-field]');
          const gate=document.querySelector('.pc-pb-reply-list .login-guard-mask');
          if(!replyTimes&&!login&&!(gate&&gate.getClientRects().length)&&!document.hidden&&location.protocol==='https:'&&location.hostname==='tieba.baidu.com'&&path&&page&&children.length>0&&children.length<=128){
            const key=location.href,now=Date.now();let state=window.__qrReplyTimes;
            if(!state||state.url!==key||now-state.started>=300000){
              state={url:key,started:now,rows:null};window.__qrReplyTimes=state;
              const abort=new AbortController(),timer=setTimeout(()=>abort.abort(),1000),hidden=()=>{if(document.hidden)abort.abort();},leave=()=>abort.abort();
              document.addEventListener('visibilitychange',hidden);window.addEventListener('pagehide',leave);
              (async()=>{try{
                const response=await fetch('/p/totalComment?tid='+path[1]+'&pn='+page,{credentials:'same-origin',redirect:'error',cache:'no-store',signal:abort.signal});
                if(!response.ok||!response.body)throw Error('unavailable');
                const reader=response.body.getReader(),decoder=new TextDecoder('utf-8',{fatal:true});let text='',bytes=0;
                while(true){const part=await reader.read();if(part.done)break;bytes+=part.value.byteLength;if(bytes>524288){await reader.cancel();throw Error('large');}text+=decoder.decode(part.value,{stream:true});}text+=decoder.decode();
                const data=JSON.parse(text);if(data.errno!==0||!data.data||!data.data.comment_list)throw Error('source');
                const opaque=v=>typeof v==='string'&&/^[1-9][0-9]{0,19}$/.test(v)?v:Number.isSafeInteger(v)&&v>0?String(v):null;
                const wanted=new Set();for(const child of children){let id=child.getAttribute('data-id');if(!id)try{id=JSON.parse(child.getAttribute('data-field')).spid;}catch(e){}id=opaque(id);if(id)wanted.add(id);}
                const rows=[],seen=new Set(),floors=Object.entries(data.data.comment_list);let scanned=0;
                if(floors.length>256)throw Error('limit');
                for(const [parent,group] of floors){if(!group||!Array.isArray(group.comment_info))throw Error('shape');
                  for(const row of group.comment_info){if(++scanned>2048)throw Error('limit');const child=opaque(row.comment_id);
                    if(opaque(row.thread_id)!==path[1]||opaque(row.post_id)!==parent||!child||seen.has(child))throw Error('identity');seen.add(child);
                    if(!Number.isSafeInteger(row.now_time)||row.now_time<946684800||row.now_time>Math.floor(Date.now()/1000))throw Error('time');
                    if(wanted.has(child))rows.push([parent,child,row.now_time]);
                  }
                }
                if(window.__qrReplyTimes===state&&location.href===key)state.rows=rows;
              }catch(e){}finally{clearTimeout(timer);document.removeEventListener('visibilitychange',hidden);window.removeEventListener('pagehide',leave);abort.abort();}})();
            }
            if(state.rows)replyTimes={url:state.url,rows:state.rows};
          }
        }catch(e){}
        """;}
}
