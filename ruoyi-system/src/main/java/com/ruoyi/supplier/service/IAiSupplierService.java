package com.ruoyi.supplier.service;

import java.util.List;
import com.ruoyi.supplier.domain.AiSupplier;

/**
 * AI供应商Service接口
 * 
 * @author ruoyi
 */
public interface IAiSupplierService 
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
     * 批量删除AI供应商
     * 
     * @param supplierIds 需要删除的AI供应商主键集合
     * @return 结果
     */
    public int deleteAiSupplierBySupplierIds(Long[] supplierIds);

    /**
     * 删除AI供应商信息
     * 
     * @param supplierId AI供应商主键
     * @return 结果
     */
    public int deleteAiSupplierBySupplierId(Long supplierId);

    /**
     * 校验供应商编码是否唯一
     * 
     * @param aiSupplier AI供应商
     * @return 结果
     */
    public boolean checkSupplierCodeUnique(AiSupplier aiSupplier);

    /**
     * 测试供应商连接
     * 
     * @param aiSupplier AI供应商
     * @return 测试结果
     */
    public String testConnection(AiSupplier aiSupplier);
}
