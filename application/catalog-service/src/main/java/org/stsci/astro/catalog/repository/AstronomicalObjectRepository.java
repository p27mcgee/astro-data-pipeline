package org.stsci.astro.catalog.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.stsci.astro.catalog.entity.AstronomicalObject;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AstronomicalObjectRepository extends JpaRepository<AstronomicalObject, Long> {

    // Basic finder methods
    Optional<AstronomicalObject> findByObjectId(String objectId);
    
    List<AstronomicalObject> findByObjectType(AstronomicalObject.ObjectType objectType);
    
    List<AstronomicalObject> findByObjectTypeAndMagnitudeIsNotNull(AstronomicalObject.ObjectType objectType);

    // Cone search - find objects within a circular area
    @Query(value = """
        SELECT ao.* FROM astronomical_objects ao 
        WHERE ST_DWithin(
            ao.position::geography,
            ST_Point(:centerRa, :centerDec)::geography,
            :radiusMeters
        )
        ORDER BY ST_Distance(
            ao.position::geography,
            ST_Point(:centerRa, :centerDec)::geography
        )
        """, nativeQuery = true)
    List<AstronomicalObject> findWithinRadius(
        @Param("centerRa") double centerRa,
        @Param("centerDec") double centerDec,
        @Param("radiusMeters") double radiusMeters
    );

    // Cone search with pagination
    @Query(value = """
        SELECT ao.* FROM astronomical_objects ao 
        WHERE ST_DWithin(
            ao.position::geography,
            ST_Point(:centerRa, :centerDec)::geography,
            :radiusMeters
        )
        ORDER BY ST_Distance(
            ao.position::geography,
            ST_Point(:centerRa, :centerDec)::geography
        )
        """, 
        countQuery = """
        SELECT COUNT(*) FROM astronomical_objects ao 
        WHERE ST_DWithin(
            ao.position::geography,
            ST_Point(:centerRa, :centerDec)::geography,
            :radiusMeters
        )
        """,
        nativeQuery = true)
    Page<AstronomicalObject> findWithinRadius(
        @Param("centerRa") double centerRa,
        @Param("centerDec") double centerDec,
        @Param("radiusMeters") double radiusMeters,
        Pageable pageable
    );

    // Box search - find objects within rectangular area
    @Query("SELECT ao FROM AstronomicalObject ao " +
           "WHERE ao.ra BETWEEN :minRa AND :maxRa " +
           "AND ao.decl BETWEEN :minDecl AND :maxDecl")
    List<AstronomicalObject> findInBox(
        @Param("minRa") double minRa,
        @Param("maxRa") double maxRa,
        @Param("minDecl") double minDecl,
        @Param("maxDecl") double maxDecl
    );

    // Magnitude-based queries
    @Query("SELECT ao FROM AstronomicalObject ao " +
           "WHERE ao.magnitude BETWEEN :minMag AND :maxMag " +
           "AND ao.magnitude IS NOT NULL " +
           "ORDER BY ao.magnitude")
    List<AstronomicalObject> findByMagnitudeRange(
        @Param("minMag") double minMag,
        @Param("maxMag") double maxMag
    );

    @Query("SELECT ao FROM AstronomicalObject ao " +
           "WHERE ao.magnitude <= :limitMag " +
           "AND ao.magnitude IS NOT NULL " +
           "AND ao.objectType = :objectType " +
           "ORDER BY ao.magnitude")
    List<AstronomicalObject> findBrighterThan(
        @Param("limitMag") double limitMag,
        @Param("objectType") AstronomicalObject.ObjectType objectType
    );

    // Cross-match queries
    @Query("SELECT ao FROM AstronomicalObject ao " +
           "WHERE ao.gaiaSourceId = :gaiaId")
    Optional<AstronomicalObject> findByGaiaSourceId(@Param("gaiaId") Long gaiaId);

    @Query("SELECT ao FROM AstronomicalObject ao " +
           "WHERE ao.simbadName = :simbadName")
    Optional<AstronomicalObject> findBySimbadName(@Param("simbadName") String simbadName);

    // Variability queries
    @Query("SELECT ao FROM AstronomicalObject ao " +
           "WHERE ao.isVariable = true " +
           "AND ao.variabilityPeriodDays BETWEEN :minPeriod AND :maxPeriod")
    List<AstronomicalObject> findVariableStarsInPeriodRange(
        @Param("minPeriod") double minPeriod,
        @Param("maxPeriod") double maxPeriod
    );

    // Time-based queries
    @Query("SELECT ao FROM AstronomicalObject ao " +
           "WHERE ao.firstObserved >= :startDate " +
           "AND ao.firstObserved <= :endDate")
    List<AstronomicalObject> findFirstObservedBetween(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT ao FROM AstronomicalObject ao " +
           "WHERE ao.lastObserved >= :startDate " +
           "AND ao.lastObserved <= :endDate")
    List<AstronomicalObject> findLastObservedBetween(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    // Quality-based queries
    @Query("SELECT ao FROM AstronomicalObject ao " +
           "WHERE ao.detectionSignificance >= :minSigma " +
           "AND ao.detectionSignificance IS NOT NULL")
    List<AstronomicalObject> findHighQualityDetections(@Param("minSigma") double minSigma);

    // Complex queries combining multiple criteria
    @Query("SELECT ao FROM AstronomicalObject ao " +
           "WHERE ao.objectType = :objectType " +
           "AND ao.magnitude BETWEEN :minMag AND :maxMag " +
           "AND ao.ra BETWEEN :minRa AND :maxRa " +
           "AND ao.decl BETWEEN :minDecl AND :maxDecl " +
           "ORDER BY ao.magnitude")
    Page<AstronomicalObject> findByTypeAndMagnitudeInRegion(
        @Param("objectType") AstronomicalObject.ObjectType objectType,
        @Param("minMag") double minMag,
        @Param("maxMag") double maxMag,
        @Param("minRa") double minRa,
        @Param("maxRa") double maxRa,
        @Param("minDecl") double minDecl,
        @Param("maxDecl") double maxDecl,
        Pageable pageable
    );

    // Statistical queries
    @Query("SELECT COUNT(ao) FROM AstronomicalObject ao WHERE ao.objectType = :objectType")
    long countByType(@Param("objectType") AstronomicalObject.ObjectType objectType);

    @Query("SELECT AVG(ao.magnitude) FROM AstronomicalObject ao " +
           "WHERE ao.objectType = :objectType AND ao.magnitude IS NOT NULL")
    Double getAverageMagnitudeByType(@Param("objectType") AstronomicalObject.ObjectType objectType);

    @Query("SELECT MIN(ao.magnitude), MAX(ao.magnitude) FROM AstronomicalObject ao " +
           "WHERE ao.objectType = :objectType AND ao.magnitude IS NOT NULL")
    Object[] getMagnitudeRangeByType(@Param("objectType") AstronomicalObject.ObjectType objectType);

    // Proper motion queries
    @Query("SELECT ao FROM AstronomicalObject ao " +
           "WHERE SQRT(ao.pmRa * ao.pmRa + ao.pmDecl * ao.pmDecl) >= :minProperMotion")
    List<AstronomicalObject> findHighProperMotionObjects(@Param("minProperMotion") double minProperMotion);

    // Parallax and distance queries
    @Query("SELECT ao FROM AstronomicalObject ao " +
           "WHERE ao.parallaxMas >= :minParallax " +
           "AND ao.parallaxMas IS NOT NULL " +
           "ORDER BY ao.parallaxMas DESC")
    List<AstronomicalObject> findNearbyObjects(@Param("minParallax") double minParallax);

    @Query("SELECT ao FROM AstronomicalObject ao " +
           "WHERE ao.distancePc BETWEEN :minDistance AND :maxDistance " +
           "AND ao.distancePc IS NOT NULL " +
           "ORDER BY ao.distancePc")
    List<AstronomicalObject> findInDistanceRange(
        @Param("minDistance") double minDistance,
        @Param("maxDistance") double maxDistance
    );

    // Photometric system queries
    @Query("SELECT ao FROM AstronomicalObject ao " +
           "WHERE ao.photometricSystem = :system " +
           "AND ao.magnitude IS NOT NULL")
    List<AstronomicalObject> findByPhotometricSystem(
        @Param("system") AstronomicalObject.PhotometricSystem system
    );

    // Recent observations
    @Query("SELECT ao FROM AstronomicalObject ao " +
           "WHERE ao.lastObserved >= :since " +
           "ORDER BY ao.lastObserved DESC")
    List<AstronomicalObject> findRecentlyObserved(@Param("since") LocalDateTime since);

    // Objects needing follow-up
    @Query("SELECT ao FROM AstronomicalObject ao " +
           "WHERE ao.observationCount = 1 " +
           "AND ao.firstObserved >= :since")
    List<AstronomicalObject> findSingleObservationObjects(@Param("since") LocalDateTime since);

    // Custom native query for advanced spatial operations
    @Query(value = """
        SELECT ao.*, 
               ST_Distance(ao.position::geography, ST_Point(:targetRa, :targetDec)::geography) / 1000.0 * 206265 as separation_arcsec
        FROM astronomical_objects ao 
        WHERE ST_DWithin(
            ao.position::geography,
            ST_Point(:targetRa, :targetDec)::geography,
            :searchRadiusMeters
        )
        AND ao.object_type = :objectType
        ORDER BY separation_arcsec
        LIMIT :maxResults
        """, nativeQuery = true)
    List<Object[]> findNearestObjectsOfType(
        @Param("targetRa") double targetRa,
        @Param("targetDec") double targetDec,
        @Param("searchRadiusMeters") double searchRadiusMeters,
        @Param("objectType") String objectType,
        @Param("maxResults") int maxResults
    );

    // Delete operations
    void deleteByObjectId(String objectId);
    
    void deleteByObjectTypeAndLastObservedBefore(
        AstronomicalObject.ObjectType objectType,
        LocalDateTime cutoffDate
    );
}