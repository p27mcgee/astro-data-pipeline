package com.mcgeecahill.astro.catalog.controller;

import com.mcgeecahill.astro.catalog.annotation.ExternalApi;
import com.mcgeecahill.astro.catalog.dto.ConeSearchRequest;
import com.mcgeecahill.astro.catalog.dto.ConeSearchResult;
import com.mcgeecahill.astro.catalog.dto.ObjectMatchResult;
import com.mcgeecahill.astro.catalog.entity.AstronomicalObject;
import com.mcgeecahill.astro.catalog.service.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

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
                request.getCenterRa(), request.getCenterDec(), request.getRadiusArcsec());

        ConeSearchResult result = catalogService.performConeSearch(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/cross-match")
    @Operation(summary = "Cross-match coordinates with catalog",
            description = "Matches a list of coordinates against the catalog to identify known objects")
    @ApiResponse(responseCode = "200", description = "Cross-match completed successfully")
    public ResponseEntity<List<ObjectMatchResult>> crossMatch(
            @Valid @RequestBody List<ConeSearchRequest> coordinates) {

        log.info("Cross-match request for {} coordinates", coordinates.size());
        List<ObjectMatchResult> matches = catalogService.crossMatchPositions(coordinates);
        return ResponseEntity.ok(matches);
    }

    @GetMapping("/statistics")
    @Operation(summary = "Get catalog statistics")
    @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully")
    public ResponseEntity<Map<String, Object>> getCatalogStatistics() {
        Map<String, Object> statistics = catalogService.getCatalogStatistics();
        return ResponseEntity.ok(statistics);
    }

    @GetMapping("/high-proper-motion")
    @Operation(summary = "Find high proper motion objects")
    @ApiResponse(responseCode = "200", description = "High proper motion objects retrieved")
    public ResponseEntity<List<AstronomicalObject>> getHighProperMotionObjects(
            @Parameter(description = "Minimum proper motion (mas/year)") @RequestParam(defaultValue = "100.0") double minProperMotion) {

        List<AstronomicalObject> objects = catalogService.findHighProperMotionObjects(minProperMotion);
        return ResponseEntity.ok(objects);
    }

    @GetMapping("/nearby")
    @Operation(summary = "Find nearby objects within specified distance")
    @ApiResponse(responseCode = "200", description = "Nearby objects retrieved")
    public ResponseEntity<List<AstronomicalObject>> getNearbyObjects(
            @Parameter(description = "Maximum distance (parsecs)") @RequestParam(defaultValue = "10.0") double maxDistance) {

        List<AstronomicalObject> objects = catalogService.findNearbyObjects(maxDistance);
        return ResponseEntity.ok(objects);
    }

    @GetMapping("/follow-up")
    @Operation(summary = "Find objects needing follow-up observations")
    @ApiResponse(responseCode = "200", description = "Objects needing follow-up retrieved")
    public ResponseEntity<List<AstronomicalObject>> getObjectsNeedingFollowUp(
            @Parameter(description = "Maximum days since last observation") @RequestParam(defaultValue = "30") int maxDays) {

        List<AstronomicalObject> objects = catalogService.findObjectsNeedingFollowUp(maxDays);
        return ResponseEntity.ok(objects);
    }
}