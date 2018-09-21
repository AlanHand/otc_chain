package Network_Module.NettyServer;

public class ServerTest {

    public static void main(String args[]){
        NettyServer nettyServer = new NettyServer();
        nettyServer.init();
        try {
            nettyServer.start();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
