package Network_Module.NettyServer;

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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.SocketChannel;

import java.util.List;
import java.util.Map;

/**
 * 服务端处理器
 */
@ChannelHandler.Sharable
public class ServerChannelHandler extends ChannelInboundHandlerAdapter {

    private NetworkService networkService = NetworkService.getInstance();
    private ConsensusService consensusService = ConsensusService.getInstance();

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        System.out.println("服务端处理:服务通道注册成功");
    }
    /**
     * 这里的服务端接收的节点为输入节点,每一个Netty客户端来连接时都会触发这个方法的运行,因此这里的SocketChannel对象对应着每一个Netty客户端与本地服务端之间的通信
     * 这里可以直接向客户端发送相应数据
     * 服务端不管在哪里发送数据客户端都会接收到
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("服务端处理:服务通道激活成功");

        SocketChannel socketChannel = (SocketChannel) ctx.channel();

        String remoteIP = socketChannel.remoteAddress().getHostString();
        int port = socketChannel.remoteAddress().getPort();
        String channelId = socketChannel.id().asLongText();

        System.out.println("服务端处理:服务端通道激活 ----服务端通道id:"+channelId+" ----服务端接收的客户端ip:"+remoteIP+"----客户端端口:"+port);
        //判断自己是否需要新的输入节点连接进来,需要的话发送握手信息,不需要的话则发送握手失败的消息(失败的消息中包含自己已经成功连接的一个输出节点信息)
        if(networkService.getInNodesMap().size() >= NetworkConstant.MAX_IN_NODES){
            //输入节点已经饱和了,返回一个握手失败的消息
            Node randomOutNode = networkService.getRandomOutNode();
            Message handShakeFailMessage = new Message(NetworkConstant.HANDSHAKE_FAIL__MESSAGE);
            handShakeFailMessage.setIp(randomOutNode.getIp());
            handShakeFailMessage.setPort(randomOutNode.getPort());
            byte[] bytes = SerializeUtils.serialize(handShakeFailMessage);
            ChannelFuture channelFuture = socketChannel.writeAndFlush(bytes);
            if(channelFuture.isSuccess()){
                System.out.println("服务端处理:激活成功,但输入节点连接数已满.向"+remoteIP+"----客户端发送握手失败消息!");
            }
        }else{
            //通道激活则向客户端发送握手的消息
            Message handShakeMessage = new Message(NetworkConstant.HANDSHAKE_SUCCESS_MESSAGE);
            byte[] bytes = SerializeUtils.serialize(handShakeMessage);
            ChannelFuture channelFuture = socketChannel.writeAndFlush(bytes);
            if(channelFuture.isSuccess()){
                System.out.println("服务端处理:激活成功,向"+remoteIP+"----客户端发送握手消息!");
            }
        }


    }
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        System.out.println("服务端处理:服务通道出现异常关闭");
        SocketChannel socketChannel = (SocketChannel) ctx.channel();
        String channelId = socketChannel.id().asLongText();
        String ip = socketChannel.remoteAddress().getHostString();
        ctx.channel().close();
        networkService.removeInNodeByChannelId(channelId);
        System.out.println("服务端处理:删除输入节点:"+ip);
        NioChannelMap.remove(channelId);

        cause.printStackTrace();
    }
    /**
     * 读取客户端发来的数据
     * @param ctx
     * @param msg
     * @throws Exception
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

        SocketChannel socketChannel = (SocketChannel) ctx.channel();
        String channelId = socketChannel.id().asLongText();
        String remoteIP = socketChannel.remoteAddress().getHostString();
        int port = socketChannel.remoteAddress().getPort();

        //将接收的数据反序列化
        byte[] bytes = (byte[])msg;
        Object deserObject = SerializeUtils.deserialize(bytes);
        //判断反序列化之后的对象(交易对象,区块对象,消息对象)
        String objectStr = CheckClass.getInstance().checkClassAndGet(deserObject);

        //处理Message对象
        if(objectStr != "-1" && objectStr.equals("0")){
            Message message = (Message) deserObject;
            Message backMessage = null;
            //握手消息
            if(NetworkConstant.HANDSHAKE_SUCCESS_MESSAGE.equals(message.getMessageType())){
                System.out.println("服务端处理:握手成功!");
                //创建一个输入节点保存
                Node inNode = new Node(remoteIP,port,Node.IN);
                inNode.setSeverPort(message.getPort());
                inNode.setChannelId(channelId);
                //设置节点为握手状态
                inNode.setStatus(Node.HANDSHAKE);
                //将通道加入集合中,该通道保持着与客户端之间的通信
                NioChannelMap.add(channelId, socketChannel);
                networkService.addInNode(inNode);

                //同时创建一个未连接的输出节点,加入自己的未连接输出节点集合中
                Node outNode = new Node(message.getIp(),message.getPort());
                outNode.setSeverPort(message.getPort());
                networkService.addNeedConnServerNode(outNode);
                //获取新节点消息
            }else if(NetworkConstant.GET_NEW_CONNECTION_NODE_MESSAGE.equals(message.getMessageType())){
                //随机从输入节点中获取一个返回给请求端
                Node getNewNode = null;
                getNewNode = networkService.getRandomInNode();
                if(getNewNode == null){
                    //随机从输出节点中获取一个节点
                    getNewNode = networkService.getRandomOutNode();
                    if(getNewNode == null){
                        //返回获取新节点失败的消息
                        backMessageFail(NetworkConstant.GET_NEW_CONNECTION_NODE_FAIL_MESSAGE,socketChannel,backMessage);
                    }else{
                        //返回获取新节点成功的消息
                        backMessageSucess(NetworkConstant.GET_NEW_CONNECTION_NODE_SUCCESS_MESSAGE , socketChannel , backMessage , getNewNode);
                    }
                }else{
                    //返回获取新节点成功的消息
                    backMessageSucess(NetworkConstant.GET_NEW_CONNECTION_NODE_SUCCESS_MESSAGE , socketChannel , backMessage , getNewNode);

                }
            //获取最新区块的高度消息,一测通过
            }else if(NetworkConstant.GET_BLOCK_HEIGHT_MESSAGE.equals(message.getMessageType())){
                long lastBlockHeight = RocksDBUtils.getInstance().getlastBlockHeight();
                backMessage = new Message(NetworkConstant.GET_BLOCK_HEIGHT_MESSAGE_SUCCESS);
                backMessage.setLastBlockHeight(lastBlockHeight);
                System.out.println("服务端处理:-------------------------------------服务端处理获取最新区块高度的消息");
                backMessage(socketChannel,backMessage);
            //获取一个指定区块的消息,一测通过
            }else if(NetworkConstant.GET_BLOCK_MESSAGE.equals(message.getMessageType())){
                System.out.println("服务端处理:处理一个获取同步区块的消息");
                backABlock(socketChannel,message);
            //收到一个共识节点消息
            }else if(NetworkConstant.CONSENSUS_NODE_MESSAGE.equals(message.getMessageType())){
                Node node = new Node(message.getIp(),message.getPort(),Node.IN);
                node.setChannelId(channelId);
                node.setNodeStyle(Node.PRODUCERNODE);
                node.setConsensusAddress(message.getConsensusAddress());

                NioChannelMap.add(channelId,socketChannel);

                networkService.addConsensusNode(message.getConsensusAddress(),node);
            }
        //处理Transaction交易对象
        }else if(objectStr != "-1" && objectStr.equals("2")){
            Transaction tx = (Transaction) deserObject;
            //验证交易对象
            Blockchain blockchain = new Blockchain(RocksDBUtils.getInstance().getLastBlockHash());
            boolean isOK = blockchain.verifyTransactions(tx);
            if(isOK){
                //将交易加入交易池
                Blockchain.putTx(tx);
            }
        //添加区块到区块链中
        }else if(objectStr != "-1" && objectStr.equals("1")){
            Block block = (Block) deserObject;
            boolean isOK = block.verifyBlock(block);
            if(isOK){
                consensusService.addDownloadBlock(block);
            }
        //接收共识节点出块的顺序
        }else if(objectStr != "-1" && objectStr.equals("3")){
            List<Bean> consensusRoundList = (List<Bean>) deserObject;
//            System.out.println("服务端处理:接收到共识顺序:"+consensusRoundList.size());
            consensusService.setConsensusRoundList(consensusRoundList);
        }
    }

    /**
     * 返回一个区块,一测通过
     * @param socketChannel
     * @param message
     */
    private void backABlock(SocketChannel socketChannel, Message message) {
        long count = message.getCount();
        String lastBlockHash = RocksDBUtils.getInstance().getLastBlockHash();
        if(lastBlockHash.equals("")){
            System.out.println("服务端处理:获取新区块时服务端得到的最新区块的哈希id为空");
            return ;
        }
        Blockchain blockchain = new Blockchain(lastBlockHash);
        Blockchain.BlockchainIterator blockchainIterator = blockchain.getBlockchainIterator();
        long currentBlockHeight = RocksDBUtils.getInstance().getlastBlockHeight();
        //网络区块高度必须和现在的区块高度相同
        if(currentBlockHeight == message.getLastBlockHeight()){

            while(blockchainIterator.hashNext()){
                Block block = blockchainIterator.next();
                //记录从链尾迭代到链中指定的区块就是返回的区块
                count--;
                if(block != null && count == 0){
                    byte[] bytes = SerializeUtils.serialize(block);
                    socketChannel.writeAndFlush(bytes);
                }
                System.out.println("服务端处理:"+"返回的区块高度:"+block.getHeight());
            }
        }
    }

    /**
     * 返回获取新连接节点失败消息
     * @param socketChannel
     */
    private void backMessageFail(String messageType , SocketChannel socketChannel , Message message) {
        message = new Message(messageType);

        backMessage(socketChannel,message);
    }

    /**
     * 返回新获取一个新节点成功的消息
     * @param messageType
     * @param socketChannel
     * @param message
     * @param node
     */
    public void backMessageSucess(String messageType , SocketChannel socketChannel , Message message , Node node){
        message = new Message(messageType);
        message.setIp(node.getIp());
        message.setPort(node.getSeverPort());

        backMessage(socketChannel,message);
    }
    public void backMessage(SocketChannel socketChannel , Message message){
        byte[] backBytes = SerializeUtils.serialize(message);
        socketChannel.writeAndFlush(backBytes);
    }
}
