package com.dao;

import com.entity.WuzixinxiEntity;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

/**
 * 库存管理专用 DAO
 * 提供悲观锁查询和原子库存操作
 */
public interface KucunDao {

    /**
     * 悲观锁查询物资行（SELECT ... FOR UPDATE）
     * 在事务内锁住该行，防止并发修改
     */
    WuzixinxiEntity selectForUpdate(@Param("wuzimingcheng") String wuzimingcheng);

    /**
     * 直接在 SQL 层增加库存（原子操作）
     */
    int increaseStockDirect(@Param("wuzimingcheng") String wuzimingcheng, @Param("delta") int delta);

    /**
     * 按物资分类聚合低库存预警
     */
    List<Map<String, Object>> selectLowStockAlert(@Param("threshold") int threshold);
}
