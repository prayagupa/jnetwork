import java.net.Proxy;
import java.net.ProxySelector;
import java.net.Socket;
import java.io.IOException;
import java.net.URI;
import java.net.InetSocketAddress;

/**
  * @author Prayag Upd
  * @since  20 March 2013
  */

public class Ping{
    static Socket socket = null;
    static boolean reachable = false;
    public static boolean ping(String url, int port){
	try {
    		socket = new Socket(url, port);
		reachable = true;
                System.out.println("reachable");
	} catch(IOException e) {
    		e.printStackTrace();
	}
        return reachable;
    }
    
    public static void setProxy(){
       		System.getProperties().put("http.proxySet", "true");
		System.getProperties().put("http.proxyHost", "10.13.222.215");
		System.getProperties().put("http.proxyPort", "8080");
    }

    public static void main(String[] args){
        setProxy();
        try{
        	Proxy proxy = (Proxy) ProxySelector.getDefault().select(new URI("http://www.yahoo.com/")).iterator().next();
                InetSocketAddress iSAddress= (InetSocketAddress) proxy.address();
	        System.out.println("HTTP_PROXY      : "+ iSAddress.getHostName());
                System.out.println("HTTP_PROXY_PORT : "+iSAddress.getPort());
        }catch(Exception ee){
                ee.printStackTrace();
        }
        System.out.println("Reachable : "+Ping.ping("http://10.13.222.239", 81));
        System.out.println("Reachable : "+Ping.ping("http://127.0.0.1", 80));
    }
}
