package com.cortex.supplier.mapper;

import java.util.List;
import com.cortex.supplier.domain.AiSupplier;

/**
 * AI供应商Mapper接口
 * 
 * @author cortex
 */
public interface AiSupplierMapper 
{
    /**
     * 查询AI供应商
     * 
     * @param supplierId AI供应商主键
     * @return AI供应商
     */
    public AiSupplier selectAiSupplierBySupplierId(Long supplierId);

    /**
     * 查询AI供应商列表
     * 
     * @param aiSupplier AI供应商
     * @return AI供应商集合
     */
    public List<AiSupplier> selectAiSupplierList(AiSupplier aiSupplier);

    /**
     * 新增AI供应商
     * 
     * @param aiSupplier AI供应商
     * @return 结果
     */
    public int insertAiSupplier(AiSupplier aiSupplier);

    /**
     * 修改AI供应商
     * 
     * @param aiSupplier AI供应商
     * @return 结果
     */
    public int updateAiSupplier(AiSupplier aiSupplier);

    /**
     * 删除AI供应商
     * 
     * @param supplierId AI供应商主键
     * @return 结果
     */
    public int deleteAiSupplierBySupplierId(Long supplierId);

    /**
     * 批量删除AI供应商
     * 
     * @param supplierIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteAiSupplierBySupplierIds(Long[] supplierIds);

    /**
     * 校验供应商编码是否唯一
     * 
     * @param supplierCode 供应商编码
     * @return 结果
     */
    public AiSupplier checkSupplierCodeUnique(String supplierCode);
}
