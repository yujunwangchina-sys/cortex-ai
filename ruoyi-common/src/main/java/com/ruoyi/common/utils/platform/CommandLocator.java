package com.ruoyi.common.utils.platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 命令定位工具类
 * 自动查找系统中已安装的命令完整路径
 * 
 * @author ruoyi
 */
public class CommandLocator {
    
    private static final Logger log = LoggerFactory.getLogger(CommandLocator.class);
    
    /** 命令路径缓存 */
    private static final Map<String, String> COMMAND_CACHE = new ConcurrentHashMap<>();
    
    /** 常用命令的可能位置（Windows） */
    private static final Map<String, List<String>> COMMON_LOCATIONS = new HashMap<>();
    
    static {
        // Python相关
        COMMON_LOCATIONS.put("python", Arrays.asList(
            "${USERPROFILE}\\AppData\\Local\\Programs\\Python\\Python312\\python.exe",
            "${USERPROFILE}\\AppData\\Local\\Programs\\Python\\Python311\\python.exe",
            "${USERPROFILE}\\AppData\\Local\\Programs\\Python\\Python310\\python.exe",
            "C:\\Python312\\python.exe",
            "C:\\Python311\\python.exe",
            "C:\\Python310\\python.exe"
        ));
        
        // Node.js相关
        COMMON_LOCATIONS.put("node", Arrays.asList(
            "${ProgramFiles}\\nodejs\\node.exe",
            "C:\\Program Files\\nodejs\\node.exe",
            "C:\\Program Files (x86)\\nodejs\\node.exe"
        ));
    }
    
    /**
     * 查找命令完整路径
     * 
     * @param command 命令名称（不带扩展名）
     * @return 完整路径，找不到返回原命令名加扩展名
     */
    public static String locateCommand(String command) {
        // 检查缓存
        if (COMMAND_CACHE.containsKey(command)) {
            String cached = COMMAND_CACHE.get(command);
            if (new File(cached).exists()) {
                return cached;
            } else {
                // 缓存失效，移除
                COMMAND_CACHE.remove(command);
            }
        }
        
        String fullPath = null;
        
        if (PlatformUtils.isWindows()) {
            fullPath = locateOnWindows(command);
        } else {
            fullPath = locateOnUnix(command);
        }
        
        if (fullPath != null) {
            COMMAND_CACHE.put(command, fullPath);
            log.info("命令定位成功: {} -> {}", command, fullPath);
            return fullPath;
        }
        
        // 找不到，返回带扩展名的命令
        String fallback = PlatformUtils.addPlatformExtension(command);
        log.warn("命令未找到，使用默认: {} -> {}", command, fallback);
        return fallback;
    }
    
    /**
     * Windows下查找命令
     */
    private static String locateOnWindows(String command) {
        // 1. 尝试使用where命令查找
        String whereResult = findUsingWhere(command);
        if (whereResult != null) {
            return whereResult;
        }
        
        // 2. 检查常见位置
        String commonLocation = checkCommonLocations(command);
        if (commonLocation != null) {
            return commonLocation;
        }
        
        // 3. 在PATH环境变量中逐个目录查找
        String pathResult = searchInPath(command);
        if (pathResult != null) {
            return pathResult;
        }
        
        return null;
    }
    
    /**
     * Unix/Linux/Mac下查找命令
     */
    private static String locateOnUnix(String command) {
        // 使用which命令查找
        try {
            Process process = new ProcessBuilder("which", command)
                .redirectErrorStream(true)
                .start();
            
            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }
            
            if (process.exitValue() == 0) {
                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                    String line = reader.readLine();
                    if (line != null && !line.isEmpty()) {
                        File file = new File(line.trim());
                        if (file.exists() && file.canExecute()) {
                            return file.getAbsolutePath();
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("which命令执行失败: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 使用where命令查找（Windows）
     */
    private static String findUsingWhere(String command) {
        try {
            String cmdWithExt = PlatformUtils.addPlatformExtension(command);
            
            Process process = new ProcessBuilder("cmd", "/c", "where", cmdWithExt)
                .redirectErrorStream(true)
                .start();
            
            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }
            
            if (process.exitValue() == 0) {
                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.contains("WindowsApps")) {
                            // 跳过Microsoft Store的别名
                            File file = new File(line);
                            if (file.exists() && file.canExecute()) {
                                return file.getAbsolutePath();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("where命令执行失败: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 检查常见安装位置
     */
    private static String checkCommonLocations(String command) {
        List<String> locations = COMMON_LOCATIONS.get(command);
        if (locations == null) {
            return null;
        }
        
        for (String location : locations) {
            String expanded = expandEnvironmentVariables(location);
            File file = new File(expanded);
            if (file.exists() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }
        
        return null;
    }
    
    /**
     * 在PATH环境变量中搜索
     */
    private static String searchInPath(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isEmpty()) {
            return null;
        }
        
        String cmdWithExt = PlatformUtils.addPlatformExtension(command);
        String[] paths = pathEnv.split(File.pathSeparator);
        
        for (String pathDir : paths) {
            if (pathDir.isEmpty() || pathDir.contains("WindowsApps")) {
                continue; // 跳过Microsoft Store目录
            }
            
            File file = new File(pathDir, cmdWithExt);
            if (file.exists() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }
        
        return null;
    }
    
    /**
     * 展开环境变量
     */
    private static String expandEnvironmentVariables(String path) {
        String result = path;
        
        // 替换常见的环境变量
        Map<String, String> envVars = new HashMap<>();
        envVars.put("${USERPROFILE}", System.getProperty("user.home"));
        envVars.put("${ProgramFiles}", System.getenv("ProgramFiles"));
        envVars.put("${ProgramFiles(x86)}", System.getenv("ProgramFiles(x86)"));
        envVars.put("${APPDATA}", System.getenv("APPDATA"));
        envVars.put("${LOCALAPPDATA}", System.getenv("LOCALAPPDATA"));
        
        for (Map.Entry<String, String> entry : envVars.entrySet()) {
            if (entry.getValue() != null) {
                result = result.replace(entry.getKey(), entry.getValue());
            }
        }
        
        return result;
    }
    
    /**
     * 清除缓存
     */
    public static void clearCache() {
        COMMAND_CACHE.clear();
    }
    
    /**
     * 检查命令是否可用
     * 
     * @param command 命令名称
     * @return 是否可用
     */
    public static boolean isCommandAvailable(String command) {
        String fullPath = locateCommand(command);
        return new File(fullPath).exists();
    }
    
    /**
     * 获取命令版本信息
     * 
     * @param command 命令名称
     * @return 版本信息，失败返回null
     */
    public static String getCommandVersion(String command) {
        try {
            String fullPath = locateCommand(command);
            
            Process process = new ProcessBuilder(fullPath, "--version")
                .redirectErrorStream(true)
                .start();
            
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }
            
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
                return reader.readLine();
            }
        } catch (Exception e) {
            log.debug("获取命令版本失败: {}", e.getMessage());
            return null;
        }
    }
}
