package com;

import com.dao.KucunDao;
import com.entity.WuzichukuEntity;
import com.entity.WuzirukuEntity;
import com.entity.WuzishenlingEntity;
import com.entity.WuzixinxiEntity;
import com.service.KucunService;
import com.service.WuzichukuService;
import com.service.WuzishenlingService;
import com.service.WuzixinxiService;
import com.utils.R;

import com.baomidou.mybatisplus.mapper.EntityWrapper;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 库存台账集成测试
 *
 * 需要连接 MySQL 数据库运行。测试前确保 wuzixinxi 表中有物资记录。
 * 每个测试用 @Transactional 自动回滚，不会污染数据。
 *
 * ====== 手动 curl 验证步骤 ======
 *
 * 0. 启动应用并登录获取 token:
 *    curl -X POST http://localhost:8080/springbootxnj6l/users/login \
 *      -H "Content-Type: application/json" \
 *      -d '{"username":"admin","password":"admin"}'
 *    # 记下返回的 token 值
 *
 * 1. 入库 → 库存增加:
 *    curl -X POST http://localhost:8080/springbootxnj6l/wuziruku/save \
 *      -H "Content-Type: application/json" -H "Token: <token>" \
 *      -d '{"rukubianhao":"RK001","wuzimingcheng":"帐篷","wuzifenlei":"救灾物资","wuzishuliang":50,"rukuriqi":"2026-06-13"}'
 *    # 然后查看库存: GET /wuzixinxi/lists 确认帐篷数量增加了 50
 *
 * 2. 出库 → 库存不足拒绝:
 *    curl -X POST http://localhost:8080/springbootxnj6l/wuzichuku/save \
 *      -H "Content-Type: application/json" -H "Token: <token>" \
 *      -d '{"chukubianhao":"CK001","wuzimingcheng":"帐篷","wuzifenlei":"救灾物资","wuzishuliang":99999,"chukuriqi":"2026-06-13"}'
 *    # 应返回 code:500, msg 含"库存不足"
 *
 * 3. 申领审批 → 自动出库:
 *    # 先创建申领:
 *    curl -X POST http://localhost:8080/springbootxnj6l/wuzishenling/save \
 *      -H "Content-Type: application/json" -H "Token: <token>" \
 *      -d '{"shenlingdanhao":"SL001","wuzimingcheng":"帐篷","wuzifenlei":"救灾物资","wuzishuliang":100,"shenlingshuliang":5,"shenlingriqi":"2026-06-13","yonghuming":"user1","xingming":"张三","shouji":"13800000000"}'
 *    # 记下返回成功后，查 /wuzishenling/lists 获取 ID, 确认 sfsh="待审"
 *    # 审批通过:
 *    curl -X POST http://localhost:8080/springbootxnj6l/sh/wuzishenling \
 *      -H "Content-Type: application/json" -H "Token: <token>" \
 *      -d '{"id":<shenlingId>,"sfsh":"是","shhf":"同意"}'
 *    # 应返回成功; 确认 sfsh 变为"已出库"; 确认 wuzichuku 表多了一条申领出库记录; 库存减少 5
 *
 * 4. 低库存预警:
 *    curl http://localhost:8080/springbootxnj6l/wuzichuku/lowstock?threshold=100 \
 *      -H "Token: <token>"
 *    # 返回按分类聚合的低库存物资列表
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KucunServiceTest {

    @Autowired
    private KucunService kucunService;

    @Autowired
    private WuzixinxiService wuzixinxiService;

    @Autowired
    private WuzishenlingService wuzishenlingService;

    @Autowired
    private WuzichukuService wuzichukuService;

    @Autowired
    private KucunDao kucunDao;

    private static final String TEST_MATERIAL = "TEST_KUCUN_ITEM_" + System.currentTimeMillis();
    private static final String TEST_CATEGORY = "测试分类";

    /**
     * 插入测试物资，初始库存 100
     */
    private void insertTestMaterial(int stock) {
        WuzixinxiEntity material = new WuzixinxiEntity();
        material.setId(new Date().getTime() + (long)(Math.random() * 1000));
        material.setWuzimingcheng(TEST_MATERIAL);
        material.setWuzifenlei(TEST_CATEGORY);
        material.setWuzishuliang(stock);
        material.setAddtime(new Date());
        wuzixinxiService.insert(material);
    }

    // ========== 1. 入库 → 库存增加 ==========

    @Test
    @Order(1)
    @Transactional
    void testRuku_increasesStock() {
        insertTestMaterial(100);

        WuzirukuEntity ruku = new WuzirukuEntity();
        ruku.setRukubianhao("RK_TEST_001");
        ruku.setWuzimingcheng(TEST_MATERIAL);
        ruku.setWuzifenlei(TEST_CATEGORY);
        ruku.setWuzishuliang(30);
        ruku.setRukuriqi(new Date());

        kucunService.ruku(ruku);

        // 验证库存从 100 → 130
        WuzixinxiEntity updated = wuzixinxiService.selectOne(
                new EntityWrapper<WuzixinxiEntity>().eq("wuzimingcheng", TEST_MATERIAL));
        assertEquals(130, updated.getWuzishuliang(), "入库后库存应为 130");
    }

    // ========== 2. 出库 → 库存扣减 ==========

    @Test
    @Order(2)
    @Transactional
    void testChuku_decreasesStock() {
        insertTestMaterial(100);

        WuzichukuEntity chuku = new WuzichukuEntity();
        chuku.setChukubianhao("CK_TEST_001");
        chuku.setWuzimingcheng(TEST_MATERIAL);
        chuku.setWuzifenlei(TEST_CATEGORY);
        chuku.setWuzishuliang(20);
        chuku.setChukuriqi(new Date());
        chuku.setChukuleixing("手动出库");

        R result = kucunService.chuku(chuku);
        assertEquals(0, result.get("code"), "出库应成功");

        WuzixinxiEntity updated = wuzixinxiService.selectOne(
                new EntityWrapper<WuzixinxiEntity>().eq("wuzimingcheng", TEST_MATERIAL));
        assertEquals(80, updated.getWuzishuliang(), "出库后库存应为 80");
    }

    // ========== 3. 出库 → 库存不足拒绝 ==========

    @Test
    @Order(3)
    @Transactional
    void testChuku_rejectsInsufficientStock() {
        insertTestMaterial(10);

        WuzichukuEntity chuku = new WuzichukuEntity();
        chuku.setChukubianhao("CK_TEST_002");
        chuku.setWuzimingcheng(TEST_MATERIAL);
        chuku.setWuzifenlei(TEST_CATEGORY);
        chuku.setWuzishuliang(50);
        chuku.setChukuriqi(new Date());

        R result = kucunService.chuku(chuku);
        assertEquals(500, result.get("code"), "库存不足应返回错误");
        assertTrue(result.get("msg").toString().contains("库存不足"), "错误信息应含'库存不足'");

        // 库存不应变化
        WuzixinxiEntity unchanged = wuzixinxiService.selectOne(
                new EntityWrapper<WuzixinxiEntity>().eq("wuzimingcheng", TEST_MATERIAL));
        assertEquals(10, unchanged.getWuzishuliang(), "库存不足时库存不应变化");
    }

    // ========== 4. 审批通过 → 自动出库 ==========

    @Test
    @Order(4)
    @Transactional
    void testShenhe_approveCreatesChukuAndDeductsStock() {
        insertTestMaterial(100);

        // 创建申领记录
        WuzishenlingEntity sl = new WuzishenlingEntity();
        long slId = new Date().getTime() + (long)(Math.random() * 1000);
        sl.setId(slId);
        sl.setShenlingdanhao("SL_TEST_001");
        sl.setWuzimingcheng(TEST_MATERIAL);
        sl.setWuzifenlei(TEST_CATEGORY);
        sl.setWuzishuliang(100);
        sl.setShenlingshuliang(25);
        sl.setShenlingriqi(new Date());
        sl.setYonghuming("testuser");
        sl.setXingming("测试用户");
        sl.setShouji("13800000000");
        sl.setSfsh("待审");
        sl.setAddtime(new Date());
        wuzishenlingService.insert(sl);

        // 审批通过
        R result = kucunService.shenhe(slId, "已批准", "同意发放");
        assertEquals(0, result.get("code"), "审批应成功");

        // 验证申领状态变为"已出库"
        WuzishenlingEntity updatedSl = wuzishenlingService.selectById(slId);
        assertEquals("已出库", updatedSl.getSfsh(), "审批通过后状态应为'已出库'");

        // 验证自动生成了出库记录
        int chukuCount = wuzichukuService.selectCount(
                new EntityWrapper<WuzichukuEntity>()
                        .eq("wuzimingcheng", TEST_MATERIAL)
                        .eq("chukuleixing", "申领出库"));
        assertTrue(chukuCount > 0, "应自动生成出库记录");

        // 验证库存扣减
        WuzixinxiEntity updated = wuzixinxiService.selectOne(
                new EntityWrapper<WuzixinxiEntity>().eq("wuzimingcheng", TEST_MATERIAL));
        assertEquals(75, updated.getWuzishuliang(), "审批出库后库存应为 75");
    }

    // ========== 5. 审批拒绝 → 不影响库存 ==========

    @Test
    @Order(5)
    @Transactional
    void testShenhe_rejectDoesNotAffectStock() {
        insertTestMaterial(100);

        WuzishenlingEntity sl = new WuzishenlingEntity();
        long slId = new Date().getTime() + (long)(Math.random() * 1000);
        sl.setId(slId);
        sl.setShenlingdanhao("SL_TEST_002");
        sl.setWuzimingcheng(TEST_MATERIAL);
        sl.setWuzifenlei(TEST_CATEGORY);
        sl.setWuzishuliang(100);
        sl.setShenlingshuliang(25);
        sl.setSfsh("待审");
        sl.setAddtime(new Date());
        wuzishenlingService.insert(sl);

        R result = kucunService.shenhe(slId, "已拒绝", "库存紧张");
        assertEquals(0, result.get("code"), "拒绝应成功");

        WuzishenlingEntity updatedSl = wuzishenlingService.selectById(slId);
        assertEquals("已拒绝", updatedSl.getSfsh(), "状态应为'已拒绝'");
        assertEquals("库存紧张", updatedSl.getShhf(), "审核回复应正确");

        // 库存不变
        WuzixinxiEntity unchanged = wuzixinxiService.selectOne(
                new EntityWrapper<WuzixinxiEntity>().eq("wuzimingcheng", TEST_MATERIAL));
        assertEquals(100, unchanged.getWuzishuliang(), "拒绝后库存不应变化");
    }

    // ========== 6. 状态机：非待审状态不能审核 ==========

    @Test
    @Order(6)
    @Transactional
    void testShenhe_rejectsNonPendingStatus() {
        WuzishenlingEntity sl = new WuzishenlingEntity();
        long slId = new Date().getTime() + (long)(Math.random() * 1000);
        sl.setId(slId);
        sl.setShenlingdanhao("SL_TEST_003");
        sl.setWuzimingcheng(TEST_MATERIAL);
        sl.setSfsh("已出库"); // 已终态
        sl.setAddtime(new Date());
        wuzishenlingService.insert(sl);

        R result = kucunService.shenhe(slId, "已批准", "");
        assertEquals(500, result.get("code"), "非待审状态应拒绝审核");
        assertTrue(result.get("msg").toString().contains("无法审核"), "错误信息应含'无法审核'");
    }

    // ========== 7. 审批时库存不足 → 拒绝批准 ==========

    @Test
    @Order(7)
    @Transactional
    void testShenhe_rejectsApprovalWhenInsufficientStock() {
        insertTestMaterial(5);

        WuzishenlingEntity sl = new WuzishenlingEntity();
        long slId = new Date().getTime() + (long)(Math.random() * 1000);
        sl.setId(slId);
        sl.setShenlingdanhao("SL_TEST_004");
        sl.setWuzimingcheng(TEST_MATERIAL);
        sl.setWuzifenlei(TEST_CATEGORY);
        sl.setWuzishuliang(5);
        sl.setShenlingshuliang(20); // 申领 20，但只有 5
        sl.setSfsh("待审");
        sl.setAddtime(new Date());
        wuzishenlingService.insert(sl);

        R result = kucunService.shenhe(slId, "已批准", "");
        assertEquals(500, result.get("code"), "库存不足应拒绝批准");

        // 状态应保持"待审"
        WuzishenlingEntity unchanged = wuzishenlingService.selectById(slId);
        assertEquals("待审", unchanged.getSfsh(), "库存不足时状态应保持待审");
    }

    // ========== 8. 低库存预警 ==========

    @Test
    @Order(8)
    @Transactional
    void testLowStockAlert() {
        insertTestMaterial(3);

        List<Map<String, Object>> alerts = kucunService.lowStockAlert(10);
        assertNotNull(alerts, "预警结果不应为 null");

        boolean found = alerts.stream()
                .anyMatch(m -> TEST_CATEGORY.equals(m.get("wuzifenlei")));
        assertTrue(found, "预警结果应包含测试分类");
    }
}
