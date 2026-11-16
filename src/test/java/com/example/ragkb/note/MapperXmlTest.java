package com.example.ragkb.note;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 不连数据库的 Mapper 校验：XML 能不能解析、动态 SQL 展开成什么。
 *
 * <p>Unit 测试只保证接口对得上，这里补「SQL 真的写对了」这一层，
 * 重点是 pgvector 的 CDATA、foreach 展开，以及 record 作为参数的属性访问。
 */
class MapperXmlTest {

    private static Configuration configuration;

    @BeforeAll
    static void parseMapperXml() throws Exception {
        configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        for (String resource : List.of("mapper/NoteMapper.xml", "mapper/NoteChunkMapper.xml")) {
            try (InputStream in = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments())
                        .parse();
            }
        }
    }

    @Test
    void every_mapper_method_has_a_statement() {
        for (Class<?> mapper : List.of(NoteMapper.class, NoteChunkMapper.class)) {
            for (Method method : mapper.getDeclaredMethods()) {
                String id = mapper.getName() + '.' + method.getName();
                assertTrue(configuration.hasStatement(id), "XML 缺少对应的 SQL：" + id);
            }
        }
    }

    @Test
    void note_insert_returns_generated_key() {
        BoundSql sql = configuration
                .getMappedStatement(NoteMapper.class.getName() + ".insert")
                .getBoundSql(Note.of("标题", "正文"));

        assertTrue(sql.getSql().toUpperCase().contains("RETURNING ID"),
                "record 没法回填主键，必须靠 RETURNING 取回 id");
    }

    @Test
    void chunk_batch_insert_expands_to_multiple_value_groups() {
        List<ChunkInsert> batch = List.of(
                new ChunkInsert(1L, 0, "片段一", 3, "test-model", "[0.1,0.2]"),
                new ChunkInsert(1L, 1, "片段二", 3, "test-model", "[0.3,0.4]"));

        BoundSql sql = configuration
                .getMappedStatement(NoteChunkMapper.class.getName() + ".insertChunks")
                .getBoundSql(Map.of("chunks", batch));

        assertEquals(12, sql.getParameterMappings().size(),
                "每片 6 个占位符，两片应展开成 12 个");
        assertTrue(sql.getSql().contains("CAST(? AS vector)"),
                "向量参数要做显式 CAST，否则 Postgres 判不出类型");
    }

    @Test
    void chunk_in_lookup_expands_one_placeholder_per_id() {
        BoundSql sql = configuration
                .getMappedStatement(NoteChunkMapper.class.getName() + ".findByIds")
                .getBoundSql(Map.of("ids", List.of(7L, 8L, 9L), "queryVec", "[0.1,0.2]"));

        String flat = sql.getSql().replaceAll("\\s+", " ");
        assertTrue(flat.contains("IN ( ? , ? , ? )"), "IN 必须逐个绑定，不能拼字符串");
        assertTrue(flat.contains("<=>"), "CDATA 里的向量运算符应原样保留");
    }

    @Test
    void vector_search_keeps_pgvector_operator() {
        BoundSql sql = configuration
                .getMappedStatement(NoteChunkMapper.class.getName() + ".findNearestChunks")
                .getBoundSql(Map.of("queryVec", "[0.1,0.2]", "model", "test-model", "k", 5));

        String flat = sql.getSql().replaceAll("\\s+", " ");
        assertTrue(flat.contains("<=>"), "CDATA 没生效的话 < 会被 XML 吃掉");
        assertTrue(flat.toUpperCase().contains("LIMIT ?"), "LIMIT 要走绑定参数");
    }

    @Test
    void constructor_mapping_covers_every_record_component() {
        assertConstructorMatches("com.example.ragkb.note.NoteMapper.noteResult", Note.class);
        assertConstructorMatches("com.example.ragkb.note.NoteChunkMapper.chunkResult",
                NoteChunk.class);
        assertConstructorMatches("com.example.ragkb.note.NoteChunkMapper.chunkMatchResult",
                ChunkMatch.class);
    }

    private static void assertConstructorMatches(String resultMapId, Class<?> recordType) {
        ResultMap resultMap = configuration.getResultMap(resultMapId);
        assertEquals(recordType.getRecordComponents().length,
                resultMap.getConstructorResultMappings().size(),
                resultMapId + " 的 constructor 参数个数必须与 record 组件数一致");
    }
}
