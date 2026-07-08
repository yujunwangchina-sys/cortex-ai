package com.cortex.plugin.service;

import java.util.List;
import com.cortex.plugin.domain.AiPluginExecutionLog;

/**
 * AI插件执行日志Service接口
 * 
 * @author cortex
 */
public interface IAiPluginExecutionLogService 
{
    /**
     * 查询AI插件执行日志
     * 
     * @param logId AI插件执行日志主键
     * @return AI插件执行日志
     */
    public AiPluginExecutionLog selectAiPluginExecutionLogByLogId(Long logId);

    /**
     * 查询AI插件执行日志列表
     * 
     * @param aiPluginExecutionLog AI插件执行日志
     * @return AI插件执行日志集合
     */
    public List<AiPluginExecutionLog> selectAiPluginExecutionLogList(AiPluginExecutionLog aiPluginExecutionLog);

    /**
     * 根据会话ID查询日志列表
     * 
     * @param sessionId 会话ID
     * @return AI插件执行日志集合
     */
    public List<AiPluginExecutionLog> selectLogListBySessionId(String sessionId);

    /**
     * 删除AI插件执行日志
     * 
     * @param logId AI插件执行日志主键
     * @return 结果
     */
    public int deleteAiPluginExecutionLogByLogId(Long logId);

    /**
     * 批量删除AI插件执行日志
     * 
     * @param logIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteAiPluginExecutionLogByLogIds(Long[] logIds);

    /**
     * 清理指定天数之前的日志
     * 
     * @param days 天数
     * @return 结果
     */
    public int cleanOldLogs(int days);
}
