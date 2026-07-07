package com.ruoyi.supplier.service;

import java.util.List;
import com.ruoyi.supplier.domain.AiModel;

/**
 * AI模型Service接口
 * 
 * @author ruoyi
 */
public interface IAiModelService 
{
    /**
     * 查询AI模型
     * 
     * @param modelId AI模型主键
     * @return AI模型
     */
    public AiModel selectAiModelByModelId(Long modelId);

    /**
     * 查询AI模型列表
     * 
     * @param aiModel AI模型
     * @return AI模型集合
     */
    public List<AiModel> selectAiModelList(AiModel aiModel);

    /**
     * 根据供应商ID查询模型列表
     * 
     * @param supplierId 供应商ID
     * @return AI模型集合
     */
    public List<AiModel> selectAiModelListBySupplierId(Long supplierId);

    /**
     * 新增AI模型
     * 
     * @param aiModel AI模型
     * @return 结果
     */
    public int insertAiModel(AiModel aiModel);

    /**
     * 修改AI模型
     * 
     * @param aiModel AI模型
     * @return 结果
     */
    public int updateAiModel(AiModel aiModel);

    /**
     * 批量删除AI模型
     * 
     * @param modelIds 需要删除的AI模型主键集合
     * @return 结果
     */
    public int deleteAiModelByModelIds(Long[] modelIds);

    /**
     * 删除AI模型信息
     * 
     * @param modelId AI模型主键
     * @return 结果
     */
    public int deleteAiModelByModelId(Long modelId);
}
