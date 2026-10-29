package app.quietreader.discovery;

import java.util.*;
import org.json.*;

/** Prospective sampling boundary; semantic independence still needs adjudication. */
public final class ProspectiveEvaluation {
    public static final String POLICY="prospective-v1";
    private ProspectiveEvaluation(){}
    public static String policies(){return POLICY+"/"+SemanticOrder.VERSION+"/"+PlatformPreference.VERSION+"/"+HotCandidate.NORMALIZATION+"/"+EventGroups.VERSION+"/"+EventGroupAssembly.VERSION+"/"+EventClaimEvidence.VERSION+"/"+EventVerificationPlan.VERSION+"/"+EventRerankCacheKey.COVERAGE_VERSION+"/"+EventVerificationBudget.VERSION;}
    public static Set<String> strings(JSONArray values)throws JSONException{
        if(values.length()>20000)throw new IllegalArgumentException("Evaluation identity budget exceeded");
        Set<String> result=new TreeSet<>();for(int n=0;n<values.length();n++){String value=values.getString(n);if(!value.matches("(?:group-)?[a-f0-9]{64}")||!result.add(value))throw new IllegalArgumentException("Invalid evaluation identity");}return result;
    }
    public static void validate(JSONObject baseline)throws JSONException{
        if(baseline.getInt("protocol_schema")!=1||baseline.getBoolean("holdout_qualified")||!POLICY.equals(baseline.getString("policy"))
                ||baseline.getString("ranking_policies").isEmpty()||baseline.getString("id").isEmpty()||baseline.getLong("cutoff_ms")<0
                ||baseline.getLong("app_epoch_ms")<=0)throw new IllegalArgumentException("Invalid evaluation baseline");
        for(String field:Arrays.asList("model_revision","source_config_sha256","ledger_sha256","active_feedback_sha256"))if(!baseline.getString(field).matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid evaluation fingerprint");
        if(baseline.getLong("profile_version")<0)throw new IllegalArgumentException("Invalid evaluation profile");
        for(String field:Arrays.asList("known_identities","known_text_hashes","used_identities","used_text_hashes"))strings(baseline.getJSONArray(field));
    }
    public static void requireCurrent(JSONObject baseline,JSONObject learning,String model,long appEpoch,String sourceConfig)throws JSONException{
        requireSettings(baseline,learning.getLong("profile_version"),model,appEpoch,sourceConfig);
        if(!baseline.getString("ledger_sha256").equals(learning.getString("ledger_sha256"))
                ||!baseline.getString("active_feedback_sha256").equals(learning.getString("active_feedback_sha256")))throw new IllegalArgumentException("偏好记录已变化或过期，请重新准备新话题评价。");
        if(learning.getLong("learning_cutoff_ms")<=baseline.getLong("cutoff_ms"))throw new IllegalArgumentException("准备后还没有新的采集窗口，请刷新来源后再试。");
    }
    public static void requireSettings(JSONObject baseline,long profileVersion,String model,long appEpoch,String sourceConfig)throws JSONException{
        try{validate(baseline);}catch(JSONException|IllegalArgumentException error){throw new IllegalArgumentException("评价准备已失效，请重新准备新话题评价。",error);}
        if(!policies().equals(baseline.getString("ranking_policies")))throw new IllegalArgumentException("推荐规则已更新，请重新准备新话题评价。");
        if(!baseline.getString("model_revision").equals(model)||baseline.getLong("app_epoch_ms")!=appEpoch
                ||!baseline.getString("source_config_sha256").equals(sourceConfig))throw new IllegalArgumentException("模型、安装或来源设置已变化，请重新准备新话题评价。");
        if(baseline.getLong("profile_version")!=profileVersion)throw new IllegalArgumentException("偏好记录已变化或过期，请重新准备新话题评价。");
    }
    public static List<FeedStore.Row> select(List<FeedStore.Row> available,JSONObject baseline,Map<String,Long> firstSeen)throws JSONException{
        validate(baseline);Set<String> known=strings(baseline.getJSONArray("known_identities")),hashes=strings(baseline.getJSONArray("known_text_hashes"));
        known.addAll(strings(baseline.getJSONArray("used_identities")));hashes.addAll(strings(baseline.getJSONArray("used_text_hashes")));
        long cutoff=baseline.getLong("cutoff_ms");List<FeedStore.Row> result=new ArrayList<>();
        for(FeedStore.Row row:available){boolean eligible=!known.contains(row.eventId);
            for(HotCandidate member:row.entries){Long first=firstSeen.get(member.id);if(first==null||first<=cutoff||known.contains(member.id)||!Collections.disjoint(known,EventGroups.aliases(member))||hashes.contains(member.textHash))eligible=false;}
            if(eligible)result.add(row);if(result.size()==100)break;
        }return result;
    }
    public static JSONObject sessionProtocol(JSONObject baseline)throws JSONException{
        validate(baseline);return new JSONObject(baseline.toString());
    }
}
