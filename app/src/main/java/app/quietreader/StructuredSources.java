package app.quietreader;

import java.util.*;
import org.jsoup.nodes.Element;
import static app.quietreader.Models.*;

/** Source acquisition only; all returned content uses the common reader. */
final class StructuredSources {
    static final String TIEBA_PAGE_NOTICE="仅展示当前页已加载的正文与回复；未加载内容不代表已读完。";
    private StructuredSources(){}
    static boolean supports(Source source){return source==Source.BAIDU||source==Source.TIEBA||source==Source.BILIBILI;}
    static Document article(Source source,org.jsoup.nodes.Document page,String url){
        Document result=new Document();result.url=url;
        Element heading=page.selectFirst(source==Source.BAIDU?"h1":".topic-title,.pb-title,h1");
        result.title=heading==null?page.title().replaceFirst("[_-](百度搜索|百度贴吧)$",""):heading.text();
        if(!UrlPolicy.belongs(source,url))return result;
        String path=java.net.URI.create(url).getPath();
        if(source==Source.BAIDU&&"/s".equals(path))baidu(page,result);
        else if(source==Source.BILIBILI)BilibiliArticle.read(page,result);
        else if(source==Source.TIEBA&&path.startsWith("/hottopic/"))tiebaTopic(page,result);
        else if(source==Source.TIEBA&&path.matches("/p/\\d+/?"))tiebaPost(page,result);
        if(!result.canPresent())result.notice="尚未取得可整理的内容；来源可能需要登录、仍在加载或已调整页面。可打开来源页确认后重试。";
        return result;
    }
    private static void baidu(Element page,Document out){
        Element content=page.selectFirst("#content_left,#results");if(content==null)return;
        Element intro=content.selectFirst(".rel-baikan-content,[tpl=rel_brief_head] [class^=summary_]");
        if(intro!=null)SourceParser.appendRegion(out,intro,"search-intro","话题介绍 · 来源摘要",false,out.url);
        // Search results are discovery/navigation, not the body of this topic.
        // If no topic introduction exists, retain at most the first result's
        // plain summary. Never display a directory of recommended article links.
        if(!out.hasContent())for(Element row:content.children()){
            if(row.hasClass("ec-tuiguang")||row.hasAttr("data-tuiguang"))continue;
            // Mobile entity biographies, videos and recommendation cards are not this event.
            if(content.id().equals("results")&&!row.attr("tpl").equals("www_index"))continue;
            Element summary=row.selectFirst(".c-abstract,[class*=abstract],.cosc-abstract,[class^=summary_]");
            if(summary==null||summary.text().trim().isEmpty())continue;
            Section section=new Section("search-summary","来源摘要",false);
            section.blocks.add(new Block("text",summary.text().trim()));out.sections.add(section);out.blocks.addAll(section.blocks);break;
        }
        cleanStoredSearch(out);
    }
    /** Old search-result caches must not revive the removed link directory. */
    static void cleanStoredSearch(Document out){
        if(!UrlPolicy.belongs(Source.BAIDU,out.url)||!"/s".equals(java.net.URI.create(out.url).getPath()))return;
        java.util.List<Section> old=new ArrayList<>(out.sections);
        out.sections.removeIf(s->s.id.startsWith("search-")&&!s.id.equals("search-intro")&&!s.id.equals("search-summary"));
        if(out.sections.isEmpty())for(Section section:old){
            Block summary=section.blocks.stream().filter(b->b.type.equals("text")&&b.inlineLinks.isEmpty()&&!b.value.trim().isEmpty()).findFirst().orElse(null);
            if(summary!=null){Section plain=new Section("search-summary","来源摘要",false);plain.blocks.add(summary);out.sections.add(plain);break;}
        }
        // Search providers wrap an entire event brief in a navigation link.
        // Treat that container as prose, including old persisted summaries, while
        // retaining actual inline citations that cover only part of a paragraph.
        for(Section section:out.sections)if(section.id.equals("search-intro")||section.id.equals("search-summary"))for(int i=0;i<section.blocks.size();i++){
            Block block=section.blocks.get(i);
            if(!block.type.equals("text")||block.inlineLinks.stream().noneMatch(link->link.start==0&&link.end==block.value.length()))continue;
            List<InlineLink> citations=new ArrayList<>();for(InlineLink link:block.inlineLinks)if(link.start!=0||link.end!=block.value.length())citations.add(link);
            Block plain=new Block(block.type,block.value,block.inlineImages,citations);plain.imageWidth=block.imageWidth;plain.imageHeight=block.imageHeight;section.blocks.set(i,plain);
        }
        out.blocks.clear();for(Section section:out.sections)out.blocks.addAll(section.blocks);
        out.related.clear();out.notice="以下为百度返回的话题介绍或摘要，不是新闻原文。";
    }

    private static void tiebaTopic(Element page,Document out){
        Element topic=page.selectFirst(".topic-wrapper");if(topic==null)return;
        Element description=topic.selectFirst(".topic-desc");
        if(description!=null)SourceParser.appendRegion(out,description,"topic-intro","话题介绍",false,out.url);
        for(Item post:TiebaTopic.posts(page,out.url)){
            out.related.add(post);
            Section section=new Section(identity("topic-post",post.url),"相关帖子 · 摘要",false);section.blocks.add(link(post.title,post.url));
            if(!post.snippet.isEmpty())section.blocks.add(new Block("text",post.snippet));
            out.sections.add(section);out.blocks.addAll(section.blocks);
        }
        out.notice="以下为当前话题页已加载的帖子入口与摘要，不是完整帖子或该话题的评论。进入帖子后查看正文与回复。";
    }
    private static void tiebaPost(Element page,Document out){
        Element content=page.selectFirst("#j_p_postlist,.p_postlist,.pc-pb-box");if(content==null)return;
        out.virtualDiscussion=content.hasClass("pc-pb-box")&&content.selectFirst(".pc-pb-reply-list .pb-comment-list")!=null;
        List<Element> roots=new ArrayList<>(content.select(".d_post_content,.lzl_content_main,.image-text>.pb-content-wrap,.pc-pb-reply-list .comment-content>.pb-rich-text"));
        Set<Element> rootSet=new HashSet<>(roots);
        Set<String> seen=new HashSet<>();Map<String,Integer> occurrences=new HashMap<>();Map<Element,String> floorSections=new IdentityHashMap<>();int index=0,position=0;
        for(Element root:roots){
            if(index>=100)break;
            boolean desktopNested=root.is(".lzl_content_main"),nested=desktopNested||root.closest(".pb-lzl-item")!=null;
            if(!nested&&root.parents().stream().anyMatch(rootSet::contains))continue;
            int floorPosition=nested?-1:position++;
            Element owner=root.closest(TIEBA_OWNERS),author=ownedTieba(owner,root,".d_name,.author-name,.user-name,.head-name,.j_user_card");
            boolean reply=nested||root.closest(".pc-pb-reply-list")!=null||floorPosition>0||out.url.matches(".*[?&]pn=(?:[2-9]|[1-9][0-9]+)(?:&.*)?$");
            String postId="";
            if(!nested&&owner!=null&&owner.hasAttr("data-field"))try{org.json.JSONObject post=new org.json.JSONObject(owner.attr("data-field")).optJSONObject("content");if(post!=null){if(post.optInt("post_no",0)>0)reply=post.optInt("post_no")>1;postId=post.optString("post_id");}}catch(Exception ignored){}
            // Desktop DOM IDs and data-field refer to the same source post.
            // Keep the historical DOM spelling so cached reply IDs remain stable.
            String key=root.id();if(key.isEmpty()&&postId.matches("[1-9][0-9]*"))key="post_content_"+postId;
            if(!nested&&owner!=null&&owner.hasClass("pb-comment-item")&&owner.attr("data-id").matches("[1-9][0-9]{0,19}"))key="post_content_"+owner.attr("data-id");
            if(desktopNested){String subId="";if(owner!=null&&owner.hasAttr("data-field"))try{subId=new org.json.JSONObject(owner.attr("data-field")).optString("spid");}catch(Exception ignored){}
                if(subId.matches("[1-9][0-9]{0,19}"))key="lzl:"+subId;
                else if(!key.isEmpty())key="lzl-dom:"+key;
            }
            if(nested&&!desktopNested&&owner!=null&&owner.attr("data-id").matches("[1-9][0-9]{0,19}"))key="lzl:"+owner.attr("data-id");
            if(nested&&!desktopNested&&owner!=null&&owner.attr("data-qr-child-id").matches("[1-9][0-9]{0,19}")){
                Element parent=owner.parent()==null?null:owner.parent().closest(".pb-comment-item[data-id]");
                if(parent!=null&&parent.attr("data-id").equals(owner.attr("data-qr-parent-id"))&&(!owner.hasAttr("data-id")||owner.attr("data-id").equals(owner.attr("data-qr-child-id"))))key="lzl:"+owner.attr("data-qr-child-id");
            }
            String parentSection="";
            if(nested&&owner!=null)for(Element ancestor:owner.parents())if(floorSections.containsKey(ancestor)){parentSection=floorSections.get(ancestor);break;}
            if(!key.isEmpty()){if(!seen.add(key))continue;}else{
                String floor="";Element meta=ownedTieba(owner,root,".pc-pb-comments-desc,.tail-info");if(meta!=null){java.util.regex.Matcher number=java.util.regex.Pattern.compile("(?:第)?([0-9]+)楼").matcher(meta.text());if(number.find())floor=number.group(1);}
                // Match the image actually rendered, including lazy placeholders becoming src.
                key=(!parentSection.isEmpty()?"lzl-parent:"+parentSection+"|":desktopNested?"lzl-fallback|":"")+(author==null?"":author.text())+"|"+(floor.isEmpty()?"page:"+DiscussionPages.page(Source.TIEBA,out.url):floor)+"|"+root.text()+root.select("img").stream().map(image->SourceParser.imageSource(image,out.url)).toList();int occurrence=occurrences.getOrDefault(key,0);occurrences.put(key,occurrence+1);key+="|"+occurrence;
            }
            String label=reply?"回复":"正文";if(author!=null&&!author.text().isEmpty())label+=" · "+author.text();
            if(!reply&&author!=null)out.byline=author.text();
            Element region=root.clone();for(Element child:new ArrayList<>(region.select(".lzl_single_post,.lzl_content_main,.pb-lzl-item")))if(child!=region)child.remove();
            String replyTo=reply?TiebaReplyRelations.extractTarget(region,out.url):"";
            if(root.hasClass("pb-rich-text")&&root.parent()!=null&&root.parent().hasClass("comment-content"))for(Element sibling:root.parent().children())if(sibling.hasClass("pb-image-list")||sibling.hasClass("image-card-wrapper"))region.appendChild(sibling.clone());
            if(SourceParser.appendRegion(out,region,identity("tieba-post",key),label,false,out.url)){
                Section section=out.sections.get(out.sections.size()-1);
                section.nestedReply=nested;section.replyToAuthor=replyTo;
                section.parentSectionId=parentSection;
                if(!nested&&owner!=null)floorSections.put(owner,section.id);
                if(key.matches("post_content_[1-9][0-9]{0,19}"))section.sourcePostId=key.substring("post_content_".length());
                if(nested&&key.matches("lzl:[1-9][0-9]{0,19}"))section.sourceChildId=key.substring(4);
                index++;
            }
        }
        DiscussionPages.pagination(Source.TIEBA,page,out);
        if(out.virtualDiscussion){
            if(content.selectFirst(".pc-pb-reply-list .login-guard-mask")!=null)out.moreStatus="login";
            else if(content.selectFirst(".pc-pb-reply-list .load-error-wrapper")!=null)out.moreStatus="error";
            // The source can have downloaded all rows while only rendering its first
            // virtual window. Only the scrolling owner can establish the actual end.
        }
        out.notice=out.moreStatus.equals("login")?"后续评论需要来源登录，已读内容保留。":out.moreStatus.equals("error")?"来源评论加载失败，已读内容保留，可重试。":TIEBA_PAGE_NOTICE;
    }
    private static final String TIEBA_OWNERS=".lzl_single_post,.l_post,.pb-lzl-item,.pb-comment-item,.pb-comment-list,.image-text";
    /** A descendant comment's author/metadata cannot stand in for its parent. */
    private static Element ownedTieba(Element owner,Element body,String selector){
        if(owner==null)return null;
        for(Element candidate:owner.select(selector))if(candidate.closest(TIEBA_OWNERS)==owner&&!candidate.parents().contains(body)&&candidate!=body&&candidate.closest("aside,nav,.recommend,.advertisement,.ad")==null)return candidate;
        return null;
    }
    private static Block link(String text,String target){return new Block("text",text,Collections.emptyList(),List.of(new InlineLink(0,text.length(),target)));}
    private static String identity(String type,String value){return type+"-"+app.quietreader.discovery.HotCandidate.hash(value).substring(0,24);}
}
