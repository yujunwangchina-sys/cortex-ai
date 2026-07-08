package com.cortex.web.controller.plugin;

import com.cortex.common.annotation.Log;
import com.cortex.common.core.controller.BaseController;
import com.cortex.common.core.domain.AjaxResult;
import com.cortex.common.enums.BusinessType;
import com.cortex.common.utils.platform.CommandLocator;
import com.cortex.common.utils.platform.PlatformUtils;
import com.cortex.common.utils.platform.RuntimeDetector;
import com.cortex.plugin.domain.RuntimeConfig;
import com.cortex.plugin.service.IRuntimeConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 运行时环境Controller
 * 
 * @author cortex
 */
@RestController
@RequestMapping("/plugin/runtime")
public class RuntimeController extends BaseController
{
    private static final Logger log = LoggerFactory.getLogger(RuntimeController.class);
    
    @Autowired
    private IRuntimeConfigService runtimeConfigService;
    
    // 从配置文件读取环境路径
    @org.springframework.beans.factory.annotation.Value("${env.path}")
    private String envBasePath;
    
    // 插件Python虚拟环境目录名
    private static final String PYTHON_VENV_DIR = "python-env";
    
    // 插件Node.js环境目录名
    private static final String NODE_ENV_DIR = "node-env";
    
    /**
     * 获取插件Python虚拟环境目录
     */
    private File getMcpPythonVenvDir() {
        return new File(envBasePath, PYTHON_VENV_DIR);
    }
    
    /**
     * 获取插件Node.js环境目录
     */
    private File getMcpNodeEnvDir() {
        return new File(envBasePath, NODE_ENV_DIR);
    }
    
    /**
     * 检测插件环境状态
     */
    @PreAuthorize("@ss.hasPermi('plugin:runtime:detect')")
    @GetMapping("/mcp-env/status")
    public AjaxResult getMcpEnvStatus() {
        Map<String, Object> status = new HashMap<>();
        
        File pythonVenv = getMcpPythonVenvDir();
        File nodeEnv = getMcpNodeEnvDir();
        
        status.put("basePath", envBasePath);
        status.put("pythonVenv", new HashMap<String, Object>() {{
            put("path", pythonVenv.getAbsolutePath());
            put("exists", pythonVenv.exists());
            put("pythonExecutable", new File(pythonVenv, "Scripts/python.exe").exists() || 
                                   new File(pythonVenv, "bin/python").exists());
        }});
        status.put("nodeEnv", new HashMap<String, Object>() {{
            put("path", nodeEnv.getAbsolutePath());
            put("exists", nodeEnv.exists());
            put("nodeModules", new File(nodeEnv, "node_modules").exists());
        }});
        
        return success(status);
    }
    
    /**
     * 创建插件Python虚拟环境
     */
    @PreAuthorize("@ss.hasPermi('plugin:runtime:detect')")
    @Log(title = "创建插件Python环境", businessType = BusinessType.OTHER)
    @PostMapping("/mcp-env/create-python")
    public AjaxResult createPythonVenv() {
        try {
            File venvDir = getMcpPythonVenvDir();
            
            if (venvDir.exists()) {
                return success("Python虚拟环境已存在: " + venvDir.getAbsolutePath());
            }
            
            log.info("开始创建插件Python虚拟环境: {}", venvDir.getAbsolutePath());
            
            // 确保父目录存在
            File parentDir = new File(envBasePath);
            if (!parentDir.exists()) {
                parentDir.mkdirs();
                log.info("创建基础目录: {}", parentDir.getAbsolutePath());
            }
            
            // 执行 python -m venv 命令
            String pythonCmd = CommandLocator.locateCommand("python");
            
            ProcessResult result = new ProcessExecutor()
                .command(pythonCmd, "-m", "venv", venvDir.getAbsolutePath())
                .readOutput(true)
                .timeout(2, TimeUnit.MINUTES)
                .execute();
            
            if (result.getExitValue() == 0) {
                log.info("Python虚拟环境创建成功: {}", venvDir.getAbsolutePath());
                
                // 升级pip
                try {
                    String pipCmd = getPythonVenvPip();
                    new ProcessExecutor()
                        .command(pipCmd, "install", "--upgrade", "pip")
                        .timeout(1, TimeUnit.MINUTES)
                        .execute();
                    log.info("pip升级完成");
                } catch (Exception e) {
                    log.warn("pip升级失败: {}", e.getMessage());
                }
                
                return success("Python虚拟环境创建成功: " + venvDir.getAbsolutePath());
            } else {
                String output = result.outputUTF8();
                log.error("创建Python虚拟环境失败: {}", output);
                return error("创建失败: " + output);
            }
            
        } catch (Exception e) {
            log.error("创建Python虚拟环境异常", e);
            return error("创建失败: " + e.getMessage());
        }
    }
    
    /**
     * 创建插件Node.js环境
     */
    @PreAuthorize("@ss.hasPermi('plugin:runtime:detect')")
    @Log(title = "创建插件Node环境", businessType = BusinessType.OTHER)
    @PostMapping("/mcp-env/create-node")
    public AjaxResult createNodeEnv() {
        try {
            File nodeDir = getMcpNodeEnvDir();
            
            if (nodeDir.exists()) {
                return success("Node.js环境已存在: " + nodeDir.getAbsolutePath());
            }
            
            log.info("开始创建插件Node.js环境: {}", nodeDir.getAbsolutePath());
            
            // 创建目录
            nodeDir.mkdirs();
            
            // 使用CommandLocator定位npm命令
            String npmCmd = CommandLocator.locateCommand("npm");
            
            // 初始化npm
            ProcessResult result = new ProcessExecutor()
                .command(npmCmd, "init", "-y")
                .directory(nodeDir)
                .readOutput(true)
                .timeout(1, TimeUnit.MINUTES)
                .execute();
            
            if (result.getExitValue() == 0) {
                log.info("Node.js环境创建成功: {}", nodeDir.getAbsolutePath());
                return success("Node.js环境创建成功: " + nodeDir.getAbsolutePath());
            } else {
                String output = result.outputUTF8();
                log.error("创建Node.js环境失败: {}", output);
                return error("创建失败: " + output);
            }
            
        } catch (Exception e) {
            log.error("创建Node.js环境异常", e);
            return error("创建失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取Python虚拟环境的python可执行文件路径
     */
    public String getPythonVenvExecutable() {
        File venvDir = getMcpPythonVenvDir();
        // Windows
        File windowsPython = new File(venvDir, "Scripts/python.exe");
        if (windowsPython.exists()) {
            return windowsPython.getAbsolutePath();
        }
        // Linux/Mac
        File unixPython = new File(venvDir, "bin/python");
        if (unixPython.exists()) {
            return unixPython.getAbsolutePath();
        }
        return null;
    }
    
    /**
     * 获取Python虚拟环境的pip可执行文件路径
     */
    public String getPythonVenvPip() {
        File venvDir = getMcpPythonVenvDir();
        // Windows
        File windowsPip = new File(venvDir, "Scripts/pip.exe");
        if (windowsPip.exists()) {
            return windowsPip.getAbsolutePath();
        }
        // Linux/Mac
        File unixPip = new File(venvDir, "bin/pip");
        if (unixPip.exists()) {
            return unixPip.getAbsolutePath();
        }
        return null;
    }
    
    /**
     * 检测系统环境
     */
    @PreAuthorize("@ss.hasPermi('plugin:runtime:detect')")
    @GetMapping("/detect")
    public AjaxResult detect()
    {
        // 初始化自定义路径提供器
        initializeCustomPathProvider();
        
        Map<String, Object> result = new HashMap<>();
        
        // 系统信息
        PlatformUtils.SystemInfo systemInfo = PlatformUtils.getSystemInfo();
        result.put("system", systemInfo);
        
        // 运行时检测
        Map<String, RuntimeDetector.RuntimeInfo> runtimes = RuntimeDetector.detectAll();
        result.put("runtimes", runtimes);
        
        // 安装指南
        result.put("installGuide", getInstallGuide(systemInfo, runtimes));
        
        // 自定义配置
        List<RuntimeConfig> configs = runtimeConfigService.selectRuntimeConfigList(new RuntimeConfig());
        result.put("customConfigs", configs);
        
        return success(result);
    }
    
    /**
     * 初始化自定义路径提供器
     */
    private void initializeCustomPathProvider() {
        RuntimeDetector.setCustomPathProvider(runtimeType -> {
            return runtimeConfigService.getExecutablePath(runtimeType);
        });
    }
    
    /**
     * 获取系统信息
     */
    @PreAuthorize("@ss.hasPermi('plugin:runtime:detect')")
    @GetMapping("/system")
    public AjaxResult getSystemInfo()
    {
        return success(PlatformUtils.getSystemInfo());
    }
    
    /**
     * 检测所有运行时
     */
    @PreAuthorize("@ss.hasPermi('plugin:runtime:detect')")
    @GetMapping("/runtimes")
    public AjaxResult getRuntimes()
    {
        // 初始化自定义路径提供器
        initializeCustomPathProvider();
        return success(RuntimeDetector.detectAll());
    }
    
    /**
     * 查询运行时配置列表
     */
    @PreAuthorize("@ss.hasPermi('plugin:runtime:config')")
    @GetMapping("/config/list")
    public AjaxResult getConfigList()
    {
        List<RuntimeConfig> list = runtimeConfigService.selectRuntimeConfigList(new RuntimeConfig());
        return success(list);
    }
    
    /**
     * 获取运行时配置详细信息
     */
    @PreAuthorize("@ss.hasPermi('plugin:runtime:config')")
    @GetMapping("/config/{runtimeType}")
    public AjaxResult getConfig(@PathVariable String runtimeType)
    {
        RuntimeConfig config = runtimeConfigService.selectRuntimeConfigByType(runtimeType);
        return success(config);
    }
    
    /**
     * 新增或更新运行时配置
     */
    @PreAuthorize("@ss.hasPermi('plugin:runtime:config')")
    @Log(title = "保存运行时配置", businessType = BusinessType.UPDATE)
    @PostMapping("/config/save")
    public AjaxResult saveConfig(@RequestBody RuntimeConfig config)
    {
        int result = runtimeConfigService.saveOrUpdateConfig(
            config.getRuntimeType(), 
            config.getExecutablePath(), 
            config.getCustomPathEnabled()
        );
        return toAjax(result);
    }
    
    /**
     * 验证运行时路径
     */
    @PreAuthorize("@ss.hasPermi('plugin:runtime:config')")
    @PostMapping("/config/verify")
    public AjaxResult verifyPath(@RequestBody Map<String, String> params)
    {
        String executablePath = params.get("executablePath");
        String runtimeType = params.get("runtimeType");
        
        if (executablePath == null || executablePath.trim().isEmpty()) {
            return error("可执行文件路径不能为空");
        }
        
        File file = new File(executablePath);
        
        Map<String, Object> result = new HashMap<>();
        result.put("exists", file.exists());
        result.put("canExecute", file.exists() && file.canExecute());
        
        if (file.exists() && file.canExecute()) {
            try {
                // 尝试获取版本信息
                RuntimeDetector.RuntimeInfo info = RuntimeDetector.detectRuntime(executablePath, "--version");
                result.put("available", info.isAvailable());
                result.put("version", info.getVersion());
                result.put("message", "路径验证成功");
                return success(result);
            } catch (Exception e) {
                result.put("available", false);
                result.put("message", "无法获取版本信息: " + e.getMessage());
                return AjaxResult.warn("路径可能无效", result);
            }
        } else if (!file.exists()) {
            result.put("message", "文件不存在");
            return AjaxResult.error("文件不存在", result);
        } else {
            result.put("message", "文件不可执行");
            return AjaxResult.error("文件不可执行", result);
        }
    }
    
    /**
     * 获取安装指南
     */
    @PreAuthorize("@ss.hasPermi('plugin:runtime:detect')")
    @GetMapping("/installGuide")
    public AjaxResult getInstallGuide()
    {
        PlatformUtils.SystemInfo systemInfo = PlatformUtils.getSystemInfo();
        Map<String, RuntimeDetector.RuntimeInfo> runtimes = RuntimeDetector.detectAll();
        return success(getInstallGuide(systemInfo, runtimes));
    }
    
    /**
     * 生成安装指南
     */
    private Map<String, Object> getInstallGuide(
        PlatformUtils.SystemInfo systemInfo, 
        Map<String, RuntimeDetector.RuntimeInfo> runtimes
    ) {
        Map<String, Object> guide = new HashMap<>();
        String osType = systemInfo.getOsType();
        
        // Python安装指南
        if (!runtimes.get("python").isAvailable()) {
            guide.put("python", getPythonInstallGuide(osType));
        }
        
        // Node.js安装指南
        if (!runtimes.get("node").isAvailable()) {
            guide.put("node", getNodeInstallGuide(osType));
        }
        
        return guide;
    }
    
    /**
     * Python安装指南
     */
    private Map<String, Object> getPythonInstallGuide(String osType) {
        Map<String, Object> guide = new HashMap<>();
        guide.put("name", "Python");
        guide.put("required", true);
        guide.put("description", "Python运行环境，用于运行Python MCP插件");
        
        if ("WINDOWS".equals(osType)) {
            guide.put("method", "官网下载");
            guide.put("url", "https://www.python.org/downloads/");
            guide.put("steps", new String[]{
                "1. 访问 https://www.python.org/downloads/",
                "2. 下载 Python 3.10+ 安装包",
                "3. 运行安装程序，勾选 'Add Python to PATH'",
                "4. 完成安装后，在命令行输入 python --version 验证"
            });
            guide.put("command", "python --version");
        } else if ("LINUX".equals(osType)) {
            guide.put("method", "包管理器");
            guide.put("steps", new String[]{
                "Ubuntu/Debian: sudo apt install python3 python3-pip",
                "CentOS/RHEL: sudo yum install python3 python3-pip",
                "验证安装: python3 --version"
            });
            guide.put("command", "python3 --version");
        } else if ("MAC".equals(osType)) {
            guide.put("method", "Homebrew");
            guide.put("steps", new String[]{
                "1. 安装Homebrew: /bin/bash -c \"$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)\"",
                "2. 安装Python: brew install python",
                "3. 验证: python3 --version"
            });
            guide.put("command", "python3 --version");
        }
        
        return guide;
    }
    
    /**
     * Node.js安装指南
     */
    private Map<String, Object> getNodeInstallGuide(String osType) {
        Map<String, Object> guide = new HashMap<>();
        guide.put("name", "Node.js");
        guide.put("required", true);
        guide.put("description", "Node.js运行环境，用于运行Node.js MCP插件");
        
        if ("WINDOWS".equals(osType)) {
            guide.put("method", "官网下载");
            guide.put("url", "https://nodejs.org/");
            guide.put("steps", new String[]{
                "1. 访问 https://nodejs.org/",
                "2. 下载 LTS 版本（推荐 v20.x）",
                "3. 运行安装程序，按默认选项安装",
                "4. 完成后在命令行输入 node --version 验证"
            });
            guide.put("command", "node --version");
        } else if ("LINUX".equals(osType)) {
            guide.put("method", "nvm (推荐)");
            guide.put("steps", new String[]{
                "1. 安装nvm: curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.0/install.sh | bash",
                "2. 重启终端",
                "3. 安装Node.js: nvm install --lts",
                "4. 验证: node --version"
            });
            guide.put("command", "node --version");
        } else if ("MAC".equals(osType)) {
            guide.put("method", "Homebrew");
            guide.put("steps", new String[]{
                "1. 安装Node.js: brew install node",
                "2. 验证: node --version && npm --version"
            });
            guide.put("command", "node --version");
        }
        
        return guide;
    }
}
