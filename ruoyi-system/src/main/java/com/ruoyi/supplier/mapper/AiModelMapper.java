package com.ruoyi.supplier.mapper;

import java.util.List;
import com.ruoyi.supplier.domain.AiModel;

/**
 * AI模型Mapper接口
 * 
 * @author ruoyi
 */
public interface AiModelMapper 
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
     * 删除AI模型
     * 
     * @param modelId AI模型主键
     * @return 结果
     */
    public int deleteAiModelByModelId(Long modelId);

    /**
     * 批量删除AI模型
     * 
     * @param modelIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteAiModelByModelIds(Long[] modelIds);

    /**
     * 根据供应商ID删除模型
     * 
     * @param supplierId 供应商ID
     * @return 结果
     */
    public int deleteAiModelBySupplierId(Long supplierId);
}
