package app.quietreader;

/** Acquisition outcome retained across asynchronous delivery; no account information. */
final class ArticleFailure {
    enum Kind { TRANSIENT, OFFLINE, SESSION_CHANGED, AUTH, RATE_LIMIT, PERMANENT, UNKNOWN }
    final Kind kind;
    final String message;
    final int httpStatus;
    final long retryAt;
    ArticleFailure(Kind kind,String message){this(kind,message,0,0);}
    private ArticleFailure(Kind kind,String message,int httpStatus,long retryAt){this.kind=kind;this.message=message;this.httpStatus=httpStatus;this.retryAt=retryAt;}
    static ArticleFailure unknown(String message){return new ArticleFailure(Kind.UNKNOWN,message);}
    static ArticleFailure session(){return new ArticleFailure(Kind.SESSION_CHANGED,"登录或缓存状态已变化，请重新读取");}
    static ArticleFailure http(String message,int status,long retryAt){
        Kind kind=status==401||status==403?Kind.AUTH:status==429?Kind.RATE_LIMIT:status==408||status==425||status>=500&&status<=599?Kind.TRANSIENT:status>=400?Kind.PERMANENT:Kind.UNKNOWN;
        return new ArticleFailure(kind,message,status,retryAt);
    }
    static ArticleFailure from(Exception error){
        String message=Repository.explain(error);
        if(error instanceof Repository.HttpFailure){Repository.HttpFailure http=(Repository.HttpFailure)error;return http(message,http.status,http.retryAt);}
        if(error instanceof java.net.SocketTimeoutException||error instanceof java.net.UnknownHostException||error instanceof java.net.ConnectException||error instanceof java.net.SocketException)return new ArticleFailure(Kind.TRANSIENT,message);
        if(error instanceof javax.net.ssl.SSLException||error instanceof SecurityException)return new ArticleFailure(Kind.PERMANENT,message);
        return unknown(message);
    }
    boolean retryable(){return kind==Kind.TRANSIENT;}
    boolean mayFallback(){return kind!=Kind.RATE_LIMIT&&kind!=Kind.PERMANENT&&retryAt<=System.currentTimeMillis();}
}
