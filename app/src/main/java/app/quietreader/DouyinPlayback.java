package app.quietreader;

/** Fallback for public videos already supplied by the current source document. */
final class DouyinPlayback {
    private DouyinPlayback() {}
    static final String SCRIPT="""
        function makeDouyinPlayback(){
          const initialPath=location.pathname;
          let reader=null,panel=null,player=null,parsed=0,detail=null,comments=null,retryAt=0,emptySince=0;
          let generation=0;const requests=new Set(),watched=new WeakSet();
          function present(v){return v!==undefined&&v!==null&&v!==false&&v!==0&&v!==''&&v!=='$undefined';}
          function safeUrl(value,poster=false){
            try{const u=new URL(value,location.href),h=u.hostname;
              if(u.protocol!=='https:'||u.username||u.password)return '';
              return (poster?h.endsWith('.douyinpic.com'):(h.endsWith('.douyinvod.com')||h==='www.douyin.com'&&u.pathname==='/aweme/v1/play/'))?u.href:'';
            }catch(e){return '';}
          }
          function publicVideo(d){
            const s=d&&d.status;
            return d&&d.video&&s&&s.privateStatus===0&&!s.isDelete&&!s.isProhibited&&!s.isReviewing&&!s.partSee
              &&!d.isPrivate&&!d.isFriendLimit&&!present(d.pcNeedLogin)&&!present(d.chargeInfo)
              &&!present(d.entertainmentPreviewConfig)&&!present(d.entertainmentVideoPaidWay);
          }
          function find(value,depth=0){
            if(!value||typeof value!=='object'||depth>12)return null;
            if(value.aweme&&value.aweme.statusCode===0&&publicVideo(value.aweme.detail)){comments=value.comment;return value.aweme.detail;}
            for(const v of Object.values(value)){const found=find(v,depth+1);if(found)return found;}
            return null;
          }
          function data(){
            const chunks=window.__pace_f;if(!Array.isArray(chunks))return null;
            if(parsed===chunks.length)return detail;parsed=chunks.length;let size=0;
            for(const part of chunks){
              if(part[0]!==1||typeof part[1]!=='string')continue;
              size+=part[1].length;if(size>3000000)break;
              if(!part[1].includes('"aweme"'))continue;
              for(const line of part[1].split('\\n')){
                const colon=line.indexOf(':');if(colon<1||line[colon+1]!=='[')continue;
                try{const found=find(JSON.parse(line.slice(colon+1)));if(found){detail=found;return detail;}}catch(e){}
              }
            }
            return detail;
          }
          function remove(){
            generation++;retryAt=0;for(const c of requests)c.abort();requests.clear();
            if(player){player.pause();player.removeAttribute('src');player.load();player=null;}
            if(reader)reader.remove();reader=null;panel=null;
            document.documentElement.removeAttribute('data-qr-douyin-fallback');
          }
          function node(tag,text,parent){const e=document.createElement(tag);if(text!==undefined)e.textContent=text;if(parent)parent.append(e);return e;}
          function commentList(host,id,initial,replyId=''){
            const revision=generation,seen=new Set();let cursor=0,more=true,busy=false;
            const list=node('div',undefined,host),button=node('button','加载更多评论',host);button.type='button';
            function append(rows){for(const c of rows){if(!c||typeof c.text!=='string'||!c.cid||seen.has(c.cid))continue;seen.add(c.cid);
              const row=node('article',undefined,list);row.className='qr-douyin-comment';
              node('strong',c.user&&c.user.nickname||'抖音用户',row);node('p',c.text,row);
              const total=Number(c.replyTotal??c.reply_comment_total)||0;
              if(!replyId&&total>0){const replies=node('div',undefined,row),open=node('button','查看回复（'+total+'）',replies);open.type='button';
                open.onclick=()=>{open.remove();commentList(replies,id,null,String(c.cid));};}
            }}
            function accept(result){
              const rows=result.comments;if(!Array.isArray(rows))throw Error('No comments');
              append(rows);const next=Number(result.cursor);more=!!(result.hasMore??result.has_more)&&Number.isFinite(next)&&next>cursor;
              if(Number.isFinite(next))cursor=next;button.hidden=!more;button.textContent=replyId?'加载更多回复':'加载更多评论';
              if(!rows.length&&!seen.size)node('p','暂无评论',list);
            }
            async function load(){
              if(busy||!more||revision!==generation)return;busy=true;button.disabled=true;button.textContent='正在加载…';
              const controller=new AbortController();requests.add(controller);const timer=setTimeout(()=>controller.abort(),15000);
              try{
                const params=new URLSearchParams({device_platform:'webapp',aid:'6383',cursor:String(cursor),count:'20'});
                if(replyId){params.set('item_id',id);params.set('comment_id',replyId);}else params.set('aweme_id',id);
                const response=await fetch('/aweme/v1/web/comment/list/'+(replyId?'reply/':'')+'?'+params,{credentials:'same-origin',signal:controller.signal});
                if(!response.ok)throw Error('HTTP');const result=await response.json();
                if(revision!==generation)return;if(result.status_code!==0)throw Error('Source unavailable');accept(result);
              }catch(e){if(revision===generation){button.hidden=false;button.textContent='加载失败，点击重试';}}
              finally{clearTimeout(timer);requests.delete(controller);if(revision===generation){busy=false;button.disabled=false;}}
            }
            button.onclick=load;
            if(initial&&initial.statusCode===0&&Array.isArray(initial.comments))accept(initial);else load();
          }
          function update(enabled){
            const route=location.pathname;
            if(!enabled||!(location.hostname==='www.douyin.com'||location.hostname==='douyin.com')){if(reader)remove();return;}
            const containers=Array.from(document.querySelectorAll('.video-detail-container')).filter(e=>!reader||!reader.contains(e));
            const nativeVideos=containers.flatMap(e=>Array.from(e.querySelectorAll('video')));
            if(!reader&&nativeVideos.some(v=>v.readyState>=2&&v.videoWidth>0))return;
            if(reader)for(const v of nativeVideos){v.pause();if(!watched.has(v)){watched.add(v);v.addEventListener('play',()=>{if(reader)v.pause();});}}
            if(reader){
              if(route!==initialPath&&route!=='/video/'+detail.awemeId){remove();return;}
              if(!reader.isConnected)document.body.append(reader);
              return;
            }
            if(!containers.length&&!(detail&&route==='/video/'+detail.awemeId))return;
            if(!emptySince){emptySince=Date.now();setTimeout(()=>update(active),1800);return;}
            if(Date.now()-emptySince<1500||Date.now()<retryAt)return;retryAt=Date.now()+1000;
            const d=data();if(!d)return;
            if(route.startsWith('/video/')){if(route!=='/video/'+d.awemeId)return;}
            else if(!route.startsWith('/hot/')||route!==initialPath)return;
            const variants=(Array.isArray(d.video.bitRateList)?d.video.bitRateList:[]).filter(v=>v.isH265===0&&v.format==='mp4'&&Number(v.width)>0&&Number(v.height)>0&&Math.min(v.width,v.height)<=720).sort((a,b)=>Math.min(b.width,b.height)-Math.min(a.width,a.height)||a.bitRate-b.bitRate);
            const addresses=variants.length?variants[0].playAddr:d.video.playAddr;
            const urls=(Array.isArray(addresses)?addresses:[]).map(v=>safeUrl(v&&v.src)).filter(Boolean);if(!urls.length)return;
            reader=node('section');reader.id='qr-douyin-reader';
            const box=node('div',undefined,reader);box.className='video-detail-container';
            panel=node('div',undefined,box);panel.id='qr-douyin-player';
            const poster=safeUrl(d.video.cover,true);if(poster){const image=node('img',undefined,panel);image.src=poster;image.alt='';}
            const button=node('button','▶ 播放视频',panel);button.type='button';
            const message=node('span','',panel);message.setAttribute('role','status');
            button.onclick=()=>{
              if(player){player.pause();player.remove();}
              player=node('video');player.dataset.qrVideo='true';player.controls=true;player.playsInline=true;player.preload='none';
              player.setAttribute('aria-label','抖音视频');if(poster)player.poster=poster;
              let index=0;const current=player;
              current.onplaying=()=>{button.hidden=true;message.textContent='';};
              current.onerror=()=>{if(player!==current)return;if(++index<urls.length){current.src=urls[index];current.play().catch(()=>{button.hidden=false;message.textContent='点击重试播放';});}else{button.hidden=false;button.textContent='重试播放';message.textContent='视频未能加载，请稍后重试或查看原网页';}};
              panel.insertBefore(current,button);current.src=urls[0];message.textContent='正在加载视频…';
              current.play().catch(()=>{button.hidden=false;message.textContent='点击重试播放';});
            };
            node('h1',d.desc||d.itemTitle||'抖音视频',reader);
            node('p',(d.authorInfo&&d.authorInfo.nickname||'')+' · 抖音',reader);
            node('h2','评论',reader);commentList(reader,String(d.awemeId),comments);
            document.body.append(reader);document.documentElement.setAttribute('data-qr-douyin-fallback','true');
          }
          return {update};
        }
        """;
}
