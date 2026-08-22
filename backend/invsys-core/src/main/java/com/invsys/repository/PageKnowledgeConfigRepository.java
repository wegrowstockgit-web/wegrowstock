package com.invsys.repository;

import com.invsys.domain.PageKnowledgeConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PageKnowledgeConfigRepository extends JpaRepository<PageKnowledgeConfig, UUID> {

    Optional<PageKnowledgeConfig> findByRoutePattern(String routePattern);

    List<PageKnowledgeConfig> findAllByOrderByRoutePatternAsc();

    @Query("""
            SELECT p FROM PageKnowledgeConfig p
            WHERE (:category = '' OR lower(p.category) = lower(:category))
              AND (:q = ''
                   OR lower(p.routePattern) LIKE lower(concat('%', :q, '%'))
                   OR lower(p.title) LIKE lower(concat('%', :q, '%'))
                   OR lower(p.category) LIKE lower(concat('%', :q, '%')))
            ORDER BY p.routePattern ASC
            """)
    List<PageKnowledgeConfig> search(@Param("q") String q, @Param("category") String category);
}
