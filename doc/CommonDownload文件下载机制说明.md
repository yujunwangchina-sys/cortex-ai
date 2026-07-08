# CommonController /common/download 文件下载机制说明

## 核心逻辑分析

### 1. 下载方法签名
```java
@GetMapping("/download")
public void fileDownload(String fileName, Boolean delete, HttpServletResponse response, HttpServletRequest request)
```

**参数说明：**
- `fileName`: 必填，文件名称（带特殊格式）
- `delete`: 可选，下载后是否删除文件
- `response`: HTTP响应对象
- `request`: HTTP请求对象

### 2. 文件名格式要求

**关键代码：**
```java
String realFileName = System.currentTimeMillis() + fileName.substring(fileName.indexOf("_") + 1);
String filePath = CortexConfig.getDownloadPath() + fileName;
```

**文件名必须包含下划线 `_`**，格式为：`{前缀}_{真实文件名}`

例如：
- ✅ 正确：`a1b2c3d4_用户列表.xlsx`
- ✅ 正确：`uuid32位字符_知识库列表.md`
- ❌ 错误：`用户列表.xlsx`（没有下划线）

### 3. 文件存储路径

**配置路径：**
```java
// CortexConfig.java
public static String getDownloadPath() {
    return getProfile() + "/download/";
}
```

**默认路径（application.yml配置）：**
```yaml
cortex:
  profile: D:/cortex/uploadPath
```

**实际下载路径：** `D:/cortex/uploadPath/download/`

### 4. 下载文件名生成规则

```java
String realFileName = System.currentTimeMillis() + fileName.substring(fileName.indexOf("_") + 1);
```

**解析逻辑：**
1. 从 `fileName` 中找到第一个 `_` 的位置
2. 取 `_` 之后的部分作为原始文件名
3. 在前面加上时间戳

**示例：**
- 输入：`uuid_知识库列表.md`
- 输出：`1704326400000知识库列表.md`

## Agent文件插件应该如何存储

### 方案对比

#### ❌ 方案1：使用 /common/download（不推荐）

**需要满足的条件：**
1. 文件必须存储在：`D:/cortex/uploadPath/download/` 目录
2. 数据库存储格式：`{uuid}_{原始文件名}`
3. 下载URL：`/common/download?fileName={uuid}_{原始文件名}`

**示例：**
```sql
-- file_name: 知识库列表.md
-- file_path: D:/cortex/uploadPath/download/a1b2c3d4e5f6_{原始文件名}
INSERT INTO ai_agent_file (file_name, file_path) 
VALUES ('知识库列表.md', 'D:/cortex/uploadPath/download/uuid32位_知识库列表.md');
```

**缺点：**
- 所有文件混在一个目录
- 无法按会话、用户分类管理
- 文件路径和文件名都包含uuid，冗余

#### ✅ 方案2：使用专用下载接口（推荐，已实现）

**优势：**
1. 文件可以按业务逻辑分类存储
2. 数据库存储灵活
3. 下载时从数据库读取原始文件名

**当前实现：**
```java
// AgentFileController.java
@GetMapping("/download/{fileId}")
public void download(@PathVariable Long fileId, HttpServletResponse response)
```

**存储方案：**
```sql
-- file_name: 原始文件名（知识库列表.md）
-- file_path: 实际存储路径（包含UUID文件名）
INSERT INTO ai_agent_file (file_name, file_path) 
VALUES (
    '知识库列表.md',  -- 原始文件名
    'D:/cortex/uploadPath/agent-workspace/cortex/admin/session123/uuid32位.md'  -- 实际路径
);
```

**下载URL：**
```
/agent/api/file/download/1
```

## 推荐实现方案

### 当前FileOperationPlugin的实现（推荐）

```java
// 1. 生成UUID文件名存储
String uuidFileName = generateUuidFileName(originalFileName);
Path filePath = sessionDir.resolve(uuidFileName);

// 2. 数据库记录
AiAgentFile fileRecord = new AiAgentFile();
fileRecord.setFileName(originalFileName);  // 原始文件名
fileRecord.setFilePath(filePath.toString());  // UUID路径

// 3. 返回专用下载链接
String downloadUrl = "/agent/api/file/download/" + fileRecord.getFileId();
```

### 下载时的处理

```java
@GetMapping("/download/{fileId}")
public void download(@PathVariable Long fileId, HttpServletResponse response) {
    // 1. 从数据库读取文件记录
    AiAgentFile fileRecord = aiAgentFileService.selectAiAgentFileByFileId(fileId);
    
    // 2. 获取实际文件路径（UUID文件名）
    Path filePath = Paths.get(fileRecord.getFilePath());
    
    // 3. 使用原始文件名作为下载文件名
    String downloadFileName = fileRecord.getFileName();  // 原始文件名
    
    // 4. 设置响应头
    response.setHeader("Content-Disposition", 
        "attachment; filename=\"" + new String(downloadFileName.getBytes("UTF-8"), "ISO-8859-1") + "\"");
    
    // 5. 写入文件流
    Files.copy(filePath, response.getOutputStream());
}
```

## 总结

### 如果要使用 /common/download

**数据库存储格式：**
```java
// file_name字段必须是：{uuid}_{原始文件名}
String dbFileName = UUID.randomUUID().toString().replace("-", "") + "_" + originalFileName;

// file_path必须是download目录
String dbFilePath = CortexConfig.getDownloadPath() + dbFileName;

// 示例
file_name: "a1b2c3d4e5f6_知识库列表.md"
file_path: "D:/cortex/uploadPath/download/a1b2c3d4e5f6_知识库列表.md"
```

**文件必须存储在：**
```
D:/cortex/uploadPath/download/a1b2c3d4e5f6_知识库列表.md
```

**下载链接：**
```
/common/download?fileName=a1b2c3d4e5f6_知识库列表.md
```

### 推荐使用专用接口（当前方案）

**优势：**
- ✅ 灵活的文件存储路径
- ✅ 清晰的业务逻辑分离
- ✅ 更好的权限控制
- ✅ 数据库字段语义清晰

**实现：**
已在 `AgentFileController.java` 中实现完成，无需修改。
