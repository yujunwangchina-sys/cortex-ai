package com.ruoyi.web.controller.plugin;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.platform.CommandLocator;
import com.ruoyi.common.utils.platform.PlatformUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * 包管理Controller
 * 支持Python pip和Node.js npm包管理
 * 
 * @author ruoyi
 */
@RestController
@RequestMapping("/plugin/package")
public class PackageManagerController extends BaseController
{
    private static final Logger log = LoggerFactory.getLogger(PackageManagerController.class);
    
    // 从配置文件读取环境路径
    @org.springframework.beans.factory.annotation.Value("${env.path}")
    private String envBasePath;
    
    // 插件Python虚拟环境目录名
    private static final String PYTHON_VENV_DIR = "python-env";
    
    // 插件Node.js环境目录名
    private static final String NODE_ENV_DIR = "node-env";
    
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
        // 回退到系统Python
        return getPythonCommand();
    }
    
    /**
     * 获取虚拟环境的pip可执行文件路径
     */
    private String getVenvPipExecutable() {
        java.io.File venvDir = new java.io.File(envBasePath, PYTHON_VENV_DIR);
        // Windows
        java.io.File windowsPip = new java.io.File(venvDir, "Scripts/pip.exe");
        if (windowsPip.exists()) {
            return windowsPip.getAbsolutePath();
        }
        // Linux/Mac
        java.io.File unixPip = new java.io.File(venvDir, "bin/pip");
        if (unixPip.exists()) {
            return unixPip.getAbsolutePath();
        }
        return null;
    }
    
    /**
     * 获取Node环境目录
     */
    private java.io.File getNodeEnvDir() {
        return new java.io.File(envBasePath, NODE_ENV_DIR);
    }
    
    /**
     * 安装Python包（安装到虚拟环境）
     */
    @PreAuthorize("@ss.hasPermi('plugin:package:install')")
    @Log(title = "安装Python包", businessType = BusinessType.OTHER)
    @PostMapping("/python/install")
    public AjaxResult installPythonPackage(@RequestBody PackageRequest request)
    {
        try {
            String packageName = request.getPackageName();
            String version = request.getVersion();
            
            log.info("安装Python包到虚拟环境: {}", packageName);
            
            // 使用虚拟环境的Python
            String pythonCmd = getVenvPythonExecutable();
            
            // 构建安装命令参数
            List<String> cmdArgs = new ArrayList<>();
            cmdArgs.add("-m");
            cmdArgs.add("pip");
            cmdArgs.add("install");
            
            if (version != null && !version.isEmpty()) {
                cmdArgs.add(packageName + "==" + version);
            } else {
                cmdArgs.add(packageName);
            }
            
            // 添加国内镜像源加速
            if (request.isUseMirror()) {
                cmdArgs.add("-i");
                cmdArgs.add("https://pypi.tuna.tsinghua.edu.cn/simple");
            }
            
            List<String> command = buildCommand(pythonCmd, cmdArgs.toArray(new String[0]));
            
            // 执行安装
            ProcessResult result = new ProcessExecutor()
                .command(command)
                .readOutput(true)
                .timeout(5, TimeUnit.MINUTES)
                .execute();
            
            String output = result.outputUTF8();
            
            if (result.getExitValue() == 0) {
                log.info("Python包安装成功: {}", packageName);
                return success("安装成功:"+output);
            } else {
                log.error("Python包安装失败: {}", output);
                return error("安装失败: " + output);
            }
            
        } catch (TimeoutException e) {
            return error("安装超时，请检查网络连接");
        } catch (Exception e) {
            log.error("安装Python包异常", e);
            return error("安装异常: " + e.getMessage());
        }
    }
    
    /**
     * 卸载Python包（从虚拟环境）
     */
    @PreAuthorize("@ss.hasPermi('plugin:package:uninstall')")
    @Log(title = "卸载Python包", businessType = BusinessType.OTHER)
    @DeleteMapping("/python/uninstall/{packageName}")
    public AjaxResult uninstallPythonPackage(@PathVariable String packageName)
    {
        try {
            log.info("从虚拟环境卸载Python包: {}", packageName);
            
            String pythonCmd = getVenvPythonExecutable();
            List<String> command = buildCommand(pythonCmd, "-m", "pip", "uninstall", "-y", packageName);
            
            ProcessResult result = new ProcessExecutor()
                .command(command)
                .readOutput(true)
                .timeout(1, TimeUnit.MINUTES)
                .execute();
            
            String output = result.outputUTF8();
            
            if (result.getExitValue() == 0) {
                return success("卸载成功:"+output);
            } else {
                return error("卸载失败: " + output);
            }
            
        } catch (Exception e) {
            log.error("卸载Python包异常", e);
            return error("卸载异常: " + e.getMessage());
        }
    }
    
    /**
     * 列出已安装的Python包（虚拟环境）
     */
    @PreAuthorize("@ss.hasPermi('plugin:package:list')")
    @GetMapping("/python/list")
    public AjaxResult listPythonPackages()
    {
        try {
            List<Map<String, Object>> allPackages = new ArrayList<>();
            
            // 使用虚拟环境的pip列出包
            try {
                String pythonCmd = getVenvPythonExecutable();
                List<String> command = buildCommand(pythonCmd, "-m", "pip", "list", "--format=json", "--disable-pip-version-check");
                
                ProcessResult result = new ProcessExecutor()
                    .command(command)
                    .readOutput(true)
                    .exitValueAny()
                    .timeout(30, TimeUnit.SECONDS)
                    .execute();
                
                String output = result.outputUTF8();
                
                if (output != null && output.trim().startsWith("[")) {
                    // 明确指定泛型类型
                    List<Map> pipPackagesList = JSON.parseArray(output, Map.class);
                    for (Map rawPkg : pipPackagesList) {
                        Map<String, Object> pkg = new HashMap<>(rawPkg);
                        pkg.put("source", "venv");
                        pkg.put("installed", true);
                        allPackages.add(pkg);
                    }
                    log.info("获取到 {} 个虚拟环境中的 Python 包", pipPackagesList.size());
                }
            } catch (Exception e) {
                log.warn("获取虚拟环境包列表失败: {}", e.getMessage());
            }
            
            // 返回列表
            String jsonResult = JSON.toJSONString(allPackages);
            return success(jsonResult);
            
        } catch (Exception e) {
            log.error("获取Python包列表异常", e);
            return error("获取失败: " + e.getMessage());
        }
    }
    
    /**
     * 安装Node.js包（到node-env环境）
     */
    @PreAuthorize("@ss.hasPermi('plugin:package:install')")
    @Log(title = "安装Node包", businessType = BusinessType.OTHER)
    @PostMapping("/node/install")
    public AjaxResult installNodePackage(@RequestBody PackageRequest request)
    {
        try {
            String packageName = request.getPackageName();
            String version = request.getVersion();
            
            log.info("安装Node.js包到node-env: {}", packageName);
            
            java.io.File nodeEnvDir = getNodeEnvDir();
            if (!nodeEnvDir.exists()) {
                return error("Node环境未创建，请先在环境检测页面创建Node环境");
            }
            
            // 构建安装命令
            List<String> cmdArgs = new ArrayList<>();
            cmdArgs.add("install");
            
            if (version != null && !version.isEmpty()) {
                cmdArgs.add(packageName + "@" + version);
            } else {
                cmdArgs.add(packageName);
            }
            
            // 添加国内镜像源
            if (request.isUseMirror()) {
                cmdArgs.add("--registry=https://registry.npmmirror.com");
            }
            
            List<String> command = buildCommand("npm", cmdArgs.toArray(new String[0]));
            
            // 执行安装（在node-env目录下）
            ProcessResult result = new ProcessExecutor()
                .command(command)
                .directory(nodeEnvDir)
                .readOutput(true)
                .timeout(5, TimeUnit.MINUTES)
                .execute();
            
            String output = result.outputUTF8();
            
            if (result.getExitValue() == 0) {
                log.info("Node.js包安装成功: {}", packageName);
                return success("安装成功:"+output);
            } else {
                log.error("Node.js包安装失败: {}", output);
                return error("安装失败: " + output);
            }
            
        } catch (TimeoutException e) {
            return error("安装超时，请检查网络连接");
        } catch (Exception e) {
            log.error("安装Node.js包异常", e);
            return error("安装异常: " + e.getMessage());
        }
    }
    
    /**
     * 卸载Node.js包（从node-env环境）
     */
    @PreAuthorize("@ss.hasPermi('plugin:package:uninstall')")
    @Log(title = "卸载Node包", businessType = BusinessType.OTHER)
    @DeleteMapping("/node/uninstall/{packageName}")
    public AjaxResult uninstallNodePackage(@PathVariable String packageName)
    {
        try {
            log.info("从node-env卸载Node.js包: {}", packageName);
            
            java.io.File nodeEnvDir = getNodeEnvDir();
            if (!nodeEnvDir.exists()) {
                return error("Node环境未创建");
            }
            
            List<String> command = buildCommand("npm", "uninstall", packageName);
            
            ProcessResult result = new ProcessExecutor()
                .command(command)
                .directory(nodeEnvDir)
                .readOutput(true)
                .timeout(1, TimeUnit.MINUTES)
                .execute();
            
            String output = result.outputUTF8();
            
            if (result.getExitValue() == 0) {
                return success("卸载成功:"+output);
            } else {
                return error("卸载失败: " + output);
            }
            
        } catch (Exception e) {
            log.error("卸载Node.js包异常", e);
            return error("卸载异常: " + e.getMessage());
        }
    }
    
    /**
     * 列出已安装的Node.js包（node-env环境）
     */
    @PreAuthorize("@ss.hasPermi('plugin:package:list')")
    @GetMapping("/node/list")
    public AjaxResult listNodePackages()
    {
        try {
            Map<String, Object> allPackages = new HashMap<>();
            Map<String, Object> dependencies = new HashMap<>();
            
            java.io.File nodeEnvDir = getNodeEnvDir();
            if (!nodeEnvDir.exists()) {
                log.warn("Node环境目录不存在: {}", nodeEnvDir.getAbsolutePath());
                allPackages.put("dependencies", dependencies);
                return success(JSON.toJSONString(allPackages));
            }
            
            // 方法1: 读取node_modules目录
            java.io.File nodeModulesDir = new java.io.File(nodeEnvDir, "node_modules");
            if (nodeModulesDir.exists() && nodeModulesDir.isDirectory()) {
                java.io.File[] packages = nodeModulesDir.listFiles();
                if (packages != null) {
                    for (java.io.File pkgDir : packages) {
                        if (pkgDir.isDirectory() && !pkgDir.getName().startsWith(".")) {
                            // 读取每个包的package.json获取版本
                            java.io.File pkgJson = new java.io.File(pkgDir, "package.json");
                            if (pkgJson.exists()) {
                                try {
                                    String content = new String(java.nio.file.Files.readAllBytes(pkgJson.toPath()));
                                    Map pkgData = JSON.parseObject(content, Map.class);
                                    
                                    Map<String, Object> pkgInfo = new HashMap<>();
                                    pkgInfo.put("version", pkgData.get("version"));
                                    pkgInfo.put("source", "node-env");
                                    dependencies.put(pkgDir.getName(), pkgInfo);
                                } catch (Exception e) {
                                    log.debug("读取包信息失败: {}", pkgDir.getName());
                                }
                            }
                        }
                    }
                }
                log.info("从node_modules获取到 {} 个包", dependencies.size());
            } else {
                log.warn("node_modules目录不存在");
            }
            
            allPackages.put("dependencies", dependencies);
            String jsonResult = JSON.toJSONString(allPackages);
            return success(jsonResult);
            
        } catch (Exception e) {
            log.error("获取Node.js包列表异常", e);
            return error("获取失败: " + e.getMessage());
        }
    }
    
    /**
     * 搜索Python包
     */
    @PreAuthorize("@ss.hasPermi('plugin:package:list')")
    @GetMapping("/python/search")
    public AjaxResult searchPythonPackage(@RequestParam String keyword)
    {
        // pip search 已被PyPI禁用，返回提示
        Map<String, String> result = new HashMap<>();
        result.put("message", "请访问 PyPI 官网搜索");
        result.put("url", "https://pypi.org/search/?q=" + keyword);
        return success(result);
    }
    
    /**
     * 搜索Node.js包
     */
    @PreAuthorize("@ss.hasPermi('plugin:package:list')")
    @GetMapping("/node/search")
    public AjaxResult searchNodePackage(@RequestParam String keyword)
    {
        try {
            List<String> command = buildCommand("npm", "search", keyword, "--json");
            
            ProcessResult result = new ProcessExecutor()
                .command(command)
                .readOutput(true)
                .timeout(30, TimeUnit.SECONDS)
                .execute();
            
            if (result.getExitValue() == 0) {
                return success(result.outputUTF8());
            } else {
                return error("搜索失败");
            }
            
        } catch (Exception e) {
            log.error("搜索Node.js包异常", e);
            return error("搜索失败: " + e.getMessage());
        }
    }
    
    /**
     * 构建跨平台命令
     * Windows下需要通过cmd /c执行
     */
    private List<String> buildCommand(String command, String... args) {
        List<String> fullCommand = new ArrayList<>();
        
        if (PlatformUtils.isWindows()) {
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
        if (PlatformUtils.isWindows()) {
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
    
    /**
     * 包请求对象
     */
    public static class PackageRequest {
        private String packageName;
        private String version;
        private boolean useMirror = true; // 默认使用国内镜像
        
        public String getPackageName() { return packageName; }
        public void setPackageName(String packageName) { this.packageName = packageName; }
        
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        
        public boolean isUseMirror() { return useMirror; }
        public void setUseMirror(boolean useMirror) { this.useMirror = useMirror; }
    }
}
