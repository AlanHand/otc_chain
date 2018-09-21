package Test;


import Account_Module.Wallet.WalletUtils;
import Client_Module.CLI;

import java.util.Set;

/**
 * 测试
 *
 * @author dingkonghua
 * @date 2018/08/01
 */
public class BlockchainTest {

    public static void main(String[] args) {
        try {

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
//            //创世区块创建
//            cli.createBlockchain(address);
//            //获取余额
//            cli.getBalance(address);
//            //打印区块链
//            cli.printChain();
//            //转账
//            cli.send(address,"1CceyiwYXh6vL6dLPw6WiNc5ihqVxwYHSA",1);
//            System.out.println("--------------------------------------------------------------------");
////            //打印区块链
//            cli.printChain();
//            //转账
//            cli.send(address,"1CceyiwYXh6vL6dLPw6WiNc5ihqVxwYHSA",1);
//            System.out.println("--------------------------------------------------------------------");
////            //打印区块链
//            cli.printChain();
//            //转账
//            cli.send(address,"1CceyiwYXh6vL6dLPw6WiNc5ihqVxwYHSA",1);
//            System.out.println("--------------------------------------------------------------------");
////            //打印区块链
//            cli.printChain();
//
//            System.out.println("--------------------------------------------------------------------");
//            Set<String> addresses = WalletUtils.getInstance().getAddresses();
//            for(String add : addresses){
//                cli.getBalance(add);
//            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
