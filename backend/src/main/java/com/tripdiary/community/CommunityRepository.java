package com.tripdiary.community;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** コミュニティの取得（オーナーは fetch join で N+1 を避ける）。検索は {@link CommunitySearchRepository}。 */
public interface CommunityRepository extends JpaRepository<Community, Long> {

    @Query("select c from Community c join fetch c.owner where c.id = :id")
    Optional<Community> findWithOwnerById(@Param("id") long id);

    @Query("select c from Community c join fetch c.owner where c.id in :ids")
    List<Community> findWithOwnerByIdIn(@Param("ids") Collection<Long> ids);

    /** 同じ名前（英字の大文字・小文字は区別しない。uq_communities_name_lower と同じ規則）があるか。 */
    @Query("select count(c) > 0 from Community c where lower(c.name) = lower(:name)")
    boolean existsByNameIgnoreCase(@Param("name") String name);
}
