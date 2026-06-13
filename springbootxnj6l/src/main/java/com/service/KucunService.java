package com.service;

import com.entity.WuzishenlingEntity;
import java.util.List;
import java.util.Map;

/**
 * 库存管理业务接口
 * 统一管理入库增库、申领审批扣库、低库存预警
 */
public interface KucunService {

    /**
     * 入库增加库存
     * 在事务内通过悲观锁保证并发安全
     *
     * @param wuzimingcheng 物资名称
     * @param quantity 入库数量（正数）
     */
    void increaseStock(String wuzimingcheng, int quantity);

    /**
     * 创建申领单
     * 自动设置 sfsh="待审核"，快照当前可用库存到 wuzishuliang
     *
     * @param requisition 申领实体（需已设置好物资名称、申领数量等字段）
     */
    void createRequisition(WuzishenlingEntity requisition);

    /**
     * 审批通过申领单
     * 原子扣减库存 + 自动生成出库记录 + 更新申领状态为"已出库"
     * 库存不足时抛出 RuntimeException
     *
     * @param requisitionId 申领单 ID
     * @param shhf 审核回复
     */
    void approveRequisition(Long requisitionId, String shhf);

    /**
     * 审批拒绝申领单
     * 更新申领状态为"拒绝"，不扣减库存
     *
     * @param requisitionId 申领单 ID
     * @param shhf 审核回复
     */
    void rejectRequisition(Long requisitionId, String shhf);

    /**
     * 低库存预警查询
     * 按物资分类聚合，返回库存量低于阈值的分类汇总
     *
     * @param threshold 库存阈值
     * @return 分类维度的低库存汇总
     */
    List<Map<String, Object>> lowStockAlert(int threshold);
}
