package com.ruoyi.common.utils.platform;

/**
 * 平台工具类
 * 用于判断操作系统类型和处理平台相关的命令
 * 
 * @author ruoyi
 */
public class PlatformUtils {
    
    private static final String OS_NAME = System.getProperty("os.name").toLowerCase();
    
    /**
     * 操作系统类型枚举
     */
    public enum OSType {
        WINDOWS,
        LINUX,
        MAC,
        UNKNOWN
    }
    
    /**
     * 系统信息类
     */
    public static class SystemInfo {
        private String osType;
        private String osName;
        private String osVersion;
        private String osArch;
        
        public SystemInfo() {
            this.osName = System.getProperty("os.name");
            this.osVersion = System.getProperty("os.version");
            this.osArch = System.getProperty("os.arch");
            
            OSType type = getOSType();
            switch (type) {
                case WINDOWS:
                    this.osType = "windows";
                    break;
                case LINUX:
                    this.osType = "linux";
                    break;
                case MAC:
                    this.osType = "mac";
                    break;
                default:
                    this.osType = "unknown";
            }
        }
        
        public String getOsType() {
            return osType;
        }
        
        public String getOsName() {
            return osName;
        }
        
        public String getOsVersion() {
            return osVersion;
        }
        
        public String getOsArch() {
            return osArch;
        }
    }
    
    /**
     * 获取系统信息
     */
    public static SystemInfo getSystemInfo() {
        return new SystemInfo();
    }
    
    /**
     * 获取当前操作系统类型
     */
    public static OSType getOSType() {
        if (OS_NAME.contains("win")) {
            return OSType.WINDOWS;
        } else if (OS_NAME.contains("mac")) {
            return OSType.MAC;
        } else if (OS_NAME.contains("nix") || OS_NAME.contains("nux") || OS_NAME.contains("aix")) {
            return OSType.LINUX;
        } else {
            return OSType.UNKNOWN;
        }
    }
    
    /**
     * 判断是否为Windows系统
     */
    public static boolean isWindows() {
        return getOSType() == OSType.WINDOWS;
    }
    
    /**
     * 判断是否为Linux系统
     */
    public static boolean isLinux() {
        return getOSType() == OSType.LINUX;
    }
    
    /**
     * 判断是否为Mac系统
     */
    public static boolean isMac() {
        return getOSType() == OSType.MAC;
    }
    
    /**
     * 获取操作系统名称
     */
    public static String getOSName() {
        return OS_NAME;
    }
    
    /**
     * 为命令添加平台特定的扩展名（仅Windows需要）
     * 
     * @param command 命令名称
     * @return 添加扩展名后的命令
     */
    public static String addPlatformExtension(String command) {
        if (!isWindows()) {
            return command;
        }
        
        // 如果已经有扩展名，直接返回
        if (command.contains(".")) {
            return command;
        }
        
        // Windows下的常见命令映射
        switch (command.toLowerCase()) {
            // Node.js相关
            case "npm":
                return "npm.cmd";
            case "node":
                return "node.exe";
            case "yarn":
                return "yarn.cmd";
            case "pnpm":
                return "pnpm.cmd";
                
            // Python相关
            case "python":
            case "python3":
                return "python.exe";
            case "pip":
            case "pip3":
                return "pip.exe";
                
            // 其他工具
            case "java":
                return "java.exe";
            case "mvn":
                return "mvn.cmd";
            case "gradle":
                return "gradle.bat";
            case "git":
                return "git.exe";
                
            default:
                // 其他命令尝试添加.exe扩展名（大多数命令是exe）
                return command + ".exe";
        }
    }
    
    /**
     * 获取Shell命令前缀（用于执行复杂命令）
     * 
     * @return Windows返回 ["cmd", "/c"]，Unix返回 ["sh", "-c"]
     */
    public static String[] getShellPrefix() {
        if (isWindows()) {
            return new String[]{"cmd", "/c"};
        } else {
            return new String[]{"sh", "-c"};
        }
    }
    
    /**
     * 获取Python命令名称
     * 
     * @return Windows返回 "python.exe"，Unix返回 "python3"
     */
    public static String getPythonCommand() {
        if (isWindows()) {
            return "python.exe";
        } else {
            return "python3";
        }
    }
    
    /**
     * 获取路径分隔符
     * 
     * @return Windows返回 ";"，Unix返回 ":"
     */
    public static String getPathSeparator() {
        return System.getProperty("path.separator");
    }
    
    /**
     * 获取文件分隔符
     * 
     * @return Windows返回 "\\"，Unix返回 "/"
     */
    public static String getFileSeparator() {
        return System.getProperty("file.separator");
    }
    
    /**
     * 获取行分隔符
     * 
     * @return 系统的换行符
     */
    public static String getLineSeparator() {
        return System.getProperty("line.separator");
    }
    
    /**
     * 判断命令是否可执行
     * 
     * @param command 命令名称
     * @return 是否可执行
     */
    public static boolean isCommandAvailable(String command) {
        try {
            String[] checkCmd;
            if (isWindows()) {
                checkCmd = new String[]{"cmd", "/c", "where", addPlatformExtension(command)};
            } else {
                checkCmd = new String[]{"which", command};
            }
            
            Process process = Runtime.getRuntime().exec(checkCmd);
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
