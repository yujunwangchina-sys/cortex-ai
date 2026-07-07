package com.ruoyi.agent.runtime.prompt;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.agent.domain.AiAgent;
import com.ruoyi.plugin.domain.AiPlugin;
import com.ruoyi.plugin.domain.AiPluginTool;
import com.ruoyi.skill.domain.SkillNode;
import com.ruoyi.supplier.domain.AiModel;
import com.ruoyi.supplier.domain.AiSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent config loader - four-table join orchestration.
 * Agent -> Skills -> Plugins+Tools -> Supplier+Model
 *
 * @author ruoyi
 */
@Component
public class AgentConfigLoader
{
    private static final Logger log = LoggerFactory.getLogger(AgentConfigLoader.class);

    @Autowired
    private com.ruoyi.agent.mapper.AiAgentMapper agentMapper;

    @Autowired
    private com.ruoyi.agent.mapper.AiAgentSkillMapper agentSkillMapper;

    @Autowired
    private com.ruoyi.agent.mapper.AiAgentPluginMapper agentPluginMapper;

    @Autowired
    private com.ruoyi.skill.mapper.SkillNodeMapper skillNodeMapper;

    @Autowired
    private com.ruoyi.plugin.mapper.AiPluginMapper pluginMapper;

    @Autowired
    private com.ruoyi.plugin.mapper.AiPluginToolMapper pluginToolMapper;

    @Autowired
    private com.ruoyi.knowledge.mapper.AiAgentKnowledgeMapper agentKbMapper;
    private com.ruoyi.agent.mapper.AiAgentDelegationMapper delegationMapper;

    @Autowired
    private com.ruoyi.supplier.mapper.AiSupplierMapper supplierMapper;

    @Autowired
    private com.ruoyi.supplier.mapper.AiModelMapper modelMapper;

    /**
     * Load agents that this agent is authorized to delegate to.
     */
    public java.util.List<AiAgent> loadDelegatableAgents(Long agentId)
    {
        try
        {
            java.util.List<Long> delegateIds = delegationMapper.selectDelegateAgentIdsByAgentId(agentId);
            if (delegateIds == null || delegateIds.isEmpty())
            {
                return java.util.Collections.emptyList();
            }
            java.util.List<AiAgent> agents = new java.util.ArrayList<>();
            for (Long id : delegateIds)
            {
                AiAgent a = agentMapper.selectAiAgentById(id);
                if (a != null && "0".equals(a.getStatus()))
                {
                    agents.add(a);
                }
            }
            return agents;
        }
        catch (Exception e)
        {
            log.warn("Failed to load delegatable agents [agentId={}]", agentId, e);
            return java.util.Collections.emptyList();
        }
    }

    /**
     * Load knowledge bases authorized for this agent.
     */
    public java.util.List<com.ruoyi.knowledge.domain.AiAgentKnowledge> loadKnowledgeBases(Long agentId)
    {
        try
        {
            return agentKbMapper.selectActiveByAgentId(agentId);
        }
        catch (Exception e)
        {
            log.warn("加载知识库授权失败 [agentId={}]: {}", agentId, e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    public AiAgent loadAgent(String agentCode)
    {
        AiAgent query = new AiAgent();
        query.setAgentCode(agentCode);
        List<AiAgent> list = agentMapper.selectAiAgentList(query);
        if (list == null || list.isEmpty())
        {
            throw new RuntimeException("Agent not found: " + agentCode);
        }
        AiAgent agent = list.get(0);
        if ("1".equals(agent.getStatus()))
        {
            throw new RuntimeException("Agent disabled: " + agentCode);
        }
        return agent;
    }

    /**
     * Load Agent's skills (three-layer isolation)
     */
    public List<SkillNode> loadSkills(Long agentId, String businessSystem, String userLoginName)
    {
        List<Long> skillIds = agentSkillMapper.selectSkillIdsByAgentId(agentId);
        List<SkillNode> skills = new ArrayList<>();
        for (Long skillId : skillIds)
        {
            SkillNode node = skillNodeMapper.selectNodeById(skillId);
            if (node == null || node.getContent() == null || node.getContent().isEmpty())
            {
                continue;
            }
            String scope = node.getSkillScope() != null ? node.getSkillScope() : "system";
            if ("system".equals(scope))
            {
                String nodeBizSys = node.getBusinessSystem() != null ? node.getBusinessSystem() : "cortex";
                if (businessSystem == null || businessSystem.isEmpty() || businessSystem.equals(nodeBizSys))
                {
                    skills.add(node);
                }
            }
            else if ("user".equals(scope))
            {
                if (userLoginName != null && userLoginName.equals(node.getOwnerUser()))
                {
                    skills.add(node);
                }
            }
        }
        log.debug("Loaded skills [agentId={}, businessSystem={}, user={}, count={}]",
                agentId, businessSystem, userLoginName, skills.size());
        return skills;
    }

    /**
     * Load skill index for an agent: a flat list of all visible skill nodes
     * (skill packages expanded into their child skills, with descriptions extracted
     * from DESCRIPTION.md or content first-lines).
     *
     * <p>This replaces the old loadSkills for PromptBuilder — it returns skill nodes
     * with lightweight metadata (name, id, short description) but does NOT require
     * full content to be loaded. The LLM pulls full content via skill_view.
     *
     * @param agentId         Agent ID
     * @param businessSystem  Business system identifier
     * @param userLoginName   User login name
     * @return Flat list of skill nodes (packages + individual skills)
     */
    public List<SkillNode> loadSkillIndex(Long agentId, String businessSystem, String userLoginName)
    {
        List<Long> skillIds = agentSkillMapper.selectSkillIdsByAgentId(agentId);
        List<SkillNode> result = new ArrayList<>();

        for (Long skillId : skillIds)
        {
            SkillNode node = skillNodeMapper.selectNodeById(skillId);
            if (node == null) continue;

            // Permission check (three-layer isolation)
            if (!isSkillVisible(node, businessSystem, userLoginName)) continue;

            String nodeType = node.getNodeType() != null ? node.getNodeType() : "skill";

            if ("skill_package".equals(nodeType) || "directory".equals(nodeType))
            {
                // Expand skill package: scan children for skills
                result.add(node); // Add the package itself (as a category header)
                expandSkillPackage(node, businessSystem, userLoginName, result);
            }
            else if ("skill".equals(nodeType))
            {
                result.add(node);
            }
            else
            {
                // file or other — include as-is
                result.add(node);
            }
        }

        log.debug("Loaded skill index [agentId={}, businessSystem={}, user={}, count={}]",
                agentId, businessSystem, userLoginName, result.size());
        return result;
    }

    /**
     * Recursively expand a skill package node, collecting all child skill nodes.
     * Reads DESCRIPTION.md content if present to enhance package-level description.
     */
    private void expandSkillPackage(SkillNode pkgNode, String businessSystem,
                                    String userLoginName, List<SkillNode> result)
    {
        List<SkillNode> children = skillNodeMapper.selectChildrenByParentId(pkgNode.getId());
        if (children == null) return;

        // 1. 先查找 DESCRIPTION.md 并读取内容（第一次渐进式披露）
        String packageDescription = extractPackageDescription(children);
        if (packageDescription != null && !packageDescription.isEmpty())
        {
            // 将描述保存到技能包节点的 skillMetadata 中（临时，不持久化）
            try
            {
                com.alibaba.fastjson2.JSONObject meta = new com.alibaba.fastjson2.JSONObject();
                if (pkgNode.getSkillMetadata() != null && !pkgNode.getSkillMetadata().isEmpty())
                {
                    meta = com.alibaba.fastjson2.JSON.parseObject(pkgNode.getSkillMetadata());
                }
                meta.put("packageDescription", packageDescription);
                pkgNode.setSkillMetadata(meta.toJSONString());
            }
            catch (Exception ignored) {}
        }

        // 2. 遍历子节点，收集技能
        for (SkillNode child : children)
        {
            if (!isSkillVisible(child, businessSystem, userLoginName)) continue;

            String childType = child.getNodeType() != null ? child.getNodeType() : "skill";

            if ("skill".equals(childType))
            {
                result.add(child);
            }
            else if ("skill_package".equals(childType) || "directory".equals(childType))
            {
                // Nested package — recurse
                expandSkillPackage(child, businessSystem, userLoginName, result);
            }
            // Skip file nodes here (DESCRIPTION.md, etc.) — they are metadata
        }
    }

    /**
     * Extract package description from DESCRIPTION.md file.
     * 
     * @param children Child nodes of the package
     * @return Package description (first 200 chars), or null if not found
     */
    private String extractPackageDescription(List<SkillNode> children)
    {
        if (children == null) return null;

        // 查找 DESCRIPTION.md 文件（不区分大小写）
        for (SkillNode child : children)
        {
            if (child.getIsDirectory() != null && child.getIsDirectory()) continue;
            
            String name = child.getName();
            if (name == null) continue;
            
            if (name.equalsIgnoreCase("DESCRIPTION.md"))
            {
                // 懒加载内容（如果未加载）
                if (child.getContent() == null || child.getContent().isEmpty())
                {
                    SkillNode fullNode = skillNodeMapper.selectNodeWithContent(child.getId());
                    if (fullNode != null && fullNode.getContent() != null)
                    {
                        child.setContent(fullNode.getContent());
                    }
                }
                
                String content = child.getContent();
                if (content == null || content.isEmpty()) return null;
                
                // 提取描述：跳过前置元数据（YAML frontmatter），取第一段有效内容
                String desc = extractFirstMeaningfulContent(content);
                
                // 限制长度（避免占用过多token）
                if (desc.length() > 200)
                {
                    desc = desc.substring(0, 197) + "...";
                }
                
                return desc;
            }
        }
        
        return null;
    }

    /**
     * Extract first meaningful content from markdown (skip YAML frontmatter).
     */
    private String extractFirstMeaningfulContent(String content)
    {
        if (content == null || content.isEmpty()) return "";
        
        String[] lines = content.split("\n");
        boolean inFrontmatter = false;
        boolean frontmatterClosed = false;
        StringBuilder result = new StringBuilder();
        
        for (String line : lines)
        {
            String trimmed = line.trim();
            
            // 处理 YAML frontmatter
            if (trimmed.equals("---"))
            {
                if (!inFrontmatter && !frontmatterClosed)
                {
                    inFrontmatter = true;
                    continue;
                }
                else if (inFrontmatter)
                {
                    inFrontmatter = false;
                    frontmatterClosed = true;
                    continue;
                }
            }
            
            if (inFrontmatter) continue;
            
            // 跳过空行和标题行
            if (trimmed.isEmpty()) continue;
            if (trimmed.startsWith("#")) continue;
            
            // 找到第一段有效内容
            result.append(trimmed).append(" ");
            
            // 收集足够的内容后停止（约200字符）
            if (result.length() > 200) break;
        }
        
        return result.toString().trim();
    }

    /**
     * Check if a skill node is visible to the given user (three-layer isolation).
     */
    private boolean isSkillVisible(SkillNode node, String businessSystem, String userLoginName)
    {
        String scope = node.getSkillScope() != null ? node.getSkillScope() : "system";
        if ("system".equals(scope))
        {
            String nodeBizSys = node.getBusinessSystem() != null ? node.getBusinessSystem() : "cortex";
            return businessSystem == null || businessSystem.isEmpty() || businessSystem.equals(nodeBizSys);
        }
        else if ("user".equals(scope))
        {
            return userLoginName != null && userLoginName.equals(node.getOwnerUser());
        }
        return true;
    }

    /**
     * Load all visible skills (for SkillManagerPlugin listing)
     */
    public List<SkillNode> loadVisibleSkills(String businessSystem, String userLoginName)
    {
        return skillNodeMapper.selectVisibleSkills(businessSystem, userLoginName);
    }

    /**
     * Load Agent's plugins
     */
    public List<AiPlugin> loadPlugins(Long agentId)
    {
        List<Long> pluginIds = agentPluginMapper.selectPluginIdsByAgentId(agentId);
        List<AiPlugin> plugins = new ArrayList<>();
        for (Long pluginId : pluginIds)
        {
            AiPlugin plugin = pluginMapper.selectAiPluginByPluginId(pluginId);
            if (plugin != null && "0".equals(plugin.getStatus()))
            {
                plugins.add(plugin);
            }
        }
        log.debug("Loaded plugins [agentId={}, count={}]", agentId, plugins.size());
        return plugins;
    }

    /**
     * Load tools under plugins
     */
    public List<AiPluginTool> loadTools(List<AiPlugin> plugins)
    {
        System.out.println("==================== 加载工具列表 ====================");
        System.out.println("插件数量: " + plugins.size());
        
        List<AiPluginTool> tools = new ArrayList<>();
        for (AiPlugin plugin : plugins)
        {
            System.out.println("从插件加载工具: " + plugin.getPluginName() + " (ID=" + plugin.getPluginId() + ")");
            List<AiPluginTool> pluginTools = pluginToolMapper.selectAiPluginToolListByPluginId(plugin.getPluginId());
            if (pluginTools != null)
            {
                System.out.println("  -> 找到" + pluginTools.size() + "个工具");
                for (AiPluginTool tool : pluginTools)
                {
                    System.out.println("     * " + tool.getToolCode() + " - " + tool.getToolName());
                }
                tools.addAll(pluginTools);
            }
            else
            {
                System.out.println("  -> 未找到工具");
            }
        }
        System.out.println("工具加载完成，共" + tools.size() + "个");
        System.out.println("====================================================");
        
        log.debug("Loaded tools [count={}]", tools.size());
        return tools;
    }

    /**
     * Select model by modelId (runtime switching)
     */
    public ModelSelection selectModelById(Long modelId)
    {
        AiModel model = modelMapper.selectAiModelByModelId(modelId);
        if (model == null)
        {
            throw new RuntimeException("Model not found (id=" + modelId + ")");
        }
        AiSupplier supplier = supplierMapper.selectAiSupplierBySupplierId(model.getSupplierId());
        if (supplier == null)
        {
            throw new RuntimeException("Supplier not found (id=" + model.getSupplierId() + ")");
        }
        // Load fallbacks using the model's own type
        String modelType = model.getModelType() != null ? model.getModelType() : "chat";
        List<ModelSelection> fallbacks = loadFallbacks(model, modelType);
        return new ModelSelection(supplier, model, fallbacks);
    }

    /**
     * Load fallback models for a primary model selection.
     * Picks other enabled models of the same type (excluding the primary model).
     */
    public List<ModelSelection> loadFallbacks(AiModel primaryModel, String modelType)
    {
        List<ModelSelection> fallbacks = new ArrayList<>();
        try
        {
            AiModel query = new AiModel();
            query.setModelType(modelType);
            query.setStatus("0");
            List<AiModel> all = modelMapper.selectAiModelList(query);
            if (all != null)
            {
                for (AiModel m : all)
                {
                    if (primaryModel != null && m.getModelId().equals(primaryModel.getModelId())) continue;
                    AiSupplier s = supplierMapper.selectAiSupplierBySupplierId(m.getSupplierId());
                    if (s != null && "0".equals(s.getStatus()))
                    {
                        fallbacks.add(new ModelSelection(s, m));
                    }
                }
            }
        }
        catch (Exception e)
        {
            log.warn("Failed to load fallback models", e);
        }
        return fallbacks;
    }

    /**
     * Dynamically select model by agent preference and requested modality type.
     *
     * @param agent     Agent config
     * @param modelType Modality: chat / vision / multimodal
     * @return Selected model + supplier
     */
    public ModelSelection selectModel(AiAgent agent, String modelType)
    {
        // 1. Try modelPreference JSON for specified modality
        Long modelId = null;
        if (agent.getModelPreference() != null && !agent.getModelPreference().isEmpty())
        {
            try
            {
                JSONObject pref = JSON.parseObject(agent.getModelPreference());
                if (pref.containsKey(modelType))
                {
                    modelId = pref.getLong(modelType);
                }
                // fallback: if specified modality not found, try chat
                if (modelId == null && pref.containsKey("chat"))
                {
                    modelId = pref.getLong("chat");
                }
            }
            catch (Exception e)
            {
                log.warn("Failed to parse modelPreference [agent={}]", agent.getAgentCode(), e);
            }
        }

        // 2. If no preference, find first enabled model of this type
        AiModel model = null;
        if (modelId != null)
        {
            model = modelMapper.selectAiModelByModelId(modelId);
        }
        if (model == null)
        {
            AiModel query = new AiModel();
            query.setModelType(modelType);
            query.setStatus("0");
            List<AiModel> models = modelMapper.selectAiModelList(query);
            if (models != null && !models.isEmpty())
            {
                model = models.get(0);
            }
            else
            {
                // fallback: any enabled model
                AiModel fallbackQuery = new AiModel();
                fallbackQuery.setStatus("0");
                List<AiModel> all = modelMapper.selectAiModelList(fallbackQuery);
                if (all != null && !all.isEmpty())
                {
                    model = all.get(0);
                }
            }
        }

        if (model == null)
        {
            throw new RuntimeException("No available AI model (type=" + modelType + ")");
        }

        // 3. Load supplier
        AiSupplier supplier = supplierMapper.selectAiSupplierBySupplierId(model.getSupplierId());
        if (supplier == null)
        {
            throw new RuntimeException("Supplier not found (id=" + model.getSupplierId() + ")");
        }
        if ("1".equals(supplier.getStatus()))
        {
            throw new RuntimeException("Supplier disabled: " + supplier.getSupplierName());
        }

        // 4. Load fallbacks
        List<ModelSelection> fallbacks = loadFallbacks(model, modelType);
        return new ModelSelection(supplier, model, fallbacks);
    }

    /**
     * Model selection result
     */
    public static class ModelSelection
    {
        public final AiSupplier supplier;
        public final AiModel model;
        public final List<ModelSelection> fallbacks;

        public ModelSelection(AiSupplier supplier, AiModel model)
        {
            this(supplier, model, java.util.Collections.emptyList());
        }

        public ModelSelection(AiSupplier supplier, AiModel model, List<ModelSelection> fallbacks)
        {
            this.supplier = supplier;
            this.model = model;
            this.fallbacks = fallbacks != null ? fallbacks : java.util.Collections.emptyList();
        }
    }
}
