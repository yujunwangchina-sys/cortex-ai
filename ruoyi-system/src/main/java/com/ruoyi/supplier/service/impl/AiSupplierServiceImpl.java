package com.ruoyi.supplier.service.impl;

import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.supplier.domain.AiSupplier;
import com.ruoyi.supplier.mapper.AiModelMapper;
import com.ruoyi.supplier.mapper.AiSupplierMapper;
import com.ruoyi.supplier.service.IAiSupplierService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

/**
 * AI供应商Service业务层处理
 * 
 * @author ruoyi
 */
@Service
public class AiSupplierServiceImpl implements IAiSupplierService 
{
    @Autowired
    private AiSupplierMapper aiSupplierMapper;

    @Autowired
    private AiModelMapper aiModelMapper;

    /**
     * 查询AI供应商
     * 
     * @param supplierId AI供应商主键
     * @return AI供应商
     */
    @Override
    public AiSupplier selectAiSupplierBySupplierId(Long supplierId)
    {
        return aiSupplierMapper.selectAiSupplierBySupplierId(supplierId);
    }

    /**
     * 查询AI供应商列表
     * 
     * @param aiSupplier AI供应商
     * @return AI供应商
     */
    @Override
    public List<AiSupplier> selectAiSupplierList(AiSupplier aiSupplier)
    {
        return aiSupplierMapper.selectAiSupplierList(aiSupplier);
    }

    /**
     * 新增AI供应商
     * 
     * @param aiSupplier AI供应商
     * @return 结果
     */
    @Override
    public int insertAiSupplier(AiSupplier aiSupplier)
    {
        return aiSupplierMapper.insertAiSupplier(aiSupplier);
    }

    /**
     * 修改AI供应商
     * 
     * @param aiSupplier AI供应商
     * @return 结果
     */
    @Override
    public int updateAiSupplier(AiSupplier aiSupplier)
    {
        return aiSupplierMapper.updateAiSupplier(aiSupplier);
    }

    /**
     * 批量删除AI供应商
     * 
     * @param supplierIds 需要删除的AI供应商主键
     * @return 结果
     */
    @Override
    @Transactional
    public int deleteAiSupplierBySupplierIds(Long[] supplierIds)
    {
        // 同时删除关联的模型
        for (Long supplierId : supplierIds)
        {
            aiModelMapper.deleteAiModelBySupplierId(supplierId);
        }
        return aiSupplierMapper.deleteAiSupplierBySupplierIds(supplierIds);
    }

    /**
     * 删除AI供应商信息
     * 
     * @param supplierId AI供应商主键
     * @return 结果
     */
    @Override
    @Transactional
    public int deleteAiSupplierBySupplierId(Long supplierId)
    {
        // 同时删除关联的模型
        aiModelMapper.deleteAiModelBySupplierId(supplierId);
        return aiSupplierMapper.deleteAiSupplierBySupplierId(supplierId);
    }

    /**
     * 校验供应商编码是否唯一
     * 
     * @param aiSupplier AI供应商
     * @return 结果
     */
    @Override
    public boolean checkSupplierCodeUnique(AiSupplier aiSupplier)
    {
        Long supplierId = StringUtils.isNull(aiSupplier.getSupplierId()) ? -1L : aiSupplier.getSupplierId();
        AiSupplier info = aiSupplierMapper.checkSupplierCodeUnique(aiSupplier.getSupplierCode());
        if (StringUtils.isNotNull(info) && info.getSupplierId().longValue() != supplierId.longValue())
        {
            return false;
        }
        return true;
    }

    /**
     * 获取供应商可用的测试模型
     */
    private String getTestModel(Long supplierId)
    {
        if (supplierId == null)
        {
            return null;
        }
        
        List<com.ruoyi.supplier.domain.AiModel> models = aiModelMapper.selectAiModelListBySupplierId(supplierId);
        
        // 优先选择启用的chat类型模型，按排序取第一个
        return models.stream()
            .filter(m -> "0".equals(m.getStatus()) && "chat".equals(m.getModelType()))
            .sorted((a, b) -> {
                int aSort = a.getSortOrder() != null ? a.getSortOrder() : 999;
                int bSort = b.getSortOrder() != null ? b.getSortOrder() : 999;
                return Integer.compare(aSort, bSort);
            })
            .map(com.ruoyi.supplier.domain.AiModel::getModelCode)
            .findFirst()
            .orElse(null);
    }

    /**
     * 测试供应商连接 - 使用LangChain4j优雅测试
     * 
     * @param aiSupplier AI供应商
     * @return 测试结果
     */
    @Override
    public String testConnection(AiSupplier aiSupplier)
    {
        long startTime = System.currentTimeMillis();
        
        try
        {
            // 验证必填参数
            if (StringUtils.isEmpty(aiSupplier.getApiBaseUrl()))
            {
                return "API基础地址不能为空";
            }
            
            if (StringUtils.isEmpty(aiSupplier.getApiKey()))
            {
                return "API密钥不能为空，无法进行连接测试";
            }

            // 获取该供应商的测试模型
            String testModelCode = getTestModel(aiSupplier.getSupplierId());
            
            if (StringUtils.isEmpty(testModelCode))
            {
                return "⚠️ 该供应商暂无可用的chat类型模型，请先添加模型后再测试";
            }

            // 使用LangChain4j进行优雅的AI调用测试
            OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                    .apiKey(aiSupplier.getApiKey())
                    .baseUrl(aiSupplier.getApiBaseUrl())
                    .modelName(testModelCode)
                    .timeout(Duration.ofSeconds(30))
                    .maxRetries(0)
                    .logRequests(true)   // 临时开启日志帮助调试
                    .logResponses(true);

            ChatLanguageModel model = builder.build();

            // 发送测试消息
            String simpleResponse = model.generate("Hi");

            long duration = System.currentTimeMillis() - startTime;

            if (StringUtils.isNotEmpty(simpleResponse))
            {
                return String.format("✅ 连接成功！模型响应正常 [模型: %s] [响应: %s] (耗时: %dms)", 
                    testModelCode, 
                    simpleResponse.length() > 50 ? simpleResponse.substring(0, 50) + "..." : simpleResponse,
                    duration);
            }
            else
            {
                return String.format("⚠️ 连接成功，但模型未返回内容 [模型: %s] (耗时: %dms)", testModelCode, duration);
            }
        }
        catch (Exception e)
        {
            long duration = System.currentTimeMillis() - startTime;
            String errorMsg = e.getMessage();
            
            // 打印详细异常信息帮助调试
            e.printStackTrace();
            
            // 优雅地处理常见错误
            if (errorMsg != null)
            {
                if (errorMsg.contains("401") || errorMsg.contains("Unauthorized"))
                {
                    return String.format("❌ API Key无效或未授权 (耗时: %dms)", duration);
                }
                else if (errorMsg.contains("403") || errorMsg.contains("Forbidden"))
                {
                    return String.format("❌ 访问被拒绝，请检查API Key权限 (耗时: %dms)", duration);
                }
                else if (errorMsg.contains("404") || errorMsg.contains("Not Found"))
                {
                    return String.format("❌ API地址不正确，请检查baseUrl配置 (耗时: %dms)", duration);
                }
                else if (errorMsg.contains("model_not_found") || errorMsg.contains("Model not exist"))
                {
                    return String.format("❌ 模型不存在，请检查模型编码是否正确 (耗时: %dms)", duration);
                }
                else if (errorMsg.contains("timeout") || errorMsg.contains("timed out"))
                {
                    return String.format("❌ 连接超时，请检查网络或API地址 (耗时: %dms)", duration);
                }
                else if (errorMsg.contains("Connection refused"))
                {
                    return String.format("❌ 连接被拒绝，请检查API地址和网络 (耗时: %dms)", duration);
                }
                else if (errorMsg.contains("choices") && errorMsg.contains("null"))
                {
                    return String.format("❌ API返回格式异常，可能是API Key无效或余额不足 (耗时: %dms)", duration);
                }
            }
            
            // 返回异常类型和消息
            String exceptionType = e.getClass().getSimpleName();
            return String.format("❌ 连接失败 [%s]: %s (耗时: %dms)", 
                exceptionType,
                errorMsg != null ? errorMsg : "未知错误", 
                duration);
        }
    }
}
