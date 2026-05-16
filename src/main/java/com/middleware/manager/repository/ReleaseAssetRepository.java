package com.middleware.manager.repository;

import com.middleware.manager.domain.ReleaseAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ReleaseAssetRepository extends JpaRepository<ReleaseAsset, Long>, JpaSpecificationExecutor<ReleaseAsset> {

    Optional<ReleaseAsset> findByDownloadTokenAndPublishedTrue(String downloadToken);

    boolean existsByMiddlewareNameIgnoreCaseAndVersionIgnoreCaseAndOriginalFileNameIgnoreCase(
            String middlewareName,
            String version,
            String originalFileName
    );

    boolean existsByStoredFileNameIgnoreCase(String storedFileName);

    boolean existsBySoftwareTypeId(Long softwareTypeId);
}
