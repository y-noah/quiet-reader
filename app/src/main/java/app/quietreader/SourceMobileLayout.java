package app.quietreader;

import org.json.JSONObject;
import static app.quietreader.Models.*;

/** Reflows the source DOM; keeps its event handlers, media and origin storage intact. */
final class SourceMobileLayout {
    static boolean supports(Source source) {
        return source==Source.BAIDU||source==Source.TIEBA||source==Source.BILIBILI||source==Source.DOUYIN;
    }
    /** Re-enter the topic's server-rendered document after login or Activity recreation. */
    static String recoveryUrl(Source source,String url) {
        if(source!=Source.DOUYIN||url==null)return url;
        try {
            java.net.URI uri=new java.net.URI(url);
            if(!"https".equals(uri.getScheme())||uri.getUserInfo()!=null||!("www.douyin.com".equals(uri.getHost())||"douyin.com".equals(uri.getHost()))||!uri.getPath().matches("/video/[0-9]+"))return url;
            String query=uri.getRawQuery();if(query==null)return url;
            for(String part:query.split("&"))if(part.matches("sentenceId=[0-9]{1,20}"))return "https://www.douyin.com/hot/"+part.substring(11);
        }catch(Exception ignored){}
        return url;
    }
    static String script(Source source,boolean enabled) {
        if(!supports(source))return "void 0";
        String css=COMMON+switch(source){case BAIDU->BAIDU;case TIEBA->TIEBA;case BILIBILI->BILIBILI;case DOUYIN->DOUYIN;default->"";};
        return "(function(){const enabled="+enabled+",kind="+JSONObject.quote(source.name())+",css="+JSONObject.quote(css)+";"+DouyinPlayback.SCRIPT+SCRIPT+"})()";
    }
    private static final String SCRIPT="""
        if(window.__qrMobile){window.__qrMobile.set(enabled);return;}
        let active=enabled,viewport=null,previous=null,created=false,pending=0;
        const root=document.documentElement;
        const playback=kind==='DOUYIN'?makeDouyinPlayback():null;
        function allowed(){
          const h=location.hostname,p=location.pathname;
          if(kind==='BAIDU')return h==='www.baidu.com'&&p==='/s';
          if(kind==='TIEBA')return h==='tieba.baidu.com'&&(p.startsWith('/p/')||p==='/f'||p.startsWith('/hottopic/'));
          if(kind==='BILIBILI')return (h==='www.bilibili.com'||h==='m.bilibili.com')&&p.startsWith('/video/');
          return (h==='www.douyin.com'||h==='douyin.com')&&(p.startsWith('/hot/')||p.startsWith('/video/'));
        }
        function recognized(){return document.querySelector(kind==='BAIDU'?'#content_left':kind==='TIEBA'?'.topic-wrapper,.main-content,#j_p_postlist':kind==='BILIBILI'?'#mirror-vdcon':'.video-detail-container');}
        function update(){
          if(pending){clearTimeout(pending);pending=0;}
          if(playback)playback.update(active&&allowed());
          if(!active||!allowed()){
            root.removeAttribute('data-qr-mobile');root.removeAttribute('data-qr-page');
            if(viewport){if(created)viewport.remove();else if(previous===null)viewport.removeAttribute('content');else viewport.setAttribute('content',previous);viewport=null;}
            return;
          }
          // Keep one viewport while an SPA temporarily replaces its content tree.
          if(!recognized()&&!root.hasAttribute('data-qr-mobile'))return;
          if(!document.getElementById('qr-mobile-style')){const style=document.createElement('style');style.id='qr-mobile-style';style.textContent=css;document.head.append(style);}
          if(!viewport){viewport=document.querySelector('meta[name=viewport]');created=!viewport;if(!viewport){viewport=document.createElement('meta');viewport.name='viewport';document.head.append(viewport);}previous=viewport.getAttribute('content');viewport.content='width=device-width, initial-scale=1';}
          if(root.getAttribute('data-qr-mobile')!==kind)root.setAttribute('data-qr-mobile',kind);
          if(kind==='DOUYIN'){const page=location.pathname.startsWith('/hot/')?'hot':'video';if(root.getAttribute('data-qr-page')!==page)root.setAttribute('data-qr-page',page);}
        }
        const observer=new MutationObserver(()=>{if(!pending)pending=setTimeout(update,160);});
        observer.observe(root,{childList:true,subtree:true});
        window.addEventListener('popstate',update);
        window.__qrMobile={set(value){active=value;update();}};
        update();
        """;
    private static final String COMMON="""
        html[data-qr-mobile],html[data-qr-mobile] body{min-width:0!important;width:100%!important;margin:0!important;-webkit-text-size-adjust:100%!important;}
        html[data-qr-mobile] body{box-sizing:border-box!important;}
        html[data-qr-mobile] h1,html[data-qr-mobile] h2,html[data-qr-mobile] h3{white-space:normal!important;overflow-wrap:anywhere!important;}
        """;
    private static final String BAIDU="""
        html[data-qr-mobile=BAIDU] :is(#wrapper,#wrapper_wrapper,#container,#content_left){box-sizing:border-box!important;width:100%!important;min-width:0!important;max-width:100%!important;margin:0!important;padding:0!important;}
        html[data-qr-mobile=BAIDU] :is(#content_right,#s_side_wrapper,#head,#s_top_wrap,#u,#help){display:none!important;}
        html[data-qr-mobile=BAIDU] :is(#s_tab,#s_tab_inner,#page,#rs_new){box-sizing:border-box!important;max-width:100%!important;min-width:0!important;margin:0!important;padding:8px 12px!important;overflow-x:auto!important;}
        html[data-qr-mobile=BAIDU] #content_left>div{box-sizing:border-box!important;width:100%!important;max-width:100%!important;margin:0 0 12px!important;padding:12px!important;}
        html[data-qr-mobile=BAIDU] #content_left :is(.c-container,.c-row,.c-span9,.c-span12,.c-span24,.c-offset){box-sizing:border-box!important;width:auto!important;min-width:0!important;max-width:100%!important;margin-left:0!important;}
        html[data-qr-mobile=BAIDU] #content_left .c-row{display:flex!important;flex-wrap:wrap!important;gap:8px;}
        html[data-qr-mobile=BAIDU] #content_left .cos-row{box-sizing:border-box!important;display:flex!important;flex-wrap:wrap!important;width:100%!important;min-width:0!important;max-width:100%!important;gap:8px!important;margin:0!important;}
        html[data-qr-mobile=BAIDU] #content_left .cos-row>.cos-col{box-sizing:border-box!important;flex:0 0 100%!important;width:100%!important;min-width:0!important;max-width:100%!important;padding:0!important;margin:0!important;}
        html[data-qr-mobile=BAIDU] #content_left .cos-image{max-width:100%!important;}
        html[data-qr-mobile=BAIDU] #content_left :is(p,.c-abstract){font-size:16px!important;line-height:1.65!important;}
        html[data-qr-mobile=BAIDU] #content_left h3{font-size:19px!important;line-height:1.5!important;height:auto!important;}
        html[data-qr-mobile=BAIDU] #content_left img{max-width:100%!important;object-fit:contain;}
        html[data-qr-mobile=BAIDU] #content_left :is(table,[class*=wrapper],[class*=container]){max-width:100%!important;box-sizing:border-box!important;overflow-wrap:anywhere;}
        html[data-qr-mobile=BAIDU] #page a{display:inline-flex!important;min-height:44px!important;align-items:center;}
        """;
    private static final String TIEBA="""
        html[data-qr-mobile=TIEBA] :is(#app,.app,.content-container,.main-content,.center-content,.topic-bg,.topic-wrapper,.topic-body,.topic-body>.main,#related-feed,#related-thread-list,.pb_content,.p_postlist,#j_p_postlist){box-sizing:border-box!important;width:100%!important;min-width:0!important;max-width:100%!important;margin:0!important;float:none!important;padding:0!important;}
        html[data-qr-mobile=TIEBA] :is(.left-content,.right-content,.topic-body>.sidebar,.topic-body>.aside,.topic-body>.right,.topic-body>.right-aside,#head,#com_userbar,.head_main,.pb_footer,.right_section){display:none!important;}
        html[data-qr-mobile=TIEBA] :is(.content-container,.main-content){display:block!important;}
        html[data-qr-mobile=TIEBA] :is(.topic-header-info,.thread-container,.pb-content-wrap){box-sizing:border-box!important;width:100%!important;min-width:0!important;padding:12px!important;}
        html[data-qr-mobile=TIEBA] .topic-header-img{height:100px!important;background-size:cover!important;}
        html[data-qr-mobile=TIEBA] :is(.topic-title-wrapper,.topic-title){width:auto!important;height:auto!important;font-size:22px!important;line-height:1.5!important;}
        html[data-qr-mobile=TIEBA] .thread-list>li{display:flex!important;flex-wrap:wrap!important;box-sizing:border-box!important;padding:12px!important;width:100%!important;}
        html[data-qr-mobile=TIEBA] .thread-list :is(.left,.center,.right){float:none!important;position:static!important;width:100%!important;min-width:0!important;margin:0!important;box-sizing:border-box!important;}
        html[data-qr-mobile=TIEBA] .thread-list .left{width:auto!important;}
        html[data-qr-mobile=TIEBA] .thread-list .title{display:block!important;font-size:18px!important;line-height:1.5!important;white-space:normal!important;overflow-wrap:anywhere!important;}
        html[data-qr-mobile=TIEBA] :is(.thread-list .content,.pb-content-item,.d_post_content){font-size:17px!important;line-height:1.7!important;overflow-wrap:anywhere!important;max-width:100%!important;}
        html[data-qr-mobile=TIEBA] :is(.photo-wrapper,.big-photos){width:100%!important;max-width:100%!important;overflow-x:auto!important;}
        html[data-qr-mobile=TIEBA] :is(.pb-content-wrap,.pb-content-item,.d_post_content,.photo-wrapper) img{max-width:100%!important;height:auto!important;}
        html[data-qr-mobile=TIEBA] :is(.l_post,.d_post_content_main){box-sizing:border-box!important;min-width:0!important;max-width:100%!important;width:auto!important;margin-left:0!important;}
        html[data-qr-mobile=TIEBA] .d_author{float:none!important;width:auto!important;padding:8px!important;}
        html[data-qr-mobile=TIEBA] .d_post_content_main{padding:12px!important;}

        html[data-qr-mobile=TIEBA] :is(.topic-desc,.topic-title-wrapper,.user-action-box,#post-chandelier,.topic-header-info>*,.pb-page-wrapper,.pc-pb-box,.pc-pb-box>.container,.image-text,.pc-pb-reply-list,.pb-comment-list){box-sizing:border-box!important;width:100%!important;min-width:0!important;max-width:100%!important;margin:0!important;float:none!important;}
        html[data-qr-mobile=TIEBA] :is(.topic-title-wrapper,.pc-pb-box>.container){display:block!important;}
        html[data-qr-mobile=TIEBA] .topic-desc{white-space:normal!important;overflow-wrap:anywhere!important;height:auto!important;font-size:16px!important;line-height:1.6!important;}
        html[data-qr-mobile=TIEBA] :is(.topic-title,.realtime-num,.topic-talk-number){float:none!important;display:inline!important;}
        html[data-qr-mobile=TIEBA] .topic-title{display:block!important;margin-bottom:8px!important;}
        html[data-qr-mobile=TIEBA] :is(.realtime-num,.topic-talk-number){font-size:15px!important;}
        html[data-qr-mobile=TIEBA] .pb-title-wrap{box-sizing:border-box!important;width:100%!important;min-width:0!important;max-width:100%!important;height:auto!important;padding:12px!important;}
        html[data-qr-mobile=TIEBA] .pb-title{white-space:normal!important;overflow-wrap:anywhere!important;font-size:20px!important;line-height:1.5!important;}
        html[data-qr-mobile=TIEBA] :is(.richtext-item,.pb-content-wrap>*,.pc-pb-reply-list>*){box-sizing:border-box!important;min-width:0!important;max-width:100%!important;}
        html[data-qr-mobile=TIEBA] .tieba-login-wrapper{box-sizing:border-box!important;width:calc(100% - 24px)!important;max-width:380px!important;min-width:0!important;left:12px!important;right:auto!important;top:12px!important;bottom:auto!important;margin:0!important;transform:none!important;max-height:calc(100vh - 24px)!important;overflow:auto!important;padding:20px!important;}
        html[data-qr-mobile=TIEBA] .tieba-login-wrapper :is(.tang-pass-login,.tang-pass-footerBar,.pass-form-logo,.tang-pass-qrcode-content,.tang-pass-qrcode-init,.tang-pass-qrcode-title,.login-qrcode-title,.login-qrcode-tip){box-sizing:border-box!important;width:100%!important;min-width:0!important;max-width:100%!important;}
        html[data-qr-mobile=TIEBA] .tieba-login-wrapper .close-btn{right:8px!important;top:8px!important;min-width:32px!important;min-height:32px!important;}

        html[data-qr-mobile=TIEBA] .pc-main-page-layout>.top-bar-wrapper{display:none!important;}
        html[data-qr-mobile=TIEBA] :is(.pc-main-page-layout,.pc-main-page-layout>.content-container,.pc-pb-box){height:100vh!important;min-height:0!important;}
        html[data-qr-mobile=TIEBA] .pc-pb-reply-top{display:flex!important;flex-wrap:wrap!important;height:auto!important;gap:12px!important;padding:12px!important;}
        html[data-qr-mobile=TIEBA] :is(.card-tab-wrapper,.sub-tab-container){flex-shrink:0!important;max-width:100%!important;width:auto!important;}
        html[data-qr-mobile=TIEBA] :is(.tab-item,.sub-tab-item){white-space:nowrap!important;min-height:40px!important;display:inline-flex!important;align-items:center!important;}
        html[data-qr-mobile=TIEBA] .pc-pb-first-floor-interactive{flex-wrap:wrap!important;gap:12px!important;padding:12px!important;box-sizing:border-box!important;}

        html[data-qr-mobile=TIEBA] .comment-content{box-sizing:border-box!important;min-width:0!important;max-width:100%!important;width:auto!important;margin:0 0 0 48px!important;}
        html[data-qr-mobile=TIEBA] .pb-title{word-break:normal!important;}
        """;
    private static final String BILIBILI="""
        html[data-qr-mobile=BILIBILI] :is(#app,#mirror-vdcon,.left-container){box-sizing:border-box!important;display:block!important;width:100%!important;min-width:0!important;max-width:100%!important;margin:0!important;padding:0!important;}
        html[data-qr-mobile=BILIBILI] #app{padding-top:0!important;}
        html[data-qr-mobile=BILIBILI] :is(.bili-header,.mini-header,.right-container,.fixed-sidenav-storage){display:none!important;}
        html[data-qr-mobile=BILIBILI] :is(#viewbox_report,#arc_toolbar_report,.video-desc-container,.video-tag-container,#commentapp){box-sizing:border-box!important;width:100%!important;max-width:100%!important;padding:12px!important;margin:0!important;}
        html[data-qr-mobile=BILIBILI] #viewbox_report{height:auto!important;}
        html[data-qr-mobile=BILIBILI] h1{font-size:20px!important;line-height:1.5!important;height:auto!important;overflow:visible!important;}
        html[data-qr-mobile=BILIBILI] :is(#playerWrap,#bilibili-player-placeholder,#bilibili-player){width:100%!important;min-width:0!important;height:auto!important;aspect-ratio:16/9!important;margin:0!important;}
        html[data-qr-mobile=BILIBILI] :is(.bpx-player-container,.bpx-player-primary-area,.bpx-player-video-area,.bpx-player-video-perch,.bpx-player-video-wrap){width:100%!important;min-width:0!important;height:100%!important;}
        html[data-qr-mobile=BILIBILI] #playerWrap video{object-fit:contain!important;}
        html[data-qr-mobile=BILIBILI] .bpx-player-sending-area{display:none!important;}
        html[data-qr-mobile=BILIBILI] #arc_toolbar_report{display:flex!important;flex-wrap:wrap!important;gap:12px!important;height:auto!important;}
        html[data-qr-mobile=BILIBILI] :is(.video-toolbar-left,.video-toolbar-right){display:flex!important;flex-wrap:wrap!important;gap:16px!important;}
        html[data-qr-mobile=BILIBILI] .video-desc-container{font-size:16px!important;line-height:1.6!important;}
        html[data-qr-mobile=BILIBILI] :is(.video-toolbar-left,.video-toolbar-right)>*{min-height:44px!important;margin:0!important;}
        html[data-qr-mobile=BILIBILI] :is(.bpx-player-ctrl-quality,.bpx-player-ctrl-wide,.bpx-player-ctrl-web,.bpx-player-ctrl-pip){display:none!important;}
        html[data-qr-mobile=BILIBILI] .bpx-player-control-bottom{padding:0 4px!important;}
        """;
    private static final String DOUYIN="""
        html[data-qr-douyin-fallback] #root{display:none!important;}
        html[data-qr-douyin-fallback],html[data-qr-douyin-fallback] body{overflow-y:auto!important;height:auto!important;}
        html[data-qr-mobile=DOUYIN] #qr-douyin-reader{display:block!important;position:relative;width:100%;box-sizing:border-box;background:#171923;color:#f5f5f5;padding-bottom:24px;font-size:16px;line-height:1.65;}
        html[data-qr-mobile=DOUYIN] #qr-douyin-reader :is(h1,h2,p){margin:12px 16px;white-space:pre-wrap;overflow-wrap:anywhere;}
        html[data-qr-mobile=DOUYIN] #qr-douyin-reader h2{font-size:18px;}
        html[data-qr-mobile=DOUYIN] .qr-douyin-comment{margin:0 16px;padding:14px 0;border-bottom:1px solid #ffffff24;}
        html[data-qr-mobile=DOUYIN] .qr-douyin-comment p{margin:4px 0!important;}
        html[data-qr-mobile=DOUYIN] .qr-douyin-comment strong{font-size:14px;color:#bbc1d3;}
        html[data-qr-mobile=DOUYIN] #qr-douyin-reader button{min-height:44px;border:1px solid #ffffff30;border-radius:10px;background:#292d3b;color:#fff;padding:8px 16px;margin:8px;font-size:15px;}
        html[data-qr-mobile=DOUYIN] #qr-douyin-reader button[hidden]{display:none!important;}

        html[data-qr-mobile=DOUYIN] .video-detail-container{position:relative!important;}
        html[data-qr-mobile=DOUYIN] #qr-douyin-player{position:absolute!important;inset:0!important;z-index:100!important;display:flex;align-items:center;justify-content:center;background:#000;color:#fff;overflow:hidden;}
        html[data-qr-mobile=DOUYIN] #qr-douyin-player :is(img,video){position:absolute!important;inset:0!important;width:100%!important;height:100%!important;object-fit:contain!important;}
        html[data-qr-mobile=DOUYIN] #qr-douyin-player video{background:#000;}
        html[data-qr-mobile=DOUYIN] #qr-douyin-player button{position:relative;z-index:2;min-height:48px;padding:12px 22px;border:0;border-radius:24px;background:#fff;color:#111;font-size:17px;}
        html[data-qr-mobile=DOUYIN] #qr-douyin-player button[hidden]{display:none!important;}
        html[data-qr-mobile=DOUYIN] #qr-douyin-player span{position:absolute;bottom:48px;left:12px;right:12px;text-align:center;background:#0009;font-size:14px;}
        html[data-qr-mobile=DOUYIN] :is(#douyin-navigation,#douyin-header){display:none!important;}
        html[data-qr-mobile=DOUYIN] :is(#root,#douyin-right-container,.parent-route-container,.route-scroll-container,.playerControlHeight,.leftContainer){box-sizing:border-box!important;width:100%!important;min-width:0!important;max-width:100%!important;margin:0!important;padding:0!important;}
        html[data-qr-mobile=DOUYIN] :is(.playerControlHeight,.leftContainer){display:block!important;height:auto!important;}
        html[data-qr-mobile=DOUYIN] .playerControlHeight>.detailPage{display:none!important;}
        html[data-qr-mobile=DOUYIN] .leftContainer>.detailPageSmallScreen{display:none!important;}
        html[data-qr-mobile=DOUYIN] .video-detail-container{width:100%!important;min-width:0!important;height:min(62vh,560px)!important;min-height:200px!important;border-radius:0!important;}
        html[data-qr-mobile=DOUYIN] .video-detail-container>div{width:100%!important;height:100%!important;}
        html[data-qr-mobile=DOUYIN] .video-detail-container :is(.xgplayer,.xg-video-container){width:100%!important;height:100%!important;}
        html[data-qr-mobile=DOUYIN] video{max-width:100%!important;object-fit:contain!important;}
        html[data-qr-mobile=DOUYIN] .leftContainer>div:not(.video-detail-container){box-sizing:border-box!important;width:100%!important;min-width:0!important;max-width:100%!important;padding:12px!important;}
        html[data-qr-mobile=DOUYIN] h1{font-size:18px!important;line-height:1.6!important;height:auto!important;max-height:none!important;-webkit-line-clamp:unset!important;}
        html[data-qr-mobile=DOUYIN] .xgplayer-controls{min-width:0!important;max-width:100%!important;}

        html[data-qr-mobile=DOUYIN] .leftContainer>div:empty{display:none!important;}
        html[data-qr-mobile=DOUYIN] .leftContainer :is(div:has(>h1),div:has(>div>h1)){display:block!important;height:auto!important;max-height:none!important;overflow:visible!important;white-space:normal!important;}
        html[data-qr-mobile=DOUYIN] .leftContainer div:has(>h1)>div{display:none!important;}
        html[data-qr-mobile=DOUYIN] .leftContainer div:has(>h1) h1{display:block!important;}
        html[data-qr-mobile=DOUYIN] .leftContainer div:has(>div>h1)~div{display:flex!important;flex-wrap:wrap!important;width:100%!important;height:auto!important;gap:8px!important;}

        html[data-qr-mobile=DOUYIN][data-qr-page=hot] .video-detail-container:not(:has(video,#qr-douyin-player)){position:relative!important;cursor:pointer!important;}
        html[data-qr-mobile=DOUYIN][data-qr-page=hot] .video-detail-container:not(:has(video,#qr-douyin-player))::after{content:"▶ 点击打开视频";position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);white-space:nowrap;background:#252630;color:#fff;padding:14px 20px;border-radius:28px;font-size:17px;pointer-events:none;}
        """;
}
