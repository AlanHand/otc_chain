package Consensus_Module.dpos_pbft;

import Account_Module.Transaction.Transaction;
import Account_Module.Transaction.UTXOSet;
import Consensus_Module.Bean;
import Consensus_Module.ConsensusConstant;
import DB_Module.block.Block;
import DB_Module.block.Blockchain;
import DB_Module.RocksDBUtils;
import Network_Module.Message;
import Network_Module.NetworkService;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 共识服务类
 * 单例模式
 * 1.投票选出委托人节点
 * 2.对委托人节点进行随机排序出块
 * 3.委托人节点将将交易池的交易打包成区块进行签名,然后广播给所有的共识节点 ,并且广播一个prepare<h,d,s>消息,h是这个区块的高度,d是这个区块的摘要信息,s是本节点的签名
 * 4.收到prepare消息后，节点开始在内存中累加消息数量，当收到超过f+1不同节点的prepare消息后，节点进入prepared状态，之后会广播一个commit<h, d, s>消息
 * 5.每个节点收到超过2f+1个不同节点的commit消息后，就认为该区块已经达成一致，进入committed状态，并将其持久化到区块链数据库中
 * 6.系统在在收到第一个高度为h的block时，启动一个定时器，当定时到期后，如果还没达成一致，就放弃本次共识
 */
public class ConsensusService {

    private static ConsensusService instance = new ConsensusService();

    //本地账户的地址,当只有本地节点为共识节点时才会有这个值
    private String localAddress;

    //默认设置本节点不是共识节点
    private boolean isConsensusNode  = true;

    //设置默认的共识节点总数据为21个
    private int consensusNodeCount = ConsensusConstant.CONSENSUSNODECOUNT;

    //共识状态初始化为 未共识状态
    private String consensusState = ConsensusConstant.CONSENSUS_STATE_UNPREPARED;

    //21个得票最高的共识节点,key为出块人地址,value为出块人得到的投票数
    private List<Map.Entry<byte[], Long>> voted21Best = new ArrayList<>();

    //收到的prepare消息集合,key为验证人地址,value为PrepareMessage
    private Map<String,Message> prepareMessageMap = new ConcurrentHashMap<>();

    //收到的commited消息集合,key为验证人地址,value为PrepareMessage
    private Map<String,Message> commitedMessageMap = new ConcurrentHashMap<>();

    //保存每一轮的共识出块顺序(为了解决内存,只保存最近10的共识顺序的数据)
    private List<Bean> consensusRoundList = new ArrayList<>();

    public synchronized static ConsensusService getInstance() {
        return instance;
    }

    private ConsensusService() {
    }

    /**
     * 共识服务开始运行
     */
    public void start(){

        //让共识出块顺序的线程运行
        System.out.println("共识模块:共识出块顺序的线程运行.210秒执行一次.......");
        ScheduledExecutorService consensusRoundService = Executors.newSingleThreadScheduledExecutor();
        //立即运行出块顺序的线程,以后每隔210秒运行一次,一测OK
        consensusRoundService.scheduleAtFixedRate(ConsensusRoundService.getInstance(),3, 210, TimeUnit.SECONDS);

        //让出块线程运行
        System.out.println("共识模块:出块线程运行.10秒执行一次.......");
        ScheduledExecutorService consensusProduceBlockService = Executors.newSingleThreadScheduledExecutor();
        //立即运行出块顺序的线程,以后每隔210秒运行一次
//        consensusProduceBlockService.scheduleAtFixedRate(ConsensusProduceBlockService.getInstance(),1, 10, TimeUnit.SECONDS);
        consensusProduceBlockService.scheduleAtFixedRate(ConsensusProduceBlockService.getInstance(),5, 10, TimeUnit.SECONDS);

    }

    /**
     * 添加下载区块到区块链中
     * @param block
     */
    public void addDownloadBlock(Block block){
        //验证区块高度
        if(RocksDBUtils.getInstance().getBlocksMap().size()  == block.getHeight()){
            RocksDBUtils.getInstance().putBlock(block);
        }
    }

    /**
     * 添加共识区块到磁盘中
     * @param block
     */
    public void addConsensusBlock(Block block) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                //不断的循环判断共识状态是否已经到了commited状态,到的话则将区块持久化到链中
                while(getConsensusState().equals(ConsensusConstant.CONSENSUS_STATE_COMMITED)){
                    System.out.println("共识模块:commited消息收集饱和,开始持久化区块,收集commited消息数为:"+getCommitedMessageMap().size());
                    //将区块持久化到磁盘中
                    RocksDBUtils.getInstance().putBlock(block);
                    //重新设置共识状态为初始化状态
                    setConsensusState(ConsensusConstant.CONSENSUS_STATE_UNPREPARED);
                    //交易缓存池清除掉与新区块中交易相同的id
                    ConcurrentHashMap<String,Transaction> txMap = Blockchain.getTxMap();
                    Transaction[] transactions = block.getTransactions();
                    for(int i = 0 ; i< transactions.length ; i++){
                        txMap.remove(Hex.encodeHexString(transactions[i].getTxId()));
                    }
                    System.out.println("共识模块:从缓存池中移除掉区块中的交易,剩余缓存池的交易数量:"+Blockchain.getTxMap().size());
                    //广播这个区块给所有的共识节点
                    NetworkService.getInstance().broadcastBlockToAllConsensusNodes(block);
                    //将Prepare消息集合和Commited消息集合清0
                    getPrepareMessageMap().clear();
                    getCommitedMessageMap().clear();
                    System.out.println("共识模块:清空prepare消息:"+getPrepareMessageMap().size()+"----清空commited消息:"+getCommitedMessageMap().size());
                    //更新utxo索引
                    UTXOSet.updateUtxoAndStore(block);
                    //睡眠0.05秒
                    try {
                        Thread.currentThread().sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();

    }
    /**
     * 验证prepare消息里面的签名数据
     * @param message
     */
    public synchronized void processPrepareMessage(Message message) {

        byte[] signature = message.getSignature();
        byte[] publicKey = message.getPublicKey();
        String hashId = message.getHashId();
        long receiveBlockHeight = message.getLastBlockHeight();

        //验证签名数据
        boolean isPass = veriftPrepare(publicKey, hashId, signature);
        //验证区块高度
        if(RocksDBUtils.getInstance().getBlocksMap().size()  == receiveBlockHeight){
            System.out.println("共识模块:ParepareMessage 区块高度验证通过,收到的区块高度:"+receiveBlockHeight+"----本地的区块高度:"+(RocksDBUtils.getInstance().getBlocksMap().size()-1));
            if(isPass){
                if(isConsensusNode){
                    //添加到PrepareMessage消息集合中
                    addPrepareMessage(message);
                    if(this.prepareMessageMap.size() > (((ConsensusConstant.CONSENSUSNODECOUNT-1)/3)+1)){
                        //Prepare消息收到的数量超过共识要求之后则共识状态改为Prepare状态
                        this.setConsensusState(ConsensusConstant.CONSENSUS_STATE_PREPARED);
                        System.out.println("11111111:"+this.prepareMessageMap.size()+"----共识状态:"+getConsensusState());
                        //开始广播commit<h, d, s>消息
                        ConsensusProduceBlockService.getInstance().startBrocadCommitedMessage(message);
                    }else{
                        System.out.println("11111111:"+this.prepareMessageMap.size()+"----共识状态:"+getConsensusState());
                        //再次将PrepareMessage进行广播
                        NetworkService.getInstance().broadcastPrepareMessage(message);
                    }
                }
            }
        }else{
//            System.out.println("测试000000000000000:"+this.prepareMessageMap.size()+"----共识状态:"+getConsensusState());
//            System.out.println("ParepareMessage 区块高度验证失败,收到的区块高度:"+receiveBlockHeight+"----本地的区块高度:"+(RocksDBUtils.getInstance().getBlocksMap().size()-1));
        }
    }
    /**
     * 验证prepare消息里面的签名数据
     * @param message
     */
    public synchronized void processCommitedMessage(Message message){
        byte[] signature = message.getSignature();
        byte[] publicKey = message.getPublicKey();
        String hashId = message.getHashId();
        long lastBlockHeight = message.getLastBlockHeight();

        //验证签名数据
        boolean isPass = veriftPrepare(publicKey, hashId, signature);
        //验证区块高度
        if(RocksDBUtils.getInstance().getBlocksMap().size()  == lastBlockHeight){
            System.out.println("CommitedMessage 区块高度验证通过,收到的区块高度:"+lastBlockHeight+"----本地的区块高度:"+(RocksDBUtils.getInstance().getBlocksMap().size()-1));
            if(isPass){
                if(isConsensusNode){
                    //添加到CommitedMessage消息集合中
                    addCommitedMessage(message);
                    if(this.commitedMessageMap.size() > ((((ConsensusConstant.CONSENSUSNODECOUNT-1)/3) * 2)+1)){
                        //Commited消息收到的数量超过共识要求之后则共识状态改为Commited状态
                        this.setConsensusState(ConsensusConstant.CONSENSUS_STATE_COMMITED);
                        System.out.println("22222222:"+this.commitedMessageMap.size()+"----共识状态:"+getConsensusState());
                    }else{
                        System.out.println("22222222:"+this.commitedMessageMap.size()+"----共识状态:"+getConsensusState());
                        //再次将CommitedMessage进行广播
                        NetworkService.getInstance().broadcastCommitedMessage(message);
                    }
                }
            }
        }else{
            System.out.println("CommitedMessage 区块高度验证失败,收到的区块高度:"+lastBlockHeight+"----本地的区块高度:"+RocksDBUtils.getInstance().getBlocksMap().size());
        }
    }
    /**
     * 验证区块的签名,只需要用公钥对签名数据进行解签,然后与区块的哈希id作比较即可
     * 代码需要测试,这里是对PrepareMessage和CommitedMessage都可以验证的
     * @return
     */
    public boolean veriftPrepare(byte[] publicKey ,String hashId , byte[] signature){
        Security.addProvider(new BouncyCastleProvider());
        ECParameterSpec ecParameters = ECNamedCurveTable.getParameterSpec("secp256k1");
        try{
            KeyFactory keyFactory = KeyFactory.getInstance("ECDSA", BouncyCastleProvider.PROVIDER_NAME);
            Signature ecdsaVerify = Signature.getInstance("SHA256withECDSA", BouncyCastleProvider.PROVIDER_NAME);

            // 使用椭圆曲线 x,y 点去生成公钥Key
            BigInteger x = new BigInteger(1, Arrays.copyOfRange(publicKey, 1, 33));
            BigInteger y = new BigInteger(1, Arrays.copyOfRange(publicKey, 33, 65));
            ECPoint ecPoint = ecParameters.getCurve().createPoint(x, y);

            ECPublicKeySpec keySpec = new ECPublicKeySpec(ecPoint, ecParameters);
            PublicKey pKey = keyFactory.generatePublic(keySpec);
            ecdsaVerify.initVerify(pKey);//设置解密的公钥
            ecdsaVerify.update(hashId.getBytes());//设置解签后需要比对的数据,即区块的hashId
            if (!ecdsaVerify.verify(signature)) {
                return false;
            }else {
                return true;
            }

        }catch (Exception e){
            e.printStackTrace();
        }
        return true;
    }

    public String getLocalAddress() {
        return localAddress;
    }

    public void setLocalAddress(String localAddress) {
        this.localAddress = localAddress;
    }

    public boolean isConsensusNode() {
        return isConsensusNode;
    }

    public void setConsensusNode(boolean consensusNode) {
        isConsensusNode = consensusNode;
    }

    public int getConsensusNodeCount() {
        return consensusNodeCount;
    }

    public void setConsensusNodeCount(int consensusNodeCount) {
        this.consensusNodeCount = consensusNodeCount;
    }

    public String getConsensusState() {
        return consensusState;
    }

    public void setConsensusState(String consensusState) {
        this.consensusState = consensusState;
    }

    public Map<String, Message> getPrepareMessageMap() {
        return prepareMessageMap;
    }

    public void setPrepareMessageMap(Map<String, Message> prepareMessageMap) {
        this.prepareMessageMap = prepareMessageMap;
    }

    public Map<String, Message> getCommitedMessageMap() {
        return commitedMessageMap;
    }

    public void setCommitedMessageMap(Map<String, Message> commitedMessageMap) {
        this.commitedMessageMap = commitedMessageMap;
    }
    public void addPrepareMessage(Message message){
        this.prepareMessageMap.put(message.getVerifyAddress(),message);
    }
    public void addCommitedMessage(Message message){
        this.commitedMessageMap.put(message.getVerifyAddress(),message);
    }
    public void clearPrepareMessage(){
        this.prepareMessageMap.clear();
    }
    public void setVoted21Best(List<Map.Entry<byte[], Long>> voted21Best) {
        this.voted21Best = voted21Best;
    }

    public List<Bean> getConsensusRoundList() {
        return consensusRoundList;
    }

    public void setConsensusRoundList(List<Bean> consensusRoundList) {
        this.consensusRoundList = consensusRoundList;
    }
}
