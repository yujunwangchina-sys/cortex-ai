package com.ruoyi.plugin.builtin.impl;

import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.exception.job.TaskException;
import com.ruoyi.plugin.builtin.IBuiltinPlugin;
import com.ruoyi.plugin.builtin.PluginInfo;
import com.ruoyi.plugin.builtin.ToolDefinition;
import com.ruoyi.plugin.builtin.ToolResult;
import com.ruoyi.quartz.domain.SysJob;
import com.ruoyi.quartz.service.ISysJobService;
import com.ruoyi.quartz.util.CronUtils;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 定时任务管理插件
 * 
 * 功能：
 * 1. task_list - 查询定时任务列表
 * 2. task_create - 创建定时任务
 * 3. task_update - 修改定时任务
 * 4. task_delete - 删除定时任务
 * 5. task_run - 立即执行定时任务（提前执行）
 * 6. task_status - 修改任务状态（启用/暂停）
 * 7. task_detail - 查询任务详情
 * 
 * @author ruoyi
 */
@Component
public class ScheduledTaskPlugin implements IBuiltinPlugin
{
    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskPlugin.class);

    @Autowired
    private ISysJobService jobService;

    @Override
    public PluginInfo getPluginInfo()
    {
        PluginInfo info = new PluginInfo("定时任务管理", "scheduled-task", "创建和管理定时任务，支持Cron表达式");
        info.setVersion("1.0.0");
        info.setAuthor("CORTEX");
        info.setCategory("system");
        info.setEmoji("⏰");
        info.setRequireApproval(true); // 定时任务需要审批
        return info;
    }

    @Override
    public List<ToolDefinition> getTools()
    {
        List<ToolDefinition> tools = new ArrayList<>();

        // 1. task_list - 查询任务列表
        ToolDefinition listTool = new ToolDefinition();
        listTool.setName("task_list");
        listTool.setDescription("查询定时任务列表。可选参数：jobName(任务名称), status(0正常/1暂停)");
        Map<String, Object> listSchema = new HashMap<>();
        listSchema.put("type", "object");
        Map<String, Object> listProps = new HashMap<>();
        listProps.put("jobName", Map.of("type", "string", "description", "任务名称（模糊搜索）"));
        listProps.put("status", Map.of("type", "string", "description", "任务状态：0=正常，1=暂停"));
        listSchema.put("properties", listProps);
        listTool.setInputSchema(listSchema);
        tools.add(listTool);

        // 2. task_create - 创建任务
        ToolDefinition createTool = new ToolDefinition();
        createTool.setName("task_create");
        createTool.setDescription(
            "创建定时任务。\n" +
            "必填参数：\n" +
            "- jobName: 任务名称（如：每日报表生成）\n" +
            "- jobGroup: 任务组名（如：DEFAULT、REPORT、BACKUP）\n" +
            "- invokeTarget: 调用目标字符串（如：ryTask.ryNoParams）\n" +
            "- cronExpression: Cron表达式（如：0 0 2 * * ? 每天凌晨2点）\n\n" +
            "可选参数：\n" +
            "- misfirePolicy: 错过策略（0=默认,1=立即执行,2=执行一次,3=放弃执行）\n" +
            "- concurrent: 是否并发（0=允许,1=禁止）\n" +
            "- remark: 备注说明\n\n" +
            "常用Cron示例：\n" +
            "- 每分钟: 0 * * * * ?\n" +
            "- 每小时: 0 0 * * * ?\n" +
            "- 每天凌晨2点: 0 0 2 * * ?\n" +
            "- 每周一9点: 0 0 9 ? * MON\n" +
            "- 每月1号: 0 0 0 1 * ?\n\n" +
            "注意：invokeTarget必须是白名单中的类和方法"
        );
        Map<String, Object> createSchema = new HashMap<>();
        createSchema.put("type", "object");
        Map<String, Object> createProps = new HashMap<>();
        createProps.put("jobName", Map.of("type", "string", "description", "任务名称"));
        createProps.put("jobGroup", Map.of("type", "string", "description", "任务组名"));
        createProps.put("invokeTarget", Map.of("type", "string", "description", "调用目标字符串（如：ryTask.ryNoParams）"));
        createProps.put("cronExpression", Map.of("type", "string", "description", "Cron表达式"));
        createProps.put("misfirePolicy", Map.of("type", "string", "description", "错过执行策略：0=默认,1=立即执行,2=执行一次,3=放弃执行", "default", "0"));
        createProps.put("concurrent", Map.of("type", "string", "description", "是否并发：0=允许,1=禁止", "default", "1"));
        createProps.put("remark", Map.of("type", "string", "description", "备注说明"));
        createSchema.put("properties", createProps);
        createSchema.put("required", List.of("jobName", "jobGroup", "invokeTarget", "cronExpression"));
        createTool.setInputSchema(createSchema);
        tools.add(createTool);

        // 3. task_update - 修改任务
        ToolDefinition updateTool = new ToolDefinition();
        updateTool.setName("task_update");
        updateTool.setDescription("修改定时任务。必须提供jobId，其他参数与task_create相同。");
        Map<String, Object> updateSchema = new HashMap<>();
        updateSchema.put("type", "object");
        Map<String, Object> updateProps = new HashMap<>();
        updateProps.put("jobId", Map.of("type", "integer", "description", "任务ID"));
        updateProps.put("jobName", Map.of("type", "string", "description", "任务名称"));
        updateProps.put("jobGroup", Map.of("type", "string", "description", "任务组名"));
        updateProps.put("invokeTarget", Map.of("type", "string", "description", "调用目标字符串"));
        updateProps.put("cronExpression", Map.of("type", "string", "description", "Cron表达式"));
        updateProps.put("misfirePolicy", Map.of("type", "string", "description", "错过执行策略"));
        updateProps.put("concurrent", Map.of("type", "string", "description", "是否并发"));
        updateProps.put("remark", Map.of("type", "string", "description", "备注说明"));
        updateSchema.put("properties", updateProps);
        updateSchema.put("required", List.of("jobId"));
        updateTool.setInputSchema(updateSchema);
        tools.add(updateTool);

        // 4. task_delete - 删除任务
        ToolDefinition deleteTool = new ToolDefinition();
        deleteTool.setName("task_delete");
        deleteTool.setDescription("删除定时任务。参数：jobIds（任务ID数组，如：[1,2,3]）");
        Map<String, Object> deleteSchema = new HashMap<>();
        deleteSchema.put("type", "object");
        Map<String, Object> deleteProps = new HashMap<>();
        deleteProps.put("jobIds", Map.of(
            "type", "array",
            "description", "要删除的任务ID数组",
            "items", Map.of("type", "integer")
        ));
        deleteSchema.put("properties", deleteProps);
        deleteSchema.put("required", List.of("jobIds"));
        deleteTool.setInputSchema(deleteSchema);
        tools.add(deleteTool);

        // 5. task_run - 立即执行
        ToolDefinition runTool = new ToolDefinition();
        runTool.setName("task_run");
        runTool.setDescription("立即执行定时任务（提前执行，不影响原定时计划）。参数：jobId（任务ID）");
        Map<String, Object> runSchema = new HashMap<>();
        runSchema.put("type", "object");
        Map<String, Object> runProps = new HashMap<>();
        runProps.put("jobId", Map.of("type", "integer", "description", "任务ID"));
        runSchema.put("properties", runProps);
        runSchema.put("required", List.of("jobId"));
        runTool.setInputSchema(runSchema);
        tools.add(runTool);

        // 6. task_status - 修改状态
        ToolDefinition statusTool = new ToolDefinition();
        statusTool.setName("task_status");
        statusTool.setDescription("修改任务状态（启用/暂停）。参数：jobId（任务ID），status（0=启用，1=暂停）");
        Map<String, Object> statusSchema = new HashMap<>();
        statusSchema.put("type", "object");
        Map<String, Object> statusProps = new HashMap<>();
        statusProps.put("jobId", Map.of("type", "integer", "description", "任务ID"));
        statusProps.put("status", Map.of("type", "string", "description", "任务状态：0=启用，1=暂停"));
        statusSchema.put("properties", statusProps);
        statusSchema.put("required", List.of("jobId", "status"));
        statusTool.setInputSchema(statusSchema);
        tools.add(statusTool);

        // 7. task_detail - 查询详情
        ToolDefinition detailTool = new ToolDefinition();
        detailTool.setName("task_detail");
        detailTool.setDescription("查询任务详细信息。参数：jobId（任务ID）");
        Map<String, Object> detailSchema = new HashMap<>();
        detailSchema.put("type", "object");
        Map<String, Object> detailProps = new HashMap<>();
        detailProps.put("jobId", Map.of("type", "integer", "description", "任务ID"));
        detailSchema.put("properties", detailProps);
        detailSchema.put("required", List.of("jobId"));
        detailTool.setInputSchema(detailSchema);
        tools.add(detailTool);

        return tools;
    }

    @Override
    public String executeTool(String toolName, Map<String, Object> arguments)
    {
        try
        {
            switch (toolName)
            {
                case "task_list": return listTasks(arguments);
                case "task_create": return createTask(arguments);
                case "task_update": return updateTask(arguments);
                case "task_delete": return deleteTask(arguments);
                case "task_run": return runTask(arguments);
                case "task_status": return changeStatus(arguments);
                case "task_detail": return getTaskDetail(arguments);
                default:
                    return ToolResult.error("未知工具: " + toolName).toJson();
            }
        }
        catch (Exception e)
        {
            log.error("定时任务工具执行失败 [tool={}]", toolName, e);
            return ToolResult.error("执行失败: " + e.getMessage()).toJson();
        }
    }

    // ==================== task_list ====================

    private String listTasks(Map<String, Object> args)
    {
        SysJob queryParam = new SysJob();
        
        if (args.containsKey("jobName"))
        {
            queryParam.setJobName((String) args.get("jobName"));
        }
        if (args.containsKey("status"))
        {
            queryParam.setStatus((String) args.get("status"));
        }

        List<SysJob> list = jobService.selectJobList(queryParam);

        ToolResult result = ToolResult.success("查询成功，共 " + list.size() + " 个任务");
        
        // 格式化任务列表
        List<Map<String, Object>> tasks = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        
        for (SysJob job : list)
        {
            Map<String, Object> taskInfo = new LinkedHashMap<>();
            taskInfo.put("jobId", job.getJobId());
            taskInfo.put("jobName", job.getJobName());
            taskInfo.put("jobGroup", job.getJobGroup());
            taskInfo.put("invokeTarget", job.getInvokeTarget());
            taskInfo.put("cronExpression", job.getCronExpression());
            taskInfo.put("status", job.getStatus().equals("0") ? "正常" : "暂停");
            taskInfo.put("concurrent", job.getConcurrent().equals("0") ? "允许" : "禁止");
            
            // 计算下次执行时间
            Date nextTime = job.getNextValidTime();
            taskInfo.put("nextExecution", nextTime != null ? sdf.format(nextTime) : "无");
            
            taskInfo.put("remark", job.getRemark());
            taskInfo.put("createTime", job.getCreateTime() != null ? sdf.format(job.getCreateTime()) : "");
            
            tasks.add(taskInfo);
        }
        
        result.addData("tasks", tasks);
        result.addData("total", list.size());
        
        return result.toJson();
    }

    // ==================== task_create ====================

    private String createTask(Map<String, Object> args) throws SchedulerException, TaskException
    {
        String jobName = (String) args.get("jobName");
        String jobGroup = (String) args.get("jobGroup");
        String invokeTarget = (String) args.get("invokeTarget");
        String cronExpression = (String) args.get("cronExpression");

        if (jobName == null || jobName.isBlank())
        {
            return ToolResult.error("任务名称不能为空").toJson();
        }
        if (jobGroup == null || jobGroup.isBlank())
        {
            return ToolResult.error("任务组名不能为空").toJson();
        }
        if (invokeTarget == null || invokeTarget.isBlank())
        {
            return ToolResult.error("调用目标不能为空").toJson();
        }
        if (cronExpression == null || cronExpression.isBlank())
        {
            return ToolResult.error("Cron表达式不能为空").toJson();
        }

        // 验证Cron表达式
        if (!CronUtils.isValid(cronExpression))
        {
            return ToolResult.error("Cron表达式格式不正确: " + cronExpression).toJson();
        }

        SysJob job = new SysJob();
        job.setJobName(jobName);
        job.setJobGroup(jobGroup);
        job.setInvokeTarget(invokeTarget);
        job.setCronExpression(cronExpression);
        
        // 可选参数
        job.setMisfirePolicy(args.getOrDefault("misfirePolicy", "0").toString());
        job.setConcurrent(args.getOrDefault("concurrent", "1").toString());
        job.setStatus("0"); // 默认启用
        
        if (args.containsKey("remark"))
        {
            job.setRemark((String) args.get("remark"));
        }

        int result = jobService.insertJob(job);

        if (result > 0)
        {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date nextTime = CronUtils.getNextExecution(cronExpression);
            
            ToolResult toolResult = ToolResult.success("定时任务创建成功");
            toolResult.addData("jobId", job.getJobId());
            toolResult.addData("jobName", jobName);
            toolResult.addData("nextExecution", nextTime != null ? sdf.format(nextTime) : "无");
            return toolResult.toJson();
        }

        return ToolResult.error("创建任务失败").toJson();
    }

    // ==================== task_update ====================

    private String updateTask(Map<String, Object> args) throws SchedulerException, TaskException
    {
        Object jobIdObj = args.get("jobId");
        if (jobIdObj == null)
        {
            return ToolResult.error("缺少参数: jobId").toJson();
        }

        Long jobId = Long.parseLong(jobIdObj.toString());
        SysJob job = jobService.selectJobById(jobId);
        
        if (job == null)
        {
            return ToolResult.error("任务不存在: " + jobId).toJson();
        }

        // 更新字段
        if (args.containsKey("jobName"))
        {
            job.setJobName((String) args.get("jobName"));
        }
        if (args.containsKey("jobGroup"))
        {
            job.setJobGroup((String) args.get("jobGroup"));
        }
        if (args.containsKey("invokeTarget"))
        {
            job.setInvokeTarget((String) args.get("invokeTarget"));
        }
        if (args.containsKey("cronExpression"))
        {
            String cronExpression = (String) args.get("cronExpression");
            if (!CronUtils.isValid(cronExpression))
            {
                return ToolResult.error("Cron表达式格式不正确: " + cronExpression).toJson();
            }
            job.setCronExpression(cronExpression);
        }
        if (args.containsKey("misfirePolicy"))
        {
            job.setMisfirePolicy((String) args.get("misfirePolicy"));
        }
        if (args.containsKey("concurrent"))
        {
            job.setConcurrent((String) args.get("concurrent"));
        }
        if (args.containsKey("remark"))
        {
            job.setRemark((String) args.get("remark"));
        }

        int result = jobService.updateJob(job);

        if (result > 0)
        {
            return ToolResult.success("任务更新成功: " + job.getJobName()).toJson();
        }

        return ToolResult.error("更新任务失败").toJson();
    }

    // ==================== task_delete ====================

    private String deleteTask(Map<String, Object> args) throws SchedulerException
    {
        Object jobIdsObj = args.get("jobIds");
        if (jobIdsObj == null)
        {
            return ToolResult.error("缺少参数: jobIds").toJson();
        }

        List<?> jobIdsList = (List<?>) jobIdsObj;
        if (jobIdsList.isEmpty())
        {
            return ToolResult.error("jobIds不能为空").toJson();
        }

        Long[] jobIds = new Long[jobIdsList.size()];
        for (int i = 0; i < jobIdsList.size(); i++)
        {
            jobIds[i] = Long.parseLong(jobIdsList.get(i).toString());
        }

        jobService.deleteJobByIds(jobIds);

        return ToolResult.success("成功删除 " + jobIds.length + " 个任务").toJson();
    }

    // ==================== task_run ====================

    private String runTask(Map<String, Object> args) throws SchedulerException
    {
        Object jobIdObj = args.get("jobId");
        if (jobIdObj == null)
        {
            return ToolResult.error("缺少参数: jobId").toJson();
        }

        Long jobId = Long.parseLong(jobIdObj.toString());
        SysJob job = jobService.selectJobById(jobId);
        
        if (job == null)
        {
            return ToolResult.error("任务不存在: " + jobId).toJson();
        }

        boolean result = jobService.run(job);

        if (result)
        {
            return ToolResult.success("任务已触发执行: " + job.getJobName()).toJson();
        }

        return ToolResult.error("任务执行失败（任务可能不存在或已过期）").toJson();
    }

    // ==================== task_status ====================

    private String changeStatus(Map<String, Object> args) throws SchedulerException
    {
        Object jobIdObj = args.get("jobId");
        Object statusObj = args.get("status");
        
        if (jobIdObj == null || statusObj == null)
        {
            return ToolResult.error("缺少参数: jobId 或 status").toJson();
        }

        Long jobId = Long.parseLong(jobIdObj.toString());
        String status = statusObj.toString();

        if (!status.equals("0") && !status.equals("1"))
        {
            return ToolResult.error("status参数必须是 0（启用）或 1（暂停）").toJson();
        }

        SysJob job = jobService.selectJobById(jobId);
        if (job == null)
        {
            return ToolResult.error("任务不存在: " + jobId).toJson();
        }

        job.setStatus(status);
        int result = jobService.changeStatus(job);

        if (result > 0)
        {
            String statusText = status.equals("0") ? "启用" : "暂停";
            return ToolResult.success("任务状态已更改为：" + statusText + " - " + job.getJobName()).toJson();
        }

        return ToolResult.error("状态更改失败").toJson();
    }

    // ==================== task_detail ====================

    private String getTaskDetail(Map<String, Object> args)
    {
        Object jobIdObj = args.get("jobId");
        if (jobIdObj == null)
        {
            return ToolResult.error("缺少参数: jobId").toJson();
        }

        Long jobId = Long.parseLong(jobIdObj.toString());
        SysJob job = jobService.selectJobById(jobId);
        
        if (job == null)
        {
            return ToolResult.error("任务不存在: " + jobId).toJson();
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("jobId", job.getJobId());
        detail.put("jobName", job.getJobName());
        detail.put("jobGroup", job.getJobGroup());
        detail.put("invokeTarget", job.getInvokeTarget());
        detail.put("cronExpression", job.getCronExpression());
        detail.put("misfirePolicy", getMisfirePolicyText(job.getMisfirePolicy()));
        detail.put("concurrent", job.getConcurrent().equals("0") ? "允许并发" : "禁止并发");
        detail.put("status", job.getStatus().equals("0") ? "正常运行" : "已暂停");
        detail.put("remark", job.getRemark());
        detail.put("createBy", job.getCreateBy());
        detail.put("createTime", job.getCreateTime() != null ? sdf.format(job.getCreateTime()) : "");
        detail.put("updateBy", job.getUpdateBy());
        detail.put("updateTime", job.getUpdateTime() != null ? sdf.format(job.getUpdateTime()) : "");
        
        // 计算下次执行时间
        Date nextTime = job.getNextValidTime();
        detail.put("nextExecution", nextTime != null ? sdf.format(nextTime) : "无");

        ToolResult result = ToolResult.success("查询成功");
        result.addData("task", detail);
        
        return result.toJson();
    }

    // ==================== Helper Methods ====================

    private String getMisfirePolicyText(String policy)
    {
        switch (policy)
        {
            case "1": return "立即触发执行";
            case "2": return "触发一次执行";
            case "3": return "不触发立即执行";
            default: return "默认策略";
        }
    }
}
