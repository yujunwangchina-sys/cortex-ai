package com.ruoyi.plugin.builtin.impl;

import com.ruoyi.plugin.builtin.IBuiltinPlugin;
import com.ruoyi.plugin.builtin.PluginInfo;
import com.ruoyi.plugin.builtin.ToolDefinition;
import com.ruoyi.plugin.builtin.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.*;
import java.util.Date;

/**
 * 数据库查询内置插件 (MySQL)
 * 只读查询: 仅允许 SELECT/SHOW/DESCRIBE/EXPLAIN
 * 安全限制: 自动 LIMIT、查询超时、最大行数、危险关键词拦截
 *
 * 配置方式: 在插件的 env_vars 中配置 JSON:
 * {"dbHost":"localhost","dbPort":"3306","dbName":"mydb","dbUser":"root","dbPass":"123456"}
 *
 * 工具:
 * 1. db_tables  - 列出所有表
 * 2. db_schema  - 查看表结构
 * 3. db_query   - 执行只读查询
 *
 * @author ruoyi
 */
@Component
public class DatabaseQueryPlugin implements IBuiltinPlugin
{
    private static final Logger log = LoggerFactory.getLogger(DatabaseQueryPlugin.class);

    /** 默认最大返回行数 */
    private static final int DEFAULT_LIMIT = 500;

    /** 最大允许行数 */
    private static final int MAX_LIMIT = 5000;

    /** 查询超时(秒) */
    private static final int QUERY_TIMEOUT_SEC = 30;

    /** 危险关键词(SQL注入/破坏性操作) */
    private static final Set<String> DANGEROUS_KEYWORDS = Set.of(
            "insert", "update", "delete", "drop", "alter", "truncate", "create",
            "grant", "revoke", "replace", "merge", "call", "exec", "execute",
            "lock", "unlock", "flush", "reset", "shutdown", "kill",
            "load_file", "outfile", "dumpfile", "into"
    );

    /** 允许的SQL前缀 */
    private static final Set<String> ALLOWED_PREFIXES = Set.of(
            "select", "show", "describe", "desc", "explain"
    );

    /** 数据库连接配置 */
    private volatile Map<String, String> dbConfig = null;

    @Override
    public PluginInfo getPluginInfo()
    {
        PluginInfo info = new PluginInfo("数据库查询", "database-query", "MySQL只读查询工具(db_tables/db_schema/db_query)");
        info.setVersion("1.0.0");
        info.setAuthor("CORTEX");
        info.setCategory("database");
        info.setEmoji("🗄️");
        info.setRequireApproval(false);
        return info;
    }

    /**
     * 接收 env_vars 配置 (由 ToolDispatcher 通过反射调用)
     */
    public void setEnvVars(Map<String, String> envVars)
    {
        if (envVars != null)
        {
            this.dbConfig = new HashMap<>(envVars);
            log.info("数据库查询插件配置已更新: host={}, port={}, db={}",
                    envVars.get("dbHost"), envVars.get("dbPort"), envVars.get("dbName"));
        }
    }

    @Override
    public List<ToolDefinition> getTools()
    {
        List<ToolDefinition> tools = new ArrayList<>();

        // 1. db_tables
        ToolDefinition tables = new ToolDefinition();
        tables.setName("db_tables");
        tables.setDescription("列出当前数据库中的所有表名。无需参数。先调用此工具了解数据库结构。");
        Map<String, Object> tablesSchema = new HashMap<>();
        tablesSchema.put("type", "object");
        tablesSchema.put("properties", new HashMap<>());
        tables.setInputSchema(tablesSchema);
        tools.add(tables);

        // 2. db_schema
        ToolDefinition schema = new ToolDefinition();
        schema.setName("db_schema");
        schema.setDescription("查看指定表的列结构(列名、类型、是否可空、主键等)。参数: tableName。");
        Map<String, Object> schemaSchema = new HashMap<>();
        schemaSchema.put("type", "object");
        Map<String, Object> schemaProps = new HashMap<>();
        schemaProps.put("tableName", Map.of("type", "string", "description", "表名"));
        schemaSchema.put("properties", schemaProps);
        schemaSchema.put("required", List.of("tableName"));
        schema.setInputSchema(schemaSchema);
        tools.add(schema);

        // 3. db_query
        ToolDefinition query = new ToolDefinition();
        query.setName("db_query");
        query.setDescription(
            "执行只读SQL查询(仅SELECT/SHOW/DESCRIBE/EXPLAIN)。\n" +
            "安全限制:\n" +
            "- 自动添加LIMIT (默认" + DEFAULT_LIMIT + "行, 最大" + MAX_LIMIT + "行)\n" +
            "- 查询超时" + QUERY_TIMEOUT_SEC + "秒\n" +
            "- 禁止INSERT/UPDATE/DELETE/DROP等写操作\n" +
            "- 禁止LOAD_FILE/OUTFILE等危险函数\n\n" +
            "参数: sql(SQL语句), limit(可选,最大返回行数,默认" + DEFAULT_LIMIT + ")"
        );
        Map<String, Object> querySchema = new HashMap<>();
        querySchema.put("type", "object");
        Map<String, Object> queryProps = new HashMap<>();
        queryProps.put("sql", Map.of("type", "string", "description", "SELECT查询语句"));
        queryProps.put("limit", Map.of("type", "integer", "description", "最大返回行数(可选,默认" + DEFAULT_LIMIT + ",上限" + MAX_LIMIT + ")", "default", DEFAULT_LIMIT));
        querySchema.put("properties", queryProps);
        querySchema.put("required", List.of("sql"));
        query.setInputSchema(querySchema);
        tools.add(query);

        return tools;
    }

    @Override
    public String executeTool(String toolName, Map<String, Object> arguments)
    {
        try
        {
            // Ensure config is loaded
            if (dbConfig == null || dbConfig.isEmpty())
            {
                return ToolResult.error("数据库未配置。请在插件管理中设置 env_vars: {dbHost, dbPort, dbName, dbUser, dbPass}").toJson();
            }

            switch (toolName)
            {
                case "db_tables": return listTables();
                case "db_schema": return describeTable(arguments);
                case "db_query": return executeQuery(arguments);
                default:
                    return ToolResult.error("未知工具: " + toolName).toJson();
            }
        }
        catch (Exception e)
        {
            log.error("数据库工具执行失败 [tool={}]", toolName, e);
            return ToolResult.error("执行失败: " + e.getMessage()).toJson();
        }
    }

    // ==================== db_tables ====================

    private String listTables() throws Exception
    {
        String sql = "SHOW TABLES";
        // SHOW TABLES is safe, no need for LIMIT
        return executeSqlRaw(sql, 1000);
    }

    // ==================== db_schema ====================

    private String describeTable(Map<String, Object> args) throws Exception
    {
        String tableName = (String) args.get("tableName");
        if (tableName == null || tableName.isBlank())
        {
            return ToolResult.error("缺少参数: tableName").toJson();
        }

        // Validate table name (alphanumeric + underscore only)
        if (!tableName.matches("[a-zA-Z_][a-zA-Z0-9_]*"))
        {
            return ToolResult.error("非法表名: " + tableName).toJson();
        }

        String sql = "DESCRIBE `" + tableName + "`";
        return executeSqlRaw(sql, 500);
    }

    // ==================== db_query ====================

    private String executeQuery(Map<String, Object> args) throws Exception
    {
        String sql = (String) args.get("sql");
        if (sql == null || sql.isBlank())
        {
            return ToolResult.error("缺少参数: sql").toJson();
        }

        // Parse limit
        int limit = DEFAULT_LIMIT;
        Object limitObj = args.get("limit");
        if (limitObj != null)
        {
            try
            {
                limit = Integer.parseInt(limitObj.toString());
                if (limit < 1) limit = DEFAULT_LIMIT;
                if (limit > MAX_LIMIT) limit = MAX_LIMIT;
            }
            catch (NumberFormatException e)
            {
                limit = DEFAULT_LIMIT;
            }
        }

        // Validate SQL
        String validationError = validateSql(sql);
        if (validationError != null)
        {
            return ToolResult.error("SQL安全检查未通过: " + validationError).toJson();
        }

        // Add LIMIT if not present
        sql = ensureLimit(sql, limit);

        log.info("DB查询: {}", sql);

        return executeSqlRaw(sql, limit);
    }

    // ==================== SQL Security ====================

    /**
     * Validate SQL for safety
     * @return null if safe, error message if not
     */
    private String validateSql(String sql)
    {
        // Normalize: remove comments, trim, lowercase for checking
        String normalized = sql
                .replaceAll("--.*$", "")       // line comments
                .replaceAll("/\\*.*?\\*/", "")  // block comments
                .replaceAll("#.*$", "")         // MySQL comments
                .trim();

        if (normalized.isEmpty())
        {
            return "SQL为空";
        }

        String lower = normalized.toLowerCase();

        // Check prefix - must start with allowed keyword
        String firstWord = lower.split("\\s+")[0];
        if (!ALLOWED_PREFIXES.contains(firstWord))
        {
            return "仅允许 SELECT/SHOW/DESCRIBE/EXPLAIN, 检测到: " + firstWord.toUpperCase();
        }

        // Check for dangerous keywords (as whole words, not substrings)
        // Split by non-alphanumeric to get words
        String[] words = lower.split("[^a-zA-Z0-9_]+");
        for (String word : words)
        {
            if (DANGEROUS_KEYWORDS.contains(word))
            {
                return "检测到危险关键词: " + word.toUpperCase();
            }
        }

        // Check for multiple statements (semicolons)
        // Allow trailing semicolon but not multiple statements
        String withoutTrailingSemicolon = normalized.replaceAll(";\\s*$", "");
        if (withoutTrailingSemicolon.contains(";"))
        {
            return "不允许执行多条SQL语句";
        }

        // Check for subquery tricks (INTO in subqueries)
        if (lower.contains("into outfile") || lower.contains("into dumpfile"))
        {
            return "检测到文件导出操作";
        }

        return null; // safe
    }

    /**
     * Ensure the SQL has a LIMIT clause, add one if missing
     */
    private String ensureLimit(String sql, int limit)
    {
        String lower = sql.toLowerCase().trim();

        // SHOW TABLES and DESCRIBE don't need LIMIT
        if (lower.startsWith("show") || lower.startsWith("describe") || lower.startsWith("desc"))
        {
            return sql;
        }

        // Check if LIMIT already exists
        if (lower.matches(".*\\blimit\\s+\\d+.*"))
        {
            // LIMIT exists, but enforce max
            // Extract the limit value and cap it
            return sql.replaceAll("(?i)limit\\s+\\d+", "LIMIT " + limit);
        }

        // Remove trailing semicolon, add LIMIT, restore semicolon
        String result = sql.trim();
        boolean hasSemicolon = result.endsWith(";");
        if (hasSemicolon) result = result.substring(0, result.length() - 1).trim();

        result += " LIMIT " + limit;
        if (hasSemicolon) result += ";";

        return result;
    }

    // ==================== Execution ====================

    /**
     * Execute SQL and return results as JSON
     */
    private String executeSqlRaw(String sql, int maxRows) throws Exception
    {
        long startTime = System.currentTimeMillis();

        try (Connection conn = createConnection())
        {
            conn.setReadOnly(true);

            try (Statement stmt = conn.createStatement())
            {
                stmt.setQueryTimeout(QUERY_TIMEOUT_SEC);
                stmt.setMaxRows(maxRows);

                boolean hasResultSet = stmt.execute(sql);

                if (hasResultSet)
                {
                    ResultSet rs = stmt.getResultSet();
                    String result = formatResultSet(rs, maxRows);
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("DB查询完成 [duration={}ms]", duration);
                    return result;
                }
                else
                {
                    // For SHOW/DESCRIBE that might not return update count
                    return ToolResult.success("查询执行成功(无结果集)").toJson();
                }
            }
        }
    }

    /**
     * Format ResultSet as JSON tool result
     */
    private String formatResultSet(ResultSet rs, int maxRows) throws SQLException
    {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();

        // Column metadata
        List<Map<String, Object>> columns = new ArrayList<>();
        for (int i = 1; i <= columnCount; i++)
        {
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("name", meta.getColumnLabel(i));
            col.put("type", meta.getColumnTypeName(i));
            columns.add(col);
        }

        // Rows
        List<Map<String, Object>> rows = new ArrayList<>();
        int rowCount = 0;
        while (rs.next() && rowCount < maxRows)
        {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++)
            {
                Object value = rs.getObject(i);
                if (value == null)
                {
                    row.put(meta.getColumnLabel(i), null);
                }
                else if (value instanceof byte[])
                {
                    row.put(meta.getColumnLabel(i), "[BLOB]");
                }
                else if (value instanceof Date)
                {
                    row.put(meta.getColumnLabel(i), value.toString());
                }
                else
                {
                    row.put(meta.getColumnLabel(i), value);
                }
            }
            rows.add(row);
            rowCount++;
        }

        // Check if there are more rows
        boolean hasMore = rs.next();

        ToolResult result = ToolResult.success("查询成功, 返回 " + rowCount + " 行" + (hasMore ? " (还有更多数据, 请缩小查询范围或添加LIMIT)" : ""));
        result.addData("columns", columns);
        result.addData("rows", rows);
        result.addData("rowCount", rowCount);
        result.addData("truncated", hasMore);
        return result.toJson();
    }

    // ==================== Connection ====================

    private Connection createConnection() throws Exception
    {
        String host = dbConfig.getOrDefault("dbHost", "localhost");
        String port = dbConfig.getOrDefault("dbPort", "3306");
        String dbName = dbConfig.get("dbName");
        String user = dbConfig.get("dbUser");
        String pass = dbConfig.getOrDefault("dbPass", "");

        if (dbName == null || user == null)
        {
            throw new RuntimeException("数据库配置不完整, 需要 dbHost/dbPort/dbName/dbUser/dbPass");
        }

        String url = String.format(
                "jdbc:mysql://%s:%s/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=GMT%%2B8&allowPublicKeyRetrieval=true&useSSL=false&connectTimeout=5000&socketTimeout=%d000",
                host, port, dbName, QUERY_TIMEOUT_SEC
        );

        return DriverManager.getConnection(url, user, pass);
    }
}