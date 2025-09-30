package org.stsci.astro.processor.service.algorithm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AlgorithmRegistryService
 * Tests algorithm discovery, validation, and research capabilities
 */
@ExtendWith(MockitoExtension.class)
class AlgorithmRegistryServiceTest {

    @InjectMocks
    private AlgorithmRegistryService algorithmRegistryService;

    @BeforeEach
    void setUp() {
        // Manually call PostConstruct method since @InjectMocks doesn't call it
        algorithmRegistryService.initializeRegistry();
    }

    // ========== Algorithm Discovery Tests ==========

    @Test
    void getSupportedAlgorithmTypes_ShouldReturnAllKnownTypes() {
        // When
        Set<String> supportedTypes = algorithmRegistryService.getSupportedAlgorithmTypes();

        // Then
        assertNotNull(supportedTypes);
        assertFalse(supportedTypes.isEmpty(), "Should have supported algorithm types");

        // Verify essential algorithm types are present
        assertTrue(supportedTypes.contains("dark-subtraction"),
                "Should support dark subtraction algorithms");
        assertTrue(supportedTypes.contains("flat-correction"),
                "Should support flat field correction algorithms");
        assertTrue(supportedTypes.contains("cosmic-ray-removal"),
                "Should support cosmic ray removal algorithms");
        assertTrue(supportedTypes.contains("bias-subtraction"),
                "Should support bias subtraction algorithms");
    }

    @Test
    void getAvailableAlgorithms_DarkSubtraction_ShouldReturnAllVariants() {
        // Given
        String algorithmType = "dark-subtraction";

        // When
        List<AlgorithmRegistryService.AlgorithmInfo> algorithms =
                algorithmRegistryService.getAvailableAlgorithms(algorithmType);

        // Then
        assertNotNull(algorithms);
        assertFalse(algorithms.isEmpty(), "Should have dark subtraction algorithms");

        // Verify we have expected algorithms
        assertTrue(algorithms.stream().anyMatch(a -> a.getId().equals("default")),
                "Should have default dark subtraction algorithm");
        assertTrue(algorithms.stream().anyMatch(a -> a.getId().equals("scaled-dark")),
                "Should have scaled dark subtraction algorithm");
        assertTrue(algorithms.stream().anyMatch(a -> a.getId().equals("adaptive-dark")),
                "Should have adaptive dark subtraction algorithm");

        // Verify all algorithms have required properties
        algorithms.forEach(algorithm -> {
            assertNotNull(algorithm.getId(), "Algorithm should have ID");
            assertNotNull(algorithm.getName(), "Algorithm should have name");
            assertNotNull(algorithm.getDescription(), "Algorithm should have description");
            assertNotNull(algorithm.getVersion(), "Algorithm should have version");
            assertNotNull(algorithm.getParameters(), "Algorithm should have parameters");
        });
    }

    @Test
    void getAvailableAlgorithms_CosmicRayRemoval_ShouldIncludeExperimentalAlgorithms() {
        // Given
        String algorithmType = "cosmic-ray-removal";

        // When
        List<AlgorithmRegistryService.AlgorithmInfo> algorithms =
                algorithmRegistryService.getAvailableAlgorithms(algorithmType);

        // Then
        assertNotNull(algorithms);
        assertFalse(algorithms.isEmpty(), "Should have cosmic ray removal algorithms");

        // Verify we have both supported and experimental algorithms
        boolean hasSupported = algorithms.stream().anyMatch(a -> a.isSupported() && !a.isExperimental());
        boolean hasExperimental = algorithms.stream().anyMatch(a -> a.isExperimental());

        assertTrue(hasSupported, "Should have supported cosmic ray algorithms");
        assertTrue(hasExperimental, "Should have experimental cosmic ray algorithms");

        // Verify neural network algorithm is present but experimental
        algorithms.stream()
                .filter(a -> a.getId().equals("neural-cr"))
                .findFirst()
                .ifPresent(neuralAlgo -> {
                    assertTrue(neuralAlgo.isExperimental(), "Neural CR algorithm should be experimental");
                    assertFalse(neuralAlgo.isSupported(), "Neural CR algorithm should not be production-supported");
                });
    }

    // ========== Algorithm Validation Tests ==========

    @Test
    void getSupportedAlgorithms_ShouldReturnOnlyProductionReady() {
        // Given
        String algorithmType = "cosmic-ray-removal";

        // When
        List<AlgorithmRegistryService.AlgorithmInfo> supportedAlgorithms =
                algorithmRegistryService.getSupportedAlgorithms(algorithmType);

        // Then
        assertNotNull(supportedAlgorithms);
        assertFalse(supportedAlgorithms.isEmpty(), "Should have supported algorithms");

        // Verify all returned algorithms are production-supported
        supportedAlgorithms.forEach(algorithm -> {
            assertTrue(algorithm.isSupported(),
                    "All returned algorithms should be production-supported: " + algorithm.getId());
        });

        // Verify we have essential supported algorithms
        assertTrue(supportedAlgorithms.stream().anyMatch(a -> a.getId().equals("lacosmic")),
                "Should have L.A.Cosmic algorithm as supported");
        assertTrue(supportedAlgorithms.stream().anyMatch(a -> a.getId().equals("lacosmic-v2")),
                "Should have enhanced L.A.Cosmic algorithm as supported");
    }

    @Test
    void getExperimentalAlgorithms_ShouldReturnOnlyExperimental() {
        // Given
        String algorithmType = "cosmic-ray-removal";

        // When
        List<AlgorithmRegistryService.AlgorithmInfo> experimentalAlgorithms =
                algorithmRegistryService.getExperimentalAlgorithms(algorithmType);

        // Then
        assertNotNull(experimentalAlgorithms);
        assertFalse(experimentalAlgorithms.isEmpty(), "Should have experimental algorithms");

        // Verify all returned algorithms are experimental
        experimentalAlgorithms.forEach(algorithm -> {
            assertTrue(algorithm.isExperimental(),
                    "All returned algorithms should be experimental: " + algorithm.getId());
        });

        // Verify neural network algorithm is included
        assertTrue(experimentalAlgorithms.stream().anyMatch(a -> a.getId().equals("neural-cr")),
                "Should include neural network cosmic ray algorithm");
    }

    @Test
    void isAlgorithmSupported_SupportedAlgorithm_ShouldReturnTrue() {
        // Given
        String algorithmType = "dark-subtraction";
        String algorithmId = "default";

        // When
        boolean isSupported = algorithmRegistryService.isAlgorithmSupported(algorithmType, algorithmId);

        // Then
        assertTrue(isSupported, "Default dark subtraction algorithm should be supported");
    }

    @Test
    void isAlgorithmSupported_ExperimentalAlgorithm_ShouldReturnFalse() {
        // Given
        String algorithmType = "cosmic-ray-removal";
        String algorithmId = "neural-cr";

        // When
        boolean isSupported = algorithmRegistryService.isAlgorithmSupported(algorithmType, algorithmId);

        // Then
        assertFalse(isSupported, "Neural network algorithm should not be production-supported");
    }

    // ========== Algorithm Information Tests ==========

    @Test
    void getAlgorithmInfo_ValidAlgorithm_ShouldReturnCompleteInfo() {
        // Given
        String algorithmType = "flat-correction";
        String algorithmId = "default";

        // When
        AlgorithmRegistryService.AlgorithmInfo algorithmInfo =
                algorithmRegistryService.getAlgorithmInfo(algorithmType, algorithmId);

        // Then
        assertNotNull(algorithmInfo, "Should return algorithm information");
        assertEquals(algorithmId, algorithmInfo.getId());
        assertEquals("Standard Flat Field Correction", algorithmInfo.getName());
        assertNotNull(algorithmInfo.getDescription());
        assertEquals("1.0", algorithmInfo.getVersion());
        assertTrue(algorithmInfo.isSupported());
        assertFalse(algorithmInfo.isExperimental());

        // Verify parameters are present
        Map<String, String> parameters = algorithmInfo.getParameters();
        assertNotNull(parameters);
        assertTrue(parameters.containsKey("normalizationMethod"));
        assertTrue(parameters.containsKey("outlierRejection"));
        assertTrue(parameters.containsKey("rejectionSigma"));
    }

    @Test
    void getAlgorithmInfo_NonExistentAlgorithm_ShouldReturnNull() {
        // Given
        String algorithmType = "dark-subtraction";
        String algorithmId = "non-existent-algorithm";

        // When
        AlgorithmRegistryService.AlgorithmInfo algorithmInfo =
                algorithmRegistryService.getAlgorithmInfo(algorithmType, algorithmId);

        // Then
        assertNull(algorithmInfo, "Should return null for non-existent algorithm");
    }

    // ========== Algorithm Type Normalization Tests ==========

    @Test
    void getAvailableAlgorithms_WithVariousTypeFormats_ShouldNormalizeCorrectly() {
        // Test various input formats that should normalize to the same type
        String[] darkSubtractionVariants = {
                "dark-subtraction", "dark", "dark_subtraction", "darksubtraction"
        };

        List<AlgorithmRegistryService.AlgorithmInfo> expectedAlgorithms =
                algorithmRegistryService.getAvailableAlgorithms("dark-subtraction");

        for (String variant : darkSubtractionVariants) {
            // When
            List<AlgorithmRegistryService.AlgorithmInfo> algorithms =
                    algorithmRegistryService.getAvailableAlgorithms(variant);

            // Then
            assertEquals(expectedAlgorithms.size(), algorithms.size(),
                    "Variant '" + variant + "' should return same algorithms as canonical form");

            // Verify same algorithm IDs are returned
            Set<String> expectedIds = expectedAlgorithms.stream()
                    .map(AlgorithmRegistryService.AlgorithmInfo::getId)
                    .collect(java.util.stream.Collectors.toSet());
            Set<String> actualIds = algorithms.stream()
                    .map(AlgorithmRegistryService.AlgorithmInfo::getId)
                    .collect(java.util.stream.Collectors.toSet());

            assertEquals(expectedIds, actualIds,
                    "Variant '" + variant + "' should return same algorithm IDs");
        }
    }

    @Test
    void getAvailableAlgorithms_InvalidType_ShouldReturnEmptyList() {
        // Given
        String invalidType = "non-existent-algorithm-type";

        // When
        List<AlgorithmRegistryService.AlgorithmInfo> algorithms =
                algorithmRegistryService.getAvailableAlgorithms(invalidType);

        // Then
        assertNotNull(algorithms);
        assertTrue(algorithms.isEmpty(), "Should return empty list for invalid algorithm type");
    }

    // ========== Algorithm Parameters Tests ==========

    @Test
    void getAlgorithmInfo_LACosmicEnhanced_ShouldHaveExtendedParameters() {
        // Given
        String algorithmType = "cosmic-ray-removal";
        String algorithmId = "lacosmic-v2";

        // When
        AlgorithmRegistryService.AlgorithmInfo algorithmInfo =
                algorithmRegistryService.getAlgorithmInfo(algorithmType, algorithmId);

        // Then
        assertNotNull(algorithmInfo);
        Map<String, String> parameters = algorithmInfo.getParameters();

        // Verify enhanced parameters are present
        assertTrue(parameters.containsKey("starPreservation"),
                "Enhanced L.A.Cosmic should have star preservation parameter");
        assertTrue(parameters.containsKey("edgeHandling"),
                "Enhanced L.A.Cosmic should have edge handling parameter");

        // Verify standard L.A.Cosmic parameters are also present
        assertTrue(parameters.containsKey("sigclip"));
        assertTrue(parameters.containsKey("sigfrac"));
        assertTrue(parameters.containsKey("objlim"));
    }

    @Test
    void getAlgorithmInfo_AdaptiveDark_ShouldHaveSpecializedParameters() {
        // Given
        String algorithmType = "dark-subtraction";
        String algorithmId = "adaptive-dark";

        // When
        AlgorithmRegistryService.AlgorithmInfo algorithmInfo =
                algorithmRegistryService.getAlgorithmInfo(algorithmType, algorithmId);

        // Then
        assertNotNull(algorithmInfo);
        assertTrue(algorithmInfo.isExperimental(), "Adaptive dark should be experimental");

        Map<String, String> parameters = algorithmInfo.getParameters();
        assertTrue(parameters.containsKey("windowSize"),
                "Adaptive dark should have window size parameter");
        assertTrue(parameters.containsKey("adaptiveThreshold"),
                "Adaptive dark should have adaptive threshold parameter");
        assertTrue(parameters.containsKey("preserveStars"),
                "Adaptive dark should have preserve stars parameter");
    }

    // ========== Algorithm Summary Tests ==========

    @Test
    void getAlgorithmSummary_ShouldProvideCompleteOverview() {
        // When
        Map<String, Object> summary = algorithmRegistryService.getAlgorithmSummary();

        // Then
        assertNotNull(summary);
        assertFalse(summary.isEmpty(), "Summary should not be empty");

        // Verify each algorithm type has proper summary structure
        Set<String> expectedTypes = Set.of("dark-subtraction", "flat-correction",
                "cosmic-ray-removal", "bias-subtraction");

        for (String type : expectedTypes) {
            assertTrue(summary.containsKey(type),
                    "Summary should contain type: " + type);

            @SuppressWarnings("unchecked")
            Map<String, Object> typeSummary = (Map<String, Object>) summary.get(type);

            assertTrue(typeSummary.containsKey("total"),
                    "Type summary should contain total count");
            assertTrue(typeSummary.containsKey("supported"),
                    "Type summary should contain supported count");
            assertTrue(typeSummary.containsKey("experimental"),
                    "Type summary should contain experimental count");
            assertTrue(typeSummary.containsKey("algorithms"),
                    "Type summary should contain algorithm list");

            // Verify counts are reasonable
            Integer total = (Integer) typeSummary.get("total");
            Integer supported = (Integer) typeSummary.get("supported");
            Integer experimental = (Integer) typeSummary.get("experimental");

            assertTrue(total > 0, "Should have algorithms for type: " + type);
            assertTrue(supported >= 0, "Supported count should be non-negative");
            assertTrue(experimental >= 0, "Experimental count should be non-negative");
        }
    }

    // ========== Research Workflow Support Tests ==========

    @Test
    void getAvailableAlgorithms_ShouldSupportResearchWorkflows() {
        // Test that we have sufficient algorithms for research comparison

        String[] algorithmTypes = {"dark-subtraction", "flat-correction", "cosmic-ray-removal"};

        for (String type : algorithmTypes) {
            // When
            List<AlgorithmRegistryService.AlgorithmInfo> algorithms =
                    algorithmRegistryService.getAvailableAlgorithms(type);

            // Then
            assertTrue(algorithms.size() >= 2,
                    "Should have at least 2 algorithms for research comparison in type: " + type);

            // Verify we have both stable and experimental options
            boolean hasStable = algorithms.stream().anyMatch(a -> a.isSupported() && !a.isExperimental());
            assertTrue(hasStable,
                    "Should have at least one stable algorithm for baseline comparison in type: " + type);
        }
    }

    @Test
    void getExperimentalAlgorithms_ShouldEnableResearchInnovation() {
        // Verify that experimental algorithms are properly marked for research use

        // When
        List<AlgorithmRegistryService.AlgorithmInfo> experimentalCR =
                algorithmRegistryService.getExperimentalAlgorithms("cosmic-ray-removal");

        // Then
        assertFalse(experimentalCR.isEmpty(), "Should have experimental cosmic ray algorithms");

        // Verify experimental algorithms have research-appropriate metadata
        experimentalCR.forEach(algorithm -> {
            assertTrue(algorithm.isExperimental(), "Should be marked as experimental");
            assertNotNull(algorithm.getDescription(), "Should have description for researchers");
            assertNotNull(algorithm.getParameters(), "Should have configurable parameters");

            // Experimental algorithms should have version information
            assertNotNull(algorithm.getVersion(), "Should have version for tracking");
        });
    }

    // ========== Error Handling and Edge Cases ==========

    @Test
    void getAvailableAlgorithms_NullType_ShouldReturnEmptyList() {
        // When
        List<AlgorithmRegistryService.AlgorithmInfo> algorithms =
                algorithmRegistryService.getAvailableAlgorithms(null);

        // Then
        assertNotNull(algorithms);
        assertTrue(algorithms.isEmpty(), "Should return empty list for null type");
    }

    @Test
    void getAvailableAlgorithms_EmptyType_ShouldReturnEmptyList() {
        // When
        List<AlgorithmRegistryService.AlgorithmInfo> algorithms =
                algorithmRegistryService.getAvailableAlgorithms("");

        // Then
        assertNotNull(algorithms);
        assertTrue(algorithms.isEmpty(), "Should return empty list for empty type");
    }

    @Test
    void isAlgorithmSupported_NullParameters_ShouldReturnFalse() {
        // When
        boolean result1 = algorithmRegistryService.isAlgorithmSupported(null, "default");
        boolean result2 = algorithmRegistryService.isAlgorithmSupported("dark-subtraction", null);
        boolean result3 = algorithmRegistryService.isAlgorithmSupported(null, null);

        // Then
        assertFalse(result1, "Should return false for null algorithm type");
        assertFalse(result2, "Should return false for null algorithm ID");
        assertFalse(result3, "Should return false for both null parameters");
    }
}