package com.example.ragkb.note;

import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NoteChunkRepository extends ListCrudRepository<NoteChunk, Long> {

    List<NoteChunk> findByNoteIdOrderByChunkIndex(Long noteId);

    long countByNoteId(Long noteId);

    @Modifying
    @Query("DELETE FROM note_chunks WHERE note_id = :noteId")
    void deleteByNoteId(@Param("noteId") Long noteId);

    @Modifying
    @Query("""
        INSERT INTO note_chunks
            (note_id, chunk_index, content, char_count, embedding_model, embedding)
        VALUES
            (:noteId, :chunkIndex, :content, :charCount, :model,
             CAST(:embedding AS vector))
        """)
    void insertChunk(@Param("noteId") Long noteId,
                     @Param("chunkIndex") int chunkIndex,
                     @Param("content") String content,
                     @Param("charCount") int charCount,
                     @Param("model") String model,
                     @Param("embedding") String embedding);

    @Query("""
        SELECT c.id          AS chunk_id,
               c.note_id     AS note_id,
               n.title       AS title,
               c.chunk_index AS chunk_index,
               c.content     AS content,
               n.created_at  AS created_at,
               c.embedding <=> CAST(:queryVec AS vector) AS distance
        FROM note_chunks c
        JOIN notes n ON n.id = c.note_id
        WHERE c.embedding IS NOT NULL
          AND c.embedding_model = :model
        ORDER BY c.embedding <=> CAST(:queryVec AS vector)
        LIMIT :k
        """)
    List<ChunkMatch> findNearestChunks(@Param("queryVec") String queryVec,
                                       @Param("model") String model,
                                       @Param("k") int k);

    @Query("""
        SELECT c.id          AS chunk_id,
               c.note_id     AS note_id,
               n.title       AS title,
               c.chunk_index AS chunk_index,
               c.content     AS content,
               n.created_at  AS created_at,
               c.embedding <=> CAST(:queryVec AS vector) AS distance
        FROM note_chunks c
        JOIN notes n ON n.id = c.note_id
        WHERE c.id IN (:ids)
        """)
    List<ChunkMatch> findByIds(@Param("ids") List<Long> ids,
                               @Param("queryVec") String queryVec);

}

