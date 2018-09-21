package Account_Module.Wallet;

import Account_Module.util.BtcAddressUtils;
import com.google.common.collect.Maps;
import lombok.Cleanup;
import Account_Module.util.Base58Check;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SealedObject;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.util.Map;
import java.util.Set;


/**
 * 钱包工具类
 * 功能:
 *      1.初始化创建钱包的文件,从磁盘加载钱包文件
 *      2.保存所有的钱包到磁盘中(使用AES对称加密,密码由用户设置)
 *      3.获取钱包文件的所有的地址
 *      4.根据一个地址获取一个钱包
 *      5.根据公钥获取钱包地址
 *      6.创建一个钱包(公钥,私钥)
 * @author dingkonghua
 * @date 2018/07/27
 */
public class WalletUtils {

    //钱包工具实例
    private volatile static WalletUtils instance;
    public static WalletUtils getInstance() {
        if (instance == null) {
            synchronized (WalletUtils.class) {
                if (instance == null) {
                    instance = new WalletUtils();
                }
            }
        }
        return instance;
    }

    private WalletUtils() {
        initWalletFile();
    }

    //钱包文件
    private final static String WALLET_FILE = "wallet.dat";
    //加密算法AES对称加密,将保存的钱包数据通过密码进行对称加密
    private static final String ALGORITHM = "AES";
    //todo 保存钱包的密码,这里应该由每个账户来指定
    private static final byte[] CIPHER_TEXT = "2oF@5sC%DNf32y!TmiZi!tG9W5rLaniD".getBytes();

    /**
     * 初始化钱包文件
     */
    private void initWalletFile() {
        File file = new File(WALLET_FILE);
        if (!file.exists()) {
            this.saveToDisk(new Wallets());
        } else {
            this.loadFromDisk();
        }
    }

    /**
     * 获取所有的钱包地址
     * @return
     */
    public Set<String> getAddresses() {
        Wallets wallets = this.loadFromDisk();
        return wallets.getAddresses();
    }

    /**
     * 获取钱包数据
     * @param address 钱包地址
     * @return
     */
    public Wallet getWallet(String address) {
        Wallets wallets = this.loadFromDisk();
        return wallets.getWallet(address);
    }
    /**
     * 根据公钥Hash获取钱包地址
     * @return
     */
    public synchronized  String getAddressByPublicHashKey(byte[] publicHashKey) {
        try {

            // 2. 添加版本 0x00
            ByteArrayOutputStream addrStream = new ByteArrayOutputStream();
            addrStream.write((byte) 0);
            addrStream.write(publicHashKey);
            byte[] versionedPayload = addrStream.toByteArray();

            // 3. 计算校验码
            byte[] checksum = BtcAddressUtils.checksum(versionedPayload);

            // 4. 得到 version + paylod + checksum 的组合
            addrStream.write(checksum);
            byte[] binaryAddress = addrStream.toByteArray();

            // 5. 执行Base58转换处理
            return Base58Check.rawBytesToBase58(binaryAddress);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
    /**
     * 根据公钥获取钱包地址
     * @return
     */
    public synchronized String getAddressByPublicKey(byte[] publicKey) {
        try {
            // 1. 获取 ripemdHashedKey
            byte[] ripemdHashedKey = BtcAddressUtils.ripeMD160Hash(publicKey);

            // 2. 添加版本 0x00
            ByteArrayOutputStream addrStream = new ByteArrayOutputStream();
            addrStream.write((byte) 0);
            addrStream.write(ripemdHashedKey);
            byte[] versionedPayload = addrStream.toByteArray();

            // 3. 计算校验码
            byte[] checksum = BtcAddressUtils.checksum(versionedPayload);

            // 4. 得到 version + paylod + checksum 的组合
            addrStream.write(checksum);
            byte[] binaryAddress = addrStream.toByteArray();

            // 5. 执行Base58转换处理
            return Base58Check.rawBytesToBase58(binaryAddress);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
    /**
     * 创建钱包
     * @return
     */
    public Wallet createWallet() {
        Wallet wallet = new Wallet();
        Wallets wallets = this.loadFromDisk();
        wallets.addWallet(wallet);
        this.saveToDisk(wallets);
        return wallet;
    }

    /**
     * 保存钱包数据
     */
    private void saveToDisk(Wallets wallets) {
        try {
            if (wallets == null) {
                System.out.println("保存钱包失败, wallets is null ");
                throw new Exception("ERROR: Fail to save wallet to file !");
            }
            SecretKeySpec sks = new SecretKeySpec(CIPHER_TEXT, ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, sks);
            SealedObject sealedObject = new SealedObject(wallets, cipher);
            // Wrap the output stream
            @Cleanup CipherOutputStream cos = new CipherOutputStream(new BufferedOutputStream(new FileOutputStream(WALLET_FILE)), cipher);
            @Cleanup ObjectOutputStream outputStream = new ObjectOutputStream(cos);
            outputStream.writeObject(sealedObject);
        } catch (Exception e) {
            System.out.println("保存钱包到磁盘失败");
            e.printStackTrace();
            throw new RuntimeException("Fail to save wallet to disk !");
        }
    }

    /**
     * 加载钱包数据
     */
    private Wallets loadFromDisk() {
        try {
            SecretKeySpec sks = new SecretKeySpec(CIPHER_TEXT, ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, sks);
            @Cleanup CipherInputStream cipherInputStream = new CipherInputStream(new BufferedInputStream(new FileInputStream(WALLET_FILE)), cipher);
            @Cleanup ObjectInputStream inputStream = new ObjectInputStream(cipherInputStream);
            SealedObject sealedObject = (SealedObject) inputStream.readObject();
            return (Wallets) sealedObject.getObject(cipher);
        } catch (Exception e) {
            System.out.println("Fail to load wallet from disk ! ");
            e.printStackTrace();
            throw new RuntimeException("Fail to load wallet from disk ! ");
        }
    }

    /**
     * 钱包存储对象
     */
    public static class Wallets implements Serializable {
        private static final long serialVersionUID = -2542070981569243131L;
        private Map<String, Wallet> walletMap = Maps.newHashMap();

        /**
         * 添加钱包
         * @param wallet
         */
        private void addWallet(Wallet wallet) {
            try {
                this.walletMap.put(wallet.getAddress(), wallet);
            } catch (Exception e) {
                System.out.println("添加钱包失败");
                e.printStackTrace();
            }
        }

        /**
         * 获取所有的钱包地址
         * @return
         */
        public Set<String> getAddresses() {
            if (walletMap == null || walletMap.size() == 0) {
                System.out.println("当前没有钱包创建");
                return null;
            }
            return walletMap.keySet();
        }

        /**
         * 根据钱包地址获取钱包数据
         * @param address 钱包地址
         * @return
         */
        Wallet getWallet(String address) {
            // 检查钱包地址是否合法
            try {
                Base58Check.base58ToBytes(address);
            } catch (Exception e) {
                System.out.println("获取钱包失败,钱包地址为address=" + address);
                e.printStackTrace();
                return null;
            }
            Wallet wallet = walletMap.get(address);
            if (wallet == null) {
                System.out.println("获取钱包失败,当前没有该地址的钱包 address=" + address);
                return null;
            }
            return wallet;
        }
    }
}
