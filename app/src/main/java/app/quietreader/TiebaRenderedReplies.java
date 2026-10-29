package app.quietreader;

/** Read only the public reply data bound to an exact rendered component root.
 * No app store, author/account object, text matching, extra request or timer. */
final class TiebaRenderedReplies {
    static final String KIND="tieba-vue2-rendered-v1";
    private TiebaRenderedReplies(){}
    static String script(){return """
        var renderedReplyTimes=null;
        try{
          for(const old of document.querySelectorAll('.pb-lzl-item[data-qr-child-id],.pb-lzl-item[data-qr-parent-id]')){old.removeAttribute('data-qr-child-id');old.removeAttribute('data-qr-parent-id');}
          const path=location.pathname.match(new RegExp('^/p/([1-9][0-9]{0,19})/?$'));
          if(!login&&!document.hidden&&location.protocol==='https:'&&location.hostname==='tieba.baidu.com'&&path){
            const nodes=document.querySelectorAll('.pc-pb-reply-list .pb-lzl-item');
            if(nodes.length>0&&nodes.length<=128){
              const opaque=v=>typeof v==='string'&&/^[1-9][0-9]{0,19}$/.test(v)?v:Number.isSafeInteger(v)&&v>0?String(v):null;
              const found=[],seen=new Set();let valid=true;
              for(const node of nodes){
                const component=node.__vue__,props=component&&component.$props,data=props&&props.commentData;
                if(!component||component.$el!==node||!data||typeof data!=='object')continue;
                const parent=node.closest('.pb-comment-item[data-id]'),pid=opaque(props.pid),id=opaque(data.id),existing=node.getAttribute('data-id');
                if(!parent||pid!==parent.getAttribute('data-id')||!id||existing!==null&&existing!==id)continue;
                if(seen.has(id)){valid=false;break;}seen.add(id);
                found.push({node:node,pid:pid,id:id,time:data.time});
              }
              if(valid){
                const rows=[];
                for(const item of found){
                  item.node.setAttribute('data-qr-child-id',item.id);item.node.setAttribute('data-qr-parent-id',item.pid);
                  if(Number.isSafeInteger(item.time)&&item.time>=946684800&&item.time<=Math.floor(Date.now()/1000))rows.push([item.pid,item.id,item.time]);
                }
                if(rows.length)renderedReplyTimes={url:location.href,kind:'tieba-vue2-rendered-v1',rows:rows};
              }
            }
          }
        }catch(e){}
        """;}
}
