package app.quietreader;

import org.json.JSONObject;

/** Fixed native-owned DOM update for our escaped reader, never source scripts. */
final class ReaderImageState {
    private ReaderImageState(){}
    static String settle(String page,String failedUrl){
        return "(()=>{const page="+JSONObject.quote(page)+",failed="+(failedUrl==null?"null":JSONObject.quote(failedUrl))+";"+"""
            if(location.href.split('#')[0]!==page.split('#')[0])return 0;
            const images=Array.from(document.querySelectorAll('figure > a > img'));
            const broken=images.filter(i=>i.complete&&i.naturalWidth===0&&!i.closest('figure').classList.contains('image-unavailable')&&
              (!failed||i.src===failed||i.getAttribute('src')===failed));
            if(!broken.length)return 0;
            const anchor=Array.from(document.querySelectorAll('p[data-reading-anchor]')).find(p=>{
              const r=p.getBoundingClientRect();return r.height>0&&r.bottom>0&&r.top<innerHeight;
            });
            const before=anchor?anchor.getBoundingClientRect().top:0;
            for(const image of broken){
              const figure=image.closest('figure'),action=figure.querySelector('.image-open');
              if(!action)continue;
              figure.classList.add('image-unavailable');
              action.textContent='图片未加载 · 查看大图';
              action.setAttribute('aria-label','图片未加载，打开大图后可重试');
            }
            if(anchor){const shift=anchor.getBoundingClientRect().top-before;if(Math.abs(shift)>.5)scrollBy(0,shift);}
            return broken.length;
            })()
            """;
    }
}
