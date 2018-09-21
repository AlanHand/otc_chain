package Account_Module.util;

import org.apache.commons.lang3.ArrayUtils;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.stream.Stream;

/**
 * 字节数组工具类
 *
 * @author dingkonghua
 * @date 2018/07/27
 */
public class ByteUtils {

    /**
     * 将多个字节数组合并成一个字节数组
     *
     * @param bytes
     * @return
     */
    public static byte[] merge(byte[]... bytes) {
        Stream<Byte> stream = Stream.of();
        for (byte[] b : bytes) {
            stream = Stream.concat(stream, Arrays.stream(ArrayUtils.toObject(b)));
        }
        return ArrayUtils.toPrimitive(stream.toArray(Byte[]::new));
    }

    /**
     * long 类型的值转 byte[]
     *
     * @param val
     * @return
     */
    public static byte[] toBytes(long val) {
        return ByteBuffer.allocate(Long.BYTES).putLong(val).array();
    }

}
