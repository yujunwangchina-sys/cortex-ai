package com.ruoyi.plugin.domain.vo;

import lombok.Data;
import java.util.List;

/**
 * 插件包扫描结果VO（包含MCP包和内置插件）
 *
 * @author ruoyi
 */
@Data
public class McpPackageScanResult {
    
    /** Python包列表 */
    private List<McpPackageInfo> pythonPackages;
    
    /** Node.js包列表 */
    private List<McpPackageInfo> nodePackages;
    
    /** 内置插件列表 */
    private List<BuiltinPluginInfo> builtinPlugins;
    
    /** 总计包数量 */
    private Integer totalCount;
    
    /** 已配置数量 */
    private Integer configuredCount;
    
    /** 未配置数量 */
    private Integer unconfiguredCount;
    
    /** 扫描时间 */
    private String scanTime;
    
    /** 是否支持Python扫描 */
    private Boolean pythonAvailable;
    
    /** 是否支持Node扫描 */
    private Boolean nodeAvailable;
    
    /** 是否支持内置插件扫描 */
    private Boolean builtinAvailable;
}
