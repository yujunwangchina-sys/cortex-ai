package com.cortex.web.controller.agent;

import com.cortex.agent.domain.AiAgent;
import com.cortex.agent.runtime.ApiRateLimiter;
import com.cortex.agent.service.IAiAgentService;
import com.cortex.common.core.controller.BaseController;
import com.cortex.common.core.domain.AjaxResult;
import com.cortex.common.core.domain.entity.SysUser;
import com.cortex.common.core.domain.model.LoginUser;
import com.cortex.common.utils.StringUtils;
import com.cortex.framework.web.service.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Agent Widget配置接口
 * 用于cortex-chat.js获取配置信息，以及第三方系统通过API Key换取登录令牌
 *
 * @author cortex
 */
@RestController
@RequestMapping("/agent/widget")
public class AgentWidgetController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(AgentWidgetController.class);

    @Autowired
    private IAiAgentService agentService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ApiRateLimiter rateLimiter;

    /** 第三方Widget令牌拥有的权限集合（仅限Agent运行时相关，不含系统管理权限） */
    private static final Set<String> WIDGET_PERMISSIONS;
    static {
        WIDGET_PERMISSIONS = new HashSet<>();
        WIDGET_PERMISSIONS.add("agent:agent:query");
        WIDGET_PERMISSIONS.add("runtime:session:query");
        WIDGET_PERMISSIONS.add("runtime:session:remove");
        WIDGET_PERMISSIONS.add("runtime:approval:query");
        WIDGET_PERMISSIONS.add("runtime:approval:approve");
        WIDGET_PERMISSIONS.add("runtime:approval:reject");
        WIDGET_PERMISSIONS.add("runtime:execlog:query");
        WIDGET_PERMISSIONS.add("runtime:execlog:remove");
    }

    /**
     * 获取Widget配置信息
     * 通过API Key查询关联的业务系统和Agent信息
     */
    @GetMapping("/config")
    public AjaxResult getConfig(@RequestHeader(value = "X-Api-Key", required = false) String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return error("API Key is required");
        }
        if (!rateLimiter.tryAcquire(apiKey)) {
            return error("请求过于频繁，请稍后再试");
        }
        try {
            AiAgent apiKeyEntity = agentService.validateApiKey(apiKey);
            if (apiKeyEntity == null) {
                return error("Invalid API Key");
            }
            Map<String, Object> configData = new HashMap<>();
            configData.put("businessSystem", apiKeyEntity.getBusinessSystem());
            configData.put("agentCode", apiKeyEntity.getAgentCode());
            configData.put("agentName", apiKeyEntity.getAgentName());
            return success(configData);
        } catch (Exception e) {
            log.error("获取Widget配置失败", e);
            return error("获取配置失败: " + e.getMessage());
        }
    }

    /**
     * 第三方系统通过API Key换取登录令牌（JWT）
     *
     * 工作流程：
     * 1. 验证API Key有效性，查询关联的Agent
     * 2. 校验userLoginName参数
     * 3. 构造一个仅含运行时权限的虚拟LoginUser，签发JWT
     * 4. 返回token + 业务系统 + Agent信息
     *
     * 前端Widget页面拿到token后写入Cookie，后续所有组件（对话/审批/文件/语音）
     * 走标准Bearer鉴权即可复用，无需逐个接口改造。
     */
    @PostMapping("/auth")
    public AjaxResult auth(@RequestHeader(value = "X-Api-Key", required = false) String apiKey,
                           @RequestBody(required = false) Map<String, String> body) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return error("API Key is required");
        }
        if (!rateLimiter.tryAcquire(apiKey)) {
            return error("请求过于频繁，请稍后再试");
        }
        String userLoginName = body != null ? body.get("userLoginName") : null;
        if (StringUtils.isEmpty(userLoginName)) {
            return error("userLoginName is required");
        }
        try {
            AiAgent agent = agentService.validateApiKey(apiKey);
            if (agent == null) {
                return error("Invalid API Key");
            }
            if (!"0".equals(agent.getStatus())) {
                return error("Agent已停用");
            }

            // 构造虚拟登录用户（仅运行时权限，不对应真实sys_user）
            SysUser synthUser = new SysUser();
            synthUser.setUserId(0L);
            synthUser.setUserName(userLoginName);
            synthUser.setNickName(userLoginName);
            LoginUser loginUser = new LoginUser(synthUser, WIDGET_PERMISSIONS);
            String token = tokenService.createToken(loginUser);

            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("businessSystem", agent.getBusinessSystem());
            data.put("agentCode", agent.getAgentCode());
            data.put("agentName", agent.getAgentName());
            data.put("userLoginName", userLoginName);

            log.info("Widget令牌签发 [agent={}, user={}, bs={}]",
                    agent.getAgentCode(), userLoginName, agent.getBusinessSystem());
            return success(data);
        } catch (Exception e) {
            log.error("Widget鉴权失败", e);
            return error("鉴权失败: " + e.getMessage());
        }
    }
}