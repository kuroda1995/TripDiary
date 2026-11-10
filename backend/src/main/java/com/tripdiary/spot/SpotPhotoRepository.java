package com.tripdiary.spot;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpotPhotoRepository extends JpaRepository<SpotPhoto, Long> {

    boolean existsByS3Key(String s3Key);

    /** 指定したキーのうち、いずれかのスポットで使われているもの（孤立ファイルの判定に使う）。 */
    @Query("select p.s3Key from SpotPhoto p where p.s3Key in :keys")
    List<String> findUsedKeys(@Param("keys") Collection<String> keys);
}
