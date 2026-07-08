package com.cortex.plugin.service;

import com.cortex.plugin.domain.vo.PackageDetectionResult;

/**
 * 包检测服务接口
 * 
 * @author cortex
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
