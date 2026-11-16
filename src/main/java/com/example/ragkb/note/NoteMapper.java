package com.example.ragkb.note;

import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * notes 表的访问入口。
 *
 * <p>SQL 全部在同名 XML（resources/mapper/NoteMapper.xml）里，接口只声明签名，
 * 不再靠方法名派生查询语句。
 */
@Mapper
public interface NoteMapper {

    /**
     * 插入并返回自增主键。
     *
     * <p>record 不可变，没法用 useGeneratedKeys 把主键回填到参数对象上，
     * 所以 XML 里走的是 {@code INSERT ... RETURNING id}。
     */
    Long insert(Note note);

    Optional<Note> findById(long id);

    List<Note> findAll();

    boolean existsById(long id);

    /** 返回受影响行数，调用方可以直接拿它判断删除是否命中 */
    int deleteById(long id);

    List<Note> findByTitleContainingIgnoreCase(@Param("keyword") String keyword);

    int updateIndexStatus(@Param("id") long id, @Param("status") String status);

    List<Long> findIdsNotReady();
}
