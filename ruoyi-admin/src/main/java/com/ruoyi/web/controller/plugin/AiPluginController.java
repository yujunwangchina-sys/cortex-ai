package com.ruoyi.web.controller.plugin;

import java.util.List;
import java.util.Map;

import com.ruoyi.plugin.mapper.AiPluginMapper;
import com.ruoyi.plugin.service.IBuiltinPluginScanService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.plugin.domain.AiPlugin;
import com.ruoyi.plugin.service.IAiPluginService;
import com.ruoyi.plugin.service.IMcpPackageScanService;
import com.ruoyi.plugin.service.IPackageDetectionService;
import com.ruoyi.plugin.domain.vo.McpPackageScanResult;
import com.ruoyi.plugin.domain.vo.McpPackageInfo;
import com.ruoyi.plugin.domain.vo.PackageDetectionResult;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;

/**
 * AI插件Controller
 * 
 * @author ruoyi
 */
@RestController
@RequestMapping("/plugin/list")
public class AiPluginController extends BaseController
{
    @Autowired
    private IAiPluginService aiPluginService;

    @Autowired
    private IMcpPackageScanService mcpPackageScanService;

    @Autowired
    private IPackageDetectionService packageDetectionService;
    
    @Autowired
    private AiPluginMapper aiPluginMapper;
    
    @Autowired
    private IBuiltinPluginScanService builtinPluginScanService;

    /**
     * 查询AI插件列表
     */
    @PreAuthorize("@ss.hasPermi('plugin:list:query')")
    @GetMapping("/list")
    public TableDataInfo list(AiPlugin aiPlugin)
    {
        startPage();
        List<AiPlugin> list = aiPluginService.selectAiPluginList(aiPlugin);
        return getDataTable(list);
    }

    /**
     * 查询AI插件简化列表（用于引用）
     */
    @PreAuthorize("@ss.hasPermi('plugin:list:query')")
    @GetMapping("/simple")
    public AjaxResult listSimple()
    {
        AiPlugin aiPlugin = new AiPlugin();
        aiPlugin.setStatus("0"); // 只返回启用的插件
        List<AiPlugin> list = aiPluginService.selectAiPluginList(aiPlugin);
        return success(list);
    }

    /**
     * 导出AI插件列表
     */
    @PreAuthorize("@ss.hasPermi('plugin:list:export')")
    @Log(title = "AI插件", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiPlugin aiPlugin)
    {
        List<AiPlugin> list = aiPluginService.selectAiPluginList(aiPlugin);
        ExcelUtil<AiPlugin> util = new ExcelUtil<AiPlugin>(AiPlugin.class);
        util.exportExcel(response, list, "AI插件数据");
    }

    /**
     * 获取AI插件详细信息
     */
    @PreAuthorize("@ss.hasPermi('plugin:list:query')")
    @GetMapping(value = "/{pluginId}")
    public AjaxResult getInfo(@PathVariable("pluginId") Long pluginId)
    {
        return success(aiPluginService.selectAiPluginByPluginId(pluginId));
    }

    /**
     * 新增AI插件
     */
    @PreAuthorize("@ss.hasPermi('plugin:list:add')")
    @Log(title = "AI插件", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiPlugin aiPlugin)
    {
        // 检查插件名称是否唯一
        if (aiPluginMapper.checkPluginNameUnique(aiPlugin.getPluginName()) != null) {
            return error("新增插件失败，插件名称'" + aiPlugin.getPluginName() + "'已存在");
        }

        
        return toAjax(aiPluginService.insertAiPlugin(aiPlugin));
    }

    /**
     * 修改AI插件
     */
    @PreAuthorize("@ss.hasPermi('plugin:list:edit')")
    @Log(title = "AI插件", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiPlugin aiPlugin)
    {
        // 检查插件名称是否唯一（排除自己）
        AiPlugin existingByName = aiPluginMapper.checkPluginNameUnique(aiPlugin.getPluginName());
        if (existingByName != null && !existingByName.getPluginId().equals(aiPlugin.getPluginId())) {
            return error("修改插件失败，插件名称'" + aiPlugin.getPluginName() + "'已存在");
        }
        
        return toAjax(aiPluginService.updateAiPlugin(aiPlugin));
    }

    /**
     * 删除AI插件
     */
    @PreAuthorize("@ss.hasPermi('plugin:list:remove')")
    @Log(title = "AI插件", businessType = BusinessType.DELETE)
	@DeleteMapping("/{pluginIds}")
    public AjaxResult remove(@PathVariable Long[] pluginIds)
    {
        return toAjax(aiPluginService.deleteAiPluginByPluginIds(pluginIds));
    }

    /**
     * 测试插件连接
     */
    @PreAuthorize("@ss.hasPermi('plugin:list:test')")
    @Log(title = "AI插件", businessType = BusinessType.OTHER)
    @PostMapping("/test")
    public AjaxResult testConnection(@RequestBody AiPlugin aiPlugin)
    {
        String result = aiPluginService.testConnection(aiPlugin);
        return success(result);
    }

    /**
     * 启动MCP插件
     */
    @PreAuthorize("@ss.hasPermi('plugin:list:edit')")
    @Log(title = "AI插件", businessType = BusinessType.OTHER)
    @PostMapping("/start/{pluginName}")
    public AjaxResult start(@PathVariable String pluginName)
    {
        String result = aiPluginService.startMcpPlugin(pluginName);
        return success(result);
    }

    /**
     * 停止MCP插件
     */
    @PreAuthorize("@ss.hasPermi('plugin:list:edit')")
    @Log(title = "AI插件", businessType = BusinessType.OTHER)
    @PostMapping("/stop/{pluginName}")
    public AjaxResult stop(@PathVariable String pluginName)
    {
        String result = aiPluginService.stopMcpPlugin(pluginName);
        return success(result);
    }

    /**
     * 同步插件工具
     */
    @PreAuthorize("@ss.hasPermi('plugin:tool:sync')")
    @Log(title = "AI插件", businessType = BusinessType.OTHER)
    @PostMapping("/syncTools/{pluginName}")
    public AjaxResult syncTools(@PathVariable String pluginName)
    {
        String result = aiPluginService.syncPluginTools(pluginName);
        return success(result);
    }

    /**
     * 重新加载所有启用的MCP插件
     */
    @PreAuthorize("@ss.hasPermi('plugin:list:edit')")
    @Log(title = "重新加载插件", businessType = BusinessType.OTHER)
    @PostMapping("/reloadAll")
    public AjaxResult reloadAll()
    {
        try {
            List<AiPlugin> plugins = aiPluginService.selectEnabledPluginList();
            
            int total = 0;
            int success = 0;
            int failed = 0;
            
            for (AiPlugin plugin : plugins) {
                if (!"mcp".equals(plugin.getPluginType())) {
                    continue;
                }
                
                total++;
                String pluginName = plugin.getPluginName();
                
                try {
                    // 先停止
                    aiPluginService.stopMcpPlugin(pluginName);
                    // 再启动
                    aiPluginService.startMcpPlugin(pluginName);
                    success++;
                } catch (Exception e) {
                    failed++;
                    logger.error("重新加载插件失败: {}", pluginName, e);
                }
            }
            
            String message = String.format("重新加载完成。总计: %d, 成功: %d, 失败: %d", total, success, failed);
            return success(message);
            
        } catch (Exception e) {
            return error("重新加载失败: " + e.getMessage());
        }
    }

    // ==================== MCP包扫描相关接口 ====================

    /**
     * 扫描所有MCP包
     */
    @PreAuthorize("@ss.hasPermi('plugin:list:query')")
    @GetMapping("/scanMcpPackages")
    public AjaxResult scanMcpPackages()
    {
        try {
            McpPackageScanResult result = mcpPackageScanService.scanAllMcpPackages();
            return success(result);
        } catch (Exception e) {
            logger.error("扫描MCP包失败", e);
            return error("扫描失败: " + e.getMessage());
        }
    }

    /**
     * 启用MCP包
     */
    @PreAuthorize("@ss.hasPermi('plugin:list:add')")
    @Log(title = "AI插件", businessType = BusinessType.INSERT)
    @PostMapping("/enableMcpPackage")
    public AjaxResult enableMcpPackage(@RequestBody McpPackageInfo packageInfo)
    {
        try {
            if (packageInfo.getPackageName() == null || packageInfo.getPackageName().isEmpty()) {
                return error("包名不能为空");
            }
            if (packageInfo.getRuntimeType() == null || packageInfo.getRuntimeType().isEmpty()) {
                return error("运行时类型不能为空");
            }
            
            // 支持传入version、pluginName、envVars和requireApproval参数
            Long pluginId = mcpPackageScanService.enableMcpPackage(
                packageInfo.getPackageName(), 
                packageInfo.getRuntimeType(),
                packageInfo.getVersion(),
                packageInfo.getPluginName(),
                packageInfo.getEnvVars(),
                packageInfo.getRequireApproval()
            );
            return success("启用成功，插件ID: " + pluginId);
        } catch (Exception e) {
            logger.error("启用MCP包失败", e);
            return error("启用失败: " + e.getMessage());
        }
    }

    /**
     * 禁用MCP包
     */
    @PreAuthorize("@ss.hasPermi('plugin:list:edit')")
    @Log(title = "AI插件", businessType = BusinessType.UPDATE)
    @PostMapping("/disableMcpPackage")
    public AjaxResult disableMcpPackage(@RequestBody McpPackageInfo packageInfo)
    {
        try {
            if (packageInfo.getPackageName() == null || packageInfo.getPackageName().isEmpty()) {
                return error("包名不能为空");
            }
            
            boolean success = mcpPackageScanService.disableMcpPackage(packageInfo.getPackageName());
            if (success) {
                return success("禁用成功");
            } else {
                return error("禁用失败，插件不存在");
            }
        } catch (Exception e) {
            logger.error("禁用MCP包失败", e);
            return error("禁用失败: " + e.getMessage());
        }
    }

    /**
     * 获取包元数据
     */
    @PreAuthorize("@ss.hasPermi('plugin:list:query')")
    @GetMapping("/getPackageMetadata/{packageName}/{runtimeType}")
    public AjaxResult getPackageMetadata(@PathVariable String packageName, @PathVariable String runtimeType)
    {
        try {
            McpPackageInfo metadata = mcpPackageScanService.getPackageMetadata(packageName, runtimeType);
            return success(metadata);
        } catch (Exception e) {
            logger.error("获取包元数据失败", e);
            return error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取MCP插件运行日志
     */
    @PreAuthorize("@ss.hasPermi('plugin:list:query')")
    @GetMapping("/logs/{pluginName}")
    public AjaxResult getLogs(@PathVariable String pluginName)
    {
        try {
            // 默认返回最近500行
            List<String> logs = aiPluginService.getMcpPluginLogs(pluginName, 500);
            return success(logs);
        } catch (Exception e) {
            logger.error("获取插件日志失败", e);
            return error("获取日志失败: " + e.getMessage());
        }
    }

    /**
     * 检测并下载包
     */
    @PreAuthorize("@ss.hasPermi('plugin:list:add')")
    @PostMapping("/detectPackage")
    public AjaxResult detectPackage(@RequestBody Map<String, String> params)
    {
        try {
            String packageName = params.get("packageName");
            if (packageName == null || packageName.trim().isEmpty()) {
                return error("包名不能为空");
            }
            
            PackageDetectionResult result = packageDetectionService.detectAndDownload(packageName.trim());
            return success(result);
        } catch (Exception e) {
            logger.error("检测包失败", e);
            return error("检测失败: " + e.getMessage());
        }
    }


    /**
     * 加载内置插件到列表
     */
    @PreAuthorize("@ss.hasPermi('plugin:list:add')")
    @Log(title = "AI插件", businessType = BusinessType.INSERT)
    @PostMapping("/loadBuiltinPlugin")
    public AjaxResult loadBuiltinPlugin(@RequestBody Map<String, String> params)
    {
        try {
            String builtinClass = params.get("builtinClass");
            if (builtinClass == null || builtinClass.isEmpty()) {
                return error("插件类名不能为空");
            }
            
            String pluginName = params.get("pluginName");
            
            Long pluginId = builtinPluginScanService.loadBuiltinPlugin(builtinClass, pluginName);
            return success("加载成功，插件ID: " + pluginId);
        } catch (Exception e) {
            logger.error("加载内置插件失败", e);
            return error("加载失败: " + e.getMessage());
        }
    }
    
    /**
     * 卸载内置插件（从列表移除）
     */
    @PreAuthorize("@ss.hasPermi('plugin:list:remove')")
    @Log(title = "AI插件", businessType = BusinessType.DELETE)
    @PostMapping("/unloadBuiltinPlugin")
    public AjaxResult unloadBuiltinPlugin(@RequestBody Map<String, String> params)
    {
        try {
            String builtinClass = params.get("builtinClass");
            if (builtinClass == null || builtinClass.isEmpty()) {
                return error("插件类名不能为空");
            }
            
            boolean success = builtinPluginScanService.unloadBuiltinPlugin(builtinClass);
            if (success) {
                return success("卸载成功");
            } else {
                return error("卸载失败，插件不存在");
            }
        } catch (Exception e) {
            logger.error("卸载内置插件失败", e);
            return error("卸载失败: " + e.getMessage());
        }
    }
}
