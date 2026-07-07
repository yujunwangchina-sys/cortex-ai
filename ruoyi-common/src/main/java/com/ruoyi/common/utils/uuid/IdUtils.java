package com.ruoyi.common.utils.uuid;

/**
 * ID生成器工具类
 * 
 * @author ruoyi
 */
public class IdUtils
{
    /**
     * 雪花算法ID生成器实例（单例）
     * workerId: 1, datacenterId: 1
     */
    private static final SnowflakeIdWorker snowflakeIdWorker = new SnowflakeIdWorker(1, 1);

    /**
     * 获取随机UUID
     * 
     * @return 随机UUID
     */
    public static String randomUUID()
    {
        return UUID.randomUUID().toString();
    }

    /**
     * 简化的UUID，去掉了横线
     * 
     * @return 简化的UUID，去掉了横线
     */
    public static String simpleUUID()
    {
        return UUID.randomUUID().toString(true);
    }

    /**
     * 获取随机UUID，使用性能更好的ThreadLocalRandom生成UUID
     * 
     * @return 随机UUID
     */
    public static String fastUUID()
    {
        return UUID.fastUUID().toString();
    }

    /**
     * 简化的UUID，去掉了横线，使用性能更好的ThreadLocalRandom生成UUID
     * 
     * @return 简化的UUID，去掉了横线
     */
    public static String fastSimpleUUID()
    {
        return UUID.fastUUID().toString(true);
    }

    /**
     * 获取雪花算法生成的ID
     * 
     * @return 雪花算法ID(Long类型)
     */
    public static long getSnowflakeId()
    {
        return snowflakeIdWorker.nextId();
    }

    /**
     * 获取雪花算法生成的ID字符串
     * 
     * @return 雪花算法ID的字符串形式
     */
    public static String getSnowflakeIdStr()
    {
        return String.valueOf(snowflakeIdWorker.nextId());
    }
}
