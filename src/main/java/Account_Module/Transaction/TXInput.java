package Account_Module.Transaction;

import Account_Module.util.BtcAddressUtils;

import java.util.Arrays;

/**
 * 交易输入
 *
 * @author dingkonghua
 * @date 2017/07/27
 */

public class TXInput {

    //上一笔交易来源的Id hash值,一笔交易输入是上一笔交易的输出,而要找出这个交易数据就得知道这笔交易输出是在哪个交易中
    private byte[] txId;
    //交易输出索引,指向这笔交易输入来自于txId所在交易中的那个具体的交易输出
    private int txOutputIndex;
    //签名,发送者私钥的签名数据
    private byte[] signature;
    //公钥,发送者的公钥
    private byte[] pubKey;

    public TXInput(){}

    /**
     * 交易熟肉构造函数
     * @param txId              这笔交易输入指向的上一笔交易的id
     * @param txOutputIndex     这笔交易输入指向的上一笔交易中输出数组TXOutput[]的索引
     * @param signature         这笔交易发送者私钥的签名数据
     * @param pubKey            这笔交易发送者的公钥
     */
    public TXInput(byte[] txId, int txOutputIndex, byte[] signature, byte[] pubKey) {
        this.txId = txId;
        this.txOutputIndex = txOutputIndex;
        this.signature = signature;
        this.pubKey = pubKey;
    }

    public byte[] getTxId() {
        return txId;
    }

    public void setTxId(byte[] txId) {
        this.txId = txId;
    }

    public int getTxOutputIndex() {
        return txOutputIndex;
    }

    public void setTxOutputIndex(int txOutputIndex) {
        this.txOutputIndex = txOutputIndex;
    }

    public byte[] getSignature() {
        return signature;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    public byte[] getPubKey() {
        return pubKey;
    }

    public void setPubKey(byte[] pubKey) {
        this.pubKey = pubKey;
    }

    /**
     * 检查公钥hash是否用于交易输入
     * @param pubKeyHash
     * @return
     */
    public boolean usesKey(byte[] pubKeyHash) {
        byte[] lockingHash = BtcAddressUtils.ripeMD160Hash(this.getPubKey());
        return Arrays.equals(lockingHash, pubKeyHash);
    }

}
