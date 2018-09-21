package Network_Module;

import Account_Module.util.SerializeUtils;
import io.netty.channel.socket.SocketChannel;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 节点发现处理器,每隔一定时间运行,有待测试
 */
public class NodeDiscoverService implements Runnable {

    private static NodeDiscoverService instance = new NodeDiscoverService();
    private NodeDiscoverService() {}
    public synchronized static NodeDiscoverService getInstance(){
        return instance;
    }

    private  NetworkService networkService = NetworkService.getInstance();
    @Override
    public void run() {
        try{
            //1.判断自己的输出节点是否饱和,只要未饱和就从输入节点和可连接节点中获取来填充
            Map<String, Node> outNodesMap = networkService.getOutNodesMap();
            if(outNodesMap.size() < NetworkConstant.MAX_OUT_NODES){
//                System.out.println("网络模块:节点发现程序:本节点连接的输出节点数:"+outNodesMap.size()+"----可连接的最大输出节点数:"+NetworkConstant.MAX_OUT_NODES);
                //2.先从输出节点获取可以连接的节点,若是自己保存的输出连接节点数(非种子节点)达到饱和,则将种子节点移除,去获取一个新的非种子节点
                //将输入节点当做服务节点去连接
                Map<String, Node> inNodesMap = networkService.getInNodesMap();
                Map<String, Node> needConnServerNodesMap = networkService.getNeedConnServerNodesMap();
                if(inNodesMap.size() != 0 ){
//                    System.out.println("网络模块:节点发现程序:输出节点数小于最大连接数,从输入节点集合中获取一个节点去连接");

                    //从输入节点中随机选择一个节点当做服务节点去连接
                    Collection<Node> values = inNodesMap.values();
                    Object[] objects = values.toArray();
                    Node node = (Node)objects[new Random().nextInt(objects.length)];

                    //去连接一个新的输出节点
                    networkService.connOutNode(node);

                }else if(needConnServerNodesMap.size() != 0){
//                    System.out.println("网络模块:节点发现程序:输出节点数小于最大连接数,从可连接服务节点节点集合中获取一个节点去连接");
                    //可连接节点中获取
                    Collection<Node> values = needConnServerNodesMap.values();
                    Object[] objects = values.toArray();
                    Node node = (Node)objects[new Random().nextInt(objects.length)];

                    //去连接一个新的输出节点
                    networkService.connOutNode(node);

                }else{
                    System.out.println("网络模块:节点发现程序:输出节点数小于最大连接数,并且输入节点与了连接节点数都为0,输出连接数:"+outNodesMap.size()+",获取一个新节点的请求");
                    Collection<Node> values = outNodesMap.values();
                    Object[] objects = values.toArray();
                    Node node = (Node)objects[new Random().nextInt(objects.length)];
                    SocketChannel socketChannel = NioChannelMap.get(node.getChannelId());

                    //向此输出结点发送获取一个新的可连接的节点事件信息
                    Message message = new Message(NetworkConstant.GET_NEW_CONNECTION_NODE_MESSAGE);
                    //设置本机的ip地址,防止服务端返回来的是本机的ip
                    message.setIp(NetworkConstant.serverIp);
                    byte[] bytes = SerializeUtils.serialize(message);
                    //发送
                    socketChannel.writeAndFlush(bytes);
                }
                //当输出节点等于饱和时,需要将输出节点中的种子节点去掉,减小种子节点的连接压力
            }else if(outNodesMap.size() == NetworkConstant.MAX_OUT_NODES){
                List<Node> seedsList = networkService.getSeedsList();

                Map<String,Node> copyMap = new ConcurrentHashMap<>();
                copyMap.putAll(outNodesMap);
                for(Map.Entry<String,Node> entry : copyMap.entrySet()){
                    for(Node seedNode : seedsList){
                        //如果种子节点的ip和输出节点的ip一样则在输出节点中删除种子节点,在通道集合中删除与种子节点的连接scoketChannel
                        if(seedNode.getIp().equals(entry.getValue().getIp()));
                        String channalId = entry.getKey();
                        networkService.removeOutNodeByChannelId(channalId);
                        NioChannelMap.remove(channalId);
                    }
                }
            }
        }catch (Exception e){
            System.out.println("网络模块:节点发现程序:网络发现节点程序出现异常!");
            e.printStackTrace();
        }

    }
}
