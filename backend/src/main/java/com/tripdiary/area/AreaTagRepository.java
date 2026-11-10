package com.tripdiary.area;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AreaTagRepository extends JpaRepository<AreaTag, Long> {

    List<AreaTag> findAllByOrderByLevelAscSortOrderAscIdAsc();
}
