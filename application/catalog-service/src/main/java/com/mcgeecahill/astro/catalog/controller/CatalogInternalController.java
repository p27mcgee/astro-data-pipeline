package com.mcgeecahill.astro.catalog.controller;

import com.mcgeecahill.astro.catalog.annotation.InternalApi;
import com.mcgeecahill.astro.catalog.entity.AstronomicalObject;
import com.mcgeecahill.astro.catalog.service.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/catalog/internal")
@RequiredArgsConstructor
@Tag(name = "Catalog Management (Internal)", description = "Internal API for catalog ingestion and maintenance")
@InternalApi("Used by image processing pipeline for catalog population and internal operations")
public class CatalogInternalController {

    private final CatalogService catalogService;

    @PostMapping("/objects/bulk")
    @Operation(summary = "Bulk import astronomical objects",
            description = "Imports multiple objects from image processing pipeline. Used by image-processor service.")
    @ApiResponse(responseCode = "201", description = "Objects imported successfully")
    @ApiResponse(responseCode = "400", description = "Invalid object data")
    public ResponseEntity<Map<String, Object>> bulkImportObjects(
            @Valid @RequestBody List<AstronomicalObject> objects) {

        log.info("Bulk importing {} astronomical objects", objects.size());

        int imported = catalogService.bulkImportObjects(objects);

        Map<String, Object> result = new HashMap<>();
        result.put("totalObjects", objects.size());
        result.put("imported", imported);
        result.put("failed", objects.size() - imported);

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/objects/save")
    @Operation(summary = "Save single astronomical object",
            description = "Saves or updates a single astronomical object")
    @ApiResponse(responseCode = "201", description = "Object saved successfully")
    public ResponseEntity<AstronomicalObject> saveObject(
            @Valid @RequestBody AstronomicalObject object) {

        log.info("Saving astronomical object");
        AstronomicalObject saved = catalogService.saveObject(object);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @DeleteMapping("/cleanup/transients")
    @Operation(summary = "Clean up transient objects",
            description = "Removes old cosmic rays and artifacts from catalog")
    @ApiResponse(responseCode = "200", description = "Cleanup completed")
    public ResponseEntity<Map<String, Object>> cleanupTransients(
            @Parameter(description = "Delete objects older than days") @RequestParam(defaultValue = "7") int olderThanDays) {

        log.info("Cleaning up transient objects older than {} days", olderThanDays);

        int deleted = catalogService.cleanupTransientObjects(olderThanDays);

        Map<String, Object> result = new HashMap<>();
        result.put("deleted", deleted);
        result.put("olderThanDays", olderThanDays);

        return ResponseEntity.ok(result);
    }
}