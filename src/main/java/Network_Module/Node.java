package Network_Module;

/**
 * 节点类
 */
public class Node {
    //节点id,代表网络中一个唯一的节点,用uuid表示
    private String channelId;

    // 1: inNode  输入节点是指别的客户端连接到本地的服务端的节点,服务端根据不同事件的信息返回不通的数据,比如链的高度,区块的大小等等
    // 2: outNode 输出节点是本地做为客户端去连接别的节点,当本地启动的时候就会去同步区块数据
    private int type;
    public final static int IN = 1;
    public final static int OUT = 2;

    // 0 : 表示种子节点(种子节点不一定是出块节点),1表示出块节点(当出块节点未达到数量时也要出块),2表示普通节点(只负责同步区块数据,产生交易,广播交易)
    private int nodeStyle;
    public final static int SEEDNODE = 0;
    public final static int PRODUCERNODE = 1;
    public final static int COMMONNODE = 2;

    private String ip;
    private Integer port;//客户端去连接时的端口
    private Integer severPort = 0;//节点的服务端口

    //0: wait(本地Netty服务接收到一个节点的连接请求,本地创建Netty客户端去连接)
    //1: connecting(本地创建Nett客户端处于正在连接状态,等待处理器响应)
    //2: handshake(本地Netty客户端接收到服务端的数据,可以通信了)
    //3: close关闭状态,仅仅是保存在内存中,目前还没有用到
    public final static int WAIT = 0;
    public final static int CONNECTING = 1;
    public final static int HANDSHAKE = 2;
    public final static int BAD = 3;
    private volatile int status;

    //出块人地址
    private String consensusAddress;

    public Node(String ip, int port){
        this.ip = ip;
        this.port = port;
    }

    public Node(String ip, int port, int type) {
        this.ip = ip;
        this.port = port;
        if (type == Node.OUT) {
            this.severPort = port;
        }
        this.type = type;
    }

    public int getNodeStyle() {
        return nodeStyle;
    }

    public void setNodeStyle(int nodeStyle) {
        this.nodeStyle = nodeStyle;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }


    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public Integer getSeverPort() {
        return severPort;
    }

    public void setSeverPort(Integer severPort) {
        this.severPort = severPort;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getConsensusAddress() {
        return consensusAddress;
    }

    public void setConsensusAddress(String consensusAddress) {
        this.consensusAddress = consensusAddress;
    }
}
