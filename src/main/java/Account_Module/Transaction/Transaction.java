package Account_Module.Transaction;


import Account_Module.Wallet.Wallet;
import Account_Module.Wallet.WalletUtils;
import DB_Module.RocksDBUtils;
import DB_Module.block.Blockchain;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.math.ec.ECPoint;
import Account_Module.util.BtcAddressUtils;
import Account_Module.util.SerializeUtils;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

/**
 * 交易
 * 一测通过
 * @author dingkonghua
 * @date 2017/07/27
 */
public class Transaction {

    private static final int SUBSIDY = 10;//创世交易中coinbase交易的默认初始值,其实就是设置一个链发行多少币的值,这个值应该分拆到创世交易中的多个账户地址中

    //交易的Hash id,由输入数据与输出数据的SHA256产生
    private byte[] txId;
    //交易输入
    private TXInput[] inputs;
    //交易输出
    private TXOutput[] outputs;
    //创建日期
    private long createTime;
    //交易类型
    private String txType;

    public Transaction(){

    }

    public Transaction(byte[] txId, TXInput[] inputs, TXOutput[] outputs, long createTime) {
        this.txId = txId;
        this.inputs = inputs;
        this.outputs = outputs;
        this.createTime = createTime;
    }

    /**
     * 计算交易信息的Hash值
     * 方法:
     *      这里计算一个交易的hashID是根据这笔交易中所有的输入(不包含签名数据)和输出数据,时间数据进行SHA256加密的
     * @return
     */
    public byte[] hash() {
        // 使用序列化的方式对Transaction对象进行深度复制,此时复制出来的信息含有交易id
        byte[] serializeBytes = SerializeUtils.serialize(this);
        Transaction copyTx = (Transaction) SerializeUtils.deserialize(serializeBytes);
        //设置交易id为空字节数据
        copyTx.setTxId(new byte[]{});
        //返回再次序列化之后的交易数据,并将其进行SHA256加密
        return DigestUtils.sha256(SerializeUtils.serialize(copyTx));
    }

    /**
     * 创建CoinBase交易,一个区块的奖励交易,对出块人的奖励
     * 特点 :   1.交易输入没有指向上一笔的交易输出
     *          2.只有一个交易输入
     *          3.这个交易输入指向上一笔交易的索引为 -1
     *          4.至少包含一个交易输出,作为出块者的奖励,奖励金额由系统设置,奖励者就是出块者的账号
     *
     * @param to   收账的钱包地址
     * @param data 解锁脚本数据
     * @return
     */
    public static Transaction newCoinbaseTX(String to, String data) {
        if (StringUtils.isBlank(data)) {
            data = String.format("Reward to '%s'", to);
        }
        // 创建交易输入
        TXInput txInput = new TXInput(new byte[]{}, -1, null, data.getBytes());
        // 创建交易输出
        TXOutput txOutput = TXOutput.newTXOutput(SUBSIDY, to);
        // 创建交易
        Transaction tx = new Transaction(null, new TXInput[]{txInput}, new TXOutput[]{txOutput}, System.currentTimeMillis());
        // 设置交易ID
        tx.setTxId(tx.hash());
        return tx;
    }

    /**
     * 创建CoinBase交易,指定数量
     * @param to
     * @param data
     * @param amount
     * @return
     */
    public static Transaction newCoinbaseTX(String to, String data,int amount) {
        if (StringUtils.isBlank(data)) {
            data = String.format("Reward to '%s'", to);
        }
        // 创建交易输入
        TXInput txInput = new TXInput(new byte[]{}, -1, null, data.getBytes());
        // 创建交易输出
        TXOutput txOutput = TXOutput.newTXOutput(amount, to);
        // 创建交易
        Transaction tx = new Transaction(null, new TXInput[]{txInput}, new TXOutput[]{txOutput}, System.currentTimeMillis());
        // 设置交易ID
        tx.setTxId(tx.hash());
        tx.setTxType(AccountConstant.TRANSACTION_TYPE_COINBASE);
        return tx;
    }

    /**
     * 是否为 Coinbase 交易
     *
     * @return
     */
    public boolean isCoinbase() {
        return this.getInputs().length == 1
                && this.getInputs()[0].getTxId().length == 0
                && this.getInputs()[0].getTxOutputIndex() == -1;
    }


    /**
     * 从 from 向  to 支付一定的 amount 的金额
     * 生成一笔完整的Transaction流程:
     * 1.根据发送者的地址得到发送者的公钥,然后根据公钥比对从区块链中获取所有关于该公钥的交易输出,获取几个累加的值比转账金额大的几个交易输出即可
     * 2.根据获取到的交易输出重新封装为当前交易的交易输入,交易输入的tx_id和txOutputIndex为上一笔交易输出所在交易中的交易id和交易输出索引,公钥就是当前发送者的公钥
     * 3.根据转账金额和接收者的地址封装交易输出,若是有剩余的金额则封装为交易输入(也可以作为交易费给出块者)
     * 4.将交易输入,交易输出和当前时间封装为一个交易对象,将该对象序列化然后Sha256加密得到交易id
     * 5.对上面的交易Transaction做一个拷贝,只拷贝所有的交易输入(不包含公钥,也没有签名),交易输出,然后循环每一步交易输入:根据交易输入中的上一笔交易id和交易输出索引得到该笔交易输入的公钥,然后对当前这个拷贝的交易CopyTransaction进行序列化,加密得到交易id再设置到CopyTransaction中,
     *   在将设置的当前交易输入的公钥设置为null,然后用发送者的私钥对交易id进行签名,最后再将签名的数据设置到原本的Transaction的交易输入中,也就是说一笔交易的每一个交易输入的签名都不一样,但是解签的时候必须按照签名的流程走,直到循环完所有的交易输入
     *
     * 交易验证,对接收到的Transaction进行解签:
     * 1.拷贝一份Transaction中的交易输入(不含公钥,私钥也为null)和交易输出,然后根据当前时间封装为一个CopyTransaction, 然后流程和上面的一样,对每一个交易输入都生成了一个加了密的交易id,然后根据发送者的公钥对原始Transaction中的对应交易输入中的签名数据解签和该交易id进行比对,若是每个签名解签之后都与交易id相同则验证成功
     *
     * 问题:根据发送者的公钥扫描区块链获取发送者的所有未花费的交易输出时,怎么判断就是未花费的呢?或者说不需要判断吗? 已解决
     * @param from       支付钱包地址
     * @param to         收款钱包地址
     * @param amount     交易金额
     * @param blockchain 区块链
     * @param type       默认是转账交易
     * @return
     */
    public synchronized static Transaction newUTXOTransactionByType(String from, String to, int amount, Blockchain blockchain , String type) throws Exception {
        // 获取钱包
        Wallet senderWallet = WalletUtils.getInstance().getWallet(from);
        //发送者的公钥,用于设置在交易输入中
        byte[] pubKey = senderWallet.getPublicKey();
        //公钥转hash160,用于查询发送者的utxo
        byte[] pubKeyHash = BtcAddressUtils.ripeMD160Hash(pubKey);

        //获取该地址可花费的交易输出
        SpendableOutputResult result = new UTXOSet(blockchain).findSpendableOutputsByType(pubKeyHash, amount , type);
        //获取这些交易输出的总价值
        int accumulated = result.getAccumulated();
        //key为交易id,value为id对应的交易输出的索引集合
        Map<String, int[]> unspentOuts = result.getUnspentOuts();

        if (accumulated < amount) {
            System.out.println("ERROR: 账户没有足够的余额 ! 可用余额=" + accumulated + ", 转账金额=" + amount);
            return null;
        }

        //封装交易输入:将发送者可用的交易输出封装为一个或多个交易输入
        Iterator<Map.Entry<String, int[]>> iterator = unspentOuts.entrySet().iterator();
        TXInput[] txInputs = {};
        while (iterator.hasNext()) {
            Map.Entry<String, int[]> entry = iterator.next();
            String txIdStr = entry.getKey();
            int[] outIds = entry.getValue();
            byte[] txId = Hex.decodeHex(txIdStr);
            //将账户对应的每一笔交易输出索引封装为一笔交易输入
            for (int outIndex : outIds) {
                txInputs = ArrayUtils.add(txInputs, new TXInput(txId, outIndex, null, pubKey));
            }
        }

        //封装交易输出,将发送者发送的金额封装为一笔交易输出,交易输出的公钥Hash为接收者的公钥Hash
        TXOutput[] txOutput = {};
        txOutput = ArrayUtils.add(txOutput, TXOutput.newTXOutput(amount, to));
        if (accumulated > amount) {
            //余额也封装为一笔交易输出,但是这笔交易输出的公钥Hash为发送者的公钥Hash
            txOutput = ArrayUtils.add(txOutput, TXOutput.newTXOutput((accumulated - amount), from));
        }

        //将交易输入和交易输出封装为一笔交易,此时所有的交易输入还没有签名,仅仅有发送者的公钥而已
        Transaction newTx = new Transaction(null, txInputs, txOutput, System.currentTimeMillis());
        //先将交易数据序列化然后SHA256加密得到交易id,再设置在交易中
        newTx.setTxId(newTx.hash());
        //todo 问题出来了 , 当一个账户的连续第二笔交易转账时对这笔交易的签名需要遍历区块链中的所有相关的交易输入进行签名,但是此时该账户的上一笔交易并没有被打包出块,因此就会出现通过id在链上找不到上一笔的交易
        //todo 目前的解决办法就是在10秒之内一个账户只能转一次账
        // 进行交易签名,交易数据用发送者的私钥签名
        blockchain.signTransaction(newTx, senderWallet.getPrivateKey());

//        //更新内存中的utxo集合
//        newTx.setTxType(type);
//        UTXOSet.updateMemoryTempUtxo(newTx);

        return newTx;
    }


    /**
     * 创建用于签名的交易数据副本，交易输入的 signature 和 pubKey 需要设置为null,其余数据都拷贝下来了
     * @return
     */
    public Transaction trimmedCopy() {
        TXInput[] tmpTXInputs = new TXInput[this.getInputs().length];
        for (int i = 0; i < this.getInputs().length; i++) {
            TXInput txInput = this.getInputs()[i];
            tmpTXInputs[i] = new TXInput(txInput.getTxId(), txInput.getTxOutputIndex(), null, null);
        }

        TXOutput[] tmpTXOutputs = new TXOutput[this.getOutputs().length];
        for (int i = 0; i < this.getOutputs().length; i++) {
            TXOutput txOutput = this.getOutputs()[i];
            tmpTXOutputs[i] = new TXOutput(txOutput.getValue(), txOutput.getPubKeyHash());
        }

        return new Transaction(this.getTxId(), tmpTXInputs, tmpTXOutputs, this.getCreateTime());
    }


    /**
     * 签名
     *  步骤 :
     *      1.拷贝原来的交易中所有的交易输入和交易输出,但是不拷贝交易输入中的签名和公钥
     *      2.获取每一笔交易输入对应的上一笔交易输出的公钥hash作为这一笔交易输入的公钥
     *      3,对拷贝的交易数据进行序列化并SHA256加密得到 HashID
     *      4.再次将这一笔的交易输入的公钥至为null
     *      5.用私钥将上面的HashID进行签名得到签名数据
     *      6.将5中得到的签名数据设置到原始的交易中对应的那一笔交易输入中
     *      7.重复2-6,直到设置完所有交易输入的签名
     *
     * @param privateKey 当前交易发送者的私钥
     * @param prevTxMap  当前交易中所有交易输入对应的上一笔交易的集合
     */
    public void sign(BCECPrivateKey privateKey, Map<String, Transaction> prevTxMap) throws Exception {
        // coinbase 交易信息不需要签名，因为它不存在交易输入信息
        if (this.isCoinbase()) {
            return;
        }
        // 再次验证一下交易信息中的交易输入是否正确，也就是能否查找对应的交易数据
        for (TXInput txInput : this.getInputs()) {
            if (prevTxMap.get(Hex.encodeHexString(txInput.getTxId())) == null) {
                throw new RuntimeException("ERROR: Previous transaction is not correct");
            }
        }

        // 创建用于签名的交易信息的副本,副本中的交易输入没有公钥,也没有签名
        Transaction txCopy = this.trimmedCopy();

        Security.addProvider(new BouncyCastleProvider());
        Signature ecdsaSign = Signature.getInstance("SHA256withECDSA", BouncyCastleProvider.PROVIDER_NAME);
        ecdsaSign.initSign(privateKey);

        for (int i = 0; i < txCopy.getInputs().length; i++) {
            TXInput txInputCopy = txCopy.getInputs()[i];
            // 获取交易输入TxID对应的交易数据
            Transaction prevTx = prevTxMap.get(Hex.encodeHexString(txInputCopy.getTxId()));
            // 获取交易输入所对应的上一笔交易中的交易输出
            TXOutput prevTxOutput = prevTx.getOutputs()[txInputCopy.getTxOutputIndex()];
            //设置当前交易输入的公钥为上一笔交易输出的公钥哈希
            txInputCopy.setPubKey(prevTxOutput.getPubKeyHash());//todo 这里的拷贝交易输入设置公钥的过程可以优化,直接从原始交易的交易输入中设置也行(后期考虑)
            txInputCopy.setSignature(null);
            // 得到要签名的数据，即交易ID,因为每一次循环得到的交易输入都更新了交易输入的公钥哈希,因此在对交易进行序列化加密得到的id都是不一样的,因此下面的每一个签名更新也就不一样
            txCopy.setTxId(txCopy.hash());
            txInputCopy.setPubKey(null);

            // 对整个交易信息仅进行签名，即对交易ID进行签名
            ecdsaSign.update(txCopy.getTxId());
            byte[] signature = ecdsaSign.sign();

            // 将整个交易数据的签名赋值给交易输入，因为交易输入需要包含整个交易信息的签名
            // 注意是将得到的签名赋值给原交易信息中的交易输入
            this.getInputs()[i].setSignature(signature);
        }
    }


    /**
     * 验证交易信息
     * @param prevTxMap 当前交易中所有交易输入的上一笔交易集合
     * @return
     */
    public boolean verify(Map<String, Transaction> prevTxMap) throws Exception {
        // coinbase 交易信息不需要签名，也就无需验证
        if (this.isCoinbase()) {
            return true;
        }

        // 验证当前交易中所有的交易输入是否是来自上一笔的交易输出
        for (TXInput txInput : this.getInputs()) {
            if (prevTxMap.get(Hex.encodeHexString(txInput.getTxId())) == null) {
                throw new RuntimeException("ERROR: Previous transaction is not correct");
            }
        }

        // 创建用于签名验证的交易信息的副本
        Transaction txCopy = this.trimmedCopy();

        Security.addProvider(new BouncyCastleProvider());
        ECParameterSpec ecParameters = ECNamedCurveTable.getParameterSpec("secp256k1");
        KeyFactory keyFactory = KeyFactory.getInstance("ECDSA", BouncyCastleProvider.PROVIDER_NAME);
        Signature ecdsaVerify = Signature.getInstance("SHA256withECDSA", BouncyCastleProvider.PROVIDER_NAME);

        //对每一笔交易输入进行解签并且比对签名数据
        for (int i = 0; i < this.getInputs().length; i++) {

            TXInput txInput = this.getInputs()[i];
            // 获取交易输入TxID对应的上一笔交易
            Transaction prevTx = prevTxMap.get(Hex.encodeHexString(txInput.getTxId()));
            // 获取交易输入所对应的上一笔交易中的交易输出
            TXOutput prevTxOutput = prevTx.getOutputs()[txInput.getTxOutputIndex()];

            TXInput txInputCopy = txCopy.getInputs()[i];
            txInputCopy.setSignature(null);
            txInputCopy.setPubKey(prevTxOutput.getPubKeyHash());
            // 得到要解签后比对的数据，即交易ID,和交易签名一样的流程
            txCopy.setTxId(txCopy.hash());
            //重新设置拷贝交易输出的公钥为null,好让下一次循环中txCopy的hash能和签名是的流程一样
            txInputCopy.setPubKey(null);

            // 使用椭圆曲线 x,y 点去生成公钥Key
            BigInteger x = new BigInteger(1, Arrays.copyOfRange(txInput.getPubKey(), 1, 33));
            BigInteger y = new BigInteger(1, Arrays.copyOfRange(txInput.getPubKey(), 33, 65));
            ECPoint ecPoint = ecParameters.getCurve().createPoint(x, y);

            ECPublicKeySpec keySpec = new ECPublicKeySpec(ecPoint, ecParameters);
            PublicKey publicKey = keyFactory.generatePublic(keySpec);
            ecdsaVerify.initVerify(publicKey);//设置解密的公钥
            ecdsaVerify.update(txCopy.getTxId());//设置解签后需要比对的数据,即加密的交易id
            if (!ecdsaVerify.verify(txInput.getSignature())) {
                return false;
            }
        }
        return true;
    }

    public static int getSUBSIDY() {
        return SUBSIDY;
    }

    public byte[] getTxId() {
        return txId;
    }

    public void setTxId(byte[] txId) {
        this.txId = txId;
    }

    public TXInput[] getInputs() {
        return inputs;
    }

    public void setInputs(TXInput[] inputs) {
        this.inputs = inputs;
    }

    public TXOutput[] getOutputs() {
        return outputs;
    }

    public void setOutputs(TXOutput[] outputs) {
        this.outputs = outputs;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public String getTxType() {
        return txType;
    }

    //设置交易类型就必须设置交易输出的交易类型
    public void setTxType(String txType) {
        this.txType = txType;

        for(TXOutput txOutput : outputs){
            txOutput.setTxOutputType(txType);
        }
    }
}
