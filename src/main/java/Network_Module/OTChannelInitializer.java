package Network_Module;

import Network_Module.KryoSerializer.NettyKryoDecoder;
import Network_Module.KryoSerializer.NettyKryoEncoder;
import com.esotericsoftware.kryo.Kryo;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

/**
 * 通道初始化用于处理与其它节点连接的初始化操作
 */
public class OTChannelInitializer<T extends ChannelInboundHandlerAdapter> extends ChannelInitializer<SocketChannel> {

    private T t;

    /**
     * 构造函数参数中必须是继承ChannelInboundHandlerAdapter的类
     * @param t
     */
    public OTChannelInitializer(T t) {
        this.t = t;
    }

    /**
     * 负责通道初始化话操作,设置消息处理器
     * @param socketChannel
     * @throws Exception
     */
    @Override
    protected void initChannel(SocketChannel socketChannel) throws Exception {
        ChannelPipeline p = socketChannel.pipeline();
//        p.addLast("decoder", new LengthFieldBasedFrameDecoder(10 * 1024 * 1024, 0, 8, 0, 8));
//        p.addLast("encoder0", new LengthFieldPrepender(8, false));
        //添加自己的消息处理器,其实这个模板类T就是ClientChannelHandler(针对客户端消息)或者ServerChannelHandler(针对服务端消息)
        // 以("\n")为结尾分割的 解码器
//        p.addLast("framer", new DelimiterBasedFrameDecoder(8192, Delimiters.lineDelimiter()));
        // 解码和编码，应和客户端一致
//        p.addLast("decoder", new StringDecoder());
//        p.addLast("encoder", new StringEncoder());
        p.addLast("encoder", new NettyKryoEncoder());
        p.addLast("decoder", new NettyKryoDecoder());
        p.addLast(t);
    }
}