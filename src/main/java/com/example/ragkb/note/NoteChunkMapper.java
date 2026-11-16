package com.example.ragkb.note;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * note_chunks 表的访问入口。
 *
 * <p>向量检索的两条 SQL 在 XML 里用 CDATA 包裹，因为 pgvector 的 {@code <=>}
 * 运算符里的小于号会被 XML 当成标签起点。
 */
@Mapper
public interface NoteChunkMapper {

    List<NoteChunk> findAll();

    List<NoteChunk> findByNoteIdOrderByChunkIndex(long noteId);

    long countByNoteId(long noteId);

    int deleteByNoteId(long noteId);

    /** 一次多条 INSERT，切片多时能把 N 次往返压成 1 次 */
    int insertChunks(@Param("chunks") List<ChunkInsert> chunks);

    List<ChunkMatch> findNearestChunks(@Param("queryVec") String queryVec,
                                       @Param("model") String model,
                                       @Param("k") int k);

    List<ChunkMatch> findByIds(@Param("ids") List<Long> ids,
                               @Param("queryVec") String queryVec);
}
