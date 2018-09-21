package Consensus_Module.dpos_pbft;

import Account_Module.Transaction.Transaction;
import Account_Module.Wallet.WalletUtils;
import Consensus_Module.Bean;
import Consensus_Module.ConsensusConstant;
import DB_Module.block.Block;
import DB_Module.block.Blockchain;
import DB_Module.RocksDBUtils;
import Network_Module.Message;
import Network_Module.NetworkConstant;
import Network_Module.NetworkService;
import org.apache.commons.lang3.ArrayUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 共是出块线程运行类
 * 单例 , 每隔10秒运行一次
 */
public class ConsensusProduceBlockService implements Runnable {

    private static ConsensusProduceBlockService ourInstance = new ConsensusProduceBlockService();

    public static synchronized ConsensusProduceBlockService getInstance() {
        return ourInstance;
    }

    private ConsensusProduceBlockService() {
    }

    //记录出块的顺序
    private int index = 0;
    @Override
    public void run() {
        System.out.println("共识模块:出块服务,10秒运行一次");
        //根据打乱的顺序进行打包交易出块10秒运行1次
        List<Bean> voted21Best = ConsensusService.getInstance().getConsensusRoundList();
        try{
            if(index == voted21Best.size()){
                index = 0;
            }
            Bean producedAddressEntry = voted21Best.get(index);
            index++;
            Blockchain blockchain = new Blockchain(RocksDBUtils.getInstance().getLastBlockHash());
            //获取交易池中的所有交易
            Transaction[] transactions = {};
            ConcurrentHashMap<String , Transaction> txMap = Blockchain.getTxMap();
            for(Map.Entry<String , Transaction> entry : txMap.entrySet()){
                Transaction value = entry.getValue();
                transactions = ArrayUtils.add(transactions, value);
            }
            //打包出块,签名,注意这里若是当前节点不是该producedAddressEntry.getKey()账户地址的话,是没有私钥进行区块签名的
            byte[] publicHashKey = producedAddressEntry.getPublicHashKey();
            String addressByPublicHashKey = WalletUtils.getInstance().getAddressByPublicHashKey(publicHashKey);
            if(addressByPublicHashKey == null){
                //本节点没有该账户的地址
                System.out.println("共识服务:本节点没有该账户的地址,不该本节点出块 !");
                return;
            }
            Block block = blockchain.mineBlock(transactions, addressByPublicHashKey);
            if(block == null){
                //本节点没有该账户的私钥,不能出块
                System.out.println("共识服务:本节点没有该账户的私钥,不能出块!");
                return;
            }
            //开一个子线程,等待收到共识的消息然后持久化区块到自己的磁盘中
            System.out.println("99999999999999----区块id:"+block.getHash());
            ConsensusService.getInstance().addConsensusBlock(block);

            //向所有共识节点广播一个prepare<h,d,s>消息,h是这个区块的高度,d是这个区块的摘要信息(暂时就设置为出块节点的公钥+验证人的地址信息+区块的hashId),s是本节点的签名
            Message prepareMessage = new Message(block.getHeight(),block.getPubKey(),block.getHash(),block.getSignature());
            prepareMessage.setMessageType(NetworkConstant.CONSENSUS_PREPARE_MESSAGE);
            NetworkService.getInstance().broadcastPrepareMessage(prepareMessage);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * 开始广播Commited消息
     * @param message
     */
    public void startBrocadCommitedMessage(Message message){
        //收到prepare消息后，节点开始在内存中累加消息数量(放入一个集合中)，当收到超过f+1(7个)不同节点的prepare消息后，节点进入prepared状态，之后会广播一个commit<h, d, s>消息
        //每个节点收到超过2f+1个不同节点的commit消息后，就认为该区块已经达成一致，进入committed状态，并将其持久化到区块链数据库中
        if(ConsensusService.getInstance().getConsensusState().equals(ConsensusConstant.CONSENSUS_STATE_PREPARED)){
            Message commitedMessage = new Message(message.getLastBlockHeight(),message.getPublicKey(),message.getHashId(),message.getSignature());
            commitedMessage.setMessageType(NetworkConstant.CONSENSUS_COMMITED_MESSAGE);
            //第一次广播设置验证人地址为null
            message.setVerifyAddress(null);
            NetworkService.getInstance().broadcastCommitedMessage(commitedMessage);
        }
    }
}
