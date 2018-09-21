package Consensus_Module.dpos_pbft;

import Account_Module.Transaction.AccountConstant;
import Account_Module.Transaction.TXOutput;
import Account_Module.Wallet.WalletUtils;
import Consensus_Module.Bean;
import Consensus_Module.ConsensusConstant;
import DB_Module.block.Blockchain;
import DB_Module.RocksDBUtils;
import Network_Module.NetworkService;

import java.util.*;

/**
 * 共识出块顺序线程运行任务类,每隔210秒运行一次,决定出出块的顺序
 * 单例
 */
public class ConsensusRoundService implements Runnable {
    private static ConsensusRoundService instance = new ConsensusRoundService();
    public synchronized static ConsensusRoundService getInstance() {
        return instance;
    }
    private ConsensusRoundService() {
    }
    //1.判断自己是不是出块节点
    //2.遍历所有的投票交易,选出前21位作为共识节点地址
    //3.判断自己是否是这21个节点之一,是的话广播到所有的输出节点
    //4.从21个节点中随机选择一个节点进行顺序的打乱(todo 私链的话可以这样做,公链的话得重新设置打乱出块顺序的策略)
    //5.将打乱的顺序广播给所有的共识节点
    @Override
    public void run() {
        //1.首先判断自己是不是共识出块节点
        if(ConsensusService.getInstance().isConsensusNode()) {
//            System.out.println("共识模块:共识轮次线程运行");
            //2.从所有的交易中遍历出所有的投票交易(在最初的时候由种子节点离线创建21个账户地址,钱包文件也保存种子节点中,并在创世文件中添加对这21个地址的投票交易,并且被加入到创世块中),计算出前21个的账户地址作为出块节点地址
            //由于刚开始的时候并没有超过21个节点加入到区块共识中,因此由创世块中21个地址代表21个节点进行出块,直到21个节点都获得了投票交易之后才切换为21个共识节点出块,但同样也对应着21个地址
            List<Map.Entry<byte[], Long>> voted21Best = getVoted21BestByAes();//一测OK

            //从本节点遍历21个共识地址,若是发现自己节点所在的地址是共识节点的话那么广播自己是共识节点的消息,一测OK
            for (Map.Entry<byte[], Long> entry : voted21Best) {
                byte[] publichHashKey = entry.getKey();
                String address = WalletUtils.getInstance().getAddressByPublicHashKey(publichHashKey);
                if (WalletUtils.getInstance().getWallet(address) != null) {
                    //向所有的输出节点广播自己是共识节点的消息
                    NetworkService.getInstance().broadcastConsensusNodeMessage(publichHashKey);
                }
            }

            //从第一次共识的时间开始计算,计算当前应该哪个共识节点打乱顺序(按照投票大小顺序的节点对投票节点进行打乱排序)
//            int round = (int) ((System.currentTimeMillis() - ConsensusConstant.GENESISBLOCKTIME)/1000 % 210 / 10);//一测OK
            int round = new Random().nextInt(ConsensusConstant.CONSENSUSNODECOUNT);//由谁打乱这个出块顺序由从21个地址中随机选择出来[0,20]
            Map.Entry<byte[], Long> longEntry = voted21Best.get(round);

            if (longEntry != null) {
                byte[] publicHashKey = longEntry.getKey();
                String addressByPublicHashKey = WalletUtils.getInstance().getAddressByPublicHashKey(publicHashKey);//一测OK
                if (addressByPublicHashKey == null) {
                    System.out.println("共识模块:公钥Hash转换为地址出现异常");
                    //由本节点对共识节点进行出块顺序的打乱并广播
                } else {

                    //下面的问题后面来考虑,现在先把单独一个节点通过私链的方式运行起来
                    //todo 如何判断当前的区块链中没有能够产生21个节点地址的投票交易,有的仅仅是21个创世块中的投票交易?
                    //todo 对于21个创世块中的投票交易得到的21个地址仅仅只是在种子节点中才有私钥,也就是说如果不是种子节点则不能对出块进行签名
                    //todo 当网络中正真运行了超过21个共识节点的时候如何做到共识出块

                    //todo 一个节点所在的账户地址只有在得到投票交易之后并且票数在前21位才能成为共识节点 ,同时替代掉创世块中的一个共识地址 ,  否则的话只能是普通节点 , 若是21个创世块中的地址没有被21个真正的共识节点替代完,则由默认的真正共识的一同进行出块
                    //todo 一个出块节点是否只对应一个账户,有可能一个出块节点上有两个账户地址,并且都得到了投票,但是确是一个ip地址
                    //todo 目前就是说一个节点可以有多个账户地址,只要这个账户得到了投票交易并且数量在前21位即可在一个节点上进行出块

                    try {
                        //判断这个地址是否在本地节点中是否能获取到钱包,可以的话表示由本地节点进行21个共识节点的顺序打乱,目前私链的话都是有的
                        if (WalletUtils.getInstance().getWallet(addressByPublicHashKey) != null) {
//                            System.out.println("共识模块:共识轮次,开始广播共识顺序");
                            //3.获取所有的共识节点进行随机排序,得出出块节点顺序(每个出块节点的时间为10秒)
                            Collections.shuffle(voted21Best, new Random(ConsensusConstant.CONSENSUSNODECOUNT));//一测OK
                            //将List<Map.Entry<byte[],Long>>(Entry没有默认构造函数不能被cryo反序列化,因此在Handler中处理数据的时候会出问题)转换为List<Bean>
                            List<Bean> votedBeanList = new ArrayList<>();
                            for(Map.Entry<byte[],Long> entry : voted21Best){
                                votedBeanList.add(new Bean(entry.getKey(),entry.getValue()));
                            }
                            ConsensusService.getInstance().setConsensusRoundList(votedBeanList);
                            //向所有的共识节点广播这个出块顺序
                            NetworkService.getInstance().brocadVoted21Best(publicHashKey, votedBeanList);
                        } else {
                            //并不是本节点进行出块顺序的打乱,那么等待别的节点打乱出块顺序并接收经过打乱出块的顺序数据 , 私链的话不会运行这里
                        }
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }
            }
        }
    }
    /**
     * 遍历所有的投票交易,选出投票数前21个的地址,降序排序
     * 一测OK
     */
    public List<Map.Entry<byte[],Long >> getVoted21BestByAes() {
        // 关于投票交易:一个持币账户根据自己的utxo进行投票交易,不能取消投票交易(todo 其实取消投票交易就是花掉这笔投票交易?? ,投过的utxo不能再进行投票交易,但是可以进行转账交易 ?? ,没想通怎么操作,后期考虑)
        // 投票交易在一轮投完之后基本上21个节点选出来是长期成为出块节点的,除非其中的节点出现问题才会被淘汰

        //key为一个账户的公钥Hash,value为被投票的数量
        Map<byte[],Long> votedMap = new TreeMap<>(new Comparator<byte[]>() {
            @Override
            public int compare(byte[] o1, byte[] o2) {
                int offset1 = 0;
                int offset2 = 0;
                int length1 = o1.length;
                int length2 = o2.length;
                int end1 = offset1 + length1;
                int end2 = offset2 + length2;
                for (int i = offset1, j = offset2; i < end1 && j < end2; i++, j++) {
                    int a = (o1[i] & 0xff);
                    int b = (o2[j] & 0xff);
                    if (a != b) {
                        return a - b;
                    }
                }
                return length1 - length2;
            }
        });


        String lastBlockHash = RocksDBUtils.getInstance().getLastBlockHash();
        Blockchain blockchain = new Blockchain(lastBlockHash);
        Map<String, TXOutput[]> allUTXOs = blockchain.findAllUTXOs();
        //过滤出所有的投票交易
        for(Map.Entry<String,TXOutput[]> entry : allUTXOs.entrySet()){
            TXOutput[] txOutputs = entry.getValue();
            for(int i = 0 ; i < txOutputs.length ; i++){
                String txOutputType = txOutputs[i].getTxOutputType();
                //转账交易输出
                if(AccountConstant.TRANSACTION_TYPE_VOTED.equals(txOutputType)){
                    //被投票人的公钥哈希,需要经过转换得到地址
                    byte[] pubKeyHash = txOutputs[i].getPubKeyHash();
                    //叠加被投票人的投票数
                    if(votedMap.containsKey(pubKeyHash)){
                        Long voteNum = votedMap.get(pubKeyHash);
                        voteNum += txOutputs[i].getValue();
                        votedMap.put(pubKeyHash,voteNum);
                    }else{
                        int value = txOutputs[i].getValue();
                        votedMap.put(pubKeyHash, (long) value);
                    }
                }
            }
        }

        //对所有被投过票的地址进行从大到小的排序
        //这里将map.entrySet()转换成list
        List<Map.Entry<byte[],Long>> sortedVotedList = new ArrayList<Map.Entry<byte[],Long>>(votedMap.entrySet());
        //然后通过比较器来实现排序
        Collections.sort(sortedVotedList,new Comparator<Map.Entry<byte[],Long>>() {
            //降序排序
            @Override
            public int compare(Map.Entry<byte[], Long> o1,
                               Map.Entry<byte[], Long> o2) {
                return (int)(o2.getValue()- (o1.getValue()));
            }
        });

//        //此时的sortedVotedList是经过从大到小的排序集合,得到前21个的地址做为出块节点
//        for(Map.Entry<byte[],Long> mapping : sortedVotedList){
//            System.out.println(mapping.getKey()+":"+mapping.getValue());
//        }
        //集合中若是没有21个出块节点的话那么就按照有多少算多少来,若是超过21个的话只取前21个节点
        int blockAddressCount = sortedVotedList.size() >= ConsensusConstant.CONSENSUSNODECOUNT ? ConsensusConstant.CONSENSUSNODECOUNT : sortedVotedList.size();
        List<Map.Entry<byte[],Long >> votedAddressList = new ArrayList<>();
        for(int i = 0 ; i < blockAddressCount ; i++){
            votedAddressList.add(sortedVotedList.get(i));
        }

        return votedAddressList;

    }
}
