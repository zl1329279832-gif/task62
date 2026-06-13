package com.service.impl;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dao.KucunDao;
import com.entity.WuzichukuEntity;
import com.entity.WuzirukuEntity;
import com.entity.WuzishenlingEntity;
import com.entity.WuzixinxiEntity;
import com.service.KucunService;
import com.service.WuzichukuService;
import com.service.WuzirukuService;
import com.service.WuzishenlingService;
import com.utils.R;

@Service("kucunService")
public class KucunServiceImpl implements KucunService {

    @Autowired
    private KucunDao kucunDao;

    @Autowired
    private WuzirukuService wuzirukuService;

    @Autowired
    private WuzichukuService wuzichukuService;

    @Autowired
    private WuzishenlingService wuzishenlingService;

    /**
     * 入库：保存入库记录 + 增加库存
     */
    @Override
    @Transactional
    public void ruku(WuzirukuEntity ruku) {
        ruku.setId(new Date().getTime() + new Double(Math.floor(Math.random() * 1000)).longValue());
        wuzirukuService.insert(ruku);

        // 原子增加库存
        String name = ruku.getWuzimingcheng();
        int qty = ruku.getWuzishuliang();
        WuzixinxiEntity material = kucunDao.selectForUpdate(name);
        if (material != null) {
            kucunDao.updateStock(name, qty);
        }
    }

    /**
     * 出库：校验库存 → 扣减 → 保存出库记录
     */
    @Override
    @Transactional
    public R chuku(WuzichukuEntity chuku) {
        String name = chuku.getWuzimingcheng();
        int qty = chuku.getWuzishuliang();

        // 悲观锁查询当前库存
        WuzixinxiEntity material = kucunDao.selectForUpdate(name);
        if (material == null) {
            return R.error("物资【" + name + "】不存在");
        }
        if (material.getWuzishuliang() < qty) {
            return R.error("库存不足，当前库存：" + material.getWuzishuliang() + "，申请出库：" + qty);
        }

        // 原子扣减库存
        kucunDao.updateStock(name, -qty);

        // 保存出库记录
        chuku.setId(new Date().getTime() + new Double(Math.floor(Math.random() * 1000)).longValue());
        wuzichukuService.insert(chuku);
        return R.ok();
    }

    /**
     * 申领审核：状态机 待审→已出库 / 待审→已拒绝
     * 批准后自动生成出库记录并扣减库存
     */
    @Override
    @Transactional
    public R shenhe(Long shenlingId, String action, String reply) {
        WuzishenlingEntity shenling = wuzishenlingService.selectById(shenlingId);
        if (shenling == null) {
            return R.error("申领记录不存在");
        }

        String currentStatus = shenling.getSfsh();
        if (!"待审".equals(currentStatus)) {
            return R.error("当前状态为【" + currentStatus + "】，无法审核");
        }

        if ("已批准".equals(action)) {
            // ---- 批准流程：校验库存 → 扣减 → 生成出库单 → 更新状态 ----
            String name = shenling.getWuzimingcheng();
            int qty = shenling.getShenlingshuliang();

            // 悲观锁查询，防止并发超发
            WuzixinxiEntity material = kucunDao.selectForUpdate(name);
            if (material == null) {
                return R.error("物资【" + name + "】不存在");
            }
            if (material.getWuzishuliang() < qty) {
                return R.error("库存不足，当前库存：" + material.getWuzishuliang() + "，申领数量：" + qty);
            }

            // 原子扣减库存
            kucunDao.updateStock(name, -qty);

            // 自动生成出库记录
            WuzichukuEntity chuku = new WuzichukuEntity();
            chuku.setId(new Date().getTime() + new Double(Math.floor(Math.random() * 1000)).longValue());
            chuku.setChukubianhao("CK" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())
                    + (int) (Math.random() * 1000));
            chuku.setWuzimingcheng(shenling.getWuzimingcheng());
            chuku.setWuzifenlei(shenling.getWuzifenlei());
            chuku.setWuzitupian(shenling.getWuzitupian());
            chuku.setWuzishuliang(qty);
            chuku.setChukuriqi(new Date());
            chuku.setChukuleixing("申领出库");
            chuku.setChukubeizhu("申领单号：" + shenling.getShenlingdanhao());
            chuku.setYonghuming(shenling.getYonghuming());
            chuku.setXingming(shenling.getXingming());
            chuku.setShouji(shenling.getShouji());
            chuku.setAddtime(new Date());
            wuzichukuService.insert(chuku);

            // 更新申领状态 → 已出库
            shenling.setSfsh("已出库");
            shenling.setShhf(reply);
            wuzishenlingService.updateById(shenling);

            return R.ok("审批通过，已自动出库");

        } else if ("已拒绝".equals(action)) {
            shenling.setSfsh("已拒绝");
            shenling.setShhf(reply);
            wuzishenlingService.updateById(shenling);
            return R.ok("已拒绝");

        } else {
            return R.error("无效的审核操作：" + action);
        }
    }

    /**
     * 低库存预警查询
     */
    @Override
    public List<Map<String, Object>> lowStockAlert(int threshold) {
        return kucunDao.lowStockAlert(threshold);
    }
}
