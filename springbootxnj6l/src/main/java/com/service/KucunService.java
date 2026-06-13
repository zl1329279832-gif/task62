package com.service;

import com.entity.WuzichukuEntity;
import com.entity.WuzirukuEntity;
import com.utils.R;

import java.util.List;
import java.util.Map;

/**
 * 库存台账 Service
 */
public interface KucunService {

    /**
     * 入库：保存入库记录并增加库存
     */
    void ruku(WuzirukuEntity ruku);

    /**
     * 出库：校验库存后保存出库记录并扣减库存
     */
    R chuku(WuzichukuEntity chuku);

    /**
     * 申领审核：待审→已出库（批准）或 待审→已拒绝
     * @param shenlingId 申领记录 ID
     * @param action     "已批准" 或 "已拒绝"
     * @param reply      审核回复
     */
    R shenhe(Long shenlingId, String action, String reply);

    /**
     * 低库存预警查询，按物资分类聚合
     */
    List<Map<String, Object>> lowStockAlert(int threshold);
}
