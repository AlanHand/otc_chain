package DB_Module.block;

import Account_Module.Wallet.Wallet;
import Account_Module.Wallet.WalletUtils;
import Account_Module.util.ByteUtils;
import Account_Module.Transaction.MerkleTree;
import Account_Module.Transaction.Transaction;
import Consensus_Module.ConsensusConstant;
import DB_Module.RocksDBUtils;
import org.apache.commons.codec.digest.DigestUtils;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;
import java.security.*;
import java.time.Instant;
import java.util.Arrays;

/**
 * 区块
 *
 * @author dingkonghua
 * @date 2018/07/27
 */

public class Block {

    //区块hash值,由所有的交易id加密得到
    private String hash;
    //前一个区块的hash值
    private String prevBlockHash;
    //交易信息
    private Transaction[] transactions;
    //区块创建时间(单位:秒)
    private long timeStamp;
    //公钥,区块生产者的公钥,用于解密区块的签名
    private byte[] pubKey;
    //区块生产者的签名数据
    private byte[] signature;
    //区块高度
    private long height;
    //区块状态
    private String blockState;

    public Block(){ }

    public Block(String hash, String prevBlockHash, Transaction[] transactions, long timeStamp) {
        this.hash = hash;
        this.prevBlockHash = prevBlockHash;
        this.transactions = transactions;
        this.timeStamp = timeStamp;
//        this.nonce = nonce;
    }

    /**
     * 创建创世区块,创始区块中也应该有创世账户的私钥和公钥
     * @param coinbases
     * @return
     */
    public static Block newGenesisBlock(Transaction[] coinbases , String producerAddress) {
        return Block.newBlock("",coinbases , producerAddress);
    }

    /**
     * 创建新区块
     * @param previousHash
     * @param transactions
     * @param producerAddress
     * @return
     */
    public static Block newBlock(String previousHash, Transaction[] transactions , String producerAddress) {
        Block block = new Block("", previousHash, transactions, Instant.now().getEpochSecond());

        //设置区块hash
        String blockHashId = block.getBlockHashId();
        block.setHash(blockHashId);

        // 设置区块生产者的公钥,用于解签
        byte[] pubKey = WalletUtils.getInstance().getWallet(producerAddress).getPublicKey();
        block.setPubKey(pubKey);
        //设置区块签名
        byte[] signature = block.signBlock(producerAddress);
        if(signature == null){
            System.out.println("区块签名失败 !");
            return null;
        }else{
            block.setSignature(signature);
            block.setHeight(RocksDBUtils.getInstance().getBlocksMap().size());
            block.setBlockState(ConsensusConstant.BLOCKDOWNLOAD);
            return block;
        }
    }

    /**
     * 区块签名
     * @param from
     * @return
     */
    private byte[] signBlock(String from) {
        // 获取钱包
        Wallet senderWallet = WalletUtils.getInstance().getWallet(from);
        //获取私钥
        BCECPrivateKey privateKey = senderWallet.getPrivateKey();

        try{
            Security.addProvider(new BouncyCastleProvider());
            Signature ecdsaSign = Signature.getInstance("SHA256withECDSA", BouncyCastleProvider.PROVIDER_NAME);
            ecdsaSign.initSign(privateKey);

            ecdsaSign.update(hash.getBytes());
            byte[] signature = ecdsaSign.sign();

            return signature;
        }catch (Exception e){
            System.out.println("签名失败 !");
            e.printStackTrace();
            new RuntimeException(e);
        }
        return null;
    }

    /**
     * 验证区块的签名,只需要用公钥对签名数据进行解签,然后与区块的哈希id作比较即可
     * 代码需要测试
     * @return
     */
    public boolean verifyBlock(Block block){

        Security.addProvider(new BouncyCastleProvider());
        ECParameterSpec ecParameters = ECNamedCurveTable.getParameterSpec("secp256k1");
        try{
            KeyFactory keyFactory = KeyFactory.getInstance("ECDSA", BouncyCastleProvider.PROVIDER_NAME);
            Signature ecdsaVerify = Signature.getInstance("SHA256withECDSA", BouncyCastleProvider.PROVIDER_NAME);

            // 使用椭圆曲线 x,y 点去生成公钥Key
            BigInteger x = new BigInteger(1, Arrays.copyOfRange(block.getPubKey(), 1, 33));
            BigInteger y = new BigInteger(1, Arrays.copyOfRange(block.getPubKey(), 33, 65));
            ECPoint ecPoint = ecParameters.getCurve().createPoint(x, y);

            ECPublicKeySpec keySpec = new ECPublicKeySpec(ecPoint, ecParameters);
            PublicKey publicKey = keyFactory.generatePublic(keySpec);
            ecdsaVerify.initVerify(publicKey);//设置解密的公钥
            ecdsaVerify.update(block.getHash().getBytes());//设置解签后需要比对的数据,即区块的hashId
            if (!ecdsaVerify.verify(block.getSignature())) {
                return false;
            }else {
                return true;
            }

        }catch (Exception e){
            e.printStackTrace();
        }
        return true;
    }


    /**
     * 根据区块中所有的交易id组合在一起生成区块的hash id
     * 注意 : 如果区块中没有交易的话那么单纯的将所有的交易id转换为字节数据之后产生的区块id就是一样的,所以区块id还应该加入一个安全唯一的随机数
     * @return
     */
    private String getBlockHashId() {
        byte[][] bytes = new byte[transactions.length+1][];
        for(int i = 0 ; i < transactions.length ; i++ ){
            byte[] txId = transactions[i].getTxId();
            bytes[i] = txId;
        }
        byte[] timeBytes = String.valueOf(getTimeStamp()).getBytes();
        bytes[transactions.length] = timeBytes;
        //将交易id的字节数组数据组合在一起生成一个新的字节数组数据
        byte[] merge = ByteUtils.merge(bytes);
        String hash_id = DigestUtils.sha256Hex(merge);
        return hash_id;
    }

    /**
     * 对区块中的交易信息进行Hash计算得到默克尔树
     *
     * @return
     */
    public byte[] hashTransaction() {
        byte[][] txIdArrays = new byte[this.getTransactions().length][];
        for (int i = 0; i < this.getTransactions().length; i++) {
            txIdArrays[i] = this.getTransactions()[i].hash();
        }
        return new MerkleTree(txIdArrays).getRoot().getHash();
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getPrevBlockHash() {
        return prevBlockHash;
    }

    public void setPrevBlockHash(String prevBlockHash) {
        this.prevBlockHash = prevBlockHash;
    }

    public Transaction[] getTransactions() {
        return transactions;
    }

    public void setTransactions(Transaction[] transactions) {
        this.transactions = transactions;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(long timeStamp) {
        this.timeStamp = timeStamp;
    }

    public byte[] getPubKey() {
        return pubKey;
    }

    public void setPubKey(byte[] pubKey) {
        this.pubKey = pubKey;
    }

    public byte[] getSignature() {
        return signature;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    public long getHeight() {
        return height;
    }

    public void setHeight(long height) {
        this.height = height;
    }

    public String getBlockState() {
        return blockState;
    }

    public void setBlockState(String blockState) {
        this.blockState = blockState;
    }

}
