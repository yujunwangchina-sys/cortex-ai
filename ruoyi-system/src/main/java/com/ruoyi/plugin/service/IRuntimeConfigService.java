package com.ruoyi.plugin.service;

import com.ruoyi.plugin.domain.RuntimeConfig;
import java.util.List;

/**
 * 运行时配置Service接口
 * 
 * @author ruoyi
 */
public interface IRuntimeConfigService 
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
     * @param runtimeType 运行时类型（python/node）
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
     * 保存或更新运行时配置
     * 
     * @param runtimeType 运行时类型
     * @param executablePath 可执行文件路径
     * @param customPathEnabled 是否启用自定义路径
     * @return 结果
     */
    public int saveOrUpdateConfig(String runtimeType, String executablePath, Boolean customPathEnabled);

    /**
     * 删除运行时配置
     * 
     * @param id 主键ID
     * @return 结果
     */
    public int deleteRuntimeConfigById(Long id);

    /**
     * 获取运行时的可执行文件路径（优先使用自定义配置）
     * 
     * @param runtimeType 运行时类型
     * @return 可执行文件路径，如果未配置则返回null
     */
    public String getExecutablePath(String runtimeType);
}
