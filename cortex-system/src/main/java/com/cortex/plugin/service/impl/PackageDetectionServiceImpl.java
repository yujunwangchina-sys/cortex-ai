package com.cortex.plugin.service.impl;

import com.cortex.common.utils.platform.CommandLocator;
import com.cortex.plugin.domain.vo.PackageDetectionResult;
import com.cortex.plugin.service.IPackageDetectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 包检测服务实现
 * 
 * @author cortex
 */
@Service
public class PackageDetectionServiceImpl implements IPackageDetectionService {
    
    private static final Logger log = LoggerFactory.getLogger(PackageDetectionServiceImpl.class);
    
    @org.springframework.beans.factory.annotation.Value("${env.path}")
    private String envBasePath;
    
    @Override
    public PackageDetectionResult detectAndDownload(String packageName) {
        log.info("开始检测包: {}", packageName);
        
        PackageDetectionResult result = new PackageDetectionResult();
        result.setPackageName(packageName);
        result.setSuccess(false);
        
        StringBuilder detectionDetails = new StringBuilder();
        
        // 1. 查询npm仓库
        detectionDetails.append("查询npm仓库...\n");
        boolean existsInNpm = checkNpmRegistry(packageName);
        if (existsInNpm) {
            detectionDetails.append("✓ npm仓库中存在该包\n");
            detectionDetails.append("尝试下载并验证...\n");
            
            if (downloadAndVerifyNpm(packageName)) {
                detectionDetails.append("✓ 下载并验证成功\n");
                PackageInfo npmInfo = new PackageInfo();
                npmInfo.exists = true;
                npmInfo.version = "latest";
                fillResult(result, npmInfo, "npm");
                result.setSuccess(true);
                result.setDownloaded(true);
                result.setDetectionDetails(detectionDetails.toString());
                return result;
            } else {
                detectionDetails.append("✗ 下载或验证失败\n");
            }
        } else {
            detectionDetails.append("✗ npm仓库中不存在\n");
        }
        
        // 2. 查询pypi仓库
        detectionDetails.append("查询PyPI仓库...\n");
        boolean existsInPypi = checkPypiRegistry(packageName);
        if (existsInPypi) {
            detectionDetails.append("✓ PyPI仓库中存在该包\n");
            detectionDetails.append("尝试下载并验证...\n");
            
            PackageInfo pipInfo = downloadAndVerifyPip(packageName);
            if (pipInfo != null && pipInfo.exists) {
                detectionDetails.append("✓ 下载并验证成功\n");
                fillResult(result, pipInfo, "pip");
                result.setSuccess(true);
                result.setDownloaded(true);
                result.setDetectionDetails(detectionDetails.toString());
                return result;
            } else {
                detectionDetails.append("✗ 下载或验证失败\n");
            }
        } else {
            detectionDetails.append("✗ PyPI仓库中不存在\n");
        }
        
        // 全部失败
        result.setErrorMessage("包在npm和PyPI仓库中都不存在，请检查包名是否正确");
        result.setDetectionDetails(detectionDetails.toString());
        return result;
    }
    
    /**
     * 检查npm仓库中是否存在包
     */
    private boolean checkNpmRegistry(String packageName) {
        try {
            String npmCmd = CommandLocator.locateCommand("npm");
            
            // 使用淘宝镜像加速
            ProcessBuilder pb = new ProcessBuilder(
                npmCmd, "view", packageName, "version",
                "--registry=https://registry.npmmirror.com"
            );
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            boolean finished = process.waitFor(15, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            
            int exitCode = process.exitValue();
            String outputStr = output.toString().trim();
            
            // 如果exitCode=0且有版本号输出，说明包存在
            // 匹配版本号格式：x.y.z 或 x.y 或单独的数字
            boolean exists = exitCode == 0 && !outputStr.isEmpty() && 
                           (outputStr.matches("\\d+(\\.\\d+)*") || outputStr.contains("version"));
            
            if (exists) {
                log.info("npm仓库中找到包: {} version: {}", packageName, outputStr);
            } else {
                log.debug("npm仓库检查结果: exitCode={}, output={}", exitCode, outputStr);
            }
            
            return exists;
            
        } catch (Exception e) {
            log.debug("检查npm仓库失败: {}", packageName, e);
            return false;
        }
    }
    
    /**
     * 检查PyPI仓库中是否存在包
     */
    private boolean checkPypiRegistry(String packageName) {
        try {
            // 将包名转换为PyPI格式（下划线转连字符）
            String pypiPackageName = packageName.replace("_", "-");
            
            // 使用清华镜像加速
            String pipCmd = CommandLocator.locateCommand("pip");
            
            ProcessBuilder pb = new ProcessBuilder(
                pipCmd, "index", "versions", pypiPackageName,
                "-i", "https://pypi.tuna.tsinghua.edu.cn/simple"
            );
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            boolean finished = process.waitFor(15, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            
            String outputStr = output.toString();
            
            // 如果输出包含版本信息，说明包存在
            boolean exists = outputStr.contains("Available versions:") || 
                           outputStr.matches(".*\\d+\\.\\d+\\.\\d+.*");
            
            if (exists) {
                log.info("PyPI仓库中找到包: {}", pypiPackageName);
            }
            
            return exists;
            
        } catch (Exception e) {
            log.debug("检查PyPI仓库失败: {}", packageName, e);
            return false;
        }
    }
    
    /**
     * 下载并验证npm包（使用MCP Node环境）
     */
    private boolean downloadAndVerifyNpm(String packageName) {
        try {
            log.info("开始安装npm包: {}", packageName);
            
            // 获取MCP Node环境目录
            String nodeEnvPath = getMcpNodeEnvPath();
            File nodeEnvDir = new File(nodeEnvPath);
            
            if (!nodeEnvDir.exists()) {
                log.error("Node环境不存在，请先创建: {}", nodeEnvPath);
                return false;
            }
            
            // 定位npm命令
            String npmCmd = CommandLocator.locateCommand("npm");
            
            // 执行 npm install
            ProcessBuilder pb = new ProcessBuilder(npmCmd, "install", packageName);
            pb.directory(nodeEnvDir);
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    log.debug("[npm] {}", line);
                }
            }
            
            boolean finished = process.waitFor(90, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("npm安装超时: {}", packageName);
                return false;
            }
            
            int exitCode = process.exitValue();
            log.info("npm安装完成 [package={}, exitCode={}]", packageName, exitCode);
            
            return exitCode == 0;
            
        } catch (Exception e) {
            log.error("安装npm包失败: {}", packageName, e);
            return false;
        }
    }
    
    /**
     * 下载并验证pip包（使用MCP Python虚拟环境）
     * @return PackageInfo 如果成功则返回包信息，否则返回null
     */
    private PackageInfo downloadAndVerifyPip(String packageName) {
        try {
            log.info("开始安装pip包: {}", packageName);
            
            // 获取MCP Python虚拟环境的pip
            String pipCmd = getMcpPythonPip();
            
            if (pipCmd == null) {
                log.error("MCP Python虚拟环境不存在，请先创建");
                return null;
            }
            
            // 执行 pip install
            ProcessBuilder pb = new ProcessBuilder(pipCmd, "install", packageName);
            pb.redirectErrorStream(true);
            
            // 添加pip镜像环境变量
            Map<String, String> env = pb.environment();
            env.put("PIP_INDEX_URL", "https://pypi.tuna.tsinghua.edu.cn/simple");
            
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            Map<String, String> envVarDefaults = new HashMap<>();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    log.debug("[pip] {}", line);
                }
            }
            
            boolean finished = process.waitFor(90, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("pip安装超时: {}", packageName);
                return null;
            }
            
            int exitCode = process.exitValue();
            String outputStr = output.toString();
            
            log.info("pip安装完成 [package={}, exitCode={}]", packageName, exitCode);
            
            // 判断是否构建失败
            boolean buildFailed = outputStr.contains("Failed to build") || 
                                 outputStr.contains("error: could not download") ||
                                 outputStr.contains("CalledProcessError");
            
            // 如果构建失败但包存在，尝试从预定义配置获取环境变量
            if (buildFailed && envVarDefaults.isEmpty()) {
                Map<String, String> predefinedVars = getPredefinedEnvVars(packageName);
                if (!predefinedVars.isEmpty()) {
                    log.info("构建失败，使用预定义环境变量配置: {}", predefinedVars.keySet());
                    envVarDefaults.putAll(predefinedVars);
                }
            }
            
            // 成功条件
            boolean success = exitCode == 0 ||
                            outputStr.contains("Successfully installed") ||
                            (!envVarDefaults.isEmpty()) ||
                            (buildFailed && outputStr.contains("depends on"));
            
            if (buildFailed) {
                log.warn("包构建失败但可能仍可用 [package={}, envVarsDetected={}]", 
                    packageName, !envVarDefaults.isEmpty());
            }
            
            if (success) {
                PackageInfo info = new PackageInfo();
                info.exists = true;
                info.version = "latest";
                info.metadata = new HashMap<>();
                info.metadata.put("envVarDefaults", envVarDefaults);
                info.metadata.put("output", outputStr);
                
                if (buildFailed) {
                    info.metadata.put("buildWarning", "包构建失败，可能缺少编译环境(如Rust/C++)，但已安装");
                }
                
                return info;
            }
            
            return null;
            
        } catch (Exception e) {
            log.error("安装pip包失败: {}", packageName, e);
            return null;
        }
    }
    
    /**
     * 获取MCP Python虚拟环境的pip路径
     */
    private String getMcpPythonPip() {
        String envPath = getMcpPythonVenvPath();
        File venvDir = new File(envPath);
        
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
     * 获取MCP Python虚拟环境路径
     */
    private String getMcpPythonVenvPath() {
        return envBasePath + File.separator + "python-env";
    }
    
    /**
     * 获取MCP Node环境路径
     */
    private String getMcpNodeEnvPath() {
        return envBasePath + File.separator + "node-env";
    }
    
    /**
     * 获取预定义的环境变量配置（用于无法直接检测的包）
     */
    private Map<String, String> getPredefinedEnvVars(String packageName) {
        Map<String, String> vars = new HashMap<>();
        
        String lowerName = packageName.toLowerCase();
        
        // MySQL相关
        if (lowerName.contains("mysql")) {
            vars.put("MYSQL_HOST", "localhost");
            vars.put("MYSQL_PORT", "3306");
            vars.put("MYSQL_USER", "root");
            vars.put("MYSQL_PASSWORD", "");
            vars.put("MYSQL_DATABASE", "");
        }
        
        // PostgreSQL相关
        if (lowerName.contains("postgres") || lowerName.contains("pg")) {
            vars.put("POSTGRES_HOST", "localhost");
            vars.put("POSTGRES_PORT", "5432");
            vars.put("POSTGRES_USER", "postgres");
            vars.put("POSTGRES_PASSWORD", "");
            vars.put("POSTGRES_DB", "postgres");
        }
        
        // SQLite相关
        if (lowerName.contains("sqlite")) {
            vars.put("SQLITE_DB_PATH", "");
        }
        
        // Redis相关
        if (lowerName.contains("redis")) {
            vars.put("REDIS_HOST", "localhost");
            vars.put("REDIS_PORT", "6379");
            vars.put("REDIS_PASSWORD", "");
        }
        
        // MongoDB相关
        if (lowerName.contains("mongo")) {
            vars.put("MONGODB_URI", "mongodb://localhost:27017");
        }
        
        // GitHub相关
        if (lowerName.contains("github")) {
            vars.put("GITHUB_TOKEN", "");
        }
        
        // GitLab相关
        if (lowerName.contains("gitlab")) {
            vars.put("GITLAB_TOKEN", "");
            vars.put("GITLAB_URL", "https://gitlab.com");
        }
        
        // Slack相关
        if (lowerName.contains("slack")) {
            vars.put("SLACK_TOKEN", "");
        }
        
        return vars;
    }
    
    /**
     * 推断环境变量的值
     */
    private String inferEnvValue(String varName) {
        String upper = varName.toUpperCase();
        
        // HOST类
        if (upper.endsWith("_HOST") || upper.endsWith("_HOSTNAME")) {
            return "localhost";
        }
        
        // PORT类
        if (upper.endsWith("_PORT")) {
            return inferPort(upper);
        }
        
        // USER/USERNAME类
        if (upper.endsWith("_USER") || upper.endsWith("_USERNAME")) {
            if (upper.contains("MYSQL")) return "root";
            if (upper.contains("POSTGRES") || upper.contains("PG")) return "postgres";
            if (upper.contains("REDIS")) return "";
            return "admin";
        }
        
        // DATABASE类
        if (upper.endsWith("_DATABASE") || upper.endsWith("_DB") || upper.equals("DATABASE")) {
            if (upper.contains("MYSQL")) return "mysql";
            if (upper.contains("POSTGRES")) return "postgres";
            return "mydb";
        }
        
        // PASSWORD类 - 默认为空让用户填写
        if (upper.endsWith("_PASSWORD") || upper.endsWith("_PASSWD") || upper.equals("PASSWORD")) {
            return "";
        }
        
        // API KEY / TOKEN - 默认为空
        if (upper.contains("API_KEY") || upper.contains("APIKEY") ||
            upper.contains("TOKEN") || upper.contains("SECRET")) {
            return "";
        }
        
        // URL类
        if (upper.endsWith("_URL")) {
            return "";
        }
        
        // PATH类
        if (upper.endsWith("_PATH")) {
            return "";
        }
        
        // 其他默认空
        return "";
    }
    
    /**
     * 推断端口号
     */
    private String inferPort(String varName) {
        String upper = varName.toUpperCase();
        
        if (upper.contains("MYSQL")) return "3306";
        if (upper.contains("POSTGRES") || upper.contains("PG")) return "5432";
        if (upper.contains("REDIS")) return "6379";
        if (upper.contains("MONGODB") || upper.contains("MONGO")) return "27017";
        if (upper.contains("ELASTICSEARCH")) return "9200";
        if (upper.contains("HTTP")) return "80";
        if (upper.contains("HTTPS")) return "443";
        
        return "";
    }
    
    /**
     * 填充结果
     */
    private void fillResult(PackageDetectionResult result, PackageInfo info, String runtimeType) {
        result.setRuntimeType(runtimeType);
        result.setVersion(info.version != null ? info.version : "latest");
        
        // 生成插件名称
        String packageName = result.getPackageName();
        String pluginName = packageName;

        result.setPluginName(pluginName);

        // 生成启动命令
        List<String> command = new ArrayList<>();
        if ("npm".equals(runtimeType)) {
            // Node.js包：使用node直接运行
            command.add("node");
            // 包的入口文件路径需要从package.json或node_modules推断
            command.add(getMcpNodeEnvPath() + "/node_modules/" + packageName);
        } else if ("pip".equals(runtimeType)) {
            // Python包：使用虚拟环境的python运行
            String pythonExe = getMcpPythonVenvPath() + "/Scripts/python.exe";
            command.add(pythonExe);
            command.add("-m");
            // 将包名中的-转为_（Python模块命名规则）
            command.add(packageName.replace("-", "_"));
        }
        result.setStartCommand(com.alibaba.fastjson2.JSON.toJSONString(command));
        
        // 判断是否官方包
        if (packageName.startsWith("@modelcontextprotocol/")) {
            result.setIsOfficial("1");
        } else {
            result.setIsOfficial("0");
        }
        
        // 自动分类
        result.setCategory(detectCategory(packageName));
        
        // 描述
        result.setDescription(pluginName + " MCP插件");
        
        // 处理环境变量
        Map<String, String> envVars = new HashMap<>();
        
        // 从 metadata 中获取检测到的环境变量
        if (info.metadata != null) {
            // 优先使用 envVarDefaults（从包安装过程解析的）
            if (info.metadata.containsKey("envVarDefaults")) {
                @SuppressWarnings("unchecked")
                Map<String, String> detectedVars = (Map<String, String>) info.metadata.get("envVarDefaults");
                
                if (detectedVars != null && !detectedVars.isEmpty()) {
                    log.info("检测到包需要环境变量: {}", detectedVars.keySet());
                    
                    // 智能填充默认值
                    for (Map.Entry<String, String> entry : detectedVars.entrySet()) {
                        String varName = entry.getKey();
                        String detectedValue = entry.getValue();
                        
                        // 如果已经检测到值，使用检测值；否则智能推断
                        if (detectedValue != null && !detectedValue.isEmpty()) {
                            envVars.put(varName, detectedValue);
                        } else {
                            String inferredValue = getDefaultEnvValue(varName, packageName);
                            envVars.put(varName, inferredValue);
                        }
                    }
                }
            }
            // 兼容旧格式 requiredEnvVars（只有变量名列表）
            else if (info.metadata.containsKey("requiredEnvVars")) {
                @SuppressWarnings("unchecked")
                List<String> requiredVars = (List<String>) info.metadata.get("requiredEnvVars");
                
                if (requiredVars != null && !requiredVars.isEmpty()) {
                    log.info("检测到包需要环境变量: {}", requiredVars);
                    
                    for (String varName : requiredVars) {
                        String defaultValue = getDefaultEnvValue(varName, packageName);
                        envVars.put(varName, defaultValue);
                    }
                }
            }
        }
        
        result.setEnvVars(com.alibaba.fastjson2.JSON.toJSONString(envVars));
        result.setMetadata(info.metadata);
    }
    
    /**
     * 获取环境变量的默认值（动态检测系统配置）
     */
    private String getDefaultEnvValue(String varName, String packageName) {
        log.debug("为环境变量 {} 检测默认值", varName);
        
        // 1. 尝试从系统环境变量读取
        String envValue = System.getenv(varName);
        if (envValue != null && !envValue.isEmpty()) {
            log.info("从系统环境变量获取: {} = {}", varName, envValue);
            return envValue;
        }
        
        // 2. 根据变量名的语义推断并检测
        EnvVarInfo info = new EnvVarInfo(varName);
        String detectedValue = detectServiceConfig(info);
        
        if (detectedValue != null) {
            log.info("自动检测到配置: {} = {}", varName, detectedValue);
            return detectedValue;
        }
        
        // 3. 返回通用默认值
        return getGenericDefault(info);
    }
    
    /**
     * 环境变量信息
     */
    private static class EnvVarInfo {
        String serviceName;  // mysql, postgres, redis等
        String propertyType; // host, port, user, password, database等
        String rawName;      // 原始变量名
        
        EnvVarInfo(String rawName) {
            this.rawName = rawName;
            parseServiceAndProperty(rawName);
        }
        
        private void parseServiceAndProperty(String varName) {
            String lowerVar = varName.toLowerCase();
            
            // 识别服务类型
            if (lowerVar.contains("mysql")) serviceName = "mysql";
            else if (lowerVar.contains("postgres") || lowerVar.contains("pg")) serviceName = "postgres";
            else if (lowerVar.contains("redis")) serviceName = "redis";
            else if (lowerVar.contains("mongo")) serviceName = "mongodb";
            else if (lowerVar.contains("elasticsearch")) serviceName = "elasticsearch";
            else if (lowerVar.contains("rabbitmq")) serviceName = "rabbitmq";
            else serviceName = "unknown";
            
            // 识别属性类型
            if (lowerVar.endsWith("_host") || lowerVar.endsWith("_hostname")) propertyType = "host";
            else if (lowerVar.endsWith("_port")) propertyType = "port";
            else if (lowerVar.endsWith("_user") || lowerVar.endsWith("_username")) propertyType = "user";
            else if (lowerVar.endsWith("_password") || lowerVar.endsWith("_pass")) propertyType = "password";
            else if (lowerVar.endsWith("_database") || lowerVar.endsWith("_db")) propertyType = "database";
            else if (lowerVar.endsWith("_url")) propertyType = "url";
            else if (lowerVar.endsWith("_api_key") || lowerVar.endsWith("_apikey")) propertyType = "apikey";
            else if (lowerVar.endsWith("_token")) propertyType = "token";
            else propertyType = "unknown";
        }
    }
    
    /**
     * 检测服务配置
     */
    private String detectServiceConfig(EnvVarInfo info) {
        if ("unknown".equals(info.serviceName)) {
            return null;
        }
        
        // 根据属性类型检测
        switch (info.propertyType) {
            case "host":
                return detectHost(info.serviceName);
            case "port":
                return detectPort(info.serviceName);
            case "user":
                return getDefaultUser(info.serviceName);
            case "database":
                return getDefaultDatabase(info.serviceName);
            default:
                return null;
        }
    }
    
    /**
     * 检测主机地址
     */
    private String detectHost(String serviceName) {
        // 1. 检测本地端口是否监听
        Integer defaultPort = getServiceDefaultPort(serviceName);
        if (defaultPort != null && isLocalServiceRunning(defaultPort)) {
            return "localhost";
        }
        
        // 2. 检测Docker容器
        String dockerHost = detectDockerService(serviceName);
        if (dockerHost != null) return dockerHost;
        
        return "localhost";
    }
    
    /**
     * 检测端口
     */
    private String detectPort(String serviceName) {
        Integer defaultPort = getServiceDefaultPort(serviceName);
        return defaultPort != null ? String.valueOf(defaultPort) : "";
    }
    
    /**
     * 获取服务默认端口
     */
    private Integer getServiceDefaultPort(String serviceName) {
        Map<String, Integer> defaultPorts = new HashMap<>();
        defaultPorts.put("mysql", 3306);
        defaultPorts.put("postgres", 5432);
        defaultPorts.put("redis", 6379);
        defaultPorts.put("mongodb", 27017);
        defaultPorts.put("elasticsearch", 9200);
        defaultPorts.put("rabbitmq", 5672);
        return defaultPorts.get(serviceName);
    }
    
    /**
     * 获取默认用户名
     */
    private String getDefaultUser(String serviceName) {
        Map<String, String> defaultUsers = new HashMap<>();
        defaultUsers.put("mysql", "root");
        defaultUsers.put("postgres", "postgres");
        defaultUsers.put("mongodb", "admin");
        defaultUsers.put("rabbitmq", "guest");
        return defaultUsers.getOrDefault(serviceName, "");
    }
    
    /**
     * 获取默认数据库名
     */
    private String getDefaultDatabase(String serviceName) {
        Map<String, String> defaultDbs = new HashMap<>();
        defaultDbs.put("mysql", "test");
        defaultDbs.put("postgres", "postgres");
        defaultDbs.put("mongodb", "test");
        return defaultDbs.getOrDefault(serviceName, "");
    }
    
    /**
     * 获取通用默认值
     */
    private String getGenericDefault(EnvVarInfo info) {
        // 敏感信息默认为空
        if ("password".equals(info.propertyType) || 
            "apikey".equals(info.propertyType) || 
            "token".equals(info.propertyType)) {
            return "";
        }
        return "";
    }
    
    /**
     * 检测本地服务是否运行
     */
    private boolean isLocalServiceRunning(int port) {
        try {
            ProcessBuilder pb = new ProcessBuilder("netstat", "-an");
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(":" + port) && (line.contains("LISTEN") || line.contains("ESTABLISHED"))) {
                    return true;
                }
            }
            process.waitFor();
        } catch (Exception e) {
            log.debug("检测本地服务失败: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * 检测Docker容器中的服务
     */
    private String detectDockerService(String serviceName) {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "ps", "--format", "{{.Names}}");
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.toLowerCase().contains(serviceName)) {
                    return line.trim();
                }
            }
            process.waitFor();
        } catch (Exception e) {
            log.debug("检测Docker服务失败: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * 提取版本号
     */
    private String extractVersion(String output) {
        // 匹配版本号模式
        Pattern pattern = Pattern.compile("(\\d+\\.\\d+\\.\\d+)");
        Matcher matcher = pattern.matcher(output);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        // 匹配 v1.2.3 格式
        pattern = Pattern.compile("v(\\d+\\.\\d+\\.\\d+)");
        matcher = pattern.matcher(output);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return "latest";
    }
    
    /**
     * 检测分类
     */
    private String detectCategory(String packageName) {
        String lowerName = packageName.toLowerCase();
        
        if (lowerName.contains("sqlite") || lowerName.contains("postgres") || 
            lowerName.contains("mysql") || lowerName.contains("database") || 
            lowerName.contains("db")) {
            return "database";
        }
        
        if (lowerName.contains("file") || lowerName.contains("filesystem") || 
            lowerName.contains("fs")) {
            return "file_system";
        }
        
        if (lowerName.contains("search") || lowerName.contains("web") || 
            lowerName.contains("http") || lowerName.contains("fetch")) {
            return "web_search";
        }
        
        if (lowerName.contains("chart") || lowerName.contains("echarts") || 
            lowerName.contains("graph")) {
            return "utility";
        }
        
        return "custom";
    }
    
    /**
     * 包信息
     */
    private static class PackageInfo {
        boolean exists = false;
        String version;
        String message;
        Map<String, Object> metadata;
    }
}
