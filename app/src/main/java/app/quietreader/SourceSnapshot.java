package app.quietreader;

import static app.quietreader.Models.*;

/** Snapshot rendered content without changing the source page or reading account storage. */
final class SourceSnapshot {
    static String script(Source source){
        return "(function(){var html=document.documentElement.outerHTML;return JSON.stringify({url:location.href,html:html.length<4194304?html:null});})()";
    }
}
