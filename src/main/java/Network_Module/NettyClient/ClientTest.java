package Network_Module.NettyClient;

import Network_Module.Node;

import java.util.UUID;

public class ClientTest {
    public static void main(String args[]){
        new Thread(new Runnable() {
            @Override
            public void run() {
                //创建节点的uuid
//                String uuid = UUID.randomUUID().toString();//转化为String对象
//                uuid = uuid.replace("-", "");
                NettyClient nettyClient = new NettyClient("localhost", 8080);
                nettyClient.start();
            }
        }).start();
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                //创建节点的uuid
////                String uuid = UUID.randomUUID().toString();//转化为String对象
////                uuid = uuid.replace("-", "");
//                NettyClient nettyClient = new NettyClient("localhost", 8080);
//                nettyClient.init();
//            }
//        }).init();
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                //创建节点的uuid
////                String uuid = UUID.randomUUID().toString();//转化为String对象
////                uuid = uuid.replace("-", "");
//                NettyClient nettyClient = new NettyClient("localhost", 8080);
//                nettyClient.init();
//            }
//        }).init();
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                //创建节点的uuid
////                String uuid = UUID.randomUUID().toString();//转化为String对象
////                uuid = uuid.replace("-", "");
//                NettyClient nettyClient = new NettyClient("localhost", 8080);
//                nettyClient.init();
//            }
//        }).init();
    }
}
