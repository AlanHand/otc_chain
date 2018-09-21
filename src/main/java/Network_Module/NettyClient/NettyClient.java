package Network_Module.NettyClient;

import Account_Module.util.SerializeUtils;
import Network_Module.Message;
import Network_Module.NetworkConstant;
import Network_Module.Node;
import Network_Module.OTChannelInitializer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

/**
 * Netty客户端,负责连接到指定的节点
 */
public class NettyClient {

    //﻿NioEventLoopGroup 是用来处理I/O操作的多线程事件循环器
    public static EventLoopGroup worker = new NioEventLoopGroup();

    //Netty的客户端启动引导类
    Bootstrap boot;
    //socket通道
    private SocketChannel socketChannel;
    private String ip;
    private int port;


    public NettyClient( String ip , int port) {
        this.ip = ip;
        this.port = port;
        boot = new Bootstrap();
        boot.group(worker)//这一步必须的,用于处理与服务端的连接
                .channel(NioSocketChannel.class)//通道为NIO通道
                .option(ChannelOption.TCP_NODELAY, true)//tcp连接非延迟
                .option(ChannelOption.SO_KEEPALIVE, true)//保持连接
                .handler(new OTChannelInitializer<>(new ClientChannelHandler()));//通道初始化并加入自定义消息处理器,这里因为是客户端,因此添加的是客户端消息处理器
    }

    /**
     * 开始连接
     */
    public void start() {
        try {
            //调用Bootstrap同步连接
            ChannelFuture future = boot.connect(ip, port).sync();
//            ChannelFuture future = boot.connect("localhost", 8080).sync();
            //创建输出节点
            Node node = new Node(ip, port,Node.OUT);
            node.setStatus(Node.WAIT);
            if (future.isSuccess()) {
                socketChannel = (SocketChannel) future.channel();
                //连接成功获取SocketChannel通道,发送握手数据
                System.out.println(NetworkConstant.serverIp+":发送握手数据");
                //通道激活则向服务端发送握手的消息
                Message handShakeMessage = new Message(NetworkConstant.HANDSHAKE_SUCCESS_MESSAGE);
                //这里的握手消息中包含本地Netty服务端的ip地址和端口号,服务端在接收到这个握手消息时就可创建一个输出节点进行保存
                handShakeMessage.setIp(NetworkConstant.serverIp);
                handShakeMessage.setPort(NetworkConstant.serverPort);
                //握手消息中可以设置本机的Netty服务的端口号(因为客户端发送给服务端消息时并没有发送本地Netty服务端的端口,发的仅仅是本地客户端的端口)
                byte[] bytes = SerializeUtils.serialize(handShakeMessage);
                socketChannel.writeAndFlush(bytes);

            }

            //同步关闭,只有当Server关闭连接时才会触发这里的运行,因此主线程直接停止在这里的运行
            future.channel().closeFuture().sync();

        } catch (Exception e) {
            e.printStackTrace();
            if (socketChannel != null) {
                socketChannel.close();
            }
        }
    }
}