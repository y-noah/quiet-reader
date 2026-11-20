package app.quietreader;

import org.junit.Test;
import static org.junit.Assert.*;

public class ArticleFailureTest {
    @Test public void nytNativeTransportFailureReportsUnreachableSourceWithoutBrowserRetry(){
        ArticleFailure timeout=ArticleFailure.from(new java.net.SocketTimeoutException());
        assertFalse(timeout.mayFallbackFromNative(Models.Source.NYT));
        assertTrue(timeout.mayFallbackFromNative(Models.Source.WEIBO));
        ArticleFailure displayed=timeout.forNativeArticle(Models.Source.NYT);
        assertTrue(displayed.retryable());
        assertTrue(displayed.message.contains("当前网络"));
        assertFalse(displayed.message.contains("登录"));
        assertTrue(ArticleFailure.http("temporary",503,0).mayFallbackFromNative(Models.Source.NYT));
        assertTrue(ArticleFailure.http("restricted",403,0).mayFallbackFromNative(Models.Source.NYT));
    }
    @Test public void retriesAnEarlyPromotedFailureWithoutRepeatingASlowForegroundRequest(){
        ArticleFailure transientFailure=ArticleFailure.from(new java.net.SocketTimeoutException());
        assertTrue(transientFailure.retryAfterPromotion(4_999,10_000));
        assertFalse(transientFailure.retryAfterPromotion(5_000,10_000));
        assertFalse(transientFailure.retryAfterPromotion(25_000,30_000));
        assertTrue(ArticleFailure.session().retryAfterPromotion(25_000,30_000));
        assertFalse(ArticleFailure.http("retry later",503,40_000).retryAfterPromotion(0,30_000));
        assertFalse(ArticleFailure.http("forbidden",403,0).retryAfterPromotion(0,30_000));
    }
    @Test public void networkInterruptionCanRetryButCertificateAndPermissionFailuresCannot(){
        assertTrue(ArticleFailure.from(new java.net.SocketTimeoutException()).retryable());
        assertTrue(ArticleFailure.from(new java.net.UnknownHostException()).retryable());
        assertFalse(ArticleFailure.from(new javax.net.ssl.SSLException("certificate")).retryable());
        assertFalse(ArticleFailure.from(new SecurityException()).mayFallback());
        for(int status:new int[]{401,403,404,410,429})assertFalse(ArticleFailure.http("fixture",status,0).retryable());
    }
    @Test public void serverCooldownSurvivesTransportClassification(){
        long until=System.currentTimeMillis()+60_000;
        for(int status:new int[]{429,503}){
            ArticleFailure failure=ArticleFailure.from(new Repository.HttpFailure(status,until));
            assertEquals(status,failure.httpStatus);assertEquals(until,failure.retryAt);assertFalse(failure.mayFallback());
        }
        assertTrue(ArticleFailure.http("temporary",503,0).retryable());
        assertTrue(ArticleFailure.http("auth",403,0).mayFallback());
        assertFalse(ArticleFailure.http("not found",404,0).mayFallback());
    }
    @Test public void legacyCallbacksStillReceiveStatusAndCooldown(){
        long until=System.currentTimeMillis()+60_000;long[] received={0,0};
        Repository.Result<Object> legacy=new Repository.Result<>(){
            public void success(Object value){fail("unexpected success");}
            public void failure(String text){fail("structured failure discarded");}
            public void failure(String text,int status,long retryAt){received[0]=status;received[1]=retryAt;}
        };
        legacy.failure(ArticleFailure.http("busy",429,until));assertEquals(429,received[0]);assertEquals(until,received[1]);
    }
}
