package app.quietreader;

import android.app.Instrumentation;
import android.os.*;
import android.graphics.Bitmap;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.*;

/** Observe the release app's actual screen using a separate test package. */
final class TopicFrontProbe {
    private static AccessibilityNodeInfo input(AccessibilityNodeInfo node){if(node==null)return null;if("android.widget.EditText".contentEquals(node.getClassName()))return node;for(int n=0;n<node.getChildCount();n++){AccessibilityNodeInfo found=input(node.getChild(n));if(found!=null)return found;}return null;}
    static void run(Instrumentation test,Bundle args)throws Exception{
        String query=args.getString("query");
        if(query!=null){AccessibilityNodeInfo edit=null;for(int n=0;n<20&&edit==null;n++){edit=input(test.getUiAutomation().getRootInActiveWindow());if(edit==null)Thread.sleep(100);}if(edit==null)throw new IOException("No foreground search input");Bundle value=new Bundle();value.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,query);if(!edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,value))throw new IOException("Cannot set search text");Thread.sleep(1500);}
        String label=args.getString("label","front");File dir=new File(test.getTargetContext().getExternalFilesDir(null),label);dir.mkdirs();
        StringBuilder log=new StringBuilder();
        long start=SystemClock.uptimeMillis();
        if("true".equals(args.getString("launch")))test.getContext().startActivity(new android.content.Intent().setClassName("app.quietreader","app.quietreader.DiscoveryActivity").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        if(args.containsKey("x")){
            float x=Float.parseFloat(args.getString("x")),y=Float.parseFloat(args.getString("y"));
            MotionEvent down=MotionEvent.obtain(start,start,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(start,start+1,MotionEvent.ACTION_UP,x,y,0);
            try{test.getUiAutomation().injectInputEvent(down,true);test.getUiAutomation().injectInputEvent(up,true);}finally{down.recycle();up.recycle();}
        }
        for(int n=0;n<12;n++){
            long before=SystemClock.uptimeMillis()-start;Bitmap raw=test.getUiAutomation().takeScreenshot();long after=SystemClock.uptimeMillis()-start;
            if(raw==null)throw new IOException("No actual screen bitmap");
            Bitmap small=Bitmap.createScaledBitmap(raw,720,Math.round(raw.getHeight()*720f/raw.getWidth()),true);
            try(FileOutputStream out=new FileOutputStream(new File(dir,"frame-"+n+".png"))){small.compress(Bitmap.CompressFormat.PNG,100,out);}finally{if(small!=raw)small.recycle();raw.recycle();}
            log.append("frame ").append(n).append(" captured in [").append(before).append(',').append(after).append("] ms after input\n");
            Thread.sleep(20);
        }
        try(FileWriter out=new FileWriter(new File(dir,"frames.txt"))){out.write(log.toString());}
        Bundle result=new Bundle();result.putString("stream",log.toString());test.finish(-1,result);
    }
}
