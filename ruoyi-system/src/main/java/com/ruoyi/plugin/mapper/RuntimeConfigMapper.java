package com.ruoyi.plugin.mapper;

import com.ruoyi.plugin.domain.RuntimeConfig;
import java.util.List;

/**
 * 运行时配置Mapper接口
 * 
 * @author ruoyi
 */
public interface RuntimeConfigMapper 
{
    /**
     * 查询运行时配置
     * 
     * @param id 主键ID
     * @return 运行时配置
     */
    public RuntimeConfig selectRuntimeConfigById(Long id);

    /**
     * 根据运行时类型查询配置
     * 
     * @param runtimeType 运行时类型
     * @return 运行时配置
     */
    public RuntimeConfig selectRuntimeConfigByType(String runtimeType);

    /**
     * 查询运行时配置列表
     * 
     * @param runtimeConfig 运行时配置
     * @return 运行时配置集合
     */
    public List<RuntimeConfig> selectRuntimeConfigList(RuntimeConfig runtimeConfig);

    /**
     * 新增运行时配置
     * 
     * @param runtimeConfig 运行时配置
     * @return 结果
     */
    public int insertRuntimeConfig(RuntimeConfig runtimeConfig);

    /**
     * 修改运行时配置
     * 
     * @param runtimeConfig 运行时配置
     * @return 结果
     */
    public int updateRuntimeConfig(RuntimeConfig runtimeConfig);

    /**
     * 删除运行时配置
     * 
     * @param id 主键ID
     * @return 结果
     */
    public int deleteRuntimeConfigById(Long id);
}
