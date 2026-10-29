import com.sun.jdi.*;
import com.sun.jdi.connect.AttachingConnector;
import java.util.*;

/** Host-only read-only JDWP inspection of the isolated emulator application. */
public final class TiebaTaskStateCheck {
    private static int checks,failures;
    private static final String ROOT="https://tieba.baidu.com/hottopic/browse/hottopic?topic_id=990066600",SECOND="https://tieba.baidu.com/p/990066602";
    private static Value field(ObjectReference object,String name){
        if(object==null)return null;
        for(Field field:object.referenceType().allFields())if(field.name().equals(name))return object.getValue(field);
        throw new IllegalStateException("Missing field: "+name);
    }
    private static ObjectReference object(ObjectReference owner,String name){Value value=field(owner,name);return value instanceof ObjectReference?(ObjectReference)value:null;}
    private static String string(ObjectReference owner,String name){Value value=field(owner,name);return value instanceof StringReference?((StringReference)value).value():"";}
    private static void check(boolean good,String label){checks++;if(!good)failures++;System.out.println((good?"PASS ":"FAIL ")+label);}
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IllegalArgumentException("port and expected native scroll required");
        AttachingConnector connector=Bootstrap.virtualMachineManager().attachingConnectors().stream().filter(c->c.name().equals("com.sun.jdi.SocketAttach")).findFirst().orElseThrow();
        var options=connector.defaultArguments();options.get("hostname").setValue("127.0.0.1");options.get("port").setValue(args[0]);options.get("timeout").setValue("5000");
        VirtualMachine vm=connector.attach(options);
        try{
            check(vm.canGetInstanceInfo(),"VM supports read-only live instance inspection");
            ObjectReference reader=null,doc=null;long deadline=System.currentTimeMillis()+10000;
            while(System.currentTimeMillis()<deadline){
                for(ReferenceType type:vm.classesByName("app.quietreader.MainActivity"))for(ObjectReference candidate:type.instances(10)){
                    Value destroyed=field(candidate,"mDestroyed");if(destroyed instanceof BooleanValue&&((BooleanValue)destroyed).value())continue;
                    ObjectReference body=object(candidate,"reading");if(body!=null){reader=candidate;doc=body;break;}
                }
                if(reader!=null&&object(reader,"readerWeb")!=null){Value resumed=field(reader,"mResumed");if(resumed instanceof BooleanValue&&((BooleanValue)resumed).value())break;}Thread.sleep(100);
            }
            check(reader!=null&&doc!=null,"ordinary Android task restore produced a live reader body");
            if(reader==null||doc==null)throw new IllegalStateException("No restored reader");
            Value resumed=field(reader,"mResumed");check(resumed instanceof BooleanValue&&((BooleanValue)resumed).value(),"inspected reader is the resumed foreground Activity");
            ObjectReference current=object(reader,"current"),topic=object(doc,"topic");
            check(ROOT.equals(string(current,"url")),"restored request retains original recommendation topic");
            check(SECOND.equals(string(current,"selectedTopicPost")),"system restores explicit second-post request without restarting instrumentation");
            check(SECOND.equals(string(topic,"postUrl")),"restored body retains the selected actual post identity");
            check("第二帖实际标题".equals(string(doc,"title")),"restored reader shows selected post's real title");
            ObjectReference candidate=object(reader,"discoveryCandidate");
            check(ROOT.equals(string(candidate,"originalUrl")),"restored recommendation identity remains the original topic");
            ObjectReference history=object(reader,"history");check(field(history,"head").equals(field(history,"tail")),"restored reader has no duplicate topic back stack");
            check(object(reader,"dynamic")==null&&object(reader,"pendingArticle")==null,"cached selected body restores without pending source acquisition");
            ObjectReference web=object(reader,"readerWeb");int expected=Integer.parseInt(args[1]),actual=0;
            deadline=System.currentTimeMillis()+10000;
            while(System.currentTimeMillis()<deadline){Value value=field(web,"mScrollY");actual=((IntegerValue)value).value();if(expected<0?actual>0:Math.abs(actual-expected)<=8)break;Thread.sleep(100);}
            check(expected<0?actual>0:expected>0&&Math.abs(actual-expected)<=8,expected<0?"ordinary source preparation has a noninitial reading position":"restored native reading position matches within eight pixels");
            System.out.println("POSITION expected="+expected+" actual="+actual);
            System.out.println("RESTORE openPosition="+field(reader,"openPosition")+" pendingFallback="+field(reader,"pendingViewportFallback")+" pending="+(object(reader,"pendingViewport")!=null));
        }finally{vm.dispose();}
        System.out.println("checks="+checks+" failures="+failures);
        if(failures!=0)System.exit(1);
    }
}
