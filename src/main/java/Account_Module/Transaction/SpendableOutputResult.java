package Account_Module.Transaction;

import java.util.Map;

/**
 * 发送者可花费的utxo
 * @author dingkonghua
 * @date 2018/07/27
 */

public class SpendableOutputResult {

    //交易时的支付金额
    private int accumulated;
    //未花费的交易utxo,key交易id,value为可花费的交易金额
    private Map<String, int[]> unspentOuts;

    public SpendableOutputResult(int accumulated, Map<String, int[]> unspentOuts) {
        this.accumulated = accumulated;
        this.unspentOuts = unspentOuts;
    }

    public int getAccumulated() {
        return accumulated;
    }

    public void setAccumulated(int accumulated) {
        this.accumulated = accumulated;
    }

    public Map<String, int[]> getUnspentOuts() {
        return unspentOuts;
    }

    public void setUnspentOuts(Map<String, int[]> unspentOuts) {
        this.unspentOuts = unspentOuts;
    }
}
