package com.ruoyi.plugin.service.impl;

import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.plugin.domain.RuntimeConfig;
import com.ruoyi.plugin.mapper.RuntimeConfigMapper;
import com.ruoyi.plugin.service.IRuntimeConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

/**
 * 运行时配置Service业务层处理
 * 
 * @author ruoyi
 */
@Service
public class RuntimeConfigServiceImpl implements IRuntimeConfigService 
{
    private static final Logger log = LoggerFactory.getLogger(RuntimeConfigServiceImpl.class);

    @Autowired
    private RuntimeConfigMapper runtimeConfigMapper;

    /**
     * 查询运行时配置
     * 
     * @param id 主键ID
     * @return 运行时配置
     */
    @Override
    public RuntimeConfig selectRuntimeConfigById(Long id)
    {
        return runtimeConfigMapper.selectRuntimeConfigById(id);
    }

    /**
     * 根据运行时类型查询配置
     * 
     * @param runtimeType 运行时类型
     * @return 运行时配置
     */
    @Override
    public RuntimeConfig selectRuntimeConfigByType(String runtimeType)
    {
        return runtimeConfigMapper.selectRuntimeConfigByType(runtimeType);
    }

    /**
     * 查询运行时配置列表
     * 
     * @param runtimeConfig 运行时配置
     * @return 运行时配置
     */
    @Override
    public List<RuntimeConfig> selectRuntimeConfigList(RuntimeConfig runtimeConfig)
    {
        return runtimeConfigMapper.selectRuntimeConfigList(runtimeConfig);
    }

    /**
     * 新增运行时配置
     * 
     * @param runtimeConfig 运行时配置
     * @return 结果
     */
    @Override
    public int insertRuntimeConfig(RuntimeConfig runtimeConfig)
    {
        runtimeConfig.setCreateTime(DateUtils.getNowDate());
        return runtimeConfigMapper.insertRuntimeConfig(runtimeConfig);
    }

    /**
     * 修改运行时配置
     * 
     * @param runtimeConfig 运行时配置
     * @return 结果
     */
    @Override
    public int updateRuntimeConfig(RuntimeConfig runtimeConfig)
    {
        runtimeConfig.setUpdateTime(DateUtils.getNowDate());
        return runtimeConfigMapper.updateRuntimeConfig(runtimeConfig);
    }

    /**
     * 保存或更新运行时配置
     * 
     * @param runtimeType 运行时类型
     * @param executablePath 可执行文件路径
     * @param customPathEnabled 是否启用自定义路径
     * @return 结果
     */
    @Override
    public int saveOrUpdateConfig(String runtimeType, String executablePath, Boolean customPathEnabled)
    {
        RuntimeConfig config = runtimeConfigMapper.selectRuntimeConfigByType(runtimeType);
        
        if (config == null) {
            // 新增
            config = new RuntimeConfig();
            config.setRuntimeType(runtimeType);
            config.setExecutablePath(executablePath);
            config.setCustomPathEnabled(customPathEnabled);
            config.setStatus("0");
            config.setCreateTime(DateUtils.getNowDate());
            return runtimeConfigMapper.insertRuntimeConfig(config);
        } else {
            // 更新
            config.setExecutablePath(executablePath);
            config.setCustomPathEnabled(customPathEnabled);
            config.setUpdateTime(DateUtils.getNowDate());
            return runtimeConfigMapper.updateRuntimeConfig(config);
        }
    }

    /**
     * 删除运行时配置
     * 
     * @param id 主键ID
     * @return 结果
     */
    @Override
    public int deleteRuntimeConfigById(Long id)
    {
        return runtimeConfigMapper.deleteRuntimeConfigById(id);
    }

    /**
     * 获取运行时的可执行文件路径（优先使用自定义配置）
     * 
     * @param runtimeType 运行时类型
     * @return 可执行文件路径，如果未配置则返回null
     */
    @Override
    public String getExecutablePath(String runtimeType)
    {
        RuntimeConfig config = runtimeConfigMapper.selectRuntimeConfigByType(runtimeType);
        
        if (config != null && Boolean.TRUE.equals(config.getCustomPathEnabled())) {
            String path = config.getExecutablePath();
            if (path != null && !path.trim().isEmpty()) {
                // 验证路径是否存在
                File file = new File(path);
                if (file.exists() && file.canExecute()) {
                    log.debug("使用自定义{}路径: {}", runtimeType, path);
                    return path;
                } else {
                    log.warn("自定义{}路径不存在或不可执行: {}", runtimeType, path);
                }
            }
        }
        
        return null;
    }
}
