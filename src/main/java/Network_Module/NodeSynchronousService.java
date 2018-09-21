package Network_Module;

import Account_Module.Transaction.UTXOSet;
import Account_Module.util.SerializeUtils;
import DB_Module.RocksDBUtils;
import DB_Module.block.Blockchain;
import io.netty.channel.socket.SocketChannel;

import java.util.Map;
import java.util.Set;

/**
 * 专门做同步的线程运行任务类
 * 1,程序启动时由网络模块NetworkService调用启动
 * 2.若是第一次运行(区块高度为0),肯定是从种子节点获取区块高度,而且此时的种子节点就在输出节点集合中,若是第二次运行(区块高度可能为0,也可能不为0),那也是从输出节点中获取区块高度
 * 3.从2中获取的最高高度与当前磁盘保存的最高高度进行比较,差多少就去获取相应的区块
 * 4.将获取的区块持久化(在ClientChannelHandler中实现)
 * 单例模式
 */
public class NodeSynchronousService implements Runnable {

    //本地最新区块高度
    private long localBlockHeight = 0;
    //网络最新区块高度
    private long networkBlockHeight = 0;
    private boolean isSync = false;
    private String channelId;

    private NetworkService networkService = NetworkService.getInstance();

    private static NodeSynchronousService ourInstance = new NodeSynchronousService();

    public synchronized static NodeSynchronousService getInstance() {
        return ourInstance;
    }

    private NodeSynchronousService() { }

    @Override
    public void run() {

        try {
            System.out.println("网络模块:同步区块线程:等待本地服务启动,加载已连接的输出节点");
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        //获取本地区块高度
        localBlockHeight = RocksDBUtils.getInstance().getlastBlockHeight();

        //2.若是第一次运行(区块高度为0),肯定是从种子节点获取区块高度,而且此时的种子节点就在输出节点集合中,若是第二次运行(区块高度可能为0,也可能不为0),那也是从输出节点中获取区块高度
        System.out.println("网络模块:同步区块线程:同步区块线程运行 , 监测是否同步区块,当前区块高度:"+localBlockHeight);
        Map<String, Node> outNodesMap = networkService.getOutNodesMap();
        System.out.println("网络模块:同步区块线程:"+"输出节点数:"+outNodesMap.size());
        Set<String> keys = outNodesMap.keySet();
        //封装获取网络区块高度的请求消息
        Message message = new Message(NetworkConstant.GET_BLOCK_HEIGHT_MESSAGE);
        byte[] bytes = SerializeUtils.serialize(message);
        for(String channelId : keys){
            System.out.println("网络模块:同步区块线程:向输出节点"+outNodesMap.get(channelId).getIp()+"获取网络区块的最新高度......");
            NioChannelMap.get(channelId).writeAndFlush(bytes);
        }

        //当前线程睡眠5秒,等待网络节点获取最高高度
        try {
            System.out.println("网络模块:同步区块线程:等待更新最新的区块高度");
            Thread.currentThread().sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        //再一次获取本地区块高度
        localBlockHeight = RocksDBUtils.getInstance().getlastBlockHeight();

        System.out.println("网络模块:同步区块线程:同步区块线程运行 , 监测是否同步区块,当前区块高度:"+localBlockHeight+"----网络区块高度:"+networkBlockHeight);

        //若是本地高度小于网络高度,则开始下载区块
        while(localBlockHeight < networkBlockHeight){
            System.out.println("网络模块:同步区块线程:正在同步区块,当前区块高度:"+localBlockHeight+"----网络区块高度:"+networkBlockHeight);
            isSync = true;
            //从连接的输出节点中下载缺少的区块次数(这里先暂时定义为一次获取一个
            //todo 若是区块差很大的应该做不同的同步更新下载)
            long count = networkBlockHeight-localBlockHeight;
            String channelId = getChannelId();
            if(channelId == null){
                System.out.println("网络模块:同步区块线程:同步通道id为null----"+"本地区块高度:"+localBlockHeight+"----网络区块高度:"+networkBlockHeight);
            }else{
                SocketChannel socketChannel = NioChannelMap.get(channelId);
                Message getBlockMessage = new Message(NetworkConstant.GET_BLOCK_MESSAGE);
                getBlockMessage.setCount(count);
                getBlockMessage.setLastBlockHeight(networkBlockHeight);
                byte[] getBlockBytes = SerializeUtils.serialize(getBlockMessage);
                socketChannel.writeAndFlush(getBlockBytes);

                try {
                    Thread.currentThread().sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                //更新本地区块高度
                localBlockHeight = RocksDBUtils.getInstance().getlastBlockHeight();
            }
        }
        //当网络同步程序完成区块的更新之后更新内存utxo池
        RocksDBUtils.getInstance().updateUtxoIndex();
    }

    public long getLocalBlockHeight() {
        return localBlockHeight;
    }

    public void setLocalBlockHeight(long localBlockHeight) {
        this.localBlockHeight = localBlockHeight;
    }

    /**
     * 设置最新的网络区块高度,有客户端ClientChannelHandler的GET_BLOCK_HEIGHT_MESSAGE_SUCCESS消息处理程序处理
     * @param height
     */
    public synchronized void setNetworkBlockHeight(long height){
        if(networkBlockHeight < height)
        networkBlockHeight = height;
    }
    public synchronized void setChannelId(String channelId){
        this.channelId = channelId;
    }
    public String getChannelId(){
        if(channelId.equals("") || channelId == null){
            return null;
        }else{
            return channelId;
        }
    }

    public boolean isSync() {
        return isSync;
    }

    public void setSync(boolean sync) {
        isSync = sync;
    }
}
