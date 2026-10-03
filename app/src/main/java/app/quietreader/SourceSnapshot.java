package app.quietreader;

import static app.quietreader.Models.*;

/** Snapshot rendered content without changing the source page or reading account storage. */
final class SourceSnapshot {
    static String script(Source source){
        if(source!=Source.TIEBA)return "(function(){var html=document.documentElement.outerHTML;return JSON.stringify({url:location.href,html:html.length<4194304?html:null});})()";
        return "(function(){var original=document.documentElement;if(original.outerHTML.length>=4194304)return JSON.stringify({url:location.href,html:null});"
            +"var clone=original.cloneNode(true),live=original.querySelectorAll('.login-guard-mask'),copy=clone.querySelectorAll('.login-guard-mask');"
            +"for(var i=0;i<live.length;i++){var hidden=false;for(var p=live[i];p;p=p.parentElement){var s=getComputedStyle(p);if(p.hidden||s.display==='none'||s.visibility==='hidden'||s.visibility==='collapse'){hidden=true;break;}}"
            +"if(hidden&&copy[i])copy[i].remove();}return JSON.stringify({url:location.href,html:clone.outerHTML});})()";
    }
}
