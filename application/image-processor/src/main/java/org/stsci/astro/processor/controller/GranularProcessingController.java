package org.stsci.astro.processor.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.stsci.astro.processor.dto.CustomWorkflowRequest;
import org.stsci.astro.processor.dto.CustomWorkflowResponse;
import org.stsci.astro.processor.dto.GranularProcessingRequest;
import org.stsci.astro.processor.dto.GranularProcessingResponse;
import org.stsci.astro.processor.service.GranularProcessingService;

import jakarta.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/v1/processing")
@RequiredArgsConstructor
@Tag(name = "Granular Processing", description = "Individual processing step operations for research workflows")
public class GranularProcessingController {

    private final GranularProcessingService granularProcessingService;

    @PostMapping("/steps/dark-subtract")
    @Operation(
            summary = "Apply dark subtraction to FITS image",
            description = "Performs dark current subtraction using specified dark frame. Result stored in intermediate bucket for chaining."
    )
    @ApiResponse(responseCode = "200", description = "Dark subtraction completed successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request parameters")
    @ApiResponse(responseCode = "500", description = "Processing error")
    public ResponseEntity<GranularProcessingResponse> applyDarkSubtraction(
            @Valid @RequestBody GranularProcessingRequest request) {
        log.info("Processing dark subtraction request for image: {}", request.getImagePath());

        GranularProcessingResponse response = granularProcessingService.applyDarkSubtraction(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/steps/flat-correct")
    @Operation(
            summary = "Apply flat field correction to FITS image",
            description = "Performs flat field correction using specified flat frame. Result stored in intermediate bucket for chaining."
    )
    @ApiResponse(responseCode = "200", description = "Flat field correction completed successfully")
    public ResponseEntity<GranularProcessingResponse> applyFlatFieldCorrection(
            @Valid @RequestBody GranularProcessingRequest request) {
        log.info("Processing flat field correction request for image: {}", request.getImagePath());

        GranularProcessingResponse response = granularProcessingService.applyFlatFieldCorrection(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/steps/cosmic-ray-remove")
    @Operation(
            summary = "Remove cosmic rays from FITS image",
            description = "Applies L.A.Cosmic algorithm for cosmic ray detection and removal. Result stored in intermediate bucket for chaining."
    )
    @ApiResponse(responseCode = "200", description = "Cosmic ray removal completed successfully")
    public ResponseEntity<GranularProcessingResponse> removeCosmicRays(
            @Valid @RequestBody GranularProcessingRequest request) {
        log.info("Processing cosmic ray removal request for image: {}", request.getImagePath());

        GranularProcessingResponse response = granularProcessingService.removeCosmicRays(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/steps/bias-subtract")
    @Operation(
            summary = "Apply bias subtraction to FITS image",
            description = "Performs bias frame subtraction. Result stored in intermediate bucket for chaining."
    )
    @ApiResponse(responseCode = "200", description = "Bias subtraction completed successfully")
    public ResponseEntity<GranularProcessingResponse> applyBiasSubtraction(
            @Valid @RequestBody GranularProcessingRequest request) {
        log.info("Processing bias subtraction request for image: {}", request.getImagePath());

        GranularProcessingResponse response = granularProcessingService.applyBiasSubtraction(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/workflows/custom")
    @Operation(
            summary = "Execute custom processing workflow",
            description = "Executes a custom sequence of processing steps with specified algorithms and parameters"
    )
    @ApiResponse(responseCode = "200", description = "Custom workflow completed successfully")
    public ResponseEntity<CustomWorkflowResponse> executeCustomWorkflow(
            @Valid @RequestBody CustomWorkflowRequest request) {
        log.info("Processing custom workflow request with {} steps", request.getSteps().size());

        CustomWorkflowResponse response = granularProcessingService.executeCustomWorkflow(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/algorithms/{algorithmType}")
    @Operation(
            summary = "List available algorithms for a processing step",
            description = "Returns available algorithm implementations for experimentation"
    )
    @ApiResponse(responseCode = "200", description = "Algorithm list retrieved successfully")
    public ResponseEntity<?> getAvailableAlgorithms(
            @Parameter(description = "Type of processing step")
            @PathVariable String algorithmType) {
        log.info("Retrieving available algorithms for type: {}", algorithmType);

        return ResponseEntity.ok(granularProcessingService.getAvailableAlgorithms(algorithmType));
    }

    @GetMapping("/intermediate/{sessionId}/results")
    @Operation(
            summary = "List intermediate results for a processing session",
            description = "Returns list of intermediate files created during granular processing session"
    )
    @ApiResponse(responseCode = "200", description = "Intermediate results listed successfully")
    public ResponseEntity<?> getIntermediateResults(
            @Parameter(description = "Processing session ID")
            @PathVariable String sessionId) {
        log.info("Retrieving intermediate results for session: {}", sessionId);

        return ResponseEntity.ok(granularProcessingService.getIntermediateResults(sessionId));
    }
}