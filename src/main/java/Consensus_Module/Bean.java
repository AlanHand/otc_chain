package Consensus_Module;

public class Bean {
    private byte[] publicHashKey;
    private Long votedCount;
    public Bean(){

    }

    public Bean(byte[] publicHashKey, Long votedCount) {
        this.publicHashKey = publicHashKey;
        this.votedCount = votedCount;
    }

    public byte[] getPublicHashKey() {
        return publicHashKey;
    }

    public void setPublicHashKey(byte[] publicHashKey) {
        this.publicHashKey = publicHashKey;
    }

    public Long getVotedCount() {
        return votedCount;
    }

    public void setVotedCount(Long votedCount) {
        this.votedCount = votedCount;
    }
}
