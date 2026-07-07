# Maven阿里云镜像配置

## 问题描述
Maven从中央仓库下载依赖超时，导致构建失败。

## 解决方案：配置阿里云镜像

### 方法1：项目级配置（推荐）

在项目根目录的`pom.xml`中添加：

```xml
<repositories>
    <repository>
        <id>aliyun</id>
        <name>Aliyun Maven Repository</name>
        <url>https://maven.aliyun.com/repository/public</url>
        <releases>
            <enabled>true</enabled>
        </releases>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
    <repository>
        <id>central</id>
        <name>Maven Central</name>
        <url>https://repo.maven.apache.org/maven2</url>
        <releases>
            <enabled>true</enabled>
        </releases>
        <snapshots>
            <enabled>false</enabled>
        </snapshots>
    </repository>
</repositories>

<pluginRepositories>
    <pluginRepository>
        <id>aliyun-plugin</id>
        <name>Aliyun Plugin Repository</name>
        <url>https://maven.aliyun.com/repository/public</url>
        <releases>
            <enabled>true</enabled>
        </releases>
        <snapshots>
            <enabled>false</enabled>
        </snapshots>
    </pluginRepository>
</pluginRepositories>
```

### 方法2：全局配置

修改Maven的`settings.xml`文件（通常在`~/.m2/settings.xml`或`%USERPROFILE%\.m2\settings.xml`）：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 
          http://maven.apache.org/xsd/settings-1.0.0.xsd">
    
    <mirrors>
        <!-- 阿里云公共仓库 -->
        <mirror>
            <id>aliyun-public</id>
            <mirrorOf>*</mirrorOf>
            <name>Aliyun Public Repository</name>
            <url>https://maven.aliyun.com/repository/public</url>
        </mirror>
    </mirrors>
    
    <profiles>
        <profile>
            <id>aliyun</id>
            <repositories>
                <repository>
                    <id>aliyun-public</id>
                    <url>https://maven.aliyun.com/repository/public</url>
                    <releases>
                        <enabled>true</enabled>
                    </releases>
                    <snapshots>
                        <enabled>true</enabled>
                    </snapshots>
                </repository>
            </repositories>
            <pluginRepositories>
                <pluginRepository>
                    <id>aliyun-plugin</id>
                    <url>https://maven.aliyun.com/repository/public</url>
                    <releases>
                        <enabled>true</enabled>
                    </releases>
                    <snapshots>
                        <enabled>false</enabled>
                    </snapshots>
                </pluginRepository>
            </pluginRepositories>
        </profile>
    </profiles>
    
    <activeProfiles>
        <activeProfile>aliyun</activeProfile>
    </activeProfiles>
    
</settings>
```

## 清理缓存并重新下载

执行以下命令：

```bash
# 1. 删除本地仓库中的失败缓存
rm -rf ~/.m2/repository/dev/langchain4j/langchain4j-mcp

# Windows PowerShell
Remove-Item -Recurse -Force "$env:USERPROFILE\.m2\repository\dev\langchain4j\langchain4j-mcp"

# 2. 强制更新依赖
mvn clean install -U

# 或者在IDEA中
# 右键项目 -> Maven -> Reload Project
# 或者使用Maven工具窗口的刷新按钮
```

## LangChain4j MCP版本说明

**重要提示**：`langchain4j-mcp`模块是在**LangChain4j 1.0.0-alpha1**之后才引入的。

- ❌ **0.36.2** - 不支持MCP
- ✅ **1.0.0-alpha1+** - 支持MCP
- ✅ **1.0.0-alpha3** (推荐) - 稳定的Alpha版本

已将项目的LangChain4j版本升级到`1.0.0-alpha3`。

## 验证配置

```bash
# 测试Maven连接
mvn help:effective-settings

# 查看是否使用阿里云镜像
# 输出中应该包含 https://maven.aliyun.com/repository/public
```

## 其他可用的国内镜像

如果阿里云镜像有问题，可以尝试：

```xml
<!-- 华为云 -->
<mirror>
    <id>huawei</id>
    <mirrorOf>*</mirrorOf>
    <url>https://repo.huaweicloud.com/repository/maven/</url>
</mirror>

<!-- 腾讯云 -->
<mirror>
    <id>tencent</id>
    <mirrorOf>*</mirrorOf>
    <url>https://mirrors.cloud.tencent.com/nexus/repository/maven-public/</url>
</mirror>
```

## IDEA配置

在IntelliJ IDEA中：
1. File -> Settings -> Build, Execution, Deployment -> Build Tools -> Maven
2. User settings file: 指向你的settings.xml文件
3. Local repository: 查看本地仓库路径
4. 勾选"Always update snapshots"（如果需要）
