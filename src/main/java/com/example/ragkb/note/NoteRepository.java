package com.example.ragkb.note;

import java.util.List;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.query.Param;


@Repository
public interface NoteRepository extends ListCrudRepository<Note, Long> {

    List<Note> findByTitleContainingIgnoreCase(String keyword);

    @Modifying
    @Query("UPDATE notes SET index_status = :status, updated_at = CURRENT_TIMESTAMP WHERE id = :id")
    void updateIndexStatus(@Param("id") Long id, @Param("status") String status);

    @Query("SELECT id FROM notes WHERE index_status <> 'READY' ORDER BY id")
    List<Long> findIdsNotReady();
}
