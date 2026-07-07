package com.ruoyi.plugin.service.impl;

import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.plugin.builtin.IBuiltinPlugin;
import com.ruoyi.plugin.builtin.PluginInfo;
import com.ruoyi.plugin.domain.AiPlugin;
import com.ruoyi.plugin.domain.vo.BuiltinPluginInfo;
import com.ruoyi.plugin.mapper.AiPluginMapper;
import com.ruoyi.plugin.service.IBuiltinPluginScanService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 内置插件扫描服务实现
 * 
 * @author ruoyi
 */
@Slf4j
@Service
public class BuiltinPluginScanServiceImpl implements IBuiltinPluginScanService {
    
    @Autowired
    private ApplicationContext applicationContext;
    
    @Autowired
    private AiPluginMapper aiPluginMapper;
    
    @Override
    public List<BuiltinPluginInfo> scanBuiltinPlugins() {
        log.info("开始扫描内置插件...");
        
        List<BuiltinPluginInfo> pluginInfos = new ArrayList<>();
        
        // 获取所有实现了IBuiltinPlugin接口的Spring Bean
        Map<String, IBuiltinPlugin> builtinPlugins = applicationContext.getBeansOfType(IBuiltinPlugin.class);
        
        for (Map.Entry<String, IBuiltinPlugin> entry : builtinPlugins.entrySet()) {
            try {
                IBuiltinPlugin plugin = entry.getValue();
                PluginInfo info = plugin.getPluginInfo();
                
                BuiltinPluginInfo builtinInfo = new BuiltinPluginInfo();
                builtinInfo.setBuiltinClass(plugin.getClass().getName());
                builtinInfo.setDisplayName(info.getName());
                builtinInfo.setPluginName(info.getCode());
                builtinInfo.setCategory(info.getCategory());
                builtinInfo.setDescription(info.getDescription());
                builtinInfo.setVersion(info.getVersion());
                builtinInfo.setAuthor(info.getAuthor());
                builtinInfo.setEmoji(info.getEmoji());
                builtinInfo.setRequireApproval(info.isRequireApproval());
                builtinInfo.setAvailable(plugin.isAvailable());
                builtinInfo.setTools(plugin.getTools());
                
                // 检查是否已在插件列表中（统计实例数量）
                AiPlugin queryParam = new AiPlugin();
                queryParam.setBuiltinClass(plugin.getClass().getName());
                List<AiPlugin> existingPlugins = aiPluginMapper.selectAiPluginList(queryParam);
                
                if (existingPlugins != null && !existingPlugins.isEmpty()) {
                    builtinInfo.setEnabled(true);
                    builtinInfo.setPluginId(existingPlugins.get(0).getPluginId());
                    builtinInfo.setInstanceCount(existingPlugins.size()); // 实例数量
                } else {
                    builtinInfo.setEnabled(false);
                    builtinInfo.setInstanceCount(0);
                }
                
                pluginInfos.add(builtinInfo);
                
            } catch (Exception e) {
                log.error("扫描内置插件失败: {}", entry.getKey(), e);
            }
        }
        
        log.info("内置插件扫描完成，找到 {} 个插件", pluginInfos.size());
        return pluginInfos;
    }
    
    @Override
    public Long loadBuiltinPlugin(String builtinClass, String pluginName) {
        log.info("加载内置插件到插件列表: builtinClass={}, pluginName={}", builtinClass, pluginName);
        
        // 加载插件实例获取元数据
        try {
            Class<?> pluginClass = Class.forName(builtinClass);
            IBuiltinPlugin plugin = (IBuiltinPlugin) applicationContext.getBean(pluginClass);
            PluginInfo info = plugin.getPluginInfo();
            
            // 确定插件名称
            String finalPluginName;
            if (pluginName != null && !pluginName.isEmpty()) {
                // 用户自定义名称
                finalPluginName = pluginName;
            } else {
                // 使用默认名称
                finalPluginName = info.getName();
            }
            
            // 检查插件名称是否已存在
            AiPlugin existingByName = aiPluginMapper.checkPluginNameUnique(finalPluginName);
            if (existingByName != null) {
                throw new RuntimeException("插件名称已存在: " + finalPluginName + "，请使用不同的名称");
            }

            
            // 创建新插件记录
            AiPlugin aiPlugin = new AiPlugin();
            aiPlugin.setPluginName(finalPluginName);
            aiPlugin.setPluginType("builtin");
            aiPlugin.setCategory(info.getCategory());
            aiPlugin.setDescription(info.getDescription());
            aiPlugin.setIcon(info.getEmoji());
            aiPlugin.setVersion(info.getVersion());
            aiPlugin.setAuthor(info.getAuthor());
            aiPlugin.setBuiltinClass(builtinClass);
            
            // 默认状态为禁用，需要用户手动启用和配置
            aiPlugin.setStatus("1");
            
            // 使用插件自己的默认审批设置
            aiPlugin.setRequireApproval(info.isRequireApproval() ? "1" : "0");
            
            // 标记为官方插件
            aiPlugin.setIsOfficial("1");
            aiPlugin.setInstallSource("builtin");
            
            aiPlugin.setCreateTime(new Date());
            
            aiPluginMapper.insertAiPlugin(aiPlugin);
            
            log.info("内置插件加载成功: id={}, code={}, name={}", aiPlugin.getPluginId(), aiPlugin.getPluginName(), aiPlugin.getPluginName());
            return aiPlugin.getPluginId();
            
        } catch (Exception e) {
            log.error("加载内置插件失败", e);
            throw new RuntimeException("加载失败: " + e.getMessage());
        }
    }
    
    @Override
    public boolean unloadBuiltinPlugin(String builtinClass) {
        log.info("卸载内置插件: builtinClass={}", builtinClass);
        
        // 查找插件
        AiPlugin queryParam = new AiPlugin();
        queryParam.setBuiltinClass(builtinClass);
        List<AiPlugin> existingPlugins = aiPluginMapper.selectAiPluginList(queryParam);
        
        if (existingPlugins == null || existingPlugins.isEmpty()) {
            log.warn("内置插件不存在: {}", builtinClass);
            return false;
        }
        
        // 从插件列表中删除
        AiPlugin existingPlugin = existingPlugins.get(0);
        aiPluginMapper.deleteAiPluginByPluginId(existingPlugin.getPluginId());
        
        log.info("内置插件卸载成功: id={}", existingPlugin.getPluginId());
        return true;
    }
}
