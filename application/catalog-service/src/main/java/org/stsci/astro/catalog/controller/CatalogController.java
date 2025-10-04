package org.stsci.astro.catalog.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.stsci.astro.catalog.annotation.ExternalApi;
import org.stsci.astro.catalog.dto.ConeSearchRequest;
import org.stsci.astro.catalog.dto.ConeSearchResult;
import org.stsci.astro.catalog.dto.ObjectMatchResult;
import org.stsci.astro.catalog.entity.AstronomicalObject;
import org.stsci.astro.catalog.service.CatalogService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
@Tag(name = "Astronomical Catalog (External)", description = "Public API for querying astronomical object catalogs")
@ExternalApi("Primary interface for astronomical catalog queries and spatial searches")
public class CatalogController {

    private final CatalogService catalogService;

    @PostMapping("/cone-search")
    @Operation(summary = "Perform cone search around celestial coordinates",
            description = "Searches for astronomical objects within a specified radius of given RA/Dec coordinates using PostGIS spatial indexing")
    @ApiResponse(responseCode = "200", description = "Cone search results returned successfully")
    @ApiResponse(responseCode = "400", description = "Invalid search parameters")
    public ResponseEntity<ConeSearchResult> coneSearch(@Valid @RequestBody ConeSearchRequest request) {
        log.info("Cone search request: RA={}, Dec={}, radius={} arcsec",
                request.getRightAscension(), request.getDeclination(), request.getRadiusArcsec());

        ConeSearchResult result = catalogService.performConeSearch(request);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/objects/{objectId}")
    @Operation(summary = "Get astronomical object by ID")
    @ApiResponse(responseCode = "200", description = "Object retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Object not found")
    public ResponseEntity<AstronomicalObject> getObjectById(
            @Parameter(description = "Object ID") @PathVariable Long objectId) {

        Optional<AstronomicalObject> object = catalogService.getObjectById(objectId);
        return object.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/objects")
    @Operation(summary = "List astronomical objects with pagination and filtering")
    @ApiResponse(responseCode = "200", description = "Objects retrieved successfully")
    public ResponseEntity<Page<AstronomicalObject>> listObjects(
            @Parameter(description = "Minimum magnitude") @RequestParam(required = false) Double minMagnitude,
            @Parameter(description = "Maximum magnitude") @RequestParam(required = false) Double maxMagnitude,
            @Parameter(description = "Object type filter") @RequestParam(required = false) String objectType,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<AstronomicalObject> objects = catalogService.listObjects(minMagnitude, maxMagnitude, objectType, pageable);
        return ResponseEntity.ok(objects);
    }

    @PostMapping("/cross-match")
    @Operation(summary = "Cross-match coordinates with catalog",
            description = "Matches a list of coordinates against the catalog to identify known objects")
    @ApiResponse(responseCode = "200", description = "Cross-match completed successfully")
    public ResponseEntity<List<ObjectMatchResult>> crossMatch(
            @Valid @RequestBody List<ConeSearchRequest> coordinates) {

        log.info("Cross-match request for {} coordinates", coordinates.size());
        List<ObjectMatchResult> matches = catalogService.crossMatch(coordinates);
        return ResponseEntity.ok(matches);
    }

    @GetMapping("/statistics")
    @Operation(summary = "Get catalog statistics")
    @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully")
    public ResponseEntity<Map<String, Object>> getCatalogStatistics() {
        Map<String, Object> statistics = catalogService.getCatalogStatistics();
        return ResponseEntity.ok(statistics);
    }

    @GetMapping("/objects/by-name/{objectName}")
    @Operation(summary = "Search objects by name")
    @ApiResponse(responseCode = "200", description = "Objects matching name retrieved")
    public ResponseEntity<List<AstronomicalObject>> searchByName(
            @Parameter(description = "Object name (supports wildcards)") @PathVariable String objectName) {

        List<AstronomicalObject> objects = catalogService.searchByName(objectName);
        return ResponseEntity.ok(objects);
    }

    @GetMapping("/objects/{objectId}/history")
    @Operation(summary = "Get observation history for an object")
    @ApiResponse(responseCode = "200", description = "Observation history retrieved")
    @ApiResponse(responseCode = "404", description = "Object not found")
    public ResponseEntity<Map<String, Object>> getObservationHistory(
            @Parameter(description = "Object ID") @PathVariable Long objectId) {

        Map<String, Object> history = catalogService.getObservationHistory(objectId);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/quality-metrics")
    @Operation(summary = "Get catalog quality metrics")
    @ApiResponse(responseCode = "200", description = "Quality metrics retrieved successfully")
    public ResponseEntity<Map<String, Object>> getQualityMetrics() {
        Map<String, Object> metrics = catalogService.getQualityMetrics();
        return ResponseEntity.ok(metrics);
    }
}