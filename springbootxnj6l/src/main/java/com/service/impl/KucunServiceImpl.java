package com.service.impl;

import java.util.Date;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.entity.WuzichukuEntity;
import com.entity.WuzishenlingEntity;
import com.entity.WuzixinxiEntity;
import com.dao.KucunDao;
import com.service.KucunService;
import com.service.WuzichukuService;
import com.service.WuzishenlingService;
import com.service.WuzixinxiService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 库存管理业务实现
 * 核心逻辑：入库增库、申领审批（原子扣库+自动出库）、低库存预警
 */
@Service("kucunService")
public class KucunServiceImpl implements KucunService {

    @Autowired
    private KucunDao kucunDao;

    @Autowired
    private WuzixinxiService wuzixinxiService;

    @Autowired
    private WuzishenlingService wuzishenlingService;

    @Autowired
    private WuzichukuService wuzichukuService;

    /**
     * 入库增加库存
     * 使用 FOR UPDATE 锁住物资行，防止与审批并发时数据不一致
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void increaseStock(String wuzimingcheng, int quantity) {
        if (wuzimingcheng == null || wuzimingcheng.isEmpty()) {
            throw new RuntimeException("物资名称不能为空");
        }
        if (quantity <= 0) {
            throw new RuntimeException("入库数量必须大于0");
        }

        // FOR UPDATE 锁住物资行
        WuzixinxiEntity wuzi = kucunDao.selectForUpdate(wuzimingcheng);
        if (wuzi == null) {
            throw new RuntimeException("物资不存在: " + wuzimingcheng);
        }

        // 增加库存
        Integer currentQty = wuzi.getWuzishuliang();
        if (currentQty == null) {
            currentQty = 0;
        }
        wuzi.setWuzishuliang(currentQty + quantity);
        wuzixinxiService.updateById(wuzi);
    }

    /**
     * 创建申领单
     * 自动设置 sfsh="待审核"，快照当前可用库存
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createRequisition(WuzishenlingEntity req) {
        if (req.getWuzimingcheng() == null || req.getWuzimingcheng().isEmpty()) {
            throw new RuntimeException("物资名称不能为空");
        }
        if (req.getShenlingshuliang() == null || req.getShenlingshuliang() <= 0) {
            throw new RuntimeException("申领数量必须大于0");
        }

        // 查询当前可用库存（不加锁，仅做快照）
        EntityWrapper<WuzixinxiEntity> ew = new EntityWrapper<>();
        ew.eq("wuzimingcheng", req.getWuzimingcheng());
        WuzixinxiEntity wuzi = wuzixinxiService.selectOne(ew);
        if (wuzi == null) {
            throw new RuntimeException("物资不存在: " + req.getWuzimingcheng());
        }

        // 快照当前可用库存
        req.setWuzishuliang(wuzi.getWuzishuliang());

        // 设置初始状态
        req.setSfsh("待审核");

        // 插入申领记录
        wuzishenlingService.insert(req);
    }

    /**
     * 审批通过：原子扣库存 + 自动生成出库记录 + 更新申领状态
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveRequisition(Long requisitionId, String shhf) {
        // 1. 查询申领单
        WuzishenlingEntity req = wuzishenlingService.selectById(requisitionId);
        if (req == null) {
            throw new RuntimeException("申领单不存在");
        }
        if (!"待审核".equals(req.getSfsh())) {
            throw new RuntimeException("当前状态不允许审批: " + req.getSfsh());
        }

        // 2. FOR UPDATE 锁住物资行，检查库存（保证并发安全）
        WuzixinxiEntity wuzi = kucunDao.selectForUpdate(req.getWuzimingcheng());
        if (wuzi == null) {
            throw new RuntimeException("物资不存在: " + req.getWuzimingcheng());
        }

        Integer currentStock = wuzi.getWuzishuliang();
        if (currentStock == null) {
            currentStock = 0;
        }
        int requestedQty = req.getShenlingshuliang();

        if (currentStock < requestedQty) {
            throw new RuntimeException("库存不足，当前库存: " + currentStock + "，申领数量: " + requestedQty);
        }

        // 3. 扣减库存
        wuzi.setWuzishuliang(currentStock - requestedQty);
        wuzixinxiService.updateById(wuzi);

        // 4. 自动生成出库记录
        WuzichukuEntity chuku = new WuzichukuEntity();
        chuku.setId(new Date().getTime() + new Double(Math.floor(Math.random() * 1000)).longValue());
        chuku.setChukubianhao("CK" + System.currentTimeMillis());
        chuku.setWuzimingcheng(req.getWuzimingcheng());
        chuku.setWuzifenlei(req.getWuzifenlei());
        chuku.setWuzitupian(req.getWuzitupian());
        chuku.setWuzishuliang(requestedQty);
        chuku.setChukuriqi(new Date());
        chuku.setChukuleixing("申领出库");
        chuku.setChukubeizhu("申领单号: " + req.getShenlingdanhao());
        chuku.setYonghuming(req.getYonghuming());
        chuku.setXingming(req.getXingming());
        chuku.setShouji(req.getShouji());
        wuzichukuService.insert(chuku);

        // 5. 更新申领状态为"已出库"
        req.setSfsh("已出库");
        req.setShhf(shhf);
        wuzishenlingService.updateById(req);
    }

    /**
     * 审批拒绝：更新状态，不扣库存
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectRequisition(Long requisitionId, String shhf) {
        WuzishenlingEntity req = wuzishenlingService.selectById(requisitionId);
        if (req == null) {
            throw new RuntimeException("申领单不存在");
        }
        if (!"待审核".equals(req.getSfsh())) {
            throw new RuntimeException("当前状态不允许审批: " + req.getSfsh());
        }

        req.setSfsh("拒绝");
        req.setShhf(shhf);
        wuzishenlingService.updateById(req);
    }

    /**
     * 低库存预警查询
     */
    @Override
    public List<Map<String, Object>> lowStockAlert(int threshold) {
        return kucunDao.selectLowStockAlert(threshold);
    }
}
