package com.cortex.plugin.service;

import com.cortex.plugin.domain.vo.McpPackageInfo;
import com.cortex.plugin.domain.vo.McpPackageScanResult;
import java.util.List;

/**
 * MCP包扫描服务接口
 *
 * @author cortex
 */
public interface IMcpPackageScanService {
    
    /**
     * 扫描所有MCP包（Python + Node.js）
     *
     * @return 扫描结果
     */
    McpPackageScanResult scanAllMcpPackages();
    
    /**
     * 扫描Python MCP包
     *
     * @return Python包列表
     */
    List<McpPackageInfo> scanPythonMcpPackages();
    
    /**
     * 扫描Node.js MCP包
     *
     * @return Node.js包列表
     */
    List<McpPackageInfo> scanNodeMcpPackages();
    
    /**
     * 启用MCP包（写入数据库）
     *
     * @param packageName 包名
     * @param runtimeType 运行时类型
     * @param version 版本号
     * @param pluginName 插件名称（可选，用于自定义名称）
     * @param envVars 环境变量（可选）
     * @param requireApproval 是否需要审批（可选）
     * @return 插件ID
     */
    Long enableMcpPackage(String packageName, String runtimeType, String version, String pluginName, String envVars, String requireApproval);
    
    /**
     * 禁用MCP包（更新数据库status='1'）
     *
     * @param packageName 包名
     * @return 是否成功
     */
    boolean disableMcpPackage(String packageName);
    
    /**
     * 获取MCP包的元数据
     *
     * @param packageName 包名
     * @param runtimeType 运行时类型
     * @return 包详细信息
     */
    McpPackageInfo getPackageMetadata(String packageName, String runtimeType);
}
