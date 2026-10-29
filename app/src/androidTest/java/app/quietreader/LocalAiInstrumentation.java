package app.quietreader;

import android.app.Instrumentation;
import android.os.*;
import app.quietreader.ai.LocalModelEngine;
import app.quietreader.ai.ModelFiles;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Real Android CPU execution. Synthetic parity is not personal-quality evidence. */
public final class LocalAiInstrumentation extends Instrumentation {
    private int checks,failures;
    private String mode="parity";
    private boolean externalImport,loadBreakdown;
    private final StringBuilder report=new StringBuilder();
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private void memory(String phase){Debug.MemoryInfo info=new Debug.MemoryInfo();Debug.getMemoryInfo(info);report.append("PROBE_PROCESS_PSS_KIB ").append(phase).append(' ').append(info.getTotalPss()).append('\n');}
    private void systemMemory(String phase){
        try{
            String dump;
            try(ParcelFileDescriptor descriptor=getUiAutomation().executeShellCommand("dumpsys meminfo --package app.quietreader")){
                dump=read(new ParcelFileDescriptor.AutoCloseInputStream(descriptor));
            }
            File target=new File(getTargetContext().getExternalFilesDir(null),"memory-"+phase+".txt");
            try(FileOutputStream output=new FileOutputStream(target)){output.write(dump.getBytes(StandardCharsets.UTF_8));}
            long sum=0;int processes=0;java.util.regex.Matcher total=java.util.regex.Pattern.compile("TOTAL PSS:\\s+(\\d+)").matcher(dump);
            while(total.find()){sum+=Long.parseLong(total.group(1));processes++;}
            report.append("EXTERNAL_SAMPLED_PSS_KIB ").append(phase).append(" sum=").append(sum).append(" processes=").append(processes).append('\n');
            check(processes>0,"external meminfo observes live app processes at "+phase);
        }catch(Exception error){throw new IllegalStateException("External memory sampling failed",error);}
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);if(args!=null){mode=args.getString("mode","parity");externalImport="external".equals(args.getString("modelSource"));loadBreakdown="true".equals(args.getString("loadBreakdown"));}start();}
    private static long[] ids(JSONArray array)throws JSONException{long[] ids=new long[array.length()];for(int i=0;i<ids.length;i++)ids[i]=array.getLong(i);return ids;}
    private static String read(InputStream input)throws IOException{try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);return out.toString("UTF-8");}}
    @Override public void onStart(){Bundle result=new Bundle();File output=new File(getTargetContext().getExternalFilesDir(null),"local-ai-"+(mode.equals("lifecycle")?"lifecycle":"parity")+".txt");
        try{
            report.append("Synthetic Android parity; NOT whole-app peak, battery, or recommendation accuracy.\n");
            report.append("ABI ").append(Arrays.toString(Build.SUPPORTED_ABIS)).append(" API ").append(Build.VERSION.SDK_INT).append('\n');
            JSONObject golden=new JSONObject(read(getContext().getAssets().open("local-ai-golden.json")));
            ModelFiles files=new ModelFiles(getTargetContext());
            File root=files.install(externalImport?new File(getTargetContext().getExternalFilesDir(null),"models"):null);
            if(mode.equals("lifecycle")){
                // Run in a fresh instrumentation process: running direct engine parity
                // here as well would retain a second native allocator, unlike production.
                try(RankingServiceProbe probe=new RankingServiceProbe(getTargetContext(),this::check,report,this::systemMemory)){probe.bind();probe.run();}
                try(RankingServiceProbe probe=new RankingServiceProbe(getTargetContext(),this::check,report,this::systemMemory)){probe.bind();probe.idleExit();}
            }else{
            memory("before");
            try(LocalModelEngine engine=new LocalModelEngine(root,files.manifest())){
                for(String kind:new String[]{"embedding","reranker"}){
                    long verifyMs=0,verifyCpuMs=0,verifyBytes=-1,verifyHits=-1;
                    if(loadBreakdown){
                        // Diagnostic only: run the actual private verifier first.
                        // load() then sees its normal in-engine verified cache.
                        // This separates hashing from the remaining tokenizer/ORT
                        // initialization without changing production options.
                        java.lang.reflect.Method verify=LocalModelEngine.class.getDeclaredMethod("verify",String.class);verify.setAccessible(true);
                        long before=SystemClock.elapsedRealtime(),cpu=android.os.Process.getElapsedCpuTime();Object work=verify.invoke(engine,kind);
                        verifyMs=SystemClock.elapsedRealtime()-before;verifyCpuMs=android.os.Process.getElapsedCpuTime()-cpu;
                        if(work!=null){java.lang.reflect.Field bytes=work.getClass().getDeclaredField("bytesHashed"),hits=work.getClass().getDeclaredField("cacheHits");bytes.setAccessible(true);hits.setAccessible(true);verifyBytes=bytes.getLong(work);verifyHits=hits.getInt(work);}
                    }
                    long started=SystemClock.elapsedRealtime(),cpu=android.os.Process.getElapsedCpuTime();engine.load(kind);long remainingMs=SystemClock.elapsedRealtime()-started,remainingCpuMs=android.os.Process.getElapsedCpuTime()-cpu;
                    report.append("LOAD_MS ").append(kind).append(' ').append(verifyMs+remainingMs).append('\n');
                    if(loadBreakdown)report.append("LOAD_BREAKDOWN ").append(new JSONObject().put("kind",kind).put("verifyMs",verifyMs).put("verifyCpuMs",verifyCpuMs).put("verifyBytesHashed",verifyBytes).put("verifyCacheHits",verifyHits).put("postVerifyLoadMs",remainingMs).put("postVerifyLoadCpuMs",remainingCpuMs).put("scope","direct engine diagnostic; post-verify includes tokenizer and ORT; warm filesystem possible; not whole-App or phone performance")).append('\n');
                    memory(kind+"-loaded");
                    JSONArray tokenizers=golden.getJSONArray("tokenizers");
                    for(int i=0;i<tokenizers.length();i++){
                        JSONObject fixture=tokenizers.getJSONObject(i);if(!kind.equals(fixture.getString("kind")))continue;
                        long[] expected=ids(fixture.getJSONArray("ids")),actual=engine.tokenize(kind,fixture.getString("text"));
                        check(Arrays.equals(expected,actual),kind+" token IDs fixture "+i);
                        if(!Arrays.equals(expected,actual))report.append("EXPECTED ").append(Arrays.toString(expected)).append("\nACTUAL ").append(Arrays.toString(actual)).append('\n');
                    }
                    if(kind.equals("embedding")){
                        JSONArray fixtures=golden.getJSONArray("embeddings");
                        for(int i=0;i<fixtures.length();i++){
                            JSONObject fixture=fixtures.getJSONObject(i);long start=SystemClock.elapsedRealtime();float[] actual=engine.embed(fixture.getString("text"));
                            JSONArray expected=fixture.getJSONArray("vector");double dot=0,norm=0,max=0;
                            for(int j=0;j<actual.length;j++){dot+=actual[j]*expected.getDouble(j);norm+=(double)actual[j]*actual[j];max=Math.max(max,Math.abs(actual[j]-expected.getDouble(j)));}
                            check(actual.length==512&&Math.abs(norm-1)<0.0001,"embedding normalized 512D "+i);
                            check(dot>0.999&&max<0.005,"embedding desktop INT8 parity "+i);
                            report.append("EMBED_MS ").append(SystemClock.elapsedRealtime()-start).append(" COS ").append(dot).append(" MAX_DIFF ").append(max).append('\n');
                        }
                    }else{
                        JSONArray fixtures=golden.getJSONArray("reranks");List<Float> logits=new ArrayList<>();
                        for(int i=0;i<fixtures.length();i++){
                            JSONObject fixture=fixtures.getJSONObject(i);String query=fixture.getString("query"),text=fixture.getString("text");
                            check(Arrays.equals(ids(fixture.getJSONArray("ids")),engine.pair(query,text)),"reranker pair special tokens/truncation "+i);
                            long start=SystemClock.elapsedRealtime();float actual=engine.rerank(query,text);logits.add(actual);
                            check(Math.abs(actual-fixture.getDouble("logit"))<0.05,"reranker desktop INT8 parity "+i);
                            report.append("RERANK_MS ").append(SystemClock.elapsedRealtime()-start).append(" LOGIT ").append(actual).append('\n');
                        }
                        check(logits.get(0)>logits.get(1),"synthetic backup relevance smoke");
                        check(logits.get(2)>logits.get(3),"synthetic experience relevance smoke");
                    }
                    memory(kind+"-after-inference");engine.unload();memory(kind+"-unloaded");
                }
            }
            try(LocalModelEngine cancelled=new LocalModelEngine(root,files.manifest())){
                cancelled.cancel();boolean rejected=false;
                try{cancelled.embed("取消后不得执行");}catch(InterruptedIOException expected){rejected=true;}
                check(rejected,"cancelled engine rejects new inference");
            }
            File vocab=new File(root,"embedding/upstream/vocab.txt");
            try(RandomAccessFile file=new RandomAccessFile(vocab,"rw")){
                int original=file.read();
                try{
                    file.seek(0);file.write(original^1);
                    try(LocalModelEngine corrupt=new LocalModelEngine(root,files.manifest())){
                        boolean rejected=false;try{corrupt.load("embedding");}catch(IOException expected){rejected=true;}
                        check(rejected,"trusted manifest rejects corrupted private model file");
                    }
                }finally{file.seek(0);file.write(original);}
            }
            }
        }catch(Throwable t){failures++;report.append("ERROR ").append(t.getClass().getName()).append(": ").append(t.getMessage()).append('\n');}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');
        try(FileOutputStream out=new FileOutputStream(output)){out.write(report.toString().getBytes(StandardCharsets.UTF_8));}catch(Exception e){failures++;}
        result.putString("stream",report.toString());result.putInt("checks",checks);result.putInt("failures",failures);finish(failures==0?android.app.Activity.RESULT_OK:android.app.Activity.RESULT_CANCELED,result);
    }
}
