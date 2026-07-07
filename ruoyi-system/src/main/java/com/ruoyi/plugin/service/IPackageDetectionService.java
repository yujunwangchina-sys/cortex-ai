package com.ruoyi.plugin.service;

import com.ruoyi.plugin.domain.vo.PackageDetectionResult;

/**
 * 包检测服务接口
 * 
 * @author ruoyi
 */
public interface IPackageDetectionService {
    
    /**
     * 检测包并自动下载
     * 
     * @param packageName 包名
     * @return 检测结果
     */
    PackageDetectionResult detectAndDownload(String packageName);
}
