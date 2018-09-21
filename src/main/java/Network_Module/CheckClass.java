package Network_Module;

import Account_Module.Transaction.Transaction;
import DB_Module.block.Block;

import java.util.List;

/**
 * 检查反序列化之后的object是什么对象
 */
public class CheckClass {
    private static CheckClass instance = new CheckClass();
    private CheckClass(){}
    public static synchronized CheckClass getInstance(){
        return instance;
    }

    /**
     * 判断一个object对象具体是啥对象
     * @param object
     * @return 0:Message , 1:Block , 2:Transaction
     */
    public String checkClassAndGet(Object object){
        if(object instanceof Message){
            return "0";
        }else if(object instanceof Block){
            return "1";
        }else if(object instanceof Transaction){
            return "2";
        }else if(object instanceof List){
            return "3";
        }
        return "-1";
    }
}
