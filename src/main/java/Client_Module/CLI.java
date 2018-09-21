package Client_Module;

import Account_Module.Transaction.AccountConstant;
import Account_Module.Wallet.Wallet;
import Account_Module.Wallet.WalletUtils;
import Account_Module.util.SerializeUtils;
import DB_Module.block.Block;
import DB_Module.block.Blockchain;
import Consensus_Module.dpos_pbft.ConsensusService;
import Network_Module.NetworkService;
import org.apache.commons.cli.*;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import DB_Module.RocksDBUtils;
import Account_Module.Transaction.TXOutput;
import Account_Module.Transaction.Transaction;
import Account_Module.Transaction.UTXOSet;
import Account_Module.util.Base58Check;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 命令行解析器
 *
 * @author dingkonghua
 * @date 2018/07/27
 */
public class CLI {

    private String[] args;
    private Options options = new Options();

    public CLI(){}

    public CLI(String[] args) {
        this.args = args;

        Option helpCmd = Option.builder("h").desc("show help").build();
        options.addOption(helpCmd);

        Option address = Option.builder("address").hasArg(true).desc("Source wallet address").build();
        Option sendFrom = Option.builder("from").hasArg(true).desc("Source wallet address").build();
        Option sendTo = Option.builder("to").hasArg(true).desc("Destination wallet address").build();
        Option sendAmount = Option.builder("amount").hasArg(true).desc("Amount to send").build();

        options.addOption(address);
        options.addOption(sendFrom);
        options.addOption(sendTo);
        options.addOption(sendAmount);
    }


    public static void main(String[] args)throws Exception{
//        startRun();
        printChainTest();

    }

    private static void startRun() {
        try{
            CLI cli = new CLI();
            Set<String> addSet = WalletUtils.getInstance().getAddresses();
            String address = null;
            if(addSet == null){
                //钱包创建
                address = cli.createWallet();
                System.out.println("新创建的钱包地址:"+address);
            }else{
                for(String str : addSet){
                    address = str;
                    System.out.println("已经存在的钱包地址:"+address);
                }
            }
            //创世区块创建,加载genesis.json文件,创建创世交易(3个转账交易设置币的总数,21个投票交易(每个交易对应不同的地址)用于共识)
            cli.createBlockchain(address);
            cli.printChain();

            //网络模块启动,一测通过
            NetworkService.getInstance().init();

            //随机转账,但是同一个账户地址只能在10(出块时间)秒之内转账一次
            randomTransfer(cli,addSet);

            Thread.sleep(2000);

            //todo  DPOS+PBFT 出块 , 打包交易出块,必须先获取到出块人的地址,并且还必须有出块人的私钥才行

            ConsensusService.getInstance().start();

        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * 随机转账
     * @param addSet
     */
    private static void randomTransfer(CLI cli , Set<String> addSet) {

        List<String> addressList = new ArrayList<>();
        for(String add : addSet){
            addressList.add(add);
        }
        ScheduledExecutorService consensusRoundService = Executors.newSingleThreadScheduledExecutor();
        List<String> list = new ArrayList<>();
        list.add("1Mu1L46Xf3cP5oFBWsNa4MePxdL9L36DH5");
        list.add("1KMZ7ckiAJcyrm7tA2RfQrSpGRSsuEQEFE");
        list.add("1Kjhjwx3FYe26ce91JEFdF46WN1DuL3wMZ");
        //立即运行出块顺序的线程,以后每隔10秒运行一次,一测OK
        consensusRoundService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try{
                    //随机选择发送者和接收者,todo  还是存在双花问题,10秒之内一个账户不能转多次账 ,下周来重写交易签名的过程,然后加入缓存utxo池
//                    String sender = addressList.get(new Random().nextInt(addressList.size() - 1));
                    String sender = list.get(num);
                    num ++;
                    num = num % 3;
                    //随机获取一个接收者
                    String receiver = addressList.get(new Random().nextInt(addressList.size() - 1));
                    //转账交易,发送者为创世文件中的账户
                    Transaction transaction = cli.send(sender, receiver, 1,AccountConstant.TRANSACTION_TYPE_TRANSFER);
                    if(transaction != null){
                        // 将交易放入交易池中
                        Blockchain.putTx(transaction);
                        // 广播交易  一测OK
                        NetworkService.getInstance().broadcastTransaction(transaction);
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }, 1, 9, TimeUnit.SECONDS);
    }
    public static int num = 0;
    public static void printChainTest() throws Exception{
        CLI cli = new CLI();
//        cli.printChain();
        Set<String> addSet = WalletUtils.getInstance().getAddresses();
        System.out.println("余额-----------------------");
        for(String str : addSet){
            cli.getBalance(str);
        }

    }
    /**
     * 转账
     *
     * @param from
     * @param to
     * @param amount
     * @throws Exception
     */
    public Transaction send(String from, String to, int amount , String type) throws Exception {
        // 检查发送者地址是否合法
        try {
            Base58Check.base58ToBytes(from);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("客户端:发送者地址不正常 ! address=" + from);
        }
        // 检查接收者地址是否合法
        try {
            Base58Check.base58ToBytes(to);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("客户端:接收者地址不正常 ! address=" + to);
        }
        if (amount < 1) {
            //todo 后期应该可以发送小于1的数量
            System.out.println("客户端:发送数量不能小于1 ! amount=" + amount);
        }
        // 若是没有区块就创建创世区块,有的话直接返回BlockMain对象
        Blockchain blockchain = Blockchain.initBlockchainFromDB();
        // 创建新交易,blockchain是用来查询区块链中所有未花费的转账交易输出数据
        Transaction transaction = Transaction.newUTXOTransactionByType(from, to, amount, blockchain,type);
        if(transaction == null){
            return null;
        }
        transaction.setTxType(type);

        return transaction;
    }
    /**
     * 命令行解析入口
     */
    public void parse() {
        this.validateArgs(args);
        try {
            CommandLineParser parser = new DefaultParser();
            CommandLine cmd = parser.parse(options, args);
            switch (args[0]) {
                case "createblockchain":
                    String createblockchainAddress = cmd.getOptionValue("address");
                    if (StringUtils.isBlank(createblockchainAddress)) {
                        help();
                    }
                    this.createBlockchain(createblockchainAddress);
                    break;
                case "getbalance":
                    String getBalanceAddress = cmd.getOptionValue("address");
                    if (StringUtils.isBlank(getBalanceAddress)) {
                        help();
                    }
                    this.getBalance(getBalanceAddress);
                    break;
                case "send":
                    String sendFrom = cmd.getOptionValue("from");
                    String sendTo = cmd.getOptionValue("to");
                    String sendAmount = cmd.getOptionValue("amount");
                    if (StringUtils.isBlank(sendFrom) ||
                            StringUtils.isBlank(sendTo) ||
                            !NumberUtils.isDigits(sendAmount)) {
                        help();
                    }
                    this.send(sendFrom, sendTo, Integer.valueOf(sendAmount),AccountConstant.TRANSACTION_TYPE_TRANSFER);
                    break;
                case "createwallet":
                    this.createWallet();
                    break;
                case "printaddresses":
                    this.printAddresses();
                    break;
                case "printchain":
                    this.printChain();
                    break;
                case "h":
                    this.help();
                    break;
                default:
                    this.help();
            }
        } catch (Exception e) {
            System.out.println("Fail to parse Client_Module.cli command ! ");
            e.printStackTrace();;
        } finally {
            RocksDBUtils.getInstance().closeDB();
        }
    }

    /**
     * 验证入参
     *
     * @param args
     */
    private void validateArgs(String[] args) {
        if (args == null || args.length < 1) {
            help();
        }
    }

    /**
     * 创建区块链,创建创世块,第一次更新所有账户的utxo到磁盘上
     * @param address
     */
    public void createBlockchain(String address) {
        Blockchain blockchain = Blockchain.createBlockchain(address);
        UTXOSet utxoSet = new UTXOSet(blockchain);
        //更新utxo
        utxoSet.reIndex();
        System.out.println("Done ! ");
    }

    /**
     * 创建钱包
     *
     * @throws Exception
     */
    public String createWallet() throws Exception {
        Wallet wallet = WalletUtils.getInstance().createWallet();
        System.out.println("wallet address : " + wallet.getAddress());
        return wallet.getAddress();
    }

    /**
     * 打印钱包中所有地址
     */
    private void printAddresses() {
        Set<String> addresses = WalletUtils.getInstance().getAddresses();
        if (addresses == null || addresses.isEmpty()) {
            System.out.println("There isn't address");
            return;
        }
        for (String address : addresses) {
            System.out.println("Wallet address: " + address);
        }
    }

    /**
     * 查询钱包中指定地址余额
     *
     * @param address 钱包地址
     */
    public void getBalance(String address) {
        // 检查钱包地址是否合法
        try {
            Base58Check.base58ToBytes(address);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("ERROR: invalid wallet address");
            throw new RuntimeException("ERROR: invalid wallet address", e);
        }

        // 根据地址得到公钥Hash值
        byte[] versionedPayload = Base58Check.base58ToBytes(address);
        byte[] pubKeyHash = Arrays.copyOfRange(versionedPayload, 1, versionedPayload.length);

        Blockchain blockchain = Blockchain.initBlockchainFromDB();
        UTXOSet utxoSet = new UTXOSet(blockchain);

        TXOutput[] txOutputs = utxoSet.findUTXOs(pubKeyHash);
        int balance = 0;
        if (txOutputs != null && txOutputs.length > 0) {
            for (TXOutput txOutput : txOutputs) {
                balance += txOutput.getValue();
            }
        }
        System.out.println(address+"----余额:"+balance);
    }

    /**
     * 打印帮助信息
     */
    private void help() {
        System.out.println("Usage:");
        System.out.println("  createwallet - Generates a new key-pair and saves it into the wallet file");
        System.out.println("  printaddresses - print all wallet address");
        System.out.println("  getbalance -address ADDRESS - Get balance of ADDRESS");
        System.out.println("  createblockchain -address ADDRESS - Create a blockchain and send genesis DB_Module.block reward to ADDRESS");
        System.out.println("  printchain - Print all the blocks of the blockchain");
        System.out.println("  send -from FROM -to TO -amount AMOUNT - Send AMOUNT of coins from FROM address to TO");
        System.exit(0);
    }

    /**
     * 打印出区块链中的所有区块
     */
    public void printChain() {
        Blockchain blockchain = Blockchain.initBlockchainFromDB();
        for (Blockchain.BlockchainIterator iterator = blockchain.getBlockchainIterator(); iterator.hashNext(); ) {
            Block block = iterator.next();
            if (block != null) {
                System.out.println("区块高度:"+block.getHeight()+"----上一区块hash:"+block.getPrevBlockHash()+"--当前区块hash:"+block.getHash()+"--交易数量:"+block.getTransactions().length+"--出块时间:"+block.getTimeStamp());
                int count = 0;
//                for(Transaction tx : block.getTransactions()){
//                    for(TXOutput txOut : tx.getOutputs()) {
//                        count = txOut.getValue() + count;
//                    }
//                    System.out.println("区块高度--"+block.getHeight()+"--交易id:"+tx.getTxId()+"--交易输出金额:"+count);
//                    count = 0;
//                }
//                System.out.println("------------------------------------------------------------------------");
            }
        }
    }

}
