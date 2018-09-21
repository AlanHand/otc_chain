package Network_Module;

import Account_Module.Transaction.Transaction;
import Account_Module.Wallet.WalletUtils;
import Account_Module.util.SerializeUtils;
import Consensus_Module.Bean;
import DB_Module.block.Block;
import Network_Module.NettyClient.NettyClient;
import Network_Module.NettyServer.NettyServer;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 网络服务模块实现类,单利模式
 * 1.加载种子节点
 * 2.启动Netty服务端,根据默认设置的种子节点的ip和端口号启动相应的Netty客户端去连接种子节点
 * 3.启动网络发现节点程序,定时更细自己可连接的节点
 * 4.同步区块,只有同步完成之后才会进行下面的操作
 * 5.广播交易代码开发完成,未测试
 * 6.私链运行:若是全网只有1个节点在运行的话那么就是私链,不用发送数据到别的节点,仅仅是本地创建默认的21个账户进行共识
 * 7.公链运行:若是没有21个节点,那么全网将不接收转账交易,也进行出块,知道共识节点达到21个才行
 */
public class NetworkService {

    private static NetworkService instance = new NetworkService();
    private NetworkService() {}
    public synchronized static NetworkService getInstance(){
        return instance;
    }
    private NettyServer nettyServer;

    //输入节点集合(连接进来的客户端节点),最大数不能超过10个,已经连接成功,key为socket通道id,这些节点用于向别的节点返回同步的数据
    private Map<String, Node> inNodesMap = new ConcurrentHashMap<>();
    //输出节点集合(自己作为客户端去连接别的节点的集合),最大数不能超过10个,已经连接成功,key为socket通道id,这些节点用于下载同步区块的通信
    private Map<String, Node> outNodesMap = new ConcurrentHashMap<>();
    //种子节点
    private List<Node> seedsList = new ArrayList<>();
    //可以去连接的服务节点,最多10个,key为节点ip
    private Map<String,Node> needConnServerNodesMap = new ConcurrentHashMap<>();
    //共识节点,key为节点的账户地址,value为节点信息
    private Map<String,Node> consensusNodesMap = new ConcurrentHashMap<>();

    /**
     * 网络服务初始化
     * 1.加载种子节点
     * 2.启动Netty服务
     * 3.新节点发现任务启动
     * 4.开始同步区块任务启动
     */
    public void init(){

        //1.加载种子节点,默认应该写在配置文件中,但是这里直接写在一个常量类中
        //判断一下本机是否是种子节点
        InetAddress addr = null;
        try {
            addr = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        String ip = addr.getHostAddress(); //获取本机ip
        System.out.println("NetWork:本机ip地址:"+ip);
        NetworkConstant.serverIp = ip;
        NetworkConstant.serverPort = new Random().nextInt(40152)+9000;
        //判断本机是否是种子节点
        if(ip.equals(NetworkConstant.serverIp)){
            Node node = new Node(NetworkConstant.serverIp, NetworkConstant.serverPort);
            node.setSeverPort(NetworkConstant.serverPort);
            //设置为种子节点
            node.setNodeStyle(Node.SEEDNODE);
            seedsList.add(node);

        }else{
            //todo 去配置文件中加载种子节点和端口号
        }

        //2.子线程启动Netty服务端
        new Thread(new Runnable() {
            @Override
            public void run() {

                nettyServer = new NettyServer();
                try {
                    //服务端初始化
                    nettyServer.init();
                    //服务端启动
                    nettyServer.start();

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        //3.启动网络发现节点程序,定时更新自己的可连接节点,一测通过
        startNodeDsicoverTask();

        //4.同步区块任务,一测通过
        startSyncBlockTask();

    }
    /**
     * 新节点发现任务启动 todo 网络模块启动时需要调用
     */
    private void startNodeDsicoverTask(){
        System.out.println("NetWork:节点发现程序启动.5秒钟执行一次.......");
        //3.启动网络发现节点程序,定时更新自己的可连接节点,这里要更改为定时运行
        ScheduledExecutorService nodeDiscoverService = Executors.newSingleThreadScheduledExecutor();
        //3秒之后开始运行节点发现任务,以后每隔5秒运行一次
        nodeDiscoverService.scheduleAtFixedRate(NodeDiscoverService.getInstance(),3, 5, TimeUnit.SECONDS);
    }
    /**
     * 开始同步区块任务启动
     */
    private void startSyncBlockTask(){
        //一测OK
        ScheduledExecutorService synchronousService = Executors.newSingleThreadScheduledExecutor();
        System.out.println("网络模块:同步区块程序启动.......");
        synchronousService.submit(NodeSynchronousService.getInstance());
    }
    /**
     * 向所有的共识节点服务端广播prepare<h,d,s>消息,h是这个区块的高度,d是这个区块的摘要信息(暂时就设置为出块节点的地址信息+验证人的地址信息+区块的hashId),s是本节点的签名
     */
    public void broadcastPrepareMessage(Message prepareMessage){
        Map<String, Node> consensusNodeMap = getConsensusNodesMap();
        for(Map.Entry<String,Node> entry : consensusNodeMap.entrySet()){
            String channelId = entry.getValue().getChannelId();
            //排除给自己发送prepare消息
            if(WalletUtils.getInstance().getAddressByPublicKey(prepareMessage.getPublicKey()).equals(entry.getValue().getConsensusAddress())){
                continue;
            }
            prepareMessage.setVerifyAddress(entry.getKey());
            byte[] bytes = SerializeUtils.serialize(prepareMessage);
            if(NioChannelMap.get(channelId) != null){
                NioChannelMap.get(channelId).writeAndFlush(bytes);
            }
        }
    }

    /**
     * 广播Commited消息到所有的共识节点(除自己)
     * @param commitedMessage
     */
    public void broadcastCommitedMessage(Message commitedMessage) {
        Map<String, Node> consensusNodeMap = getConsensusNodesMap();
        for(Map.Entry<String,Node> entry : consensusNodeMap.entrySet()){
            String channelId = entry.getValue().getChannelId();
            //排除给自己发送prepare消息
            if(WalletUtils.getInstance().getAddressByPublicKey(commitedMessage.getPublicKey()).equals(entry.getValue().getConsensusAddress())){
                continue;
            }
            commitedMessage.setVerifyAddress(entry.getKey());
            byte[] bytes = SerializeUtils.serialize(commitedMessage);
            NioChannelMap.get(channelId).writeAndFlush(bytes);
        }
    }
    /**
     * 向所有的服务端广播自己是共识节点消息,todo 节点刚运行的时候需要调用   考虑:到底需不需要广播自己是共识节点  需要,因为要让所有的共识节点知道21个共识地址和共识地址所在的ip地址
     */
    public void broadcastConsensusNodeMessage(byte[] publicHashKey){
        Message message = new Message(NetworkConstant.CONSENSUS_NODE_MESSAGE,NetworkConstant.serverIp,NetworkConstant.serverPort);
//        System.out.println("网络模块:广播自己是共识节点的消息,地址:"+WalletUtils.getInstance().getAddressByPublicHashKey(publicHashKey)+"----服务端ip:"+NetworkConstant.serverIp);
        message.setPublicKey(publicHashKey);
        message.setConsensusAddress(WalletUtils.getInstance().getAddressByPublicHashKey(publicHashKey));
        Map<String, Node> outNodesMap = getOutNodesMap();
        for(Map.Entry<String,Node> entry : outNodesMap.entrySet()){
            String channelId = entry.getKey();
            byte[] bytes = SerializeUtils.serialize(message);
            NioChannelMap.get(channelId).writeAndFlush(bytes);
        }

    }
    /**
     * 广播区块,只广播到输出节点中(可能输出节点中包含种子节点,后期优化)
     */
    public void broadcastBlock(Block block){
        Map<String, Node> outNodesMap = getOutNodesMap();
        for(Map.Entry<String,Node> entry : outNodesMap.entrySet()){
            String channelId = entry.getKey();
            byte[] bytes = SerializeUtils.serialize(block);
            NioChannelMap.get(channelId).writeAndFlush(bytes);
        }
    }

    /**
     * 广播区块到所有的共识节点(根据ip地址除自己)
     * @param block
     */
    public void broadcastBlockToAllConsensusNodes(Block block) {
        Map<String, Node> consensusNodesMap = getConsensusNodesMap();
        for(Map.Entry<String,Node> entry : consensusNodesMap.entrySet()){

            //根据ip地址排除掉自己
            if(entry.getValue().getIp().equals(NetworkConstant.serverIp)){
                continue;
            }
            String channelId = entry.getKey();
            byte[] bytes = SerializeUtils.serialize(block);
            NioChannelMap.get(channelId).writeAndFlush(bytes);
        }
    }
    /**
     * 广播交易到所有的输入和输出节点 todo 交易创建后待交易加入到本地交易池之后在进行广播调用
     */
    public void broadcastTransaction(Transaction tx){

        System.out.println("网络模块:广播交易到所有的输入节点和输出节点");

        Map<String, Node> outNodesMap = getOutNodesMap();
        for(Map.Entry<String,Node> entry : outNodesMap.entrySet()){
            String channelId = entry.getKey();
            byte[] bytes = SerializeUtils.serialize(tx);
            NioChannelMap.get(channelId).writeAndFlush(bytes);
            System.out.println("网络模块:广播交易到输出节点,节点ip:"+entry.getValue().getIp());
        }

        Map<String, Node> inNodesMap = getInNodesMap();
        for(Map.Entry<String,Node> entry : inNodesMap.entrySet()){
            String channelId = entry.getKey();
            byte[] bytes = SerializeUtils.serialize(tx);
            NioChannelMap.get(channelId).writeAndFlush(bytes);
            System.out.println("网络模块:广播交易到输入节点,节点ip:"+entry.getValue().getIp());
        }
    }

    /**
     * 广播打乱的共识节点顺序到所有的共识节点(除自己)
     * @param voted21Best
     */
    public void brocadVoted21Best(byte[] publicHashKey , List<Bean> voted21Best) {
        Map<String, Node> consensusNodeMap = getConsensusNodesMap();

        for(Map.Entry<String,Node> entry : consensusNodeMap.entrySet()){
            String channelId = entry.getValue().getChannelId();
            //排除给自己发送prepare消息
            if(WalletUtils.getInstance().getAddressByPublicHashKey(publicHashKey).equals(entry.getValue().getConsensusAddress())){
                continue;
            }
            System.out.println("网络模块:广播共识顺序给共识节点ip:"+entry.getValue().getIp()+"----节点地址:"+entry.getKey());

            byte[] bytes = SerializeUtils.serialize(voted21Best);
            NioChannelMap.get(channelId).writeAndFlush(bytes);
        }
    }
    /**
     * 获取种子节点集合
     * @return
     */
    public List<Node> getSeedsList(){
        return seedsList;
    }

    /**
     * 添加种子节点
     * @param node
     * @return
     */
    public Node addSeedNode(Node node){
        for(Node seedNode : seedsList){
            if(seedNode.getIp().equals(node.getIp())){
                return null;
            }
        }
        seedsList.add(node);
        return node;
    }
    /**
     * 根据通道id获取输入节点
     * @param channelId
     * @return
     */
    public Node getInNodeByChannelId(String channelId){
        Node node = inNodesMap.get(channelId);
        return node == null ? null : node;
    }
    /**
     * 添加输入节点
     * @param node
     */
    public boolean addInNode(Node node){
        if(inNodesMap.size() >= NetworkConstant.MAX_IN_NODES){
            return false;
        }
        if(!inNodesMap.containsKey(node.getChannelId())){
            //判断该输入节点是否已经连入到本节点的输入节点集合了
            for(Map.Entry<String,Node> entry : inNodesMap.entrySet()){
                if(entry.getValue().getIp().equals(node.getIp())){
                    return false;
                }
            }
            inNodesMap.put(node.getChannelId(),node);
            return true;
        }
        return false;
    }
    /**
     * 移除输入节点
     * @param channelId
     */
    public void removeInNodeByChannelId(String channelId){
        if(inNodesMap.containsKey(channelId)){
            inNodesMap.remove(channelId);
        }
    }
    /**
     * 获取输出节点
     * @param channelId
     * @return
     */
    public Node getOutNodeByChannelId(String channelId){
        Node node = outNodesMap.get(channelId);
        return node == null ? null : node;
    }
    /**
     * 添加输出节点
     * @param node
     */
    public boolean addOutNode(Node node){
        if(outNodesMap.size() >= NetworkConstant.MAX_OUT_NODES){
            return false;
        }
        if(!outNodesMap.containsKey(node.getChannelId())){
            //判断该输出节点是否已经在本节点的输出节点集合了
            for(Map.Entry<String,Node> entry : outNodesMap.entrySet()){
                if(entry.getValue().getIp().equals(node.getIp())){
                    return false;
                }
            }
            outNodesMap.put(node.getChannelId(),node);
            return true;
        }else{
            return false;
        }
    }
    /**
     * 移除输出节点
     * @param channelId
     */
    public void removeOutNodeByChannelId(String channelId){
        if(outNodesMap.containsKey(channelId)){
            NioChannelMap.remove(channelId);
            outNodesMap.remove(channelId);
        }
    }
    /**
     * 连接种子节点
     * @param node
     */
    public void connOutNode(Node node){
        new Thread(new Runnable() {
            @Override
            public void run() {
//                System.out.println("网络模块:连接一个新节点,ip地址:"+node.getIp()+",端口号:"+node.getSeverPort());
                //判断新连接的ip地址是否已经存在于输出节点结合中
                Map<String, Node> outNodesMap = getOutNodesMap();
                for(Map.Entry<String,Node> entry : outNodesMap.entrySet()){
                    if(entry.getValue().getIp().equals(node.getIp())){
//                        System.out.println("网络模块:连接一个新节点,ip地址:"+node.getIp()+",但该ip地址已经连接过");
                        return ;
                    }
                }
                NettyClient nettyClient = new NettyClient(node.getIp(), node.getSeverPort());
                nettyClient.start();
            }
        }).start();
    }
    /**
     * 从输出节点集合中随机选择一个节点返回
     * @return
     */
    public Node getRandomOutNode(){

        Map<String, Node> outNodesMap = getOutNodesMap();
        if(outNodesMap.size() == 0){
            return null;
        }else{
            Collection<Node> values = outNodesMap.values();
            Object[] objects = values.toArray();
            Node node = (Node)objects[new Random().nextInt(objects.length)];
            return node;
        }
    }
    /**
     * 从输入节点集合中随机选择一个节点返回
     * @return
     */
    public Node getRandomInNode(){

        Map<String, Node> inNodesMap = getInNodesMap();
        if(inNodesMap.size() == 0){
            return null;
        }else{
            Collection<Node> values = inNodesMap.values();
            Object[] objects = values.toArray();
            Node node = (Node)objects[new Random().nextInt(objects.length)];
            return node;
        }
    }

    /**
     * 添加一个可以连接的服务端节点
     * @param node
     */
    public void addNeedConnServerNode(Node node){
        if(needConnServerNodesMap.size() >= 10 || needConnServerNodesMap.containsKey(node.getIp())){
            return;
        }else{
            needConnServerNodesMap.put(node.getIp(),node);
        }
    }

    /**
     * 添加一个共识节点
     * @return
     */
    public void addConsensusNode(String consensusAddress , Node node){
        System.out.println("网络模块:添加共识节点信息,节点ip:"+node.getIp()+"----共识地址:"+node.getConsensusAddress());
        this.consensusNodesMap.put(consensusAddress,node);
    }

    /**
     * 删除一个共识节点
     * @return
     */
    public void deleteConsensusNode(String consensusAddress){
        this.consensusNodesMap.remove(consensusAddress);
    }

    /**
     * 清楚所有的共识节点
     * @return
     */
    public void clearConsensusNode(){
        this.consensusNodesMap.clear();
    }

    public Map<String, Node> getInNodesMap() {
        return inNodesMap;
    }

    public void setInNodesMap(Map<String, Node> inNodesMap) {
        this.inNodesMap = inNodesMap;
    }

    public Map<String, Node> getOutNodesMap() {
        return outNodesMap;
    }

    public void setOutNodesMap(Map<String, Node> outNodesMap) {
        this.outNodesMap = outNodesMap;
    }

    public void setSeedsList(List<Node> seedsList) {
        this.seedsList = seedsList;
    }

    public Map<String, Node> getNeedConnServerNodesMap() {
        return needConnServerNodesMap;
    }

    public void setNeedConnServerNodesMap(Map<String, Node> needConnServerNodesMap) {
        this.needConnServerNodesMap = needConnServerNodesMap;
    }

    public Map<String, Node> getConsensusNodesMap() {
        return consensusNodesMap;
    }

    public void setConsensusNodesMap(Map<String, Node> consensusNodesMap) {
        this.consensusNodesMap = consensusNodesMap;
    }



}
