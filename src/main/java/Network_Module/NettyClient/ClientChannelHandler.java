package Network_Module.NettyClient;

import Account_Module.Transaction.Transaction;
import Account_Module.util.SerializeUtils;
import Consensus_Module.Bean;
import Consensus_Module.ConsensusConstant;
import Consensus_Module.dpos_pbft.ConsensusService;
import DB_Module.block.Block;
import DB_Module.block.Blockchain;
import DB_Module.RocksDBUtils;
import Network_Module.*;
import Network_Module.Message;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.SocketChannel;

import java.io.UnsupportedEncodingException;
import java.util.List;

/**
 * Netty客户端处理网络事件的类
 * ChannelInboundHandlerAdapter是ChannelInboundHandler的一个简单实现，
 * 默认情况下不会做任何处理，只是简单的将操作通过fire*方法传递到ChannelPipeline中的下一个ChannelHandler中让链中的下一个ChannelHandler去处理。
 * 需要注意的是信息经过channelRead方法处理之后不会自动释放（因为信息不会被自动释放所以能将消息传递给下一个ChannelHandler处理）
 */
public class ClientChannelHandler extends ChannelInboundHandlerAdapter {

    private NetworkService networkService = NetworkService.getInstance();
    public ClientChannelHandler() {
    }

    /**
     * 通道激活成功时调用的方法,这里不向服务端发送数据,只处理通道的信息
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("客户端处理:客户端激活成功");

    }

    /**
     * 通道失去时调用的方法
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx){
        try{

            SocketChannel channel = (SocketChannel) ctx.channel();
            String channelId = channel.id().asLongText();
            String remoteIP = channel.remoteAddress().getHostString();
            //关闭通道
            ctx.channel().close();
            networkService.removeOutNodeByChannelId(channelId);
            System.out.println("客户端处理:与"+remoteIP+"服务端断开连接,从本地移除该节点");
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * 信息经过channelRead方法处理之后不会自动释放（因为信息不会被自动释放所以能将消息传递给下一个ChannelHandler处理）
     * 因此下面将接收的数据拷贝一份之后才进行处理
     * 这里处理接收的数据,并且发送数据给服务端
     * @param ctx
     * @param msg
     * @throws UnsupportedEncodingException
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws UnsupportedEncodingException {

//        System.out.println("客户端接收的数据:"+msg);

        SocketChannel socketChannel = (SocketChannel) ctx.channel();

        String remoteIP = socketChannel.remoteAddress().getHostString();
        //此时的端口确实是服务端的端口,并且每次本机去连接同一ip地址的时候这个服务端口号是不会变的
        int port = socketChannel.remoteAddress().getPort();
        String channelId = socketChannel.id().asLongText();

        //根据不同的消息类型处理不同的业务逻辑
        //将接收的数据反序列化
        byte[] bytes = (byte[])msg;
        Object deserObject = SerializeUtils.deserialize(bytes);
        //判断反序列化之后的对象(交易对象,区块对象,消息对象)
        String objectStr = CheckClass.getInstance().checkClassAndGet(deserObject);


        //处理Message消息对象
        if(objectStr != "-1" && objectStr.equals("0")){
            Message message = (Message) deserObject;
            //握手成功的消息,服务端成为自己的输出节点
            if(NetworkConstant.HANDSHAKE_SUCCESS_MESSAGE.equals(message.getMessageType())){
                System.out.println("客户端处理:握手成功!");
                //创建一个输出节点保存
                Node node = new Node(remoteIP,port,Node.OUT);
                node.setChannelId(channelId);
                //设置节点为握手状态
                node.setStatus(Node.HANDSHAKE);
                //将通道加入集合中,该通道保持着与客户端之间的通信
                NioChannelMap.add(channelId, socketChannel);
                //将连接成功的节点加入输出节点集合
                networkService.addOutNode(node);
            //握手失败消息,得到一个可以连接的节点
            }else if(NetworkConstant.HANDSHAKE_FAIL__MESSAGE.equals(message.getMessageType())){
                System.out.println("客户端处理:握手失败!");
                //从失败消息中得到可以连接别的服务端的信息
                addNeedConnServerNode(message);
            //获取新节点成功消息
            }else if(NetworkConstant.GET_NEW_CONNECTION_NODE_SUCCESS_MESSAGE.equals(message.getMessageType())){
                //从获取新节点成功的消息中得到可以连接别的服务端的信息
                addNeedConnServerNode(message);
            //返回区块高度的消息
            }else if(NetworkConstant.GET_BLOCK_HEIGHT_MESSAGE_SUCCESS.equals(message.getMessageType())){
                System.out.println("客户端处理:客户端处理:-------------------------------------获取服务端返回的区块最新高度");
                //设置区块高度
                NodeSynchronousService.getInstance().setNetworkBlockHeight(message.getLastBlockHeight());
                //设置获取区块高度的那个通道id,方便在同步数据的时候使用
                NodeSynchronousService.getInstance().setChannelId(channelId);
            //接收到prepare消息
            }else if(NetworkConstant.CONSENSUS_PREPARE_MESSAGE.equals(message.getMessageType())){
                //如果接收到的prepare消息大于等于 (共识节点数/3 + 1) , 则不再处理prepare消息
                if(ConsensusService.getInstance().getPrepareMessageMap().size() > (((ConsensusConstant.CONSENSUSNODECOUNT -1)/3)+1)){

                }else{
                    //得先验证再加入到PrepareMessageMap集合中
                    ConsensusService.getInstance().processPrepareMessage(message);
                }
            }else if(NetworkConstant.CONSENSUS_COMMITED_MESSAGE.equals(message.getMessageType())){
                //如果接收到的prepare消息大于等于 (共识节点数/3 + 1) , 则不再处理prepare消息
                if(ConsensusService.getInstance().getCommitedMessageMap().size() > ((((ConsensusConstant.CONSENSUSNODECOUNT-1)/3) * 2)+1)){

                }else{
                    //得先验证再加入到CommitedMessageMap集合中
                    ConsensusService.getInstance().processCommitedMessage(message);
                }
            }
        //处理下载同步的Block对象,一测通过
        }else if(objectStr != "-1" && objectStr.equals("1")){
            Block block = (Block) deserObject;
            //将服务端返回的区块验证之后加入到磁盘中
            boolean isOK = block.verifyBlock(block);
            if(isOK){
                ConsensusService.getInstance().addDownloadBlock(block);
            }
        //处理Transaction对象
        }else if(objectStr != "-1" && objectStr.equals("2")){
            Transaction tx = (Transaction) deserObject;
            //验证交易对象
            Blockchain blockchain = new Blockchain(RocksDBUtils.getInstance().getLastBlockHash());
            boolean isOK = blockchain.verifyTransactions(tx);
            if(isOK){
                //将交易加入交易池
                Blockchain.putTx(tx);
            }
        //接收共识节点出块的顺序
        }else if(objectStr != "-1" && objectStr.equals("3")){
            List<Bean> consensusRoundList = (List<Bean>) deserObject;
//            System.out.println("客户端处理:接收到共识顺序:"+consensusRoundList.size());
            ConsensusService.getInstance().setConsensusRoundList(consensusRoundList);
        }
    }

    /**
     * 存放一个可连接的节点
     * @param message
     */
    private void addNeedConnServerNode(Message message) {
        Node node = new Node(message.getIp(), message.getPort());
        node.setSeverPort(message.getPort());
        networkService.addNeedConnServerNode(node);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        SocketChannel channel = (SocketChannel) ctx.channel();
        String channelId = channel.id().asLongText();
        String remoteIP = channel.remoteAddress().getHostString();
        ctx.channel().close();
        networkService.removeInNodeByChannelId(channelId);
        System.out.println("客户端处理:与"+remoteIP+"服务端通信发生异常,关闭通道并且从缓存中移除通道");
        cause.printStackTrace();
    }

}
