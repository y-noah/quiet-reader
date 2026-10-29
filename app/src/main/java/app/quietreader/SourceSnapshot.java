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
                "function visible(b){var v=getComputedStyle(b).visibility;return b.isConnected&&b.getClientRects().length&&v!=='hidden'&&v!=='collapse';}"+
                // A clicked button may say loading while its response is pending. Only a
                // removed/hidden control or an explicit collapse state confirms completion;
                // callers retain their own acquisition deadlines.
                "for(var i=0;i<buttons.length&&i<20;i++){var b=buttons[i];if(!visible(b))continue;if(!b.__qrExpanded&&/显示全部|展开阅读全文|阅读全文|展开全部/.test(b.textContent)){b.__qrExpanded=true;b.click();}if(b.__qrExpanded&&visible(b)&&!/收起/.test(b.textContent))expanding=true;}":"")+
                "var gates=document.querySelectorAll('.Modal-wrapper .SignFlow,.Modal .SignFlow,.SignContainer-content'),login=false;for(var j=0;j<gates.length;j++){if(gates[j].getClientRects().length){login=true;break;}}"+
                (source==Source.TIEBA?TiebaRenderedReplies.script()+TiebaReplyTimes.script():"var replyTimes=null;")+
                "var html=expanding?null:document.documentElement.outerHTML;return JSON.stringify({url:location.href,replyTimes:replyTimes,expanding:expanding,login:login,html:html!==null&&html.length<4194304?html:null});})()";
    }
}
