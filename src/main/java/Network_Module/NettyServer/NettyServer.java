package Network_Module.NettyServer;

import Network_Module.NetworkConstant;
import Network_Module.NetworkService;
import Network_Module.Node;
import Network_Module.OTChannelInitializer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Random;

/**
 * Netty服务端
 */
public class NettyServer {

    //服务端端口,[9000-49152]之间的任意一个数值
    private int port;
    //表示Netty服务端是否启动成功
    private boolean isStart = false;
    //服务端启动引导类
    private ServerBootstrap serverBootstrap;
    //用来接收进来的连接的多线程事件循环器
    private static EventLoopGroup boss;
    //用来处理已经被接收的连接的多线程事件循环器,﻿一旦‘boss’接收到连接，就会把连接信息注册到‘worker’上
    private static EventLoopGroup worker;

    public NettyServer() {
        //判断是否已经设置了本地服务端的端口,默认为8088表示没有在代码中设置过
        if(NetworkConstant.serverPort == 8088){
            //服务端端口,[9000-49152]之间的任意一个数值
            this.port = new Random().nextInt(40152)+9000;
            NetworkConstant.serverPort = port;
        }else{
            port = NetworkConstant.serverPort;
        }
        //获取本机ip,设置到常量类中
        InetAddress addr = null;
        try {
            addr = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        String ip = addr.getHostAddress().toString();
        NetworkConstant.serverIp = ip;

    }

    public void init() {
        //实例化
        boss = new NioEventLoopGroup();
        worker = new NioEventLoopGroup();
        serverBootstrap = new ServerBootstrap();//ServerBootstrap 是一个启动NIO服务的辅助启动类 你可以在这个服务中直接使用Channel
        //设置服务端相关的配置
        serverBootstrap.group(boss, worker)//﻿这一步是必须的，如果没有设置group将会报java.lang.IllegalStateException: group not set异常
                .channel(NioServerSocketChannel.class)//﻿ServerSocketChannel以NIO的selector为基础进行实现的，用来接收新的连接,﻿这里告诉Channel如何获取新的连接
                .option(ChannelOption.SO_BACKLOG, 128)//﻿option()是提供给NioServerSocketChannel用来接收进来的连接
                .childOption(ChannelOption.TCP_NODELAY, true)//childOption()是提供给由父管道ServerChannel接收到的连接,这里是非延迟
                .childOption(ChannelOption.SO_KEEPALIVE, true)//这里是保持连接
                .childHandler(new OTChannelInitializer<>(new ServerChannelHandler()));//初始化管道并设置管道事件处理器
    }

    /**
     * 服务端启动,在本节点所在的机器上面开启一个端口供客户端访问
     * @throws InterruptedException
     */
    public void start() throws InterruptedException {
        try {
            System.out.println("NettyServer:开始启动Netty服务端");
            // 开始启动Netty服务端
            ChannelFuture future = serverBootstrap.bind(port).sync();
            // 本地Netty服务启动成功之后去连接种子节点
            if(future.isSuccess()){
                isStart = true;
            }
            //根据默认设置的种子节点的ip和端口号启动相应的Netty客户端去连接种子节点
            if(isStart){
                System.out.println("NettyServer:Netty服务端启动成功,开始连接种子节点");
                List<Node> seedsList = NetworkService.getInstance().getSeedsList();
                for(Node node : seedsList){
                    System.out.println("NettyServer:连接种子节点:"+node.getIp());
                    //连接种子节点
                    NetworkService.getInstance().connOutNode(node);
                }
            }
            // 等待服务端同步停止
            future.channel().closeFuture().sync();
            System.out.println("NettyServer:服务端停止运行");
        } catch (InterruptedException e) {
            throw e;
        } finally {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }

    public boolean isStart() {
        return isStart;
    }

    public void setStart(boolean start) {
        isStart = start;
    }
}