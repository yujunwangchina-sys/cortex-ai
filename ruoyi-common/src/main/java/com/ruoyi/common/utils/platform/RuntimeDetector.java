package com.ruoyi.common.utils.platform;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 运行时环境检测工具
 * 
 * @author ruoyi
 */
public class RuntimeDetector {
    
    private static final Logger log = LoggerFactory.getLogger(RuntimeDetector.class);
    
    /**
     * 自定义路径提供器（用于从配置中获取自定义路径）
     */
    private static Function<String, String> customPathProvider = null;
    
    /**
     * 设置自定义路径提供器
     * @param provider 路径提供器函数，接收运行时类型，返回自定义路径
     */
    public static void setCustomPathProvider(Function<String, String> provider) {
        customPathProvider = provider;
    }
    
    /**
     * 运行时类型枚举
     */
    public enum RuntimeType {
        PYTHON("python", "Python"),
        NODE("node", "Node.js");
        
        private final String command;
        private final String displayName;
        
        RuntimeType(String command, String displayName) {
            this.command = command;
            this.displayName = displayName;
        }
        
        public String getCommand() {
            return command;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    /**
     * 检测所有运行时环境
     */
    public static Map<String, RuntimeInfo> detectAll() {
        Map<String, RuntimeInfo> runtimes = new HashMap<>();
        
        // 检测Python (先尝试python3，再尝试python，支持自定义路径)
        RuntimeInfo python = detectPython();
        runtimes.put("python", python);
        
        // 检测Node.js (支持自定义路径)
        RuntimeInfo node = detectNodeWithCustomPath();
        runtimes.put("node", node);
        
        return runtimes;
    }
    
    /**
     * 检测Node.js（支持自定义路径）
     */
    private static RuntimeInfo detectNodeWithCustomPath() {
        // 1. 先尝试从自定义配置获取路径
        if (customPathProvider != null) {
            String customPath = customPathProvider.apply("node");
            if (customPath != null && !customPath.trim().isEmpty()) {
                RuntimeInfo customInfo = detectRuntimeWithPath(customPath, "--version");
                if (customInfo.isAvailable()) {
                    customInfo.setCommand("node");
                    log.info("使用自定义Node路径: {}", customPath);
                    return customInfo;
                } else {
                    log.warn("自定义Node路径无效: {}", customPath);
                }
            }
        }
        
        // 2. 使用默认检测
        return detectRuntime("node", "--version");
    }
    
    /**
     * 检测Python（优先python3，支持自定义路径）
     */
    private static RuntimeInfo detectPython() {
        // 1. 先尝试从自定义配置获取路径
        if (customPathProvider != null) {
            String customPath = customPathProvider.apply("python");
            if (customPath != null && !customPath.trim().isEmpty()) {
                RuntimeInfo customInfo = detectRuntimeWithPath(customPath, "--version");
                if (customInfo.isAvailable()) {
                    customInfo.setCommand("python");
                    log.info("使用自定义Python路径: {}", customPath);
                    return customInfo;
                } else {
                    log.warn("自定义Python路径无效: {}", customPath);
                }
            }
        }
        
        // 2. 尝试 python3
        RuntimeInfo python3 = detectRuntime("python3", "--version");
        if (python3.isAvailable()) {
            python3.setCommand("python3");
            return python3;
        }
        
        // 3. 尝试 python
        RuntimeInfo python = detectRuntime("python", "--version");
        if (python.isAvailable()) {
            python.setCommand("python");
            return python;
        }
        
        return RuntimeInfo.notAvailable("python");
    }
    
    /**
     * 使用指定路径检测运行时
     */
    private static RuntimeInfo detectRuntimeWithPath(String executablePath, String... versionArgs) {
        try {
            File exeFile = new File(executablePath);
            if (!exeFile.exists()) {
                log.debug("可执行文件不存在: {}", executablePath);
                return RuntimeInfo.notAvailable(executablePath);
            }
            
            if (!exeFile.canExecute()) {
                log.debug("文件不可执行: {}", executablePath);
                return RuntimeInfo.notAvailable(executablePath);
            }
            
            // 构建完整命令
            String[] fullCommand = new String[versionArgs.length + 1];
            fullCommand[0] = executablePath;
            System.arraycopy(versionArgs, 0, fullCommand, 1, versionArgs.length);
            
            // 执行命令
            Process process = Runtime.getRuntime().exec(fullCommand);
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            
            if (!finished) {
                process.destroy();
                return RuntimeInfo.notAvailable(executablePath);
            }
            
            int exitCode = process.exitValue();
            
            if (exitCode == 0) {
                // 读取版本信息
                String version = readOutput(process);
                
                RuntimeInfo info = new RuntimeInfo(executablePath, version, true);
                info.setPath(executablePath);
                return info;
            } else {
                return RuntimeInfo.notAvailable(executablePath);
            }
            
        } catch (Exception e) {
            log.debug("检测运行时失败: {} - {}", executablePath, e.getMessage());
            return RuntimeInfo.notAvailable(executablePath);
        }
    }
    
    /**
     * 检测单个运行时
     */
    public static RuntimeInfo detectRuntime(String command, String... versionArgs) {
        try {
            // 构建命令
            String[] fullCommand;
            String os = System.getProperty("os.name").toLowerCase();
            
            // Windows下，某些命令需要通过cmd执行（如npm.cmd）
            if (os.contains("win")) {
                fullCommand = new String[versionArgs.length + 3];
                fullCommand[0] = "cmd";
                fullCommand[1] = "/c";
                fullCommand[2] = command;
                System.arraycopy(versionArgs, 0, fullCommand, 3, versionArgs.length);
            } else {
                fullCommand = new String[versionArgs.length + 1];
                fullCommand[0] = command;
                System.arraycopy(versionArgs, 0, fullCommand, 1, versionArgs.length);
            }
            
            // 执行命令
            Process process = Runtime.getRuntime().exec(fullCommand);
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            
            if (!finished) {
                process.destroy();
                return RuntimeInfo.notAvailable(command);
            }
            
            int exitCode = process.exitValue();
            
            if (exitCode == 0) {
                // 读取版本信息
                String version = readOutput(process);
                
                // 获取命令路径
                String path = getCommandPath(command);
                
                RuntimeInfo info = new RuntimeInfo(command, version, true);
                info.setPath(path);
                return info;
            } else {
                return RuntimeInfo.notAvailable(command);
            }
            
        } catch (Exception e) {
            log.debug("检测运行时失败: {} - {}", command, e.getMessage());
            return RuntimeInfo.notAvailable(command);
        }
    }
    
    /**
     * 获取命令的完整路径
     */
    private static String getCommandPath(String command) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String[] whereCommand;
            
            if (os.contains("win")) {
                // Windows使用where命令
                whereCommand = new String[]{"cmd", "/c", "where", command};
            } else {
                // Linux/Mac使用which命令
                whereCommand = new String[]{"which", command};
            }
            
            Process process = Runtime.getRuntime().exec(whereCommand);
            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            
            if (!finished) {
                process.destroy();
                return null;
            }
            
            if (process.exitValue() == 0) {
                String output = readOutput(process);
                // 返回第一行路径（where可能返回多个路径）
                String[] lines = output.split("\n");
                return lines.length > 0 ? lines[0].trim() : null;
            }
            
        } catch (Exception e) {
            log.debug("获取命令路径失败: {} - {}", command, e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 读取进程输出
     */
    private static String readOutput(Process process) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            
            String result = output.toString().trim();
            
            // 如果stdout为空，尝试读取stderr（有些命令版本信息在stderr）
            if (result.isEmpty()) {
                try (BufferedReader errReader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()))) {
                    StringBuilder errOutput = new StringBuilder();
                    while ((line = errReader.readLine()) != null) {
                        errOutput.append(line).append("\n");
                    }
                    result = errOutput.toString().trim();
                }
            }
            
            return result;
        } catch (Exception e) {
            return "";
        }
    }
    

    
    /**
     * 运行时信息类
     */
    public static class RuntimeInfo {
        private String command;
        private String version;
        private boolean available;
        private String path;
        
        public RuntimeInfo() {
        }
        
        public RuntimeInfo(String command, String version, boolean available) {
            this.command = command;
            this.version = version;
            this.available = available;
        }
        
        public static RuntimeInfo notAvailable(String command) {
            return new RuntimeInfo(command, "", false);
        }
        
        // Getters and Setters
        public String getCommand() { return command; }
        public void setCommand(String command) { this.command = command; }
        
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        
        public boolean isAvailable() { return available; }
        public void setAvailable(boolean available) { this.available = available; }
        
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        
        @Override
        public String toString() {
            return String.format("%s: %s (available=%s)", command, version, available);
        }
    }
}
