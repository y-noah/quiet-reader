package app.quietreader;

import android.app.Instrumentation;
import java.io.InputStream;
import java.security.*;
import javax.net.ssl.*;

/** Public test-only key; never included in production assets or global trust. */
final class LoopbackTestTls {
    final SSLContext context;
    final byte[] certificate;
    LoopbackTestTls(Instrumentation test)throws Exception{
        KeyStore keys=KeyStore.getInstance("PKCS12");
        try(InputStream in=test.getContext().getAssets().open("loopback-cancellation-test.p12")){keys.load(in,"loopback-test-only".toCharArray());}
        certificate=keys.getCertificate("loopback-test").getEncoded();
        KeyManagerFactory key=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());key.init(keys,"loopback-test-only".toCharArray());
        TrustManagerFactory trust=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());trust.init(keys);
        context=SSLContext.getInstance("TLS");context.init(key.getKeyManagers(),trust.getTrustManagers(),new SecureRandom());
    }
}
