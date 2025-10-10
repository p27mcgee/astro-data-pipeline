# FITS Generator with Catalog Export

## Overview

The original `fits_generator.py` generates realistic FITS files but **does not export catalog data**.

The enhanced `fits_generator_with_catalog.py` extends the original to:
- ✅ Track all generated sources (stars, galaxies, cosmic rays)
- ✅ Export ground truth catalogs to CSV
- ✅ Enable accuracy validation of processing pipeline

---

## What Catalog Data is Exported?

### Catalog Columns

| Column | Type | Description |
|--------|------|-------------|
| `source_id` | string | Unique identifier (STAR_00001, GALAXY_00042, CR_00007) |
| `source_type` | string | STAR, GALAXY, or COSMIC_RAY |
| `x_pixel` | float | X coordinate in pixels |
| `y_pixel` | float | Y coordinate in pixels |
| `ra` | float | Right Ascension (degrees, null for cosmic rays) |
| `dec` | float | Declination (degrees, null for cosmic rays) |
| `magnitude` | float | Apparent magnitude |
| `flux` | float | Flux in electrons |
| `fwhm` | float | Full Width Half Maximum (pixels, stars only) |
| `is_extended` | bool | True for galaxies, False for stars |
| `sersic_n` | float | Sérsic index (galaxies: 1.0=spiral, 4.0=elliptical) |
| `effective_radius` | float | Effective radius in pixels (galaxies only) |
| `axis_ratio` | float | Minor/major axis ratio (galaxies only) |
| `position_angle` | float | Position angle in degrees (galaxies only) |

---

## Usage Examples

### Basic Usage

```python
from fits_generator_with_catalog import AstronomicalFITSGeneratorWithCatalog

generator = AstronomicalFITSGeneratorWithCatalog()

# Generate FITS with catalog
fits_file, catalog = generator.generate_with_catalog(
    "test.fits",
    target_ra=150.0,
    target_dec=2.0,
    exposure_time=600.0
)

print(f"Generated: {fits_file}")
print(f"Sources: {len(catalog)}")
print(catalog.head())
```

### Command Line

```bash
cd application/data-simulator

# Generate single file with catalog
python fits_generator_with_catalog.py \
  --output-dir test-data \
  --count 1

# Output:
#   test-data/observation_0000.fits
#   test-data/observation_0000.csv
```

### Batch Generation

```bash
# Generate 50 FITS files with catalogs
python fits_generator_with_catalog.py \
  --output-dir test-data/batch \
  --count 50 \
  --config custom_config.yaml
```

---

## Testing Use Cases

### 1. Validate Source Detection

```python
# Generate test data
fits_file, true_catalog = generator.generate_with_catalog("test.fits")

# Process with your pipeline
detected_sources = processing_pipeline.detect_sources(fits_file)

# Compare ground truth vs detected
stars_generated = len(true_catalog[true_catalog['source_type'] == 'STAR'])
stars_detected = len(detected_sources[detected_sources['type'] == 'STAR'])

detection_rate = stars_detected / stars_generated
print(f"Star detection rate: {detection_rate:.1%}")
```

### 2. Validate Cosmic Ray Removal

```python
# Generate with known cosmic rays
fits_file, true_catalog = generator.generate_with_catalog(
    "cosmic_ray_test.fits",
    exposure_time=1800.0  # More cosmic rays
)

cosmic_rays_true = len(true_catalog[true_catalog['source_type'] == 'COSMIC_RAY'])

# Process
result = cosmic_ray_removal_service.process(fits_file)

removal_rate = result.cosmic_rays_removed / cosmic_rays_true
print(f"Removed {removal_rate:.1%} of cosmic rays")
```

### 3. Accuracy Testing

```python
# Generate test image
fits_file, true_catalog = generator.generate_with_catalog("accuracy_test.fits")

# Process and catalog
processing_result = pipeline.process(fits_file)
detected_catalog = catalog_service.extract_sources(processing_result)

# Calculate positional accuracy
for true_source in true_catalog[true_catalog['source_type'] == 'STAR']:
    # Find nearest detected source
    distances = np.sqrt(
        (detected_catalog['x'] - true_source['x_pixel'])**2 +
        (detected_catalog['y'] - true_source['y_pixel'])**2
    )

    if distances.min() < 2.0:  # Within 2 pixels
        print(f"✓ {true_source['source_id']} matched")
    else:
        print(f"✗ {true_source['source_id']} missed")
```

### 4. Integration Tests

```java
@Test
public void testSourceDetectionAccuracy() {
    // Generate test data with catalog
    String fitsFile = "test-data/source_detection_test.fits";
    String catalogFile = "test-data/source_detection_test.csv";

    // Read ground truth
    List<Source> trueSources = readCatalog(catalogFile);

    // Process FITS
    ProcessingResult result = fitsProcessingService.process(fitsFile);
    List<Source> detectedSources = result.getDetectedSources();

    // Calculate metrics
    double precision = calculatePrecision(trueSources, detectedSources);
    double recall = calculateRecall(trueSources, detectedSources);

    assertThat(precision).isGreaterThan(0.90);
    assertThat(recall).isGreaterThan(0.85);
}
```

---

## Catalog Format Example

```csv
source_id,source_type,x_pixel,y_pixel,ra,dec,magnitude,flux,fwhm,is_extended,sersic_n,effective_radius,axis_ratio,position_angle
STAR_00000,STAR,987.34,512.67,150.0123,2.0045,18.5,1234.5,2.1,False,,,,,
STAR_00001,STAR,1523.21,789.45,150.0156,2.0078,19.2,856.3,1.9,False,,,,,
GALAXY_00000,GALAXY,456.78,1234.56,149.9987,2.0123,20.5,456.7,,True,1.0,4.5,0.65,45.3
GALAXY_00001,GALAXY,1876.43,345.12,150.0234,1.9945,21.3,234.8,,True,4.0,8.2,0.82,120.7
CR_00000,COSMIC_RAY,234.0,567.0,,,,,,,False,,,,,
CR_00001,COSMIC_RAY,1098.0,876.0,,,,,,,False,,,,,
```

---

## Comparison: Original vs Enhanced

| Feature | `fits_generator.py` | `fits_generator_with_catalog.py` |
|---------|-------------------|--------------------------------|
| Generate FITS | ✅ | ✅ |
| Export Catalog | ❌ | ✅ |
| Track Sources | ❌ (internal only) | ✅ (exported) |
| CSV Output | ❌ | ✅ |
| Ground Truth | ❌ (lost) | ✅ (saved) |
| Accuracy Testing | ❌ | ✅ |
| Return Value | `str` (file path) | `Tuple[str, DataFrame]` |

---

## Configuration

Uses the same configuration as the original generator:

```yaml
# config.yaml
star_density: 100      # Stars per square arcminute
galaxy_density: 10     # Galaxies per square arcminute
cosmic_ray_rate: 0.1   # Cosmic rays per pixel per hour
image_size: [2048, 2048]
pixel_scale: 0.25      # arcsec/pixel
```

---

## Integration with Existing Tests

### Replace Original Generator

```python
# Before:
from fits_generator import AstronomicalFITSGenerator
generator = AstronomicalFITSGenerator()
fits_file = generator.generate_fits_file("test.fits")

# After:
from fits_generator_with_catalog import AstronomicalFITSGeneratorWithCatalog
generator = AstronomicalFITSGeneratorWithCatalog()
fits_file, catalog = generator.generate_with_catalog("test.fits")
# Now you have ground truth!
```

### Backward Compatible

The enhanced generator **extends** the original, so:
- ✅ All original methods still work
- ✅ Can still use `generate_fits_file()` without catalog
- ✅ New `generate_with_catalog()` is optional

---

## Performance

- **Generation time**: +5-10% overhead for catalog tracking
- **Memory**: +minimal (catalog is list of dicts)
- **Disk space**: +~50KB per catalog CSV file

---

## Future Enhancements

Potential additions:
- [ ] FITS table extension with catalog (instead of CSV)
- [ ] Photometry accuracy metrics
- [ ] Astrometry accuracy metrics
- [ ] Match detected sources to ground truth automatically
- [ ] Confusion matrix generation
- [ ] ROC curve data export

---

## Summary

**Current State**: Your original simulator generates realistic data but doesn't export the "ground truth"

**Enhanced Version**: Tracks and exports all generated sources, enabling:
- ✅ Accuracy validation
- ✅ Algorithm comparison
- ✅ Regression testing
- ✅ Performance benchmarking

**Recommendation**: Use the enhanced version for testing and validation workflows where ground truth is needed.
