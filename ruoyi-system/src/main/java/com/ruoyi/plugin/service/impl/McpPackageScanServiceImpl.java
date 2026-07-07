package com.ruoyi.plugin.service.impl;

import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.plugin.domain.AiPlugin;
import com.ruoyi.plugin.domain.vo.McpPackageInfo;
import com.ruoyi.plugin.domain.vo.McpPackageScanResult;
import com.ruoyi.plugin.mapper.AiPluginMapper;
import com.ruoyi.plugin.service.IBuiltinPluginScanService;
import com.ruoyi.plugin.service.IMcpPackageScanService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MCP包扫描服务实现
 *
 * @author ruoyi
 */
@Slf4j
@Service
public class McpPackageScanServiceImpl implements IMcpPackageScanService {
    
    @Autowired
    private AiPluginMapper aiPluginMapper;
    
    @Autowired
    private IBuiltinPluginScanService builtinPluginScanService;
    
    // 从配置文件读取环境路径
    @org.springframework.beans.factory.annotation.Value("${env.path}")
    private String envBasePath;
    
    // 插件Python虚拟环境目录名
    private static final String PYTHON_VENV_DIR = "python-env";
    
    // 插件Node.js环境目录名
    private static final String NODE_ENV_DIR = "node-env";
    
    /** MCP相关包名的关键词 */
    private static final List<String> MCP_KEYWORDS = Arrays.asList(
        "mcp-server", "mcp-", "@modelcontextprotocol"
    );
    
    /** 官方MCP包列表 */
    private static final Map<String, McpPackageMetadata> OFFICIAL_PACKAGES = new HashMap<>();
    
    /**
     * 获取虚拟环境的Python可执行文件路径
     */
    private String getVenvPythonExecutable() {
        java.io.File venvDir = new java.io.File(envBasePath, PYTHON_VENV_DIR);
        // Windows
        java.io.File windowsPython = new java.io.File(venvDir, "Scripts/python.exe");
        if (windowsPython.exists()) {
            return windowsPython.getAbsolutePath();
        }
        // Linux/Mac
        java.io.File unixPython = new java.io.File(venvDir, "bin/python");
        if (unixPython.exists()) {
            return unixPython.getAbsolutePath();
        }
        return null;
    }
    
    /**
     * 获取Node环境目录
     */
    private java.io.File getNodeEnvDir() {
        return new java.io.File(envBasePath, NODE_ENV_DIR);
    }


    
    @Override
    public McpPackageScanResult scanAllMcpPackages() {
        log.info("开始扫描所有插件包...");
        
        McpPackageScanResult result = new McpPackageScanResult();
        result.setScanTime(DateUtils.getTime());
        
        // 扫描Python包
        List<McpPackageInfo> pythonPackages = new ArrayList<>();
        result.setPythonAvailable(false);
        try {
            pythonPackages = scanPythonMcpPackages();
            result.setPythonAvailable(true);
            log.info("Python MCP包扫描完成，找到 {} 个包", pythonPackages.size());
        } catch (Exception e) {
            log.warn("Python包扫描失败: {}", e.getMessage());
        }
        result.setPythonPackages(pythonPackages);
        
        // 扫描Node.js包
        List<McpPackageInfo> nodePackages = new ArrayList<>();
        result.setNodeAvailable(false);
        try {
            nodePackages = scanNodeMcpPackages();
            result.setNodeAvailable(true);
            log.info("Node.js MCP包扫描完成，找到 {} 个包", nodePackages.size());
        } catch (Exception e) {
            log.warn("Node.js包扫描失败: {}", e.getMessage());
        }
        result.setNodePackages(nodePackages);
        
        // 扫描内置插件
        List<com.ruoyi.plugin.domain.vo.BuiltinPluginInfo> builtinPlugins = new ArrayList<>();
        result.setBuiltinAvailable(false);
        try {
            builtinPlugins = builtinPluginScanService.scanBuiltinPlugins();
            result.setBuiltinAvailable(true);
            log.info("内置插件扫描完成，找到 {} 个插件", builtinPlugins.size());
        } catch (Exception e) {
            log.warn("内置插件扫描失败: {}", e.getMessage());
        }
        result.setBuiltinPlugins(builtinPlugins);
        
        // 统计
        int totalCount = pythonPackages.size() + nodePackages.size() + builtinPlugins.size();
        int configuredCount = (int) pythonPackages.stream().filter(McpPackageInfo::getEnabled).count()
                + (int) nodePackages.stream().filter(McpPackageInfo::getEnabled).count()
                + (int) builtinPlugins.stream().filter(com.ruoyi.plugin.domain.vo.BuiltinPluginInfo::getEnabled).count();
        
        result.setTotalCount(totalCount);
        result.setConfiguredCount(configuredCount);
        result.setUnconfiguredCount(totalCount - configuredCount);
        
        log.info("插件包扫描完成: 总计={}, 已启用={}, 未启用={}", 
            totalCount, configuredCount, totalCount - configuredCount);
        
        return result;
    }
    
    @Override
    public List<McpPackageInfo> scanPythonMcpPackages() {
        List<McpPackageInfo> packages = new ArrayList<>();
        
        try {
            // 使用虚拟环境的Python
            String pythonCmd = getVenvPythonExecutable();
            if (pythonCmd == null) {
                log.warn("Python虚拟环境不存在，请先创建");
                throw new RuntimeException("Python虚拟环境不存在，请先在环境检测页面创建");
            }
            
            // 使用buildCommand构建跨平台命令
            List<String> command = buildCommand(pythonCmd, "-m", "pip", "list", "--format=json", "--disable-pip-version-check");
            
            ProcessResult result = new ProcessExecutor()
                .command(command)
                .readOutput(true)
                .exitValueAny() // 允许任何退出码
                .timeout(30, TimeUnit.SECONDS)
                .execute();
            
            String output = result.outputUTF8();
            
            if (output == null || output.trim().isEmpty()) {
                log.warn("pip list命令输出为空");
                throw new RuntimeException("pip命令执行失败，无输出");
            }
            
            // 解析JSON格式的输出
            // 简单的正则解析（生产环境建议使用JSON库）
            Pattern pattern = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"[^}]*\"version\"\\s*:\\s*\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(output);
            
            while (matcher.find()) {
                String packageName = matcher.group(1);
                String version = matcher.group(2);
                
                // 检查是否是MCP相关包
                if (isMcpPackage(packageName)) {
                    try {
                        McpPackageInfo info = buildPackageInfo(
                            packageName, version, "pip", "python"
                        );
                        packages.add(info);
                    } catch (Exception e) {
                        log.warn("构建包信息失败: packageName={}, error={}", packageName, e.getMessage());
                    }
                }
            }
            
            log.info("Python虚拟环境包扫描完成，找到 {} 个MCP包", packages.size());
            
        } catch (Exception e) {
            log.error("扫描Python虚拟环境包失败", e);
            throw new RuntimeException("Python虚拟环境不可用或pip命令执行失败: " + e.getMessage());
        }
        
        return packages;
    }
    
    @Override
    public List<McpPackageInfo> scanNodeMcpPackages() {
        List<McpPackageInfo> packages = new ArrayList<>();
        
        try {
            // 使用node-env环境
            java.io.File nodeEnvDir = getNodeEnvDir();
            if (!nodeEnvDir.exists()) {
                log.warn("Node环境目录不存在，请先创建");
                throw new RuntimeException("Node环境不存在，请先在环境检测页面创建");
            }
            
            // 检查node_modules目录
            java.io.File nodeModulesDir = new java.io.File(nodeEnvDir, "node_modules");
            if (!nodeModulesDir.exists()) {
                log.info("node_modules目录不存在，没有已安装的包");
                return packages;
            }
            
            // 直接读取node_modules目录
            java.io.File[] packageDirs = nodeModulesDir.listFiles();
            if (packageDirs != null) {
                for (java.io.File pkgDir : packageDirs) {
                    if (pkgDir.isDirectory() && !pkgDir.getName().startsWith(".")) {
                        String packageName = pkgDir.getName();
                        
                        // 检查是否是MCP相关包
                        if (isMcpPackage(packageName)) {
                            // 读取package.json获取版本
                            java.io.File pkgJson = new java.io.File(pkgDir, "package.json");
                            if (pkgJson.exists()) {
                                try {
                                    String content = new String(java.nio.file.Files.readAllBytes(pkgJson.toPath()));
                                    
                                    // 简单解析版本号
                                    Pattern versionPattern = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"");
                                    Matcher versionMatcher = versionPattern.matcher(content);
                                    String version = "unknown";
                                    if (versionMatcher.find()) {
                                        version = versionMatcher.group(1);
                                    }
                                    
                                    McpPackageInfo info = buildPackageInfo(
                                        packageName, version, "npm", "node"
                                    );
                                    packages.add(info);
                                    
                                } catch (Exception e) {
                                    log.debug("读取包信息失败: {}", packageName);
                                }
                            }
                        }
                    }
                }
            }
            
            log.info("Node环境包扫描完成，找到 {} 个MCP包", packages.size());
            
        } catch (Exception e) {
            log.error("扫描Node环境包失败", e);
            throw new RuntimeException("Node环境不可用: " + e.getMessage());
        }
        
        return packages;
    }
    
    @Override
    public Long enableMcpPackage(String packageName, String runtimeType, String version, String pluginName, String envVars, String requireApproval) {
        log.info("启用MCP包: packageName={}, runtimeType={}, version={}, pluginName={}, envVars={}, requireApproval={}", 
            packageName, runtimeType, version, pluginName,
            envVars != null && !envVars.isEmpty() ? "已配置" : "未配置",
            requireApproval != null ? requireApproval : "默认");
        
        // 确定插件名称
        String finalPluginName;
        if (pluginName != null && !pluginName.isEmpty()) {
            // 用户自定义名称
            finalPluginName = pluginName;
        } else {
            // 使用默认名称（从元数据获取）
            McpPackageInfo metadata = getPackageMetadata(packageName, runtimeType);
            finalPluginName = metadata.getDisplayName();
        }
        
        // 检查插件名称是否已存在
        AiPlugin existingByName = aiPluginMapper.checkPluginNameUnique(finalPluginName);
        if (existingByName != null) {
            throw new RuntimeException("插件名称已存在: " + finalPluginName + "，请使用不同的名称");
        }
        
        // 创建新插件记录
        AiPlugin plugin = new AiPlugin();
        plugin.setPluginName(finalPluginName);
        
        plugin.setPluginType("mcp");
        plugin.setRuntimeType(runtimeType);
        plugin.setPackageName(packageName);
        
        // 从元数据获取其他信息
        McpPackageInfo metadata = getPackageMetadata(packageName, runtimeType);
        plugin.setDescription(metadata.getDescription());
        plugin.setCategory(metadata.getCategory());
        plugin.setIsOfficial(metadata.getOfficial() ? "1" : "0");
        
        // 设置版本号（优先使用传入的版本号）
        if (version != null && !version.isEmpty()) {
            plugin.setVersion(version);
        } else {
            plugin.setVersion(metadata.getVersion());
        }
        
        // 设置环境变量
        if (envVars != null && !envVars.isEmpty()) {
            plugin.setEnvVars(envVars);
            log.info("配置环境变量: {}", envVars);
        }
        
        // 生成启动命令
        String[] command = generateStartCommand(packageName, runtimeType);
        plugin.setStartCommand(String.join(" ", command));
        
        // 状态设置为启用
        plugin.setStatus("0");
        
        // 设置是否需要审批
        if (requireApproval != null && !requireApproval.isEmpty()) {
            plugin.setRequireApproval(requireApproval);
            log.info("配置审批设置: {}", requireApproval);
        } else {
            // 根据分类设置默认值（数据库和文件系统默认需要审批）
            if ("database".equals(plugin.getCategory()) || "file_system".equals(plugin.getCategory())) {
                plugin.setRequireApproval("1");
            } else {
                plugin.setRequireApproval("0");
            }
        }
        
        plugin.setCreateTime(new Date());
        
        aiPluginMapper.insertAiPlugin(plugin);
        
        log.info("MCP包启用成功: id={}, code={}", plugin.getPluginId(), plugin.getPluginName());
        return plugin.getPluginId();
    }
    
    @Override
    public boolean disableMcpPackage(String packageName) {
        log.info("禁用MCP包: packageName={}", packageName);
        
        // 查找插件
        AiPlugin queryParam = new AiPlugin();
        queryParam.setPackageName(packageName);
        List<AiPlugin> existingPlugins = aiPluginMapper.selectAiPluginList(queryParam);
        
        if (existingPlugins == null || existingPlugins.isEmpty()) {
            log.warn("MCP包不存在: {}", packageName);
            return false;
        }
        
        // 更新状态为禁用
        AiPlugin existingPlugin = existingPlugins.get(0);
        existingPlugin.setStatus("1"); // 禁用
        aiPluginMapper.updateAiPlugin(existingPlugin);
        
        log.info("MCP包禁用成功: id={}", existingPlugin.getPluginId());
        return true;
    }
    
    @Override
    public McpPackageInfo getPackageMetadata(String packageName, String runtimeType) {
        // 从官方包列表中查找
        McpPackageMetadata metadata = OFFICIAL_PACKAGES.get(packageName);
        
        McpPackageInfo info = new McpPackageInfo();
        info.setPackageName(packageName);
        info.setRuntimeType(runtimeType);
        
        if (metadata != null) {
            info.setDisplayName(metadata.displayName);
            info.setCategory(metadata.category);
            info.setDescription(metadata.description);
            info.setAuthor(metadata.author);
            info.setOfficial(metadata.official);
        } else {
            // 未知包，使用默认值
            info.setDisplayName(packageName);
            info.setCategory("utility");
            info.setDescription("MCP服务器: " + packageName);
            info.setAuthor("Unknown");
            info.setOfficial(false);
        }
        
        return info;
    }
    
    /**
     * 判断是否是MCP相关包
     */
    private boolean isMcpPackage(String packageName) {
        String lowerName = packageName.toLowerCase();
        return MCP_KEYWORDS.stream().anyMatch(lowerName::contains);
    }
    
    /**
     * 构建包信息
     */
    private McpPackageInfo buildPackageInfo(String packageName, String version, 
                                           String runtimeType, String packageType) {
        McpPackageInfo info = getPackageMetadata(packageName, runtimeType);
        info.setVersion(version);
        
        // 检查是否已在插件列表中（不管状态）
        AiPlugin queryParam = new AiPlugin();
        queryParam.setPackageName(packageName);
        List<AiPlugin> existingPlugins = aiPluginMapper.selectAiPluginList(queryParam);
        
        // 添加调试日志
        log.debug("检查包是否已加载: packageName={}, 查询结果={}", 
            packageName, existingPlugins != null ? existingPlugins.size() : 0);
        
        if (existingPlugins != null && !existingPlugins.isEmpty()) {
            // 只要在列表中就算已加载，不管状态是启用还是禁用
            AiPlugin existingPlugin = existingPlugins.get(0);
            info.setEnabled(true);
            info.setPluginId(existingPlugin.getPluginId());
            // 设置审批状态
            info.setRequireApproval(existingPlugin.getRequireApproval());
            log.info("包已在列表中: packageName={}, pluginId={}", packageName, existingPlugin.getPluginId());
        } else {
            // 不在列表中
            info.setEnabled(false);
            // 设置默认审批状态（数据库和文件系统默认需要审批）
            if ("database".equals(info.getCategory()) || "file_system".equals(info.getCategory())) {
                info.setRequireApproval("1");
            } else {
                info.setRequireApproval("0");
            }
            log.info("包不在列表中: packageName={}", packageName);
        }
        
        // 生成默认命令
        String[] command = generateStartCommand(packageName, runtimeType);
        info.setDefaultCommand(String.join(" ", command));
        
        return info;
    }
    
    /**
     * 生成插件名称
     */
    private String generatePluginCode(String packageName) {
        // 移除前缀
        String code = packageName
            .replace("@modelcontextprotocol/server-", "mcp-")
            .replace("mcp-server-", "mcp-");
        
        // 如果没有mcp前缀，添加
        if (!code.startsWith("mcp-")) {
            code = "mcp-" + code;
        }
        
        return code;
    }
    
    /**
     * 生成启动命令
     */
    private String[] generateStartCommand(String packageName, String runtimeType) {
        if ("pip".equals(runtimeType)) {
            // 使用MCP Python虚拟环境的完整路径
            String pythonExe = getVenvPythonExecutable();
            if (pythonExe == null) {
                log.warn("Python虚拟环境不存在，使用系统python命令");
                pythonExe = "python";
            }
            return new String[]{
                pythonExe,
                "-m",
                packageName.replace("-", "_")
            };
        } else if ("npm".equals(runtimeType)) {
            // 使用MCP Node环境
            return new String[]{
                "node",
                "node_modules/" + packageName
            };
        } else {
            // 默认
            return new String[]{runtimeType, packageName};
        }
    }
    
    /**
     * 包元数据内部类
     */
    private static class McpPackageMetadata {
        String displayName;
        String category;
        String description;
        String defaultRuntime;
        String author;
        boolean official;
        
        McpPackageMetadata(String displayName, String category, String description,
                          String defaultRuntime, String author, boolean official) {
            this.displayName = displayName;
            this.category = category;
            this.description = description;
            this.defaultRuntime = defaultRuntime;
            this.author = author;
            this.official = official;
        }
    }
    
    /**
     * 构建跨平台命令
     * Windows下需要通过cmd /c执行
     */
    private List<String> buildCommand(String command, String... args) {
        List<String> fullCommand = new ArrayList<>();
        String os = System.getProperty("os.name").toLowerCase();
        
        if (os.contains("win")) {
            // Windows: cmd /c command args...
            fullCommand.add("cmd");
            fullCommand.add("/c");
            fullCommand.add(command);
            for (String arg : args) {
                fullCommand.add(arg);
            }
        } else {
            // Linux/Mac: command args...
            fullCommand.add(command);
            for (String arg : args) {
                fullCommand.add(arg);
            }
        }
        
        return fullCommand;
    }
    
    /**
     * 获取Python命令
     * Windows下需要避免Microsoft Store的python别名
     */
    private String getPythonCommand() {
        String os = System.getProperty("os.name").toLowerCase();
        
        if (os.contains("win")) {
            // Windows: 使用where命令查找真实的python路径
            try {
                ProcessResult result = new ProcessExecutor()
                    .command("cmd", "/c", "where", "python")
                    .readOutput(true)
                    .timeout(5, TimeUnit.SECONDS)
                    .execute();
                
                String output = result.outputUTF8();
                if (output != null && !output.isEmpty()) {
                    String[] paths = output.split("\n");
                    for (String path : paths) {
                        path = path.trim();
                        // 跳过Microsoft Store的别名
                        if (!path.contains("WindowsApps") && !path.isEmpty()) {
                            // 验证这个python是否真的可用
                            try {
                                ProcessResult testResult = new ProcessExecutor()
                                    .command("cmd", "/c", path, "--version")
                                    .readOutput(true)
                                    .timeout(3, TimeUnit.SECONDS)
                                    .execute();
                                String versionOutput = testResult.outputUTF8();
                                if (versionOutput != null && versionOutput.toLowerCase().contains("python")) {
                                    log.info("找到可用的Python: {}", path);
                                    return path;
                                }
                            } catch (Exception e) {
                                // 这个路径不可用，尝试下一个
                                continue;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("where python失败: {}", e.getMessage());
            }
            
            // 如果where失败，尝试python3
            try {
                ProcessResult result = new ProcessExecutor()
                    .command("cmd", "/c", "python3", "--version")
                    .readOutput(true)
                    .timeout(3, TimeUnit.SECONDS)
                    .execute();
                String output = result.outputUTF8();
                if (output != null && output.toLowerCase().contains("python")) {
                    return "python3";
                }
            } catch (Exception e) {
                // python3不可用
            }
            
            // 最后尝试python（可能是别名，但没办法了）
            return "python";
            
        } else {
            // Linux/Mac: 优先使用python3
            try {
                List<String> command = buildCommand("python3", "--version");
                new ProcessExecutor()
                    .command(command)
                    .timeout(5, TimeUnit.SECONDS)
                    .execute();
                return "python3";
            } catch (Exception e) {
                return "python";
            }
        }
    }
}
