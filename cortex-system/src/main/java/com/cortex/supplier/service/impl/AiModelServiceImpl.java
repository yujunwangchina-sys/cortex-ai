package com.cortex.supplier.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.cortex.supplier.domain.AiModel;
import com.cortex.supplier.mapper.AiModelMapper;
import com.cortex.supplier.service.IAiModelService;

/**
 * AI模型Service业务层处理
 * 
 * @author cortex
 */
@Service
public class AiModelServiceImpl implements IAiModelService 
{
    @Autowired
    private AiModelMapper aiModelMapper;

    /**
     * 查询AI模型
     * 
     * @param modelId AI模型主键
     * @return AI模型
     */
    @Override
    public AiModel selectAiModelByModelId(Long modelId)
    {
        return aiModelMapper.selectAiModelByModelId(modelId);
    }

    /**
     * 查询AI模型列表
     * 
     * @param aiModel AI模型
     * @return AI模型
     */
    @Override
    public List<AiModel> selectAiModelList(AiModel aiModel)
    {
        return aiModelMapper.selectAiModelList(aiModel);
    }

    /**
     * 根据供应商ID查询模型列表
     * 
     * @param supplierId 供应商ID
     * @return AI模型集合
     */
    @Override
    public List<AiModel> selectAiModelListBySupplierId(Long supplierId)
    {
        return aiModelMapper.selectAiModelListBySupplierId(supplierId);
    }

    /**
     * 新增AI模型
     * 
     * @param aiModel AI模型
     * @return 结果
     */
    @Override
    public int insertAiModel(AiModel aiModel)
    {
        return aiModelMapper.insertAiModel(aiModel);
    }

    /**
     * 修改AI模型
     * 
     * @param aiModel AI模型
     * @return 结果
     */
    @Override
    public int updateAiModel(AiModel aiModel)
    {
        return aiModelMapper.updateAiModel(aiModel);
    }

    /**
     * 批量删除AI模型
     * 
     * @param modelIds 需要删除的AI模型主键
     * @return 结果
     */
    @Override
    public int deleteAiModelByModelIds(Long[] modelIds)
    {
        return aiModelMapper.deleteAiModelByModelIds(modelIds);
    }

    /**
     * 删除AI模型信息
     * 
     * @param modelId AI模型主键
     * @return 结果
     */
    @Override
    public int deleteAiModelByModelId(Long modelId)
    {
        return aiModelMapper.deleteAiModelByModelId(modelId);
    }
}
