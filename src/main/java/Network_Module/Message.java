package Network_Module;

/**
 * 消息封装类
 */
public class Message {
    //消息类型
    public String messageType;
    //节点的服务端ip
    private String ip;
    private int port;//这里的端口指的都是服务端的端口

    //最新区块高度
    private long lastBlockHeight;
    //出块人的公钥
    private byte[] publicKey;
    //共识节点地址
    private String consensusAddress;
    //验证人地址
    private String verifyAddress;
    //区块的hashId
    private String hashId;
    //区块的签名
    private byte[] signature;
    //最新区块高度与本地区块高度的差值
    private long count;

    public Message(){ }

    public Message(Long blockHeight , byte[] publicKey , String hash , byte[] signature){
        this.lastBlockHeight = blockHeight;
        this.publicKey = publicKey;
        this.hashId = hash;
        this.signature = signature;
    }

    public Message(String messageType){
        this.messageType = messageType;
    }
    public Message(String messageType , String ip , int port) {
        this.messageType = messageType;
        this.ip = ip;
        this.port = port;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public long getLastBlockHeight() {
        return lastBlockHeight;
    }

    public void setLastBlockHeight(long lastBlockHeight) {
        this.lastBlockHeight = lastBlockHeight;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(byte[] publicKey) {
        this.publicKey = publicKey;
    }

    public String getVerifyAddress() {
        return verifyAddress;
    }

    public void setVerifyAddress(String verifyAddress) {
        this.verifyAddress = verifyAddress;
    }

    public String getHashId() {
        return hashId;
    }

    public void setHashId(String hashId) {
        this.hashId = hashId;
    }

    public byte[] getSignature() {
        return signature;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    public String getConsensusAddress() {
        return consensusAddress;
    }

    public void setConsensusAddress(String consensusAddress) {
        this.consensusAddress = consensusAddress;
    }
}
