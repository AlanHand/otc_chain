package Network_Module;

public class NetworkConstant {
    //将本机服务端的ip地址,和port搭配启动Netty服务
    public static  String serverIp = "192.168.1.207";
    //默认为8080.但是在Netty服务启动之后就会被改变为一个[9000-49152]之间的任意一个数值
    public static  int serverPort = 8088;
    //最大数据节点数
    public static final int MAX_IN_NODES = 10;
    //最大数据节点数
    public static final int MAX_OUT_NODES = 10;


    //节点成功连接之后握手消息
    public static final String HANDSHAKE_SUCCESS_MESSAGE = "0";
    //节点成功连接之后握手失败消息
    public static final String HANDSHAKE_FAIL__MESSAGE = "00";

    //获取新节点消息
    public static final String GET_NEW_CONNECTION_NODE_MESSAGE = "1";
    //获取新节点失败消息
    public static final String GET_NEW_CONNECTION_NODE_FAIL_MESSAGE = "10";
    //获取新节点成功消息
    public static final String GET_NEW_CONNECTION_NODE_SUCCESS_MESSAGE = "11";

    //获取区块高度的消息
    public static final String GET_BLOCK_HEIGHT_MESSAGE = "2";
    //返回区块高度的消息
    public static final String GET_BLOCK_HEIGHT_MESSAGE_SUCCESS = "20";

    //获取一个指定的区块消息
    public static final String GET_BLOCK_MESSAGE = "3";

    //prepare消息,用于区块共识验证
    public static final String CONSENSUS_PREPARE_MESSAGE = "40";
    //commited消息,用于区块共识验证
    public static final String CONSENSUS_COMMITED_MESSAGE = "41";
    //共识节点消息
    public static final String CONSENSUS_NODE_MESSAGE = "5";


}
