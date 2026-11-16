package app.quietreader;

import org.json.JSONObject;

/** Reconciles our escaped reader markup without discarding decoded image nodes. */
final class ReaderDomPatch {
    private ReaderDomPatch(){}
    static String script(String html){
        String main=org.jsoup.Jsoup.parse(html).selectFirst("main").html();
        return "(()=>{const root=document.querySelector('main'),template=document.createElement('template');if(!root)return false;template.innerHTML="+JSONObject.quote(main)+";"+"""
            function key(n){
              if(n.nodeType!==1)return '';
              if(n.id)return n.tagName+'#'+n.id;
              if(n.tagName==='FIGURE'){const i=n.querySelector('img');if(i)return 'figure:'+i.getAttribute('src');}
              if(n.tagName==='IMG')return 'img:'+n.getAttribute('src');
              return '';
            }
            function update(old,fresh){
              if(old.isEqualNode(fresh))return;
              if(old.nodeType!==fresh.nodeType||old.nodeName!==fresh.nodeName){old.replaceWith(fresh.cloneNode(true));return;}
              if(old.nodeType===3){old.nodeValue=fresh.nodeValue;return;}
              if(old.nodeType!==1)return;
              for(const a of Array.from(old.attributes))if(!fresh.hasAttribute(a.name))old.removeAttribute(a.name);
              for(const a of fresh.attributes)if(old.getAttribute(a.name)!==a.value)old.setAttribute(a.name,a.value);
              children(old,fresh);
            }
            function children(old,fresh){
              const retained=new Map();for(const n of old.childNodes){const k=key(n);if(k){if(!retained.has(k))retained.set(k,[]);retained.get(k).push(n);}}
              let at=old.firstChild;
              for(const n of Array.from(fresh.childNodes)){
                const k=key(n),matches=k?retained.get(k):null;let current=k?matches&&matches.shift():at&&!key(at)&&at.nodeType===n.nodeType&&at.nodeName===n.nodeName?at:null;
                if(!current){current=n.cloneNode(true);old.insertBefore(current,at);}
                else{if(current!==at)old.insertBefore(current,at);update(current,n);}
                at=current.nextSibling;
              }
              while(at){const next=at.nextSibling;at.remove();at=next;}
            }
            children(root,template.content);return true;})()
            """;
    }
}
