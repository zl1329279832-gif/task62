package com;

import java.util.Date;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.entity.WuzichukuEntity;
import com.entity.WuzishenlingEntity;
import com.entity.WuzixinxiEntity;
import com.service.KucunService;
import com.service.WuzichukuService;
import com.service.WuzishenlingService;
import com.service.WuzixinxiService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 库存台账贯通集成测试
 *
 * 运行前提：MySQL 数据库已启动，springbootxnj6l 库已创建
 * 测试按顺序执行，每步依赖前一步的数据状态
 *
 * 运行方式：
 *   mvn test -Dtest=KucunServiceTest
 *   或在 IDE 中右键运行本类
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class KucunServiceTest {

    @Autowired
    private KucunService kucunService;

    @Autowired
    private WuzixinxiService wuzixinxiService;

    @Autowired
    private WuzishenlingService wuzishenlingService;

    @Autowired
    private WuzichukuService wuzichukuService;

    // ====================== 测试数据常量 ======================
    private static final String WUZI_TENT = "测试帐篷";
    private static final String WUZI_WATER = "测试矿泉水";
    private static final String WUZI_MEDKIT = "测试急救包";
    private static final String CATEGORY_SHELTER = "住宿类";
    private static final String CATEGORY_FOOD = "食品类";
    private static final String CATEGORY_MEDICAL = "医疗类";

    private static Long tentId;
    private static Long waterId;
    private static Long medkitId;

    // ====================== 初始化测试数据 ======================
    @BeforeEach
    void setUp() {
        // 只在第一次运行时初始化（后续测试依赖前序数据）
        if (tentId != null) return;

        // 清理可能存在的测试数据
        EntityWrapper<WuzishenlingEntity> slEw = new EntityWrapper<>();
        slEw.like("wuzimingcheng", "测试");
        wuzishenlingService.delete(slEw);

        EntityWrapper<WuzichukuEntity> ckEw = new EntityWrapper<>();
        ckEw.like("wuzimingcheng", "测试");
        wuzichukuService.delete(ckEw);

        EntityWrapper<WuzixinxiEntity> wxEw = new EntityWrapper<>();
        wxEw.like("wuzimingcheng", "测试");
        wuzixinxiService.delete(wxEw);

        // 插入测试物资
        WuzixinxiEntity tent = new WuzixinxiEntity();
        tent.setId(System.currentTimeMillis());
        tent.setWuzimingcheng(WUZI_TENT);
        tent.setWuzifenlei(CATEGORY_SHELTER);
        tent.setWuzishuliang(100);
        wuzixinxiService.insert(tent);
        tentId = tent.getId();

        WuzixinxiEntity water = new WuzixinxiEntity();
        water.setId(System.currentTimeMillis() + 1);
        water.setWuzimingcheng(WUZI_WATER);
        water.setWuzifenlei(CATEGORY_FOOD);
        water.setWuzishuliang(500);
        wuzixinxiService.insert(water);
        waterId = water.getId();

        WuzixinxiEntity medkit = new WuzixinxiEntity();
        medkit.setId(System.currentTimeMillis() + 2);
        medkit.setWuzimingcheng(WUZI_MEDKIT);
        medkit.setWuzifenlei(CATEGORY_MEDICAL);
        medkit.setWuzishuliang(5);
        wuzixinxiService.insert(medkit);
        medkitId = medkit.getId();
    }

    // ====================== 场景 1：入库联动库存增加 ======================
    @Test
    @Order(1)
    @DisplayName("场景1: 入库联动 - 库存自动增加")
    void testIncreaseStock() {
        // 帐篷初始库存 100，入库 50
        kucunService.increaseStock(WUZI_TENT, 50);

        WuzixinxiEntity tent = wuzixinxiService.selectById(tentId);
        assertEquals(150, tent.getWuzishuliang(), "入库后库存应为 150 (100+50)");
    }

    // ====================== 场景 2：入库参数校验 ======================
    @Test
    @Order(2)
    @DisplayName("场景2: 入库参数校验 - 物资不存在/数量非法")
    void testIncreaseStockValidation() {
        // 物资不存在
        RuntimeException ex1 = assertThrows(RuntimeException.class,
            () -> kucunService.increaseStock("不存在的物资", 10));
        assertTrue(ex1.getMessage().contains("物资不存在"));

        // 数量 <= 0
        RuntimeException ex2 = assertThrows(RuntimeException.class,
            () -> kucunService.increaseStock(WUZI_TENT, 0));
        assertTrue(ex2.getMessage().contains("入库数量必须大于0"));

        RuntimeException ex3 = assertThrows(RuntimeException.class,
            () -> kucunService.increaseStock(WUZI_TENT, -5));
        assertTrue(ex3.getMessage().contains("入库数量必须大于0"));
    }

    // ====================== 场景 3：创建申领自动设置待审核 ======================
    @Test
    @Order(3)
    @DisplayName("场景3: 创建申领单 - 自动设置待审核状态 + 库存快照")
    void testCreateRequisition() {
        WuzishenlingEntity req = new WuzishenlingEntity();
        req.setId(System.currentTimeMillis() + 10);
        req.setShenlingdanhao("SL-TEST-001");
        req.setWuzimingcheng(WUZI_TENT);
        req.setWuzifenlei(CATEGORY_SHELTER);
        req.setShenlingshuliang(30);
        req.setShenlingriqi(new Date());
        req.setYonghuming("testuser");
        req.setXingming("测试员");
        req.setShouji("13800138000");

        kucunService.createRequisition(req);

        // 验证状态自动设为"待审核"
        WuzishenlingEntity saved = wuzishenlingService.selectById(req.getId());
        assertNotNull(saved, "申领记录应已保存");
        assertEquals("待审核", saved.getSfsh(), "状态应为'待审核'");
        assertEquals(150, saved.getWuzishuliang(), "库存快照应为当前值 150");
        assertEquals(30, saved.getShenlingshuliang(), "申领数量应为 30");
    }

    // ====================== 场景 4：审批通过 → 自动出库 + 扣库存 ======================
    @Test
    @Order(4)
    @DisplayName("场景4: 审批通过 - 原子扣库存 + 自动生成出库记录")
    void testApproveRequisition() {
        // 先创建申领单
        WuzishenlingEntity req = new WuzishenlingEntity();
        req.setId(System.currentTimeMillis() + 20);
        req.setShenlingdanhao("SL-TEST-002");
        req.setWuzimingcheng(WUZI_TENT);
        req.setWuzifenlei(CATEGORY_SHELTER);
        req.setShenlingshuliang(30);
        req.setShenlingriqi(new Date());
        req.setYonghuming("testuser");
        req.setXingming("测试员");
        req.setShouji("13800138000");
        kucunService.createRequisition(req);

        // 记录审批前出库数量
        int chukuBefore = wuzichukuService.selectCount(new EntityWrapper<>());

        // 审批通过
        kucunService.approveRequisition(req.getId(), "同意出库");

        // 验证1：申领状态变为"已出库"
        WuzishenlingEntity approved = wuzishenlingService.selectById(req.getId());
        assertEquals("已出库", approved.getSfsh(), "审批后状态应为'已出库'");
        assertEquals("同意出库", approved.getShhf(), "审核回复应正确");

        // 验证2：库存扣减 (150 - 30 = 120)
        WuzixinxiEntity tent = wuzixinxiService.selectById(tentId);
        assertEquals(120, tent.getWuzishuliang(), "库存应为 120 (150-30)");

        // 验证3：自动生成出库记录
        int chukuAfter = wuzichukuService.selectCount(new EntityWrapper<>());
        assertEquals(chukuBefore + 1, chukuAfter, "应新增1条出库记录");

        // 验证出库记录内容
        EntityWrapper<WuzichukuEntity> ew = new EntityWrapper<>();
        ew.eq("chukuleixing", "申领出库");
        ew.eq("wuzimingcheng", WUZI_TENT);
        WuzichukuEntity chuku = wuzichukuService.selectOne(ew);
        assertNotNull(chuku, "出库记录应存在");
        assertEquals(30, chuku.getWuzishuliang(), "出库数量应为 30");
        assertEquals("申领出库", chuku.getChukuleixing(), "出库类型应为'申领出库'");
        assertTrue(chuku.getChukubianhao().startsWith("CK"), "出库编号应以 CK 开头");
        assertEquals("testuser", chuku.getYonghuming(), "用户名应匹配");
    }

    // ====================== 场景 5：库存不足 → 审批拒绝 ======================
    @Test
    @Order(5)
    @DisplayName("场景5: 库存不足 - 审批应拒绝，状态不变")
    void testApproveInsufficientStock() {
        // 创建申领 999 个帐篷（当前库存 120）
        WuzishenlingEntity req = new WuzishenlingEntity();
        req.setId(System.currentTimeMillis() + 30);
        req.setShenlingdanhao("SL-TEST-003");
        req.setWuzimingcheng(WUZI_TENT);
        req.setWuzifenlei(CATEGORY_SHELTER);
        req.setShenlingshuliang(999);
        req.setShenlingriqi(new Date());
        req.setYonghuming("testuser");
        req.setXingming("测试员");
        req.setShouji("13800138000");
        kucunService.createRequisition(req);

        // 审批 → 应抛出异常
        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> kucunService.approveRequisition(req.getId(), "同意"));
        assertTrue(ex.getMessage().contains("库存不足"), "应提示库存不足");
        assertTrue(ex.getMessage().contains("120"), "应显示当前库存 120");
        assertTrue(ex.getMessage().contains("999"), "应显示申领数量 999");

        // 验证：申领状态仍为"待审核"
        WuzishenlingEntity unchanged = wuzishenlingService.selectById(req.getId());
        assertEquals("待审核", unchanged.getSfsh(), "库存不足时状态应保持'待审核'");

        // 验证：库存不变
        WuzixinxiEntity tent = wuzixinxiService.selectById(tentId);
        assertEquals(120, tent.getWuzishuliang(), "库存应仍为 120");
    }

    // ====================== 场景 6：审批拒绝 ======================
    @Test
    @Order(6)
    @DisplayName("场景6: 审批拒绝 - 状态变为拒绝，库存不变")
    void testRejectRequisition() {
        // 使用场景5创建的申领单进行拒绝
        WuzishenlingEntity req = new WuzishenlingEntity();
        req.setId(System.currentTimeMillis() + 40);
        req.setShenlingdanhao("SL-TEST-004");
        req.setWuzimingcheng(WUZI_TENT);
        req.setWuzifenlei(CATEGORY_SHELTER);
        req.setShenlingshuliang(10);
        req.setShenlingriqi(new Date());
        req.setYonghuming("testuser");
        req.setXingming("测试员");
        req.setShouji("13800138000");
        kucunService.createRequisition(req);

        // 拒绝
        kucunService.rejectRequisition(req.getId(), "数量不合理");

        // 验证状态
        WuzishenlingEntity rejected = wuzishenlingService.selectById(req.getId());
        assertEquals("拒绝", rejected.getSfsh(), "状态应为'拒绝'");
        assertEquals("数量不合理", rejected.getShhf(), "审核回复应正确");

        // 验证库存不变
        WuzixinxiEntity tent = wuzixinxiService.selectById(tentId);
        assertEquals(120, tent.getWuzishuliang(), "拒绝后库存应不变");
    }

    // ====================== 场景 7：重复审批拒绝 ======================
    @Test
    @Order(7)
    @DisplayName("场景7: 重复审批 - 非待审核状态不允许操作")
    void testDuplicateApproval() {
        // 使用场景6已拒绝的申领单
        Long rejectedId = System.currentTimeMillis() + 40;

        RuntimeException ex1 = assertThrows(RuntimeException.class,
            () -> kucunService.approveRequisition(rejectedId, "同意"));
        assertTrue(ex1.getMessage().contains("当前状态不允许审批"));

        RuntimeException ex2 = assertThrows(RuntimeException.class,
            () -> kucunService.rejectRequisition(rejectedId, "再次拒绝"));
        assertTrue(ex2.getMessage().contains("当前状态不允许审批"));
    }

    // ====================== 场景 8：低库存预警 ======================
    @Test
    @Order(8)
    @DisplayName("场景8: 低库存预警 - 按分类聚合")
    void testLowStockAlert() {
        // 急救包库存 5，设置阈值 10 应能检测到
        List<Map<String, Object>> alerts = kucunService.lowStockAlert(10);
        assertNotNull(alerts, "预警结果不应为 null");
        assertFalse(alerts.isEmpty(), "应有低库存预警");

        // 验证医疗类在预警中
        boolean foundMedical = false;
        for (Map<String, Object> alert : alerts) {
            if (CATEGORY_MEDICAL.equals(String.valueOf(alert.get("wuzifenlei")))) {
                foundMedical = true;
                // lowStockCount >= 1
                int count = ((Number) alert.get("lowStockCount")).intValue();
                assertTrue(count >= 1, "医疗类应有至少1个低库存物资");
                // wuziNames 包含急救包
                String names = String.valueOf(alert.get("wuziNames"));
                assertTrue(names.contains(WUZI_MEDKIT), "应包含测试急救包");
            }
        }
        assertTrue(foundMedical, "医疗类应在低库存预警中");

        // 设置阈值 1，不应有预警（所有物资库存 >= 5）
        List<Map<String, Object>> noAlerts = kucunService.lowStockAlert(1);
        assertTrue(noAlerts.isEmpty(), "阈值1时不应有预警");
    }

    // ====================== 场景 9：多次入库累加 ======================
    @Test
    @Order(9)
    @DisplayName("场景9: 多次入库累加 - 库存正确叠加")
    void testMultipleInbound() {
        // 矿泉水当前 500，入库 100 + 200
        kucunService.increaseStock(WUZI_WATER, 100);
        kucunService.increaseStock(WUZI_WATER, 200);

        WuzixinxiEntity water = wuzixinxiService.selectById(waterId);
        assertEquals(800, water.getWuzishuliang(), "库存应为 800 (500+100+200)");
    }

    // ====================== 场景 10：申领单不存在 ======================
    @Test
    @Order(10)
    @DisplayName("场景10: 申领单不存在 - 应抛出异常")
    void testApproveNonExistent() {
        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> kucunService.approveRequisition(999999L, "同意"));
        assertTrue(ex.getMessage().contains("申领单不存在"));
    }
}
