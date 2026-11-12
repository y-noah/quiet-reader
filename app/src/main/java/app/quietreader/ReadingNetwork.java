package app.quietreader;

import android.content.Context;
import android.net.*;

/** A retained network handle does not prove that cached reading is online. */
final class ReadingNetwork {
    private ReadingNetwork(){}
    static boolean offline(Context context){
        ConnectivityManager manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(manager==null)return false;
        Network network=manager.getActiveNetwork();
        NetworkCapabilities capabilities=network==null?null:manager.getNetworkCapabilities(network);
        if(!usable(capabilities))return true;
        if(!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN))return false;
        // VPNs can retain INTERNET/VALIDATED after their underlying radios disconnect.
        for(Network physical:manager.getAllNetworks()){
            NetworkCapabilities candidate=manager.getNetworkCapabilities(physical);
            if(usable(candidate)&&!candidate.hasTransport(NetworkCapabilities.TRANSPORT_VPN))return false;
        }
        return true;
    }
    private static boolean usable(NetworkCapabilities capabilities){return capabilities!=null&&capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)&&capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);}
}
