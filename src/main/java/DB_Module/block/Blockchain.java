package DB_Module.block;

import Account_Module.Transaction.AccountConstant;
import Test.Test;
import com.google.common.collect.Maps;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import DB_Module.RocksDBUtils;
import Account_Module.Transaction.TXInput;
import Account_Module.Transaction.TXOutput;
import Account_Module.Transaction.Transaction;

import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 区块链类,其实只包含了最新区块的hash值,目的是为了在转账交易时方便从磁盘检索发送者所有的未花费的交易输出
 * 该区块链类可以实例化多个对象,只需要从RocksDBUtils中获取最新一个区块的hashId即可实例化
 * 功能 :   1.获取缓存交易池
 *          2.存放一笔交易到缓存池中
 *          3.实例化一个区块链对象
 *          4.创建区块链(若链为空的话创建创世交易和创世块)
 *          5.打包交易池记录出块
 *          6.添加一个区块到链中(持久化)
 *          7.获取区块链迭代器,从最新区块迭代查询到第一个创世块
 *          8.从持久化的区块链中查找所有的未花费的交易输出(总的交易输出 - 已经花费的交易输出)
 *          9.从持久化的区块链中查询所有已被花费了的交易输出(被交易输出引用了的即表示已经被花费)
 *          10.从持久化的区块链中根据交易ID查询一笔交易
 *          11.一笔交易的签名和验证
 * @author dingkonghua
 * @date 2018/08/02
 */

public class Blockchain {

    //交易缓存池,key为交易id,value为Transaction交易
    private static ConcurrentHashMap<String,Transaction> txMap;
    //是私链还是公链
    private static String chain_type = "1";//0表示公链,1表示私链,默认是私链
    //最新区块的hashId
    private String lastBlockHash;
    public Blockchain(){}
    public Blockchain(String lastBlockHash) {
        this.lastBlockHash = lastBlockHash;
    }


    /**
     * 获取交易缓存池
     * @return
     */
    public static synchronized ConcurrentHashMap getTxMap(){
        if(txMap == null){
            txMap = new ConcurrentHashMap<>();
        }
        return txMap;
    }

    /**
     * 将一笔交易放入缓存池中
     * @param tx
     */
    public static synchronized  void putTx(Transaction tx){
        //交易id转换为16进制
        String txStrId = Hex.encodeHexString(tx.getTxId());
        if(Blockchain.getTxMap().get(txStrId) == null ){
//            System.out.println("区块链:交易成功加入缓存池中");
            Blockchain.getTxMap().put(txStrId,tx);
        }else{
//            System.out.println("区块链:缓存池中已经存在这笔交易");
        }
    }

    /**
     * 实例化区块链对象
     * @return
     */
    public static Blockchain initBlockchainFromDB() {
        String lastBlockHash = RocksDBUtils.getInstance().getLastBlockHash();
        if (lastBlockHash == null) {
            throw new RuntimeException("区块链:创世区块没有创建");
        }
        return new Blockchain(lastBlockHash);
    }

    /**
     * 创建区块链,若是没有链数据的话那么加载创世文件创建创世块
     * @param address 钱包地址
     * @return
     */
    public static Blockchain createBlockchain(String address) {
        String lastBlockHash = RocksDBUtils.getInstance().getLastBlockHash();
        if (StringUtils.isBlank(lastBlockHash)) {

            //todo 加载genesis.json文件,创建创世交易(3个转账交易设置币的总数,21个投票交易(每个交易对应不同的地址)用于共识)
            // 为什么coinBase交易输入数据中一切都为空,比如上一笔交易的id,上一笔交易输出的索引和上一笔交易接收者的签名?
            // 回答 : 因为Coinbase交易在交易签名和区块验证所有的交易时直接被判断过滤了,然后就直接入到区块链中,并且CoinBase的utxo也直接写到磁盘中,在后面用到这个CoinBase交易的时候也只是用到交易输出的数据,没有用到交易输入的数据,因此CoinBase没有

            //创建创世交易中,其实创世交易和CoinBase交易都是调用CoinBase交易的代码,仅仅是类型不一样而已
            Transaction[] genesisTransactions = loadGenesisTxs();
            if(genesisTransactions != null){
                //创建创世区块并持久化该区块的数据
                Block genesisBlock = Block.newGenesisBlock(genesisTransactions,address);
                lastBlockHash = addGenesisBlockToBlockChain(genesisBlock);
            }

        }
        return new Blockchain(lastBlockHash);
    }

    /**
     * 加载创世交易
     */
    private static Transaction[] loadGenesisTxs() {
        URL resource = new Test().getClass().getResource("/genesis-block.json");
        File file = new File(resource.getPath());
        Long filelength = file.length();
        byte[] filecontent = new byte[filelength.intValue()];
        try {
            FileInputStream in = new FileInputStream(file);
            in.read(filecontent);
            in.close();
            String s = new String(filecontent, "utf-8");
            JSONObject jsonObject = JSONObject.fromObject(s);
            JSONArray txs = (JSONArray) jsonObject.get("txs");

            Transaction[] transactions = {};
            for(int i = 0 ; i < txs.size() ; i ++){
                JSONObject txJson = (JSONObject) txs.get(i);
                String address = txJson.get("address").toString();
                String amount = txJson.get("amount").toString();
                String type = txJson.get("type").toString();

                Transaction transaction = Transaction.newCoinbaseTX(address, "GenesisTrnsaction", Integer.valueOf(amount));
                transaction.setCreateTime(System.currentTimeMillis());
                if(type.equals(AccountConstant.TRANSACTION_TYPE_COINBASE)){
                    transaction.setTxType(AccountConstant.TRANSACTION_TYPE_COINBASE);
                }else if(type.equals(AccountConstant.TRANSACTION_TYPE_VOTED)){
                    transaction.setTxType(AccountConstant.TRANSACTION_TYPE_VOTED);
                }
                transactions = ArrayUtils.add(transactions, transaction);
            }
            return transactions;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 添加创世区块区块到区块链中
     * @param genesisBlock
     * @return
     */
    public static String addGenesisBlockToBlockChain(Block genesisBlock) {
        System.out.println("区块链:添加创世区块");
        String lastBlockHash;
        lastBlockHash = genesisBlock.getHash();
        RocksDBUtils.getInstance().putLastBlockHash(lastBlockHash);
        RocksDBUtils.getInstance().putBlock(genesisBlock);
        return lastBlockHash;
    }

    /**
     * 打包交易出块
     * @param transactions
     */
    public Block mineBlock(Transaction[] transactions, String producerAddress) {
        // 先验证交易记录
        for (Transaction tx : transactions) {
            if (!this.verifyTransactions(tx)) {
                System.out.println("打包交易中出现验证错误");
                throw new RuntimeException("ERROR: Fail to mine DB_Module.block ! Invalid transaction ! ");
            }
        }
        String lastBlockHash = RocksDBUtils.getInstance().getLastBlockHash();
        if (lastBlockHash == null) {
            throw new RuntimeException("ERROR: Fail to get last DB_Module.block hash ! ");
        }

        Block block = Block.newBlock(lastBlockHash, transactions , producerAddress);
        return block;
    }

    /**
     * 区块链迭代器,用于遍历磁盘上所有的区块
     */
    public class BlockchainIterator {
        private String currentBlockHash;
        private BlockchainIterator(String currentBlockHash) {
            this.currentBlockHash = currentBlockHash;
        }

        /**
         * 是否有下一个区块
         */
        public boolean hashNext() {
            if (StringUtils.isBlank(currentBlockHash)) {
                return false;
            }
            Block lastBlock = RocksDBUtils.getInstance().getBlock(currentBlockHash);
            if (lastBlock == null) {
                return false;
            }
            // 创世区块直接放行
            if (lastBlock.getPrevBlockHash().length() == 0) {
                return true;
            }
            return RocksDBUtils.getInstance().getBlock(lastBlock.getPrevBlockHash()) != null;
        }

        /**
         * 返回区块
         */
        public Block next() {
            Block currentBlock = RocksDBUtils.getInstance().getBlock(currentBlockHash);
            if (currentBlock != null) {
                this.currentBlockHash = currentBlock.getPrevBlockHash();
                return currentBlock;
            }
            return null;
        }
    }

    /**
     * 获取区块链迭代器
     * @return
     */
    public BlockchainIterator getBlockchainIterator() {
        return new BlockchainIterator(lastBlockHash);
    }

    /**
     * 查找所有的 unspent transaction outputs
     * 总的交易输出 - 已经花费的交易输出
     * @return
     */
    public Map<String, TXOutput[]> findAllUTXOs() {

        //首先从整个区块链中获取所有已经花费了的交易输出
        Map<String, int[]> allSpentTXOs = this.getAllSpentTXOs();

        Map<String, TXOutput[]> allUTXOs = Maps.newHashMap();
        // 再次遍历所有区块中的交易输出
        for (BlockchainIterator blockchainIterator = this.getBlockchainIterator(); blockchainIterator.hashNext(); ) {
            Block block = blockchainIterator.next();
            for (Transaction transaction : block.getTransactions()) {

                String txId = Hex.encodeHexString(transaction.getTxId());

                int[] spentOutIndexArray = allSpentTXOs.get(txId);
                TXOutput[] txOutputs = transaction.getOutputs();
                for (int outIndex = 0; outIndex < txOutputs.length; outIndex++) {
                    //判断一笔交易中花费了的交易输出索引集合是否包含了该笔交易中交易输出的索引outIndex
                    //包含的话则继续循环下一个索引
                    if (spentOutIndexArray != null && ArrayUtils.contains(spentOutIndexArray, outIndex)) {
                        continue;
                    }
                    TXOutput[] UTXOArray = allUTXOs.get(txId);
                    if (UTXOArray == null) {
                        UTXOArray = new TXOutput[]{txOutputs[outIndex]};
                    } else {
                        UTXOArray = ArrayUtils.add(UTXOArray, txOutputs[outIndex]);
                    }
                    allUTXOs.put(txId, UTXOArray);
                }
            }
        }
        return allUTXOs;
    }

    /**
     * 遍历整个区块链从交易输入中查询区块链中所有已被花费了的交易输出
     * @return 交易ID以及对应的交易输出下标地址
     */
    private Map<String, int[]> getAllSpentTXOs() {
        // 定义TxId ——> spentOutIndex[]，存储交易ID与已被花费的交易输出数组索引值
        Map<String, int[]> spentTXOs = Maps.newHashMap();

        //迭代区块链中的区块
        for (BlockchainIterator blockchainIterator = this.getBlockchainIterator(); blockchainIterator.hashNext(); ) {

            //获取下一个区块
            Block block = blockchainIterator.next();
            //迭代一个区块中的所有交易
            for (Transaction transaction : block.getTransactions()) {
                // 如果是 coinbase 交易，直接跳过，因为它不存在引用前一个区块的交易输入
                if (transaction.isCoinbase()) {
                    continue;
                }
                //迭代一笔交易的所有交易输入
                for (TXInput txInput : transaction.getInputs()) {
                    //获取交易输入中的交易id
                    String inTxId = Hex.encodeHexString(txInput.getTxId());

                    //
                    int[] spentOutIndexArray = spentTXOs.get(inTxId);
                    if (spentOutIndexArray == null) {
                        spentOutIndexArray = new int[]{txInput.getTxOutputIndex()};
                    } else {
                        spentOutIndexArray = ArrayUtils.add(spentOutIndexArray, txInput.getTxOutputIndex());
                    }

                    //将当前交易输入对应的上一笔交易id做为key,当前一笔交易输入对应的上一笔交易输出索引做为value存放在已经花费的交易输出集合中
                    spentTXOs.put(inTxId, spentOutIndexArray);
                }
            }
        }
        return spentTXOs;
    }


    /**
     * 根据交易ID查询一笔交易
     *
     * @param txId 交易ID
     * @return
     */
    private Transaction findTransaction(byte[] txId) {
        for (BlockchainIterator iterator = this.getBlockchainIterator(); iterator.hashNext(); ) {
            Block block = iterator.next();
            for (Transaction tx : block.getTransactions()) {
                if (Arrays.equals(tx.getTxId(), txId)) {
                    return tx;
                }
            }
        }
        throw new RuntimeException("ERROR: Can not found tx by txId ! ");
    }


    /**
     * 进行交易签名
     * 1.首先找到交易输入对应的上一笔交易
     * 2.在对交易中所有的交易输出进行签名(除了CoinBase交易)
     * @param tx         交易数据
     * @param privateKey 私钥
     */
    public void signTransaction(Transaction tx, BCECPrivateKey privateKey) throws Exception {
        // 首先找到交易输入对应的上一笔交易
        Map<String, Transaction> prevTxMap = Maps.newHashMap();
        for (TXInput txInput : tx.getInputs()) {
            //从链中根据交易id获取这笔交易
            Transaction prevTx = this.findTransaction(txInput.getTxId());
            prevTxMap.put(Hex.encodeHexString(txInput.getTxId()), prevTx);
        }
        //签名
        tx.sign(privateKey, prevTxMap);
    }

    /**
     * 交易签名验证
     *
     * @param tx
     */
    public boolean verifyTransactions(Transaction tx) {
        if (tx.isCoinbase()) {
            return true;
        }
        Map<String, Transaction> prevTx = Maps.newHashMap();

        //获取当前交易中所有交易输入对应的上一笔交易集合
        for (TXInput txInput : tx.getInputs()) {
            //查询一笔交易输入的上一笔交易来源
            Transaction transaction = this.findTransaction(txInput.getTxId());
            prevTx.put(Hex.encodeHexString(txInput.getTxId()), transaction);
        }
        try {
            //验证当前这笔交易中所有交易输入的签名
            return tx.verify(prevTx);
        } catch (Exception e) {
            System.out.println("Fail to verify transaction ! transaction invalid ! ");
            e.printStackTrace();
            throw new RuntimeException("Fail to verify transaction ! transaction invalid ! ", e);
        }
    }
}