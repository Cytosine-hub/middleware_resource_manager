package com.middleware.manager.wiki.repository;

import com.middleware.manager.wiki.entity.WikiPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WikiPageMapper {

    WikiPage findById(@Param("id") Long id);

    WikiPage findByTitleAndType(@Param("title") String title, @Param("pageType") String pageType);

    List<WikiPage> findAll();

    List<WikiPage> findByCategory(@Param("category") String category);

    List<WikiPage> findBySoftware(@Param("software") String software);

    List<WikiPage> findByStatus(@Param("status") String status);

    List<WikiPage> fulltextSearch(@Param("query") String query, @Param("limit") int limit);

    List<WikiPage> findByTitleContaining(@Param("keyword") String keyword, @Param("limit") int limit);

    int countByStatus(@Param("status") String status);

    int countAll();

    int insert(WikiPage page);

    int update(WikiPage page);

    int deleteById(@Param("id") Long id);

    List<WikiPage> findOrphanPages();

    List<WikiPage> findStalePages(@Param("days") int daysSinceUpdate);

    List<WikiPage> findByIds(@Param("ids") List<Long> ids);

    List<WikiPage> findByCategoryOrSoftware(@Param("category") String category,
                                            @Param("software") String software,
                                            @Param("limit") int limit);
}
