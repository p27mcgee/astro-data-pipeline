package org.stsci.astro.catalog.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.stsci.astro.catalog.dto.ConeSearchRequest;
import org.stsci.astro.catalog.dto.ConeSearchResult;
import org.stsci.astro.catalog.dto.ObjectMatchResult;
import org.stsci.astro.catalog.entity.AstronomicalObject;
import org.stsci.astro.catalog.repository.AstronomicalObjectRepository;
import org.stsci.astro.catalog.util.AstronomicalCalculations;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogService {

    private final AstronomicalObjectRepository objectRepository;
    private final AstronomicalCalculations astroCalculations;

    // Constants for astronomical calculations
    private static final double ARCSEC_TO_DEGREES = 1.0 / 3600.0;
    private static final double DEGREES_TO_METERS = 111319.9; // Approximate meters per degree at equator
    private static final double DEFAULT_MATCH_RADIUS_ARCSEC = 1.0;

    /**
     * Perform a cone search around specified coordinates
     */
    public ConeSearchResult performConeSearch(ConeSearchRequest request) {
        log.debug("Performing cone search: RA={}, Dec={}, radius={}\"", 
                request.getCenterRa(), request.getCenterDec(), request.getRadiusArcsec());

        // Convert arcseconds to meters for PostGIS query
        double radiusMeters = request.getRadiusArcsec() * ARCSEC_TO_DEGREES * DEGREES_TO_METERS;

        List<AstronomicalObject> objects;
        if (request.getPageable() != null) {
            Page<AstronomicalObject> page = objectRepository.findWithinRadius(
                    request.getCenterRa(), 
                    request.getCenterDec(), 
                    radiusMeters, 
                    request.getPageable()
            );
            objects = page.getContent();
        } else {
            objects = objectRepository.findWithinRadius(
                    request.getCenterRa(), 
                    request.getCenterDec(), 
                    radiusMeters
            );
        }

        // Calculate separations and apply additional filters
        List<ConeSearchResult.ObjectMatch> matches = objects.stream()
                .map(obj -> {
                    double separation = astroCalculations.calculateSeparation(
                            request.getCenterRa(), request.getCenterDec(),
                            obj.getRa(), obj.getDecl()
                    );
                    return ConeSearchResult.ObjectMatch.builder()
                            .object(obj)
                            .separationArcsec(separation)
                            .build();
                })
                .filter(match -> match.getSeparationArcsec() <= request.getRadiusArcsec())
                .filter(match -> request.getObjectTypes() == null ||
                        request.getObjectTypes().contains(match.getObject().getObjectType().name()))
                .filter(match -> request.getMinMagnitude() == null || 
                        (match.getObject().getMagnitude() != null && 
                         match.getObject().getMagnitude() >= request.getMinMagnitude()))
                .filter(match -> request.getMaxMagnitude() == null || 
                        (match.getObject().getMagnitude() != null && 
                         match.getObject().getMagnitude() <= request.getMaxMagnitude()))
                .sorted(Comparator.comparing(ConeSearchResult.ObjectMatch::getSeparationArcsec))
                .limit(request.getMaxResults() != null ? request.getMaxResults() : 1000)
                .collect(Collectors.toList());

        // Extract objects from matches for backward compatibility
        List<AstronomicalObject> resultObjects = matches.stream()
                .map(ConeSearchResult.ObjectMatch::getObject)
                .collect(Collectors.toList());

        return ConeSearchResult.builder()
                .searchRequest(request)
                .objects(resultObjects)
                .matches(matches)
                .totalResults(matches.size())
                .returnedResults(matches.size())
                .hasMoreResults(false)
                .searchTimestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Find the nearest astronomical object to given coordinates
     */
    public Optional<ObjectMatchResult> findNearestObject(double ra, double dec, 
                                                        AstronomicalObject.ObjectType objectType,
                                                        double maxSeparationArcsec) {
        log.debug("Finding nearest {} object to RA={}, Dec={} within {}\"", 
                objectType, ra, dec, maxSeparationArcsec);

        double radiusMeters = maxSeparationArcsec * ARCSEC_TO_DEGREES * DEGREES_TO_METERS;
        
        List<Object[]> results = objectRepository.findNearestObjectsOfType(
                ra, dec, radiusMeters, objectType != null ? objectType.name() : null, 1
        );

        if (results.isEmpty()) {
            return Optional.empty();
        }

        Object[] result = results.get(0);
        AstronomicalObject object = (AstronomicalObject) result[0];
        Double separationArcsec = (Double) result[1];

        return Optional.of(ObjectMatchResult.builder()
                .object(object)
                .separationArcsec(separationArcsec)
                .matchConfidence(calculateMatchConfidence(separationArcsec, maxSeparationArcsec))
                .build());
    }

    /**
     * Cross-match a list of positions against the catalog
     */
    public List<ObjectMatchResult> crossMatchPositions(List<ConeSearchRequest> positions) {
        log.info("Cross-matching {} positions against catalog", positions.size());

        return positions.stream()
                .map(pos -> findNearestObject(
                        pos.getCenterRa(), 
                        pos.getCenterDec(), 
                        null, // Any object type
                        pos.getRadiusArcsec() != null ? pos.getRadiusArcsec() : DEFAULT_MATCH_RADIUS_ARCSEC
                ))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    /**
     * Add or update astronomical object in catalog
     */
    @Transactional
    public AstronomicalObject saveObject(AstronomicalObject object) {
        log.debug("Saving astronomical object: {}", object.getObjectId());

        // Update position geometry if coordinates are provided
        if (object.getRa() != null && object.getDecl() != null) {
            object.setPosition(astroCalculations.createPoint(object.getRa(), object.getDecl()));
        }

        // Calculate distance from parallax if available
        if (object.getParallaxMas() != null && object.getParallaxMas() > 0) {
            object.setDistancePc(1000.0 / object.getParallaxMas()); // Distance in parsecs
        }

        // Set timestamps
        if (object.getId() == null) {
            object.setCreatedAt(LocalDateTime.now());
        }
        object.setUpdatedAt(LocalDateTime.now());

        return objectRepository.save(object);
    }

    /**
     * Bulk import astronomical objects
     */
    @Transactional
    public int bulkImportObjects(List<AstronomicalObject> objects) {
        log.info("Bulk importing {} astronomical objects", objects.size());

        int imported = 0;
        int batchSize = 100;

        for (int i = 0; i < objects.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, objects.size());
            List<AstronomicalObject> batch = objects.subList(i, endIndex);

            for (AstronomicalObject object : batch) {
                try {
                    saveObject(object);
                    imported++;
                } catch (Exception e) {
                    log.warn("Failed to import object {}: {}", object.getObjectId(), e.getMessage());
                }
            }

            // Log progress
            if (i % (batchSize * 10) == 0) {
                log.info("Imported {} of {} objects", i, objects.size());
            }
        }

        log.info("Successfully imported {} of {} objects", imported, objects.size());
        return imported;
    }

    /**
     * Get catalog statistics
     */
    public Map<String, Object> getCatalogStatistics() {
        Map<String, Object> stats = new HashMap<>();

        // Object counts by type
        Map<String, Long> objectCounts = new HashMap<>();
        for (AstronomicalObject.ObjectType type : AstronomicalObject.ObjectType.values()) {
            objectCounts.put(type.name(), objectRepository.countByType(type));
        }
        stats.put("objectCountsByType", objectCounts);

        // Total objects
        stats.put("totalObjects", objectRepository.count());

        // Magnitude statistics by type
        Map<String, Map<String, Double>> magnitudeStats = new HashMap<>();
        for (AstronomicalObject.ObjectType type : AstronomicalObject.ObjectType.values()) {
            Double avgMag = objectRepository.getAverageMagnitudeByType(type);
            Object[] magRange = objectRepository.getMagnitudeRangeByType(type);
            
            if (avgMag != null) {
                Map<String, Double> typeMagStats = new HashMap<>();
                typeMagStats.put("average", avgMag);
                if (magRange != null && magRange.length == 2) {
                    typeMagStats.put("minimum", (Double) magRange[0]);
                    typeMagStats.put("maximum", (Double) magRange[1]);
                }
                magnitudeStats.put(type.name(), typeMagStats);
            }
        }
        stats.put("magnitudeStatistics", magnitudeStats);

        // Recent observations
        LocalDateTime lastWeek = LocalDateTime.now().minusWeeks(1);
        List<AstronomicalObject> recentObservations = objectRepository.findRecentlyObserved(lastWeek);
        stats.put("recentObservationsCount", recentObservations.size());

        // Variable stars
        long variableStarCount = objectRepository.countByType(AstronomicalObject.ObjectType.STAR);
        stats.put("variableStarCount", variableStarCount);

        return stats;
    }

    /**
     * Find objects needing follow-up observations
     */
    public List<AstronomicalObject> findObjectsNeedingFollowUp(int maxDaysOld) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(maxDaysOld);
        return objectRepository.findSingleObservationObjects(cutoff);
    }

    /**
     * Search for high proper motion objects
     */
    public List<AstronomicalObject> findHighProperMotionObjects(double minProperMotionMasPerYear) {
        return objectRepository.findHighProperMotionObjects(minProperMotionMasPerYear);
    }

    /**
     * Find nearby objects within specified distance
     */
    public List<AstronomicalObject> findNearbyObjects(double maxDistanceParsecs) {
        double minParallax = 1000.0 / maxDistanceParsecs; // Convert distance to minimum parallax
        return objectRepository.findNearbyObjects(minParallax);
    }

    /**
     * Get objects in a specific region with magnitude constraints
     */
    public Page<AstronomicalObject> getObjectsInRegion(
            AstronomicalObject.ObjectType objectType,
            double minRa, double maxRa,
            double minDec, double maxDec,
            double minMagnitude, double maxMagnitude,
            Pageable pageable) {
        
        return objectRepository.findByTypeAndMagnitudeInRegion(
                objectType, minMagnitude, maxMagnitude,
                minRa, maxRa, minDec, maxDec, pageable
        );
    }

    /**
     * Calculate match confidence based on separation
     */
    private double calculateMatchConfidence(double separationArcsec, double maxSeparationArcsec) {
        if (separationArcsec >= maxSeparationArcsec) {
            return 0.0;
        }
        
        // Exponential decay function for confidence
        return Math.exp(-2.0 * separationArcsec / maxSeparationArcsec);
    }

    /**
     * Delete old cosmic ray detections and artifacts
     */
    @Transactional
    public int cleanupTransientObjects(int olderThanDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(olderThanDays);
        
        int deletedCosmicRays = 0;
        int deletedArtifacts = 0;

        try {
            objectRepository.deleteByObjectTypeAndLastObservedBefore(
                    AstronomicalObject.ObjectType.COSMIC_RAY, cutoff);
            deletedCosmicRays++;
        } catch (Exception e) {
            log.warn("Error deleting cosmic ray objects: {}", e.getMessage());
        }

        try {
            objectRepository.deleteByObjectTypeAndLastObservedBefore(
                    AstronomicalObject.ObjectType.ARTIFACT, cutoff);
            deletedArtifacts++;
        } catch (Exception e) {
            log.warn("Error deleting artifact objects: {}", e.getMessage());
        }

        int totalDeleted = deletedCosmicRays + deletedArtifacts;
        log.info("Cleaned up {} transient objects older than {} days", totalDeleted, olderThanDays);
        
        return totalDeleted;
    }
}