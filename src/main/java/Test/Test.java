package Test;

import java.text.DecimalFormat;
import java.util.*;

public class Test {
    public static void main(String args[])throws Exception{
//        long epochSecond = Instant.now().getEpochSecond();
//        System.out.println(epochSecond);

//        double randomDouble = randomDouble(0.9, 1.1, 2);
//        System.out.println(randomDouble);

//        BigDecimal db = new BigDecimal(Math.random() * (1.1 - 0.9) + 0.9);
//        DecimalFormat decimalFormat = new DecimalFormat(".00");//构造方法的字符格式这里如果小数不足2位,会以0补足.
//        String p = decimalFormat.format(db.floatValue());//format 返回的是字符串
//        float num = Float.valueOf(p);
//
//        //计算浮动后的值
//        float temp = Float.valueOf("0.1") * num;
//        String amount = String.valueOf(temp);
//
//        System.out.println(amount);

//        float num = (float) ((0.9 - 1.1) / 0.9);
//
//        System.out.println(num);

//        NettyNetworkModuleBootstrap_old nettyNetworkModuleBootstrap = new NettyNetworkModuleBootstrap_old();
//
//        //先启动服务端
//        nettyNetworkModuleBootstrap.init();
//        nettyNetworkModuleBootstrap.init();
//
//        CLI cli = new CLI();
//
//        String address1 = cli.createWallet();
//        String address2 = cli.createWallet();
//        String address3 = cli.createWallet();
//
//        //添加种子节点
//        Node node1 = new Node("localhost", 8080, Node.OUT);
////        node1.setAddress(address1);
//        Node node2 = new Node("localhost", 8080, Node.OUT);
////        node2.setAddress(address2);
//        Node node3 = new Node("localhost", 8080, Node.OUT);
////        node3.setAddress(address3);
//
//        NodeManager_old.getInstance().addSeedNode(node1);
//        NodeManager_old.getInstance().addSeedNode(node2);
//        NodeManager_old.getInstance().addSeedNode(node3);

//        Map<String,Node> map = new ConcurrentHashMap<>();
//        Node node = new Node("127.0.0.1", 8080, Node.IN);
//        node.setStatus(Node.WAIT);
//        map.put("1",node);
//        System.out.println("状态:"+node.getStatus());
//        map.get("1").setStatus(Node.CONNECTING);
//
//        int status = map.get("1").getStatus();
//        System.out.println("状态:"+status);

//        InetAddress addr = InetAddress.getLocalHost();
//        String ip=addr.getHostAddress().toString(); //获取本机ip
//        System.out.println(ip);
//        System.out.println("------------");
//
//        List<String> ipList = new ArrayList<String>();
//        try {
//            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
//            NetworkInterface networkInterface;
//            Enumeration<InetAddress> inetAddresses;
//            InetAddress inetAddress;
//            String ip1 = "";
//            while (networkInterfaces.hasMoreElements()) {
//                networkInterface = networkInterfaces.nextElement();
//                inetAddresses = networkInterface.getInetAddresses();
//                while (inetAddresses.hasMoreElements()) {
//                    inetAddress = inetAddresses.nextElement();
//                    if (inetAddress != null && inetAddress instanceof Inet4Address) { // IPV4
//                        ip1 = inetAddress.getHostAddress();
//                        ipList.add(ip1);
//                    }
//                }
//            }
//        } catch (SocketException e) {
//            e.printStackTrace();
//        }
//
//        for(int i = 0 ; i < ipList.size(); i++){
//            System.out.println(ip);
//        }
//
//        NetworkService.getInstance().init();

//
        //测试序列化与反序列化
//        String str = "张三李四王五张三李四王五张三李四王五张三李四王五张三李四王五";
//        System.out.println(str.getBytes().length);
//        byte[] serialize = SerializeUtils.serialize(str);
//
//        System.out.println(serialize.length);
//
//        Object object = serialize;
//        byte[] bytes = (byte[]) object;
//        System.out.println(bytes.length);
//
//        byte[] copy = new byte[bytes.length-3];
//        System.arraycopy(bytes,3,copy,0,copy.length);
//
//        String s = new String(bytes);
//        System.out.println(s);
//        String scopy = new String(copy);
//        System.out.println(scopy);

//        Message getNodeMessage = new Message("1", "127.0.0.1", 8080);
//        byte[] serialize1 = SerializeUtils.serialize(getNodeMessage);
//        System.out.println("序列化的长度:"+serialize1.length);
//
//        Object object = serialize1;
//        byte[] bytes = (byte[]) object;
//        Object deserialize = SerializeUtils.deserialize(bytes);
//
//        if(deserialize instanceof Message){
//            Message message = (Message)deserialize;
//            System.out.println(message.getIp());
//            System.out.println(message.getMessageType());
//            System.out.println(message.getPort());
//        }

//        NetworkService.getInstance().init();

//        Map<String,String> map = new HashMap<>();
//        for(Map.Entry<String,String> entry : map.entrySet()){
//            entry.getKey();
//        }
//
//        map.remove(null);

//        Map<String, Long> tm = new TreeMap<>();
//        tm.put("a", (long) 1);   tm.put("b", (long) 2);
//        tm.put("c", (long) 3);   tm.put("d", (long) 4);
//        //这里将map.entrySet()转换成list
//        List<Map.Entry<String,Long >> list = new ArrayList<Map.Entry<String,Long>>(tm.entrySet());
//        //然后通过比较器来实现排序
//        Collections.sort(list,new Comparator<Map.Entry<String,Long>>() {
//            //降序排序
//            @Override
//            public int compare(Map.Entry<String, Long> o1,
//                               Map.Entry<String, Long> o2) {
//                return (int)(o2.getValue()- (o1.getValue()));
//            }
//        });
//
//        List<Map.Entry<String,Long>> list1 = new ArrayList<>();
//        for(int i = 0 ; i < list.size() ; i++){
//            list1.add(list.get(i));
//        }
////        for(Map.Entry<String,Long> mapping : list){
////            System.out.println(mapping.getKey()+":"+mapping.getValue());
////
////            list1.add(mapping);
////        }
//
//        for(Map.Entry<String,Long> mapping : list1){
//            System.out.println(mapping.getValue());
//        }

//        URL resource = new Test().getClass().getResource("/genesis-block.json");
//        File file = new File(resource.getPath());
//        Long filelength = file.length();
//        byte[] filecontent = new byte[filelength.intValue()];
//        try {
//            FileInputStream in = new FileInputStream(file);
//            in.read(filecontent);
//            in.close();
//            String s = new String(filecontent, "utf-8");
//            JSONObject jsonObject = JSONObject.fromObject(s);
//            JSONArray txs = (JSONArray) jsonObject.get("txs");
//
//            for(int i = 0 ; i < txs.size() ; i ++){
//                JSONObject txJson = (JSONObject) txs.get(i);
//                String address = txJson.get("address").toString();
//                String amount = txJson.get("amount").toString();
//                System.out.println(address+":"+amount);
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }

//        System.out.println(new Random().nextInt(2));
//        CLI cli = new CL


//        String string1 = new String("hello world");
//        String string2 = new String("hello world");
//
//        byte[] bytes1 = string1.getBytes();
//        byte[] bytes2 = string2.getBytes();
//        System.out.println("bytes1 == bytes2 :" + (bytes1 == bytes2));
//        System.out.println("bytes1.equals(bytes2):" + bytes1.equals(bytes2));
//
//        Map<byte[],byte[]> map = new TreeMap<>(new ByteArrayComparator ());
//        map.put(bytes1,bytes1);
//
//        System.out.println("map.containsKey(bytes2) = " + map.containsKey(bytes2));

        long time = Long.valueOf("1535444411000");
        long cur = System.currentTimeMillis();
        int round = (int) ((cur - time)/1000 % 210 / 10);
        System.out.println(cur);
        System.out.println(cur - time);
        System.out.println(round);
    }
    static class ByteArrayComparator implements Comparator<byte[]> {
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
    }
    private static double randomDouble(double start,double end,int decimal){
        DecimalFormat df=new DecimalFormat("0.0");
        double rtnn = start + Math.random() * (end - start);
        if (rtnn == start || rtnn == end) {
            return randomDouble(start, end,decimal);
        }
        return new Double(df.format(rtnn).toString());
    }
}
