package com.dao;

import com.entity.WuzixinxiEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 库存台账 DAO
 */
public interface KucunDao {

    /**
     * 按物资名称行锁查询（SELECT ... FOR UPDATE）
     */
    WuzixinxiEntity selectForUpdate(@Param("wuzimingcheng") String wuzimingcheng);

    /**
     * 原子增减库存，delta 为正表示入库，为负表示出库
     */
    int updateStock(@Param("wuzimingcheng") String wuzimingcheng, @Param("delta") int delta);

    /**
     * 低库存预警：按物资分类聚合，返回库存 <= threshold 的物资
     */
    List<Map<String, Object>> lowStockAlert(@Param("threshold") int threshold);
}
