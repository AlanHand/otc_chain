package DB_Module;

import Account_Module.Transaction.UTXOSet;
import DB_Module.block.Block;
import DB_Module.block.Blockchain;
import com.google.common.collect.Maps;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import Account_Module.Transaction.TXOutput;
import Account_Module.util.SerializeUtils;

import java.util.Map;

/**
 * 存储工具单例类
 *  功能:
 *      1.存储key为blocks的所有区块数据
 *      2.存储key为chainstate的所有utxo数据
 *      3.获取DB实例
 *      4.获取最新区块的高度
 *      5.保存最新一个区块的hsshId
 *      6.保存最新区块的HashId值,key为1,value为HashId
 *      7.查询最新区块的HashId值,key为1,value为HashId
 *      8.保存一个区块到链中,持久化
 *      9.根据区块的HashId查询区块
 *     10.根据交易的id保存,查询,删除该交易中的UTXO数据
 * @author dingkonghua
 * @date 2018/07/27
 */
public class RocksDBUtils {

    //区块链数据文件,用的是RocketsDB文件数据库
    private static final String DB_FILE = "blockchain.db";
    //区块桶Key,存储区块相关的数据,相当于表名
    private static final String BLOCKS_BUCKET_KEY = "blocks";
    //链状态桶Key(存储所有的交易详情数据)
    private static final String CHAINSTATE_BUCKET_KEY = "chainstate";

    //最新一个区块hash的key值,这里设置为1是因为根据 "1" 这个key就可以从blocksBucket获取最新的区块的hashId,然后就可以迭代整个区块链了
    private static final String LAST_BLOCK_KEY = "l";
    private long lastBlockHeight;
    //所有区块的数据,key为 区块的 hash id 的字节数据 , value为区块的block的详情 字节数据
    private Map<String, byte[]> blocksMap;
    //所有未花费的交易输出数据,key为 交易id , value为交易输出的字节数组数组(utxo)
    private Map<String, byte[]> utxosMap;

    private RocksDB db;
    private volatile static RocksDBUtils instance;
    /**
     * 获取DB模块实例
     */
    public synchronized static RocksDBUtils getInstance() {
        if (instance == null) {
            synchronized (RocksDBUtils.class) {
                if (instance == null) {
                    instance = new RocksDBUtils();
                }
            }
        }
        return instance;
    }

    /**
     * 初始化DB模块
     */
    private RocksDBUtils() {
        openDB();
        initBlockMap();
        initUtxosMap();
    }


    /**
     * 获取区块高度
     * @return
     */
    public synchronized long getlastBlockHeight(){
        if(RocksDBUtils.getInstance().getBlocksMap().size() == 0 || RocksDBUtils.getInstance().getBlocksMap().size() == 1){
            lastBlockHeight = 0;
        }else{
            lastBlockHeight = RocksDBUtils.getInstance().getBlocksMap().size()-1;
        }
        return lastBlockHeight;
    }

    /**
     * 打开数据库
     */
    private void openDB() {
        try {
            db = RocksDB.open(DB_FILE);
        } catch (RocksDBException e) {
            System.out.println("Fail to open db ! ");
            e.printStackTrace();
            throw new RuntimeException("Fail to open db ! ", e);
        }
    }

    /**
     * 初始化 blocks 区块数据桶
     */
    private void initBlockMap() {
        try {
            byte[] blockBucketKey = SerializeUtils.serialize(BLOCKS_BUCKET_KEY);
            byte[] blockBucketBytes = db.get(blockBucketKey);
            if (blockBucketBytes != null) {
                blocksMap = (Map) SerializeUtils.deserialize(blockBucketBytes);
            } else {
                blocksMap = Maps.newHashMap();
                db.put(blockBucketKey, SerializeUtils.serialize(blocksMap));
            }
        } catch (RocksDBException e) {
            System.out.println("Fail to init DB_Module.block bucket ! ");
            e.printStackTrace();
            throw new RuntimeException("Fail to init DB_Module.block bucket ! ", e);
        }
    }

    /**
     * 初始化 blocks 数据桶
     */
    private void initUtxosMap() {
        try {
            byte[] chainstateBucketKey = SerializeUtils.serialize(CHAINSTATE_BUCKET_KEY);
            byte[] chainstateBucketBytes = db.get(chainstateBucketKey);
            if (chainstateBucketBytes != null) {
                utxosMap = (Map) SerializeUtils.deserialize(chainstateBucketBytes);
            } else {
                utxosMap = Maps.newConcurrentMap();
                db.put(chainstateBucketKey, SerializeUtils.serialize(utxosMap));
            }
        } catch (RocksDBException e) {
            System.out.println("Fail to init chainstate bucket ! ");
            e.printStackTrace();
            throw new RuntimeException("Fail to init chainstate bucket ! ", e);
        }
    }

    /**
     * 保存最新一个区块的Hash值
     * @param tipBlockHash
     */
    public void putLastBlockHash(String tipBlockHash) {
        try {
            blocksMap.put(LAST_BLOCK_KEY, SerializeUtils.serialize(tipBlockHash));
            db.put(SerializeUtils.serialize(BLOCKS_BUCKET_KEY), SerializeUtils.serialize(blocksMap));
        } catch (RocksDBException e) {
            System.out.println("Fail to put last DB_Module.block hash ! tipBlockHash=" + tipBlockHash);
            e.printStackTrace();
            throw new RuntimeException("Fail to put last DB_Module.block hash ! tipBlockHash=" + tipBlockHash, e);
        }
    }

    /**
     * 查询最新一个区块的Hash值
     * @return
     */
    public String getLastBlockHash() {
        byte[] lastBlockHashBytes = blocksMap.get(LAST_BLOCK_KEY);
        if (lastBlockHashBytes != null) {
            return (String) SerializeUtils.deserialize(lastBlockHashBytes);
        }
        return "";
    }

    /**
     * 保存区块
     * @param block
     */
    public synchronized void putBlock(Block block) {
        //首先判断区块是否已经存在于链中,只有在不存在的情况下才进行区块的存储
        System.out.println("DB模块:区块id:"+block.getHash());
        if(blocksMap.get(block.getHash()) == null){
            try {
                block.setHeight(blocksMap.size());
                blocksMap.put(block.getHash(), SerializeUtils.serialize(block));
                db.put(SerializeUtils.serialize(BLOCKS_BUCKET_KEY), SerializeUtils.serialize(blocksMap));
                putLastBlockHash(block.getHash());
                System.out.println("DB模块:存储一个区块到链中,区块id:"+block.getHash()+"----存储后区块高度:"+(blocksMap.size()-1));
            } catch (RocksDBException e) {
                System.out.println("DB模块:存放区块失败" + block.getHeight());
                e.printStackTrace();
            }
        }else{
            System.out.println("DB模块:存储一个区块到链中失败,链中已经存在这个区块,当前区块高度:"+(blocksMap.size()-1)+"----收到的区块高度"+block.getHeight());
        }
    }

    /**
     * 根据区块的HashId查询区块,这里必须加入并发访问限制synchronized字段,否则反序列化会出现异常
     * @param blockHash
     * @return
     */
    public synchronized Block getBlock(String blockHash) {
        byte[] blockBytes = blocksMap.get(blockHash);
        if (blockBytes != null) {
            return (Block) SerializeUtils.deserialize(blockBytes);
        }
        return null;
    }

    /**
     * 更新utxo
     */
    public synchronized void updateUtxoIndex(){
        Blockchain blockchain = Blockchain.initBlockchainFromDB();
        UTXOSet utxoSet = new UTXOSet(blockchain);
        //更新utxo
        utxoSet.reIndex();
        System.out.println("Done ! ");
    }
    /**
     * 清空内存中的utxo缓存
     */
    public void cleanChainStateBucket() {
        try {
            utxosMap.clear();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Fail to clear chainstate bucket ! ");
            throw new RuntimeException("Fail to clear chainstate bucket ! ", e);
        }
    }

    /**
     * 保存UTXO数据到缓存中和磁盘中
     * @param key   交易ID
     * @param utxos UTXOs
     */
    public void putUTXOs(String key, TXOutput[] utxos) {
        try {
            utxosMap.put(key, SerializeUtils.serialize(utxos));
            db.put(SerializeUtils.serialize(CHAINSTATE_BUCKET_KEY), SerializeUtils.serialize(utxosMap));
        } catch (Exception e) {
            System.out.println("Fail to put UTXOs into chainstate bucket ! key=" + key);
            e.printStackTrace();
            throw new RuntimeException("Fail to put UTXOs into chainstate bucket ! key=" + key, e);
        }
    }
    /**
     * 保存UTXO数据到缓存中和磁盘中
     * @param key   交易ID
     * @param utxos UTXOs
     */
    public void putUTXOsInMemory(String key, TXOutput[] utxos) {
        try {
            utxosMap.put(key, SerializeUtils.serialize(utxos));
        } catch (Exception e) {
            System.out.println("Fail to put UTXOs into chainstate bucket ! key=" + key);
            e.printStackTrace();
            throw new RuntimeException("Fail to put UTXOs into chainstate bucket ! key=" + key, e);
        }
    }


    /**
     * 从内存中查询UTXO数据
     * @param key 交易ID
     */
    public TXOutput[] getUTXOs(String key) {
        byte[] utxosByte = utxosMap.get(key);
        if (utxosByte != null) {
            return (TXOutput[]) SerializeUtils.deserialize(utxosByte);
        }
        return null;
    }


    /**
     * 从内存和磁盘中删除 UTXO 数据
     * @param key 交易ID
     */
    public void deleteUTXOs(String key) {
        try {
            utxosMap.remove(key);
            db.put(SerializeUtils.serialize(CHAINSTATE_BUCKET_KEY), SerializeUtils.serialize(utxosMap));
        } catch (Exception e) {
            System.out.println("Fail to delete UTXOs by key ! key=" + key);
            e.printStackTrace();
            throw new RuntimeException("Fail to delete UTXOs by key ! key=" + key, e);
        }
    }
    /**
     * 从内存中删除 UTXO 数据
     * @param key 交易ID
     */
    public void deleteUTXOsFromMemory(String key) {
        try {
            utxosMap.remove(key);
        } catch (Exception e) {
            System.out.println("Fail to delete UTXOs by key ! key=" + key);
            e.printStackTrace();
            throw new RuntimeException("Fail to delete UTXOs by key ! key=" + key, e);
        }
    }

    /**
     * 关闭数据库
     */
    public void closeDB() {
        try {
            db.close();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Fail to close db ! ");
            throw new RuntimeException("Fail to close db ! ", e);
        }
    }

    public static String getDbFile() {
        return DB_FILE;
    }

    public static String getBlocksBucketKey() {
        return BLOCKS_BUCKET_KEY;
    }

    public static String getChainstateBucketKey() {
        return CHAINSTATE_BUCKET_KEY;
    }

    public static String getLastBlockKey() {
        return LAST_BLOCK_KEY;
    }

    public static void setInstance(RocksDBUtils instance) {
        RocksDBUtils.instance = instance;
    }

    public RocksDB getDb() {
        return db;
    }

    public void setDb(RocksDB db) {
        this.db = db;
    }

    public Map<String, byte[]> getBlocksMap() {
        return blocksMap;
    }

    public void setBlocksMap(Map<String, byte[]> blocksMap) {
        this.blocksMap = blocksMap;
    }

    public Map<String, byte[]> getUtxosMap() {
        return utxosMap;
    }

    public void setUtxosMap(Map<String, byte[]> utxosMap) {
        this.utxosMap = utxosMap;
    }
}
