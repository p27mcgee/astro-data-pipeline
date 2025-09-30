package org.stsci.astro.processor.service.algorithm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AlgorithmRegistryService {

    private final Map<String, List<AlgorithmInfo>> algorithmRegistry = new HashMap<>();

    @PostConstruct
    public void initializeRegistry() {
        log.info("Initializing algorithm registry...");

        // Dark Subtraction Algorithms
        List<AlgorithmInfo> darkSubtractionAlgorithms = Arrays.asList(
                AlgorithmInfo.builder()
                        .id("default")
                        .name("Standard Dark Subtraction")
                        .description("Basic pixel-wise dark current subtraction using master dark frame")
                        .version("1.0")
                        .parameters(Map.of(
                                "scaleFactor", "Scaling factor for dark frame (default: 1.0)",
                                "useMedianScaling", "Use median-based scaling instead of mean (default: false)"
                        ))
                        .supported(true)
                        .experimental(false)
                        .build(),

                AlgorithmInfo.builder()
                        .id("scaled-dark")
                        .name("Exposure-Scaled Dark Subtraction")
                        .description("Dark subtraction with automatic exposure time scaling")
                        .version("1.1")
                        .parameters(Map.of(
                                "autoScale", "Automatically scale based on exposure time (default: true)",
                                "temperatureCorrection", "Apply temperature-based correction (default: false)",
                                "minScaleFactor", "Minimum allowed scale factor (default: 0.1)",
                                "maxScaleFactor", "Maximum allowed scale factor (default: 10.0)"
                        ))
                        .supported(true)
                        .experimental(false)
                        .build(),

                AlgorithmInfo.builder()
                        .id("adaptive-dark")
                        .name("Adaptive Dark Subtraction")
                        .description("Adaptive dark subtraction with local variance estimation")
                        .version("2.0")
                        .parameters(Map.of(
                                "windowSize", "Local estimation window size (default: 64)",
                                "adaptiveThreshold", "Adaptation threshold (default: 3.0)",
                                "preserveStars", "Preserve stellar sources during adaptation (default: true)"
                        ))
                        .supported(true)
                        .experimental(true)
                        .build()
        );
        algorithmRegistry.put("dark-subtraction", darkSubtractionAlgorithms);

        // Flat Field Correction Algorithms
        List<AlgorithmInfo> flatCorrectionAlgorithms = Arrays.asList(
                AlgorithmInfo.builder()
                        .id("default")
                        .name("Standard Flat Field Correction")
                        .description("Traditional flat field correction using master flat frame")
                        .version("1.0")
                        .parameters(Map.of(
                                "normalizationMethod", "Normalization method: mean, median, mode (default: median)",
                                "outlierRejection", "Enable outlier rejection (default: true)",
                                "rejectionSigma", "Outlier rejection sigma threshold (default: 3.0)"
                        ))
                        .supported(true)
                        .experimental(false)
                        .build(),

                AlgorithmInfo.builder()
                        .id("illumination-corrected")
                        .name("Illumination-Corrected Flat Field")
                        .description("Flat field correction with illumination pattern removal")
                        .version("1.2")
                        .parameters(Map.of(
                                "illuminationModel", "Illumination model: polynomial, spline (default: polynomial)",
                                "polynomialDegree", "Polynomial degree for illumination model (default: 3)",
                                "maskStars", "Mask stars during illumination fitting (default: true)",
                                "starMaskRadius", "Star masking radius in pixels (default: 5)"
                        ))
                        .supported(true)
                        .experimental(false)
                        .build()
        );
        algorithmRegistry.put("flat-correction", flatCorrectionAlgorithms);

        // Cosmic Ray Removal Algorithms
        List<AlgorithmInfo> cosmicRayAlgorithms = Arrays.asList(
                AlgorithmInfo.builder()
                        .id("lacosmic")
                        .name("L.A.Cosmic")
                        .description("Laplacian cosmic ray detection algorithm (van Dokkum 2001)")
                        .version("1.0")
                        .parameters(Map.of(
                                "sigclip", "Sigma clipping threshold (default: 4.5)",
                                "sigfrac", "Fractional detection limit (default: 0.3)",
                                "objlim", "Object detection limit (default: 5.0)",
                                "gain", "CCD gain in e-/ADU (default: 1.0)",
                                "readnoise", "Read noise in e- (default: 6.5)",
                                "niter", "Maximum number of iterations (default: 4)"
                        ))
                        .supported(true)
                        .experimental(false)
                        .build(),

                AlgorithmInfo.builder()
                        .id("lacosmic-v2")
                        .name("L.A.Cosmic Enhanced")
                        .description("Enhanced L.A.Cosmic with improved star preservation")
                        .version("2.0")
                        .parameters(Map.of(
                                "sigclip", "Sigma clipping threshold (default: 4.5)",
                                "sigfrac", "Fractional detection limit (default: 0.3)",
                                "objlim", "Object detection limit (default: 5.0)",
                                "gain", "CCD gain in e-/ADU (default: 1.0)",
                                "readnoise", "Read noise in e- (default: 6.5)",
                                "niter", "Maximum number of iterations (default: 4)",
                                "starPreservation", "Enhanced star preservation (default: true)",
                                "edgeHandling", "Improved edge handling (default: true)"
                        ))
                        .supported(true)
                        .experimental(false)
                        .build(),

                AlgorithmInfo.builder()
                        .id("median-filter")
                        .name("Median Filter Cosmic Ray Removal")
                        .description("Simple median filter-based cosmic ray detection")
                        .version("1.0")
                        .parameters(Map.of(
                                "kernelSize", "Median filter kernel size (default: 5)",
                                "threshold", "Detection threshold in sigma (default: 5.0)",
                                "iterations", "Number of filter iterations (default: 1)"
                        ))
                        .supported(true)
                        .experimental(false)
                        .build(),

                AlgorithmInfo.builder()
                        .id("neural-cr")
                        .name("Neural Network Cosmic Ray Detection")
                        .description("Deep learning-based cosmic ray detection (experimental)")
                        .version("0.1")
                        .parameters(Map.of(
                                "modelPath", "Path to trained model (required)",
                                "confidence", "Detection confidence threshold (default: 0.8)",
                                "patchSize", "Input patch size (default: 64)",
                                "overlap", "Patch overlap factor (default: 0.25)"
                        ))
                        .supported(false)
                        .experimental(true)
                        .build());
        algorithmRegistry.put("cosmic-ray-removal", cosmicRayAlgorithms);

        // Bias Subtraction Algorithms
        List<AlgorithmInfo> biasSubtractionAlgorithms = Arrays.asList(
                AlgorithmInfo.builder()
                        .id("default")
                        .name("Standard Bias Subtraction")
                        .description("Basic bias frame subtraction")
                        .version("1.0")
                        .parameters(Map.of(
                                "overscanCorrection", "Apply overscan correction (default: true)",
                                "overscanRegion", "Overscan region [x1,y1,x2,y2] (auto-detect if empty)",
                                "fitMethod", "Overscan fit method: mean, median, polynomial (default: median)"
                        ))
                        .supported(true)
                        .experimental(false)
                        .build(),

                AlgorithmInfo.builder()
                        .id("robust-bias")
                        .name("Robust Bias Subtraction")
                        .description("Bias subtraction with outlier-resistant methods")
                        .version("1.1")
                        .parameters(Map.of(
                                "outlierRejection", "Enable outlier rejection (default: true)",
                                "rejectionMethod", "Rejection method: sigma, mad, percentile (default: sigma)",
                                "rejectionThreshold", "Rejection threshold (default: 3.0)",
                                "iterativeRejection", "Use iterative rejection (default: true)"
                        ))
                        .supported(true)
                        .experimental(false)
                        .build()
        );
        algorithmRegistry.put("bias-subtraction", biasSubtractionAlgorithms);

        log.info("Algorithm registry initialized with {} algorithm types and {} total algorithms",
                algorithmRegistry.size(),
                algorithmRegistry.values().stream().mapToInt(List::size).sum());
    }

    public List<AlgorithmInfo> getAvailableAlgorithms(String algorithmType) {
        String normalizedType = normalizeAlgorithmType(algorithmType);

        if (!algorithmRegistry.containsKey(normalizedType)) {
            log.warn("Unknown algorithm type requested: {}", algorithmType);
            return Collections.emptyList();
        }

        return algorithmRegistry.get(normalizedType);
    }

    public List<AlgorithmInfo> getSupportedAlgorithms(String algorithmType) {
        return getAvailableAlgorithms(algorithmType).stream()
                .filter(AlgorithmInfo::isSupported)
                .collect(Collectors.toList());
    }

    public List<AlgorithmInfo> getExperimentalAlgorithms(String algorithmType) {
        return getAvailableAlgorithms(algorithmType).stream()
                .filter(AlgorithmInfo::isExperimental)
                .collect(Collectors.toList());
    }

    public AlgorithmInfo getAlgorithmInfo(String algorithmType, String algorithmId) {
        return getAvailableAlgorithms(algorithmType).stream()
                .filter(algo -> algo.getId().equals(algorithmId))
                .findFirst()
                .orElse(null);
    }

    public boolean isAlgorithmSupported(String algorithmType, String algorithmId) {
        AlgorithmInfo algo = getAlgorithmInfo(algorithmType, algorithmId);
        return algo != null && algo.isSupported();
    }

    public Set<String> getSupportedAlgorithmTypes() {
        return algorithmRegistry.keySet();
    }

    public Map<String, Object> getAlgorithmSummary() {
        Map<String, Object> summary = new HashMap<>();

        for (Map.Entry<String, List<AlgorithmInfo>> entry : algorithmRegistry.entrySet()) {
            String type = entry.getKey();
            List<AlgorithmInfo> algorithms = entry.getValue();

            Map<String, Object> typeSummary = new HashMap<>();
            typeSummary.put("total", algorithms.size());
            typeSummary.put("supported", algorithms.stream().mapToInt(a -> a.isSupported() ? 1 : 0).sum());
            typeSummary.put("experimental", algorithms.stream().mapToInt(a -> a.isExperimental() ? 1 : 0).sum());
            typeSummary.put("algorithms", algorithms.stream()
                    .map(algo -> Map.of(
                            "id", algo.getId(),
                            "name", algo.getName(),
                            "supported", algo.isSupported(),
                            "experimental", algo.isExperimental()
                    ))
                    .collect(Collectors.toList()));

            summary.put(type, typeSummary);
        }

        return summary;
    }

    private String normalizeAlgorithmType(String algorithmType) {
        if (algorithmType == null) {
            return "";
        }

        // Convert various formats to standard format
        String normalized = algorithmType.toLowerCase().trim();

        // Handle common variations
        switch (normalized) {
            case "dark":
            case "dark_subtraction":
            case "darksubtraction":
                return "dark-subtraction";

            case "flat":
            case "flat_correction":
            case "flatcorrection":
            case "flat_field":
            case "flatfield":
                return "flat-correction";

            case "cosmic":
            case "cosmic_ray":
            case "cosmicray":
            case "cosmic_rays":
            case "cosmicrays":
            case "cosmic_ray_removal":
            case "cosmicrayremoval":
                return "cosmic-ray-removal";

            case "bias":
            case "bias_subtraction":
            case "biassubtraction":
                return "bias-subtraction";

            default:
                return normalized;
        }
    }

    public static class AlgorithmInfo {
        private String id;
        private String name;
        private String description;
        private String version;
        private Map<String, String> parameters;
        private boolean supported;
        private boolean experimental;

        public static AlgorithmInfoBuilder builder() {
            return new AlgorithmInfoBuilder();
        }

        // Getters
        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public String getVersion() {
            return version;
        }

        public Map<String, String> getParameters() {
            return parameters;
        }

        public boolean isSupported() {
            return supported;
        }

        public boolean isExperimental() {
            return experimental;
        }

        public static class AlgorithmInfoBuilder {
            private String id;
            private String name;
            private String description;
            private String version;
            private Map<String, String> parameters = new HashMap<>();
            private boolean supported = true;
            private boolean experimental = false;

            public AlgorithmInfoBuilder id(String id) {
                this.id = id;
                return this;
            }

            public AlgorithmInfoBuilder name(String name) {
                this.name = name;
                return this;
            }

            public AlgorithmInfoBuilder description(String description) {
                this.description = description;
                return this;
            }

            public AlgorithmInfoBuilder version(String version) {
                this.version = version;
                return this;
            }

            public AlgorithmInfoBuilder parameters(Map<String, String> parameters) {
                this.parameters = parameters;
                return this;
            }

            public AlgorithmInfoBuilder supported(boolean supported) {
                this.supported = supported;
                return this;
            }

            public AlgorithmInfoBuilder experimental(boolean experimental) {
                this.experimental = experimental;
                return this;
            }

            public AlgorithmInfo build() {
                AlgorithmInfo info = new AlgorithmInfo();
                info.id = this.id;
                info.name = this.name;
                info.description = this.description;
                info.version = this.version;
                info.parameters = this.parameters;
                info.supported = this.supported;
                info.experimental = this.experimental;
                return info;
            }
        }
    }
}