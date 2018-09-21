package Account_Module.Transaction;

import Account_Module.util.Base58Check;

import java.util.Arrays;

/**
 * 交易输出
 *
 * @author dingkonghua
 * @date 2017/07/27
 */

public class TXOutput {

    //数值
    private int value;
    //公钥Hash,接收者的公钥,根据公钥可以从链中查询到一个地址所有的交易输出
    private byte[] pubKeyHash;
    //交易输出的类型
    private String txOutputType;

    public TXOutput(){}
    public TXOutput(int value, byte[] pubKeyHash) {
        this.value = value;
        this.pubKeyHash = pubKeyHash;
    }

    /**
     * 创建交易输出
     * @param value
     * @param address
     * @return
     */
    public static TXOutput newTXOutput(int value, String address) {
        // 反向转化为 byte 数组
        byte[] versionedPayload = Base58Check.base58ToBytes(address);
        byte[] pubKeyHash = Arrays.copyOfRange(versionedPayload, 1, versionedPayload.length);
        return new TXOutput(value, pubKeyHash);
    }

    /**
     * 检查交易输出是否能够使用指定的公钥哈希
     * @param pubKeyHash
     * @return
     */
    public boolean isLockedWithKey(byte[] pubKeyHash) {
        return Arrays.equals(this.getPubKeyHash(), pubKeyHash);
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public byte[] getPubKeyHash() {
        return pubKeyHash;
    }

    public void setPubKeyHash(byte[] pubKeyHash) {
        this.pubKeyHash = pubKeyHash;
    }

    public String getTxOutputType() {
        return txOutputType;
    }

    public void setTxOutputType(String txOutputType) {
        this.txOutputType = txOutputType;
    }
}
