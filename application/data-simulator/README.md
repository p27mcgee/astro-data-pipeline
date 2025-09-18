# Astronomical FITS Data Simulator

A sophisticated tool for generating realistic astronomical FITS files for testing and development of the data processing pipeline.

## Features

- **Realistic Astronomical Sources**: Generates stars and galaxies with authentic properties
- **Proper Noise Models**: Includes Poisson noise, read noise, and cosmic rays
- **Comprehensive FITS Headers**: Full astronomical metadata with WCS information
- **Multiple Observatory Support**: Simulates data from HST, JWST, VLT, Gemini, and more
- **Configurable Parameters**: Highly customizable source populations and observing conditions
- **Batch Generation**: Efficient generation of large datasets
- **S3 Integration**: Optional direct upload to AWS S3 buckets

## Installation

```bash
pip install -r requirements.txt
```

## Quick Start

### Generate a single FITS file

```python
from fits_generator import AstronomicalFITSGenerator

generator = AstronomicalFITSGenerator()
fits_file = generator.generate_fits_file(
    output_path="test_observation.fits",
    target_ra=180.0,        # Right Ascension in degrees
    target_dec=30.0,        # Declination in degrees
    exposure_time=600.0,    # Exposure time in seconds
    telescope="HST",
    instrument="WFC3",
    filter_name="F606W"
)
```

### Generate a batch of files

```bash
python fits_generator.py --output-dir ./test_data --count 100
```

### Generate with custom configuration

```bash
python fits_generator.py --output-dir ./data --count 50 --config custom_config.yaml
```

### Upload directly to S3

```bash
python fits_generator.py --output-dir ./data --count 20 --upload-s3
```

## Configuration

The simulator uses a YAML configuration file to control all aspects of data generation. Key configuration sections:

### Image Properties
```yaml
image_size: [2048, 2048]  # Height, width in pixels
pixel_scale: 0.25         # Arcseconds per pixel
```

### Detector Characteristics
```yaml
gain: 1.5               # Electrons per ADU
read_noise: 5.0         # Electrons RMS
saturation_level: 65000 # ADU
```

### Source Populations
```yaml
star_density: 100     # Stars per square arcminute
galaxy_density: 10    # Galaxies per square arcminute
```

### Observatory Equipment
```yaml
telescopes: ['HST', 'JWST', 'VLT', 'Gemini']
instruments: ['WFC3', 'NIRCam', 'FORS2', 'GMOS']
filters: ['F606W', 'F814W', 'g', 'r', 'i', 'z']
```

## Generated Data Properties

### Stars
- **Magnitude Distribution**: Realistic luminosity function with bright/faint populations
- **PSF**: Gaussian profiles with seeing-limited FWHM
- **Positions**: Random spatial distribution

### Galaxies  
- **Morphology**: Exponential (spiral) and de Vaucouleurs (elliptical) profiles
- **Size Distribution**: Realistic effective radius distribution
- **Orientation**: Random position angles and axis ratios

### Noise Sources
- **Poisson Noise**: Shot noise from photon statistics
- **Read Noise**: Gaussian detector read noise
- **Cosmic Rays**: Exponential energy distribution with realistic rate
- **Sky Background**: Uniform sky contribution

### FITS Headers
Complete astronomical metadata including:
- **WCS Information**: World Coordinate System for astrometry
- **Observation Details**: Date, time, exposure, airmass
- **Instrument Configuration**: Telescope, instrument, filter
- **Detector Properties**: Gain, read noise, saturation level
- **Environmental Conditions**: Seeing, sky background

## Advanced Usage

### Custom Source Populations

```python
config = {
    'star_density': 200,      # Double star density
    'galaxy_density': 5,      # Half galaxy density
    'cosmic_ray_rate': 0.05,  # Reduce cosmic ray rate
    'seeing': {'mean': 0.8}   # Excellent seeing
}

generator = AstronomicalFITSGenerator()
generator.config.update(config)
```

### Multi-Band Observations

```python
filters = ['g', 'r', 'i', 'z']
for filt in filters:
    filename = f"observation_{filt}.fits"
    generator.generate_fits_file(
        filename,
        target_ra=150.0,
        target_dec=2.0,
        filter_name=filt
    )
```

### Time Series Generation

```python
import datetime

base_time = datetime.datetime.now()
for i in range(24):  # One observation per hour
    obs_time = base_time + datetime.timedelta(hours=i)
    filename = f"timeseries_{i:02d}.fits"
    generator.generate_fits_file(filename, target_ra=45.0, target_dec=30.0)
```

## Integration with Pipeline

The generated FITS files are designed to work seamlessly with the astronomical data processing pipeline:

1. **Upload to S3**: Files can be automatically uploaded to the raw data bucket
2. **Trigger Processing**: S3 events trigger Airflow DAGs for processing
3. **Realistic Testing**: Authentic astronomical properties test all pipeline components
4. **Performance Benchmarking**: Known source positions enable accuracy validation

## Output File Structure

Generated FITS files contain:

```
Primary HDU:
├── Header (extensive astronomical metadata)
│   ├── Basic: TELESCOP, INSTRUME, FILTER, EXPTIME
│   ├── Coordinates: RA, DEC, CRVAL1/2, CRPIX1/2
│   ├── WCS: CD matrix, projection type
│   ├── Detector: GAIN, RDNOISE, SATURATE
│   └── Environment: AIRMASS, SEEING, SKYBKG
└── Data: 16-bit unsigned integer image array
```

## Performance

- **Generation Speed**: ~1-2 seconds per 2K×2K image
- **Memory Usage**: ~50MB per image during generation
- **Disk Space**: ~8MB per uncompressed FITS file
- **Parallel Generation**: Supports concurrent batch processing

## Validation

Generated files can be validated using standard astronomical tools:

```bash
# Check FITS file integrity
fitsverify observation.fits

# Display image statistics
fitsstat observation.fits

# View with DS9
ds9 observation.fits

# Analyze with Python
from astropy.io import fits
hdul = fits.open('observation.fits')
hdul.info()
print(hdul[0].header)
```

## S3 Integration

Configure S3 upload in the configuration file:

```yaml
s3_bucket: "astro-data-pipeline-raw-data-dev"
s3_prefix: "raw-data/fits/"
```

Or set environment variables:
```bash
export AWS_ACCESS_KEY_ID=your_access_key
export AWS_SECRET_ACCESS_KEY=your_secret_key
export AWS_DEFAULT_REGION=us-east-1
```

## Troubleshooting

### Common Issues

1. **Memory Errors**: Reduce batch size or image dimensions
2. **Slow Generation**: Check disk I/O performance
3. **Missing Dependencies**: Install required astronomical libraries
4. **S3 Upload Failures**: Verify AWS credentials and bucket permissions

### Debug Mode

Enable verbose logging for troubleshooting:

```bash
python fits_generator.py --verbose --output-dir ./debug_data --count 1
```

## Contributing

To extend the simulator:

1. **Add New Observatories**: Update telescope/instrument lists
2. **Improve Source Models**: Enhance stellar/galaxy generation
3. **Add Artifacts**: Implement additional detector effects
4. **Optimize Performance**: Profile and improve generation speed

## References

- [FITS Standard](https://fits.gsfc.nasa.gov/fits_standard.html)
- [World Coordinate System](https://fits.gsfc.nasa.gov/fits_wcs.html)
- [Astropy Documentation](https://docs.astropy.org/)
- [STScI Data Analysis Tools](https://www.stsci.edu/scientific-community/software)