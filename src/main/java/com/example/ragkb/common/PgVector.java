package com.example.ragkb.common;

import java.util.StringJoiner;

/**
 * pgvector 字面量转换。
 *
 * <p>之前挂在具体的模型客户端上作为静态方法；现在 embedding 可插拔，
 * 字面量格式属于数据库侧约定，与模型实现无关，独立出来。
 */
public final class PgVector {

    private PgVector() {
    }

    /** float[] → "[0.1,0.2,...]"，供 MyBatis 的 CAST(? AS vector) 使用 */
    public static String literal(float[] vec) {
        StringJoiner j = new StringJoiner(",", "[", "]");
        for (float v : vec) {
            j.add(Float.toString(v));
        }
        return j.toString();
    }
}
