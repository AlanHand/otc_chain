package Network_Module;

import io.netty.channel.socket.SocketChannel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 存放SocketChannel的通道集合对象
 */
public class NioChannelMap {

    //用并发的HashMap存储连接通道,key为channelId,value为ScoketChannel
    private static Map<String, SocketChannel> map = new ConcurrentHashMap<>();

    public static void add(String channelId, SocketChannel channel) {
        map.put(channelId, channel);
    }

    public static SocketChannel get(String channelId) {
        return map.get(channelId);
    }

    public static void remove(String channelId) {
        map.remove(channelId);
    }

    public static boolean containsKey(String channelId) {
        return map.containsKey(channelId);
    }

    public static Map<String, SocketChannel> channels() {
        return map;
    }
}