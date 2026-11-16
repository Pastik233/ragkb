package com.example.ragkb;

import com.example.ragkb.note.NoteChunkMapper;
import com.example.ragkb.note.NoteMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 只验证装配：Mapper 接口被扫描到、XML 从 classpath 正确加载。
 *
 * <p>刻意不连数据库：Hikari 用 initialization-fail-timeout=-1 跳过启动期建连，
 * Flyway 关掉，所以本机没有 Postgres 也能跑。真正的 SQL 执行仍需要连库烟测。
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "spring.datasource.url=jdbc:postgresql://localhost:5432/ragkb"
})
class MapperWiringTest {

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Test
    void mapper_interfaces_are_registered() {
        var configuration = sqlSessionFactory.getConfiguration();
        assertTrue(configuration.hasMapper(NoteMapper.class), "NoteMapper 没被扫描到");
        assertTrue(configuration.hasMapper(NoteChunkMapper.class), "NoteChunkMapper 没被扫描到");
    }

    @Test
    void mapper_xml_is_loaded_from_classpath() {
        var configuration = sqlSessionFactory.getConfiguration();
        assertTrue(configuration.hasStatement(NoteMapper.class.getName() + ".findById"),
                "mapper-locations 没匹配到 NoteMapper.xml");
        assertTrue(configuration.hasStatement(
                        NoteChunkMapper.class.getName() + ".findNearestChunks"),
                "mapper-locations 没匹配到 NoteChunkMapper.xml");
    }
}
