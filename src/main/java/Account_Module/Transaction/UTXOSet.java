package Account_Module.Transaction;

import Account_Module.Wallet.WalletUtils;
import DB_Module.block.Block;
import DB_Module.block.Blockchain;
import com.google.common.collect.Maps;
import lombok.Synchronized;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.ArrayUtils;
import DB_Module.RocksDBUtils;
import Account_Module.util.SerializeUtils;

import java.util.Map;

/**
 * 未被花费的交易输出池
 *
 * @author dingkonghua
 * @date 2018/07/27
 */

public class UTXOSet {

    private Blockchain blockchain;

    public UTXOSet(){}
    public UTXOSet(Blockchain blockchain){
        this.blockchain = blockchain;
    }
    /**
     * 从磁盘上寻找某个地址的能够花费的交易输出(未花费的交易输出)
     *
     * @param pubKeyHash 钱包公钥Hash
     * @param amount     需要花费的金额
     */
    public SpendableOutputResult findSpendableOutputsByType(byte[] pubKeyHash, int amount , String type) {
        //保存当前发送者用于转账的utxo,key为交易id,value为指向上一笔的交易输出索引集合
        Map<String, int[]> unspentOuts = Maps.newHashMap();
        int accumulated = 0;
        //从内存缓存utxo池中获取所有的utxo,key为交易id,value为所有的交易输出数据
        Map<String, byte[]> utxosMap = RocksDBUtils.getInstance().getUtxosMap();

        for (Map.Entry<String, byte[]> entry : utxosMap.entrySet()) {
            String txId = entry.getKey();
            //获取一个交易中的输出集合
            TXOutput[] txOutputs = (TXOutput[]) SerializeUtils.deserialize(entry.getValue());
            //遍历交易输出集合
            for (int outId = 0; outId < txOutputs.length; outId++) {

                TXOutput txOutput = txOutputs[outId];

                //判断交易输出的类型,只有符合的交易输出才会被统计,但是CoinBase交易也可以作为转账交易
                if(type.equals(txOutput.getTxOutputType()) || AccountConstant.TRANSACTION_TYPE_COINBASE.equals(txOutput.getTxOutputType())){

                    //判断该交易输出的公钥是否是当前交易的发送者公钥,是的话表示这个交易输出是当前发送者未花费的交易输出
                    if (txOutput.isLockedWithKey(pubKeyHash) && accumulated < amount) {
                        accumulated += txOutput.getValue();

                        int[] outIds = unspentOuts.get(txId);
                        if (outIds == null) {
                            outIds = new int[]{outId};
                        } else {
                            outIds = ArrayUtils.add(outIds, outId);
                        }
                        unspentOuts.put(txId, outIds);
                        if (accumulated >= amount) {
                            break;
                        }
                    }
                }
            }
            if (accumulated >= amount) {
                break;
            }
        }
        return new SpendableOutputResult(accumulated, unspentOuts);
    }


    /**
     * 查找钱包地址对应的所有UTXO
     * @param pubKeyHash 钱包公钥Hash
     * @return
     */
    public TXOutput[] findUTXOs(byte[] pubKeyHash) {
        TXOutput[] utxos = {};
        Map<String, byte[]> chainstateBucket = RocksDBUtils.getInstance().getUtxosMap();
        if (chainstateBucket.isEmpty()) {
            return utxos;
        }
        for (byte[] value : chainstateBucket.values()) {
            TXOutput[] txOutputs = (TXOutput[]) SerializeUtils.deserialize(value);
            for (TXOutput txOutput : txOutputs) {
                if (txOutput.isLockedWithKey(pubKeyHash)) {
                    utxos = ArrayUtils.add(utxos, txOutput);
                }
            }
        }
        return utxos;
    }

    /**
     * 重建 UTXO 池索引,先从内存中清除未花费的交易输出池 , 然后从区块数据中将未花费的交易输出重新写入文件
     */
    @Synchronized
    public void reIndex() {
        System.out.println("Start to reIndex UTXO set !");
        RocksDBUtils.getInstance().cleanChainStateBucket();
        //获取所有未花费的交易输出
        Map<String, TXOutput[]> allUTXOs = blockchain.findAllUTXOs();
        for (Map.Entry<String, TXOutput[]> entry : allUTXOs.entrySet()) {
            RocksDBUtils.getInstance().putUTXOs(entry.getKey(), entry.getValue());
        }
        System.out.println("ReIndex UTXO set finished ! ");
    }

    /**
     * 更新UTXO池
     * 当一个新的区块产生时，需要去做两件事情：
     * 1）从UTXO池中移除花费掉了的交易输出；
     * 2）保存新的未花费交易输出；
     *
     * @param tipBlock 最新的区块
     */
    @Synchronized
    public static void updateUtxoAndStore(Block tipBlock) {
        if (tipBlock == null) {
            System.out.println("Fail to updateUtxoAndStore UTXO set ! tipBlock is null !");
            throw new RuntimeException("Fail to updateUtxoAndStore UTXO set ! ");
        }
        for (Transaction transaction : tipBlock.getTransactions()) {

            // 根据交易输入排查出剩余未被使用的交易输出
            if (!transaction.isCoinbase()) {

                //遍历该笔交易中所有的交易输入,与未花费的交易输出做对比(比对成功表示交易输出已经被花费) , 剔除掉已经花费的交易输出
                for (TXInput txInput : transaction.getInputs()) {
                    // 余下未被使用的交易输出
                    TXOutput[] remainderUTXOs = {};
                    String txId = Hex.encodeHexString(txInput.getTxId());
                    //获取这笔交易对应的上一笔交易输出
                    TXOutput[] txOutputs = RocksDBUtils.getInstance().getUTXOs(txId);

                    if (txOutputs == null) {
                        continue;
                    }
                    //从一个交易输出的集合中剔除掉交易输入所包含的索引
                    for (int outIndex = 0; outIndex < txOutputs.length; outIndex++) {
                        if (outIndex != txInput.getTxOutputIndex()) {
                            remainderUTXOs = ArrayUtils.add(remainderUTXOs, txOutputs[outIndex]);
                        }
                    }

                    // 没有剩余则删除，否则更新
                    if (remainderUTXOs.length == 0) {
                        RocksDBUtils.getInstance().deleteUTXOs(txId);
                    } else {
                        RocksDBUtils.getInstance().putUTXOs(txId, remainderUTXOs);
                    }
                }
            }

            // CoinBase交易直接将交易输出保存到DB中
            TXOutput[] txOutputs = transaction.getOutputs();
            String txId = Hex.encodeHexString(transaction.getTxId());
            RocksDBUtils.getInstance().putUTXOs(txId, txOutputs);
        }

    }
    /**
     * 更新UTXO内存缓存池
     * 当一个新的本地交易产生时，需要从UTXO缓存池中移除花费掉了的交易输出
     * todo 问题:此方法是在10秒之内未出块时连续对一个账户出现多笔交易时才有用,但是这样做引出了另外一个问题,那就是该账户的下一比交易进行签名是从区块链中是找不到交易输入对应的上一笔交易输出的,因此此时上一笔交易还有入块
     * todo 对于上面的问题,可以考虑在查询一笔交易中交易输入对应的上一笔交易时可以加入当链中没有时可以从交易池中获取 , 该方法仅仅是一个解决方案但是没有经过实践......
     */
    @Synchronized
    public static void updateMemoryTempUtxo(Transaction transaction) {
        if (transaction == null) {
            System.out.println("失败更新内存utxo缓存,交易transaction为null !");
        }

        // 根据交易输入排查出剩余未被使用的交易输出
        //遍历该笔交易中所有的交易输入,与未花费的交易输出做对比(比对成功表示交易输出已经被花费) , 从内存缓存utxo池中剔除掉已经花费的交易输出
        for (TXInput txInput : transaction.getInputs()) {
            // 余下未被使用的交易输出
            TXOutput[] remainderUTXOs = {};
            String txId = Hex.encodeHexString(txInput.getTxId());
            //获取这笔交易中每一笔交易输入对应的上一笔交易的所有交易输出集合
            TXOutput[] txOutputs = RocksDBUtils.getInstance().getUTXOs(txId);
            System.out.println("2222:"+txInput.getTxOutputIndex());
            if (txOutputs == null) {
                continue;
            }
            //判断这笔交易输入对应的上一笔交易输出集合中消费掉的是哪些交易输出
            for (int outIndex = 0; outIndex < txOutputs.length; outIndex++) {
                System.out.println("3333:"+outIndex);
                if (outIndex != txInput.getTxOutputIndex()) {
                    //记录下未被消费的交易输出
                    remainderUTXOs = ArrayUtils.add(remainderUTXOs, txOutputs[outIndex]);
                }
            }

            // 这笔交易输出对应的上一笔的所有交易输出集合若是没有剩余则从内存中删除，否则从内存中更新
            if (remainderUTXOs.length == 0) {
                System.out.println("000000000000000000");
                RocksDBUtils.getInstance().deleteUTXOsFromMemory(txId);
            } else {
                System.out.println("11111111111111111");
                RocksDBUtils.getInstance().putUTXOsInMemory(txId, remainderUTXOs);
            }
        }

        //把交易中新的utxo临时加入到内存缓存utxo池中
        String txId = Hex.encodeHexString(transaction.getTxId());
        RocksDBUtils.getInstance().putUTXOsInMemory(txId,transaction.getOutputs());

        System.out.println("44444:"+transaction.getOutputs().length);
        for(int i = 0 ; i < transaction.getOutputs().length; i ++){
            String addressByPublicHashKey = WalletUtils.getInstance().getAddressByPublicHashKey(transaction.getOutputs()[i].getPubKeyHash());
            System.out.println("55555:"+addressByPublicHashKey+"----金额:"+transaction.getOutputs()[i].getValue()+"----输出交易类型:"+transaction.getOutputs()[i].getTxOutputType());
        }

    }
}
