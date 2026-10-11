package app.quietreader;

import static app.quietreader.Models.*;

/** Expand ordinary source content before sampling; never inspect account storage. */
final class SourceSnapshot {
    static String script(Source source){
        return "(function(){var expanding=false;"+
                (source==Source.ZHIHU?
                // Source buttons can fetch the full text asynchronously. A stable preview is
                // not a full document: wait for the content update before testing stability.
                "var buttons=document.querySelectorAll('.QuestionRichText .QuestionRichText-more,.QuestionHeader-detail .QuestionRichText-more,.AnswerItem button.ContentItem-more');"+
                "for(var i=0;i<buttons.length&&i<20;i++){var b=buttons[i];if(b.getClientRects().length&&/显示全部|展开阅读全文|阅读全文|展开全部/.test(b.textContent)){if(!b.__qrExpanded){b.__qrExpanded=Date.now();b.click();}if(Date.now()-b.__qrExpanded<6000)expanding=true;}}":"")+
                "var gates=document.querySelectorAll('.Modal-wrapper .SignFlow,.Modal .SignFlow,.SignContainer-content'),login=false;for(var j=0;j<gates.length;j++){if(gates[j].getClientRects().length){login=true;break;}}"+
                "var html=expanding?null:document.documentElement.outerHTML;return JSON.stringify({url:location.href,expanding:expanding,login:login,html:html!==null&&html.length<4194304?html:null});})()";
    }
}
