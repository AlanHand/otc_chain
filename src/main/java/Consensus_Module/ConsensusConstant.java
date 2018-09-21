package Consensus_Module;

/**
 * 共识中的常量
 */
public class ConsensusConstant {
    //将本机服务端的ip地址,和port搭配启动Netty服务
    public static boolean isConsensusNode = true;

    //共识状态, consensus_state_unprepared 未共识状态 , consensus_state_prepared 接收到足够的prepare消息 , 接收到足够的 commited消息
    public static String CONSENSUS_STATE_UNPREPARED = "consensus_state_unprepared";
    public static String CONSENSUS_STATE_PREPARED = "consensus_state_prepared";
    public static String CONSENSUS_STATE_COMMITED = "consensus_state_commited";

    //创世块产生的时间
    public static long GENESISBLOCKTIME = System.currentTimeMillis();
    public static int CONSENSUSNODECOUNT = 21;

    //区块状态
    public static String BLOCKDOWNLOAD = "download_block";
    public static String BLOCKCONSENSUS = "consensus_block";
}
