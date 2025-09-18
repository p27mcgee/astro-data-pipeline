#!/usr/bin/env python3
"""
Astronomical FITS File Generator

Generates realistic FITS files with astronomical properties for testing
the data processing pipeline. Creates images with stars, galaxies, noise,
and cosmic rays with authentic astronomical characteristics.

Author: STScI Demo Project
"""

import argparse
import logging
import random
import sys
from datetime import datetime, timedelta
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import numpy as np
from astropy.io import fits
from astropy.time import Time
from astropy.coordinates import SkyCoord
from astropy.wcs import WCS
import astropy.units as u
from scipy import ndimage
from scipy.stats import poisson
import boto3
import yaml
from tqdm import tqdm

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class AstronomicalFITSGenerator:
    """Generate realistic astronomical FITS files for testing."""
    
    def __init__(self, config_path: Optional[str] = None):
        """Initialize the FITS generator with configuration."""
        self.config = self._load_config(config_path)
        self.random_state = np.random.RandomState(self.config.get('random_seed', 42))
        
    def _load_config(self, config_path: Optional[str]) -> Dict:
        """Load configuration from YAML file or use defaults."""
        default_config = {
            'image_size': [2048, 2048],
            'pixel_scale': 0.25,  # arcsec/pixel
            'gain': 1.5,  # electrons/ADU
            'read_noise': 5.0,  # electrons
            'dark_current': 0.01,  # electrons/pixel/second
            'sky_background': 1000.0,  # electrons/pixel
            'saturation_level': 65000,  # ADU
            'telescopes': ['HST', 'JWST', 'VLT', 'Gemini', 'Keck'],
            'instruments': ['WFC3', 'NIRCam', 'FORS2', 'GMOS', 'DEIMOS'],
            'filters': ['F606W', 'F814W', 'F160W', 'F110W', 'g', 'r', 'i', 'z'],
            'star_density': 100,  # stars per square arcminute
            'galaxy_density': 10,  # galaxies per square arcminute
            'cosmic_ray_rate': 0.1,  # cosmic rays per pixel per hour
            'output_format': 'fits',
            's3_bucket': None,
            'random_seed': None
        }
        
        if config_path and Path(config_path).exists():
            with open(config_path, 'r') as f:
                user_config = yaml.safe_load(f)
                default_config.update(user_config)
                
        return default_config

    def generate_fits_file(
        self,
        output_path: str,
        target_ra: float = None,
        target_dec: float = None,
        exposure_time: float = 300.0,
        telescope: str = None,
        instrument: str = None,
        filter_name: str = None
    ) -> str:
        """Generate a single FITS file with realistic astronomical data."""
        
        # Set random parameters if not provided
        if target_ra is None:
            target_ra = self.random_state.uniform(0, 360)
        if target_dec is None:
            target_dec = self.random_state.uniform(-90, 90)
        if telescope is None:
            telescope = self.random_state.choice(self.config['telescopes'])
        if instrument is None:
            instrument = self.random_state.choice(self.config['instruments'])
        if filter_name is None:
            filter_name = self.random_state.choice(self.config['filters'])
            
        logger.info(f"Generating FITS file: {output_path}")
        logger.info(f"Target: RA={target_ra:.6f}, Dec={target_dec:.6f}")
        logger.info(f"Telescope: {telescope}, Instrument: {instrument}, Filter: {filter_name}")
        
        # Create image array
        height, width = self.config['image_size']
        image = np.zeros((height, width), dtype=np.float32)
        
        # Add sky background
        sky_level = self.config['sky_background']
        image += self.random_state.poisson(sky_level, size=(height, width))
        
        # Add stars
        self._add_stars(image, target_ra, target_dec)
        
        # Add galaxies
        self._add_galaxies(image, target_ra, target_dec)
        
        # Add cosmic rays
        self._add_cosmic_rays(image, exposure_time)
        
        # Add noise
        self._add_noise(image)
        
        # Convert to ADU and apply saturation
        gain = self.config['gain']
        image = image / gain
        image = np.clip(image, 0, self.config['saturation_level'])
        
        # Create WCS (World Coordinate System)
        wcs = self._create_wcs(target_ra, target_dec, width, height)
        
        # Create FITS header
        header = self._create_fits_header(
            target_ra, target_dec, exposure_time,
            telescope, instrument, filter_name, wcs
        )
        
        # Create FITS HDU
        hdu = fits.PrimaryHDU(data=image.astype(np.uint16), header=header)
        hdul = fits.HDUList([hdu])
        
        # Write FITS file
        output_path = Path(output_path)
        output_path.parent.mkdir(parents=True, exist_ok=True)
        hdul.writeto(output_path, overwrite=True)
        
        logger.info(f"Created FITS file: {output_path} ({image.shape})")
        return str(output_path)

    def _add_stars(self, image: np.ndarray, center_ra: float, center_dec: float):
        """Add realistic stellar sources to the image."""
        height, width = image.shape
        pixel_scale = self.config['pixel_scale'] / 3600.0  # Convert to degrees per pixel
        field_area = (height * pixel_scale) * (width * pixel_scale) * 3600  # Square arcminutes
        
        # Calculate number of stars based on density
        star_density = self.config['star_density']
        num_stars = self.random_state.poisson(star_density * field_area)
        
        logger.debug(f"Adding {num_stars} stars to {field_area:.1f} sq. arcmin field")
        
        for _ in range(num_stars):
            # Random position
            x = self.random_state.uniform(50, width - 50)
            y = self.random_state.uniform(50, height - 50)
            
            # Stellar magnitude (following luminosity function)
            magnitude = self._generate_stellar_magnitude()
            
            # Convert magnitude to flux (electrons)
            flux = self._magnitude_to_flux(magnitude)
            
            # FWHM (seeing limited)
            fwhm = self.random_state.normal(2.0, 0.3)  # pixels
            fwhm = max(1.0, fwhm)
            
            # Add stellar PSF
            self._add_stellar_psf(image, x, y, flux, fwhm)

    def _add_galaxies(self, image: np.ndarray, center_ra: float, center_dec: float):
        """Add realistic galaxy sources to the image."""
        height, width = image.shape
        pixel_scale = self.config['pixel_scale'] / 3600.0
        field_area = (height * pixel_scale) * (width * pixel_scale) * 3600
        
        galaxy_density = self.config['galaxy_density']
        num_galaxies = self.random_state.poisson(galaxy_density * field_area)
        
        logger.debug(f"Adding {num_galaxies} galaxies")
        
        for _ in range(num_galaxies):
            x = self.random_state.uniform(100, width - 100)
            y = self.random_state.uniform(100, height - 100)
            
            # Galaxy magnitude
            magnitude = self._generate_galaxy_magnitude()
            flux = self._magnitude_to_flux(magnitude)
            
            # Galaxy size and shape
            effective_radius = self.random_state.exponential(3.0) + 1.0  # pixels
            axis_ratio = self.random_state.uniform(0.3, 1.0)
            position_angle = self.random_state.uniform(0, 180)
            
            # Galaxy profile (Sersic n=1 for exponential, n=4 for de Vaucouleurs)
            sersic_n = self.random_state.choice([1.0, 4.0], p=[0.7, 0.3])
            
            self._add_galaxy_profile(
                image, x, y, flux, effective_radius, 
                axis_ratio, position_angle, sersic_n
            )

    def _add_cosmic_rays(self, image: np.ndarray, exposure_time: float):
        """Add cosmic ray hits to the image."""
        height, width = image.shape
        total_pixels = height * width
        
        # Calculate expected cosmic ray hits
        cr_rate = self.config['cosmic_ray_rate']  # per pixel per hour
        expected_hits = cr_rate * total_pixels * (exposure_time / 3600.0)
        num_hits = self.random_state.poisson(expected_hits)
        
        logger.debug(f"Adding {num_hits} cosmic ray hits")
        
        for _ in range(num_hits):
            x = self.random_state.randint(0, width)
            y = self.random_state.randint(0, height)
            
            # Cosmic ray energy (ADU)
            energy = self.random_state.exponential(5000) + 1000
            
            # Cosmic ray shape (usually elongated)
            length = self.random_state.poisson(3) + 1
            angle = self.random_state.uniform(0, 2 * np.pi)
            
            # Create cosmic ray track
            for i in range(length):
                dx = int(i * np.cos(angle))
                dy = int(i * np.sin(angle))
                
                new_x = x + dx
                new_y = y + dy
                
                if 0 <= new_x < width and 0 <= new_y < height:
                    image[new_y, new_x] += energy * (1.0 - i / length)

    def _add_noise(self, image: np.ndarray):
        """Add read noise and Poisson noise to the image."""
        # Poisson noise (already included in source generation)
        # Add read noise
        read_noise = self.config['read_noise']
        noise = self.random_state.normal(0, read_noise, size=image.shape)
        image += noise

    def _add_stellar_psf(self, image: np.ndarray, x: float, y: float, 
                        flux: float, fwhm: float):
        """Add a stellar PSF (Gaussian) at the specified position."""
        sigma = fwhm / (2.0 * np.sqrt(2.0 * np.log(2.0)))
        
        # Create PSF stamp
        size = int(6 * sigma) + 1
        if size % 2 == 0:
            size += 1
            
        center = size // 2
        y_indices, x_indices = np.ogrid[:size, :size]
        
        # Gaussian PSF
        psf = np.exp(-((x_indices - center)**2 + (y_indices - center)**2) / (2 * sigma**2))
        psf = psf / np.sum(psf) * flux
        
        # Add to image
        x_start = int(x - center)
        y_start = int(y - center)
        x_end = x_start + size
        y_end = y_start + size
        
        # Handle boundary conditions
        img_x_start = max(0, x_start)
        img_y_start = max(0, y_start)
        img_x_end = min(image.shape[1], x_end)
        img_y_end = min(image.shape[0], y_end)
        
        psf_x_start = img_x_start - x_start
        psf_y_start = img_y_start - y_start
        psf_x_end = psf_x_start + (img_x_end - img_x_start)
        psf_y_end = psf_y_start + (img_y_end - img_y_start)
        
        if img_x_end > img_x_start and img_y_end > img_y_start:
            image[img_y_start:img_y_end, img_x_start:img_x_end] += \
                psf[psf_y_start:psf_y_end, psf_x_start:psf_x_end]

    def _add_galaxy_profile(self, image: np.ndarray, x: float, y: float,
                           flux: float, r_eff: float, axis_ratio: float,
                           position_angle: float, sersic_n: float):
        """Add a galaxy with Sersic profile."""
        # Create galaxy stamp
        size = int(6 * r_eff) + 1
        if size % 2 == 0:
            size += 1
            
        center = size // 2
        y_indices, x_indices = np.ogrid[:size, :size]
        
        # Rotate coordinates
        angle_rad = np.radians(position_angle)
        x_rot = (x_indices - center) * np.cos(angle_rad) + (y_indices - center) * np.sin(angle_rad)
        y_rot = -(x_indices - center) * np.sin(angle_rad) + (y_indices - center) * np.cos(angle_rad)
        
        # Elliptical radius
        r_ellipse = np.sqrt(x_rot**2 + (y_rot / axis_ratio)**2)
        
        # Sersic profile
        if sersic_n == 1.0:  # Exponential profile
            b_n = 1.678
            profile = np.exp(-b_n * (r_ellipse / r_eff))
        else:  # de Vaucouleurs (n=4)
            b_n = 7.669
            profile = np.exp(-b_n * ((r_ellipse / r_eff)**(1.0/sersic_n) - 1))
        
        profile = profile / np.sum(profile) * flux
        
        # Add to image (same boundary handling as stellar PSF)
        x_start = int(x - center)
        y_start = int(y - center)
        x_end = x_start + size
        y_end = y_start + size
        
        img_x_start = max(0, x_start)
        img_y_start = max(0, y_start)
        img_x_end = min(image.shape[1], x_end)
        img_y_end = min(image.shape[0], y_end)
        
        psf_x_start = img_x_start - x_start
        psf_y_start = img_y_start - y_start
        psf_x_end = psf_x_start + (img_x_end - img_x_start)
        psf_y_end = psf_y_start + (img_y_end - img_y_start)
        
        if img_x_end > img_x_start and img_y_end > img_y_start:
            image[img_y_start:img_y_end, img_x_start:img_x_end] += \
                profile[psf_y_start:psf_y_end, psf_x_start:psf_x_end]

    def _generate_stellar_magnitude(self) -> float:
        """Generate stellar magnitude following a realistic luminosity function."""
        # Simplified stellar luminosity function
        # Most stars are faint, fewer bright stars
        if self.random_state.random() < 0.1:
            # Bright stars (10% probability)
            return self.random_state.uniform(12.0, 18.0)
        else:
            # Faint stars (90% probability)  
            return self.random_state.uniform(18.0, 25.0)

    def _generate_galaxy_magnitude(self) -> float:
        """Generate galaxy magnitude following a realistic distribution."""
        # Galaxies are generally fainter than stars
        return self.random_state.uniform(18.0, 27.0)

    def _magnitude_to_flux(self, magnitude: float) -> float:
        """Convert magnitude to flux in electrons."""
        # Approximate conversion for typical exposure
        # mag = -2.5 * log10(flux) + zeropoint
        zeropoint = 25.0  # Typical for space telescopes
        flux = 10**((zeropoint - magnitude) / 2.5)
        return max(1.0, flux)  # Minimum 1 electron

    def _create_wcs(self, ra: float, dec: float, width: int, height: int) -> WCS:
        """Create World Coordinate System for the image."""
        wcs = WCS(naxis=2)
        
        # Reference pixel (center of image)
        wcs.wcs.crpix = [width / 2.0, height / 2.0]
        
        # Reference coordinates
        wcs.wcs.crval = [ra, dec]
        
        # Pixel scale (degrees per pixel)
        pixel_scale_deg = self.config['pixel_scale'] / 3600.0
        wcs.wcs.cdelt = [-pixel_scale_deg, pixel_scale_deg]  # Note: RA decreases with increasing X
        
        # Coordinate system
        wcs.wcs.ctype = ["RA---TAN", "DEC--TAN"]
        wcs.wcs.cunit = ["deg", "deg"]
        
        return wcs

    def _create_fits_header(self, ra: float, dec: float, exposure_time: float,
                           telescope: str, instrument: str, filter_name: str,
                           wcs: WCS) -> fits.Header:
        """Create a comprehensive FITS header with astronomical metadata."""
        header = fits.Header()
        
        # Basic image information
        header['SIMPLE'] = True
        header['BITPIX'] = 16
        header['NAXIS'] = 2
        header['NAXIS1'] = self.config['image_size'][1]
        header['NAXIS2'] = self.config['image_size'][0]
        header['EXTEND'] = True
        
        # Observatory and instrument
        header['TELESCOP'] = telescope
        header['INSTRUME'] = instrument
        header['FILTER'] = filter_name
        
        # Observation details
        obs_date = datetime.now() - timedelta(days=self.random_state.randint(1, 365))
        header['DATE-OBS'] = obs_date.strftime('%Y-%m-%dT%H:%M:%S')
        header['EXPTIME'] = exposure_time
        header['OBSTYPE'] = 'SCIENCE'
        
        # Target information
        header['OBJECT'] = f'FIELD_RA{ra:.3f}_DEC{dec:.3f}'
        header['RA'] = ra
        header['DEC'] = dec
        header['EQUINOX'] = 2000.0
        header['RADESYS'] = 'ICRS'
        
        # Detector characteristics
        header['GAIN'] = self.config['gain']
        header['RDNOISE'] = self.config['read_noise']
        header['SATURATE'] = self.config['saturation_level']
        header['PIXSCALE'] = self.config['pixel_scale']
        
        # Environmental conditions
        header['AIRMASS'] = self.random_state.uniform(1.0, 2.5)
        header['SEEING'] = self.random_state.normal(1.2, 0.3)
        header['SKYBKG'] = self.config['sky_background']
        
        # Processing information
        header['CREATOR'] = 'AstroDataPipeline-Simulator'
        header['VERSION'] = '1.0.0'
        header['SIMULATED'] = True
        
        # Add WCS to header
        header.update(wcs.to_header())
        
        # Additional metadata
        header['HISTORY'] = 'Simulated astronomical observation'
        header['HISTORY'] = f'Generated on {datetime.now().isoformat()}'
        header['COMMENT'] = 'This is a simulated FITS file for testing purposes'
        
        return header

    def generate_batch(self, output_dir: str, count: int, 
                      upload_to_s3: bool = False) -> List[str]:
        """Generate a batch of FITS files."""
        output_dir = Path(output_dir)
        output_dir.mkdir(parents=True, exist_ok=True)
        
        generated_files = []
        
        logger.info(f"Generating {count} FITS files in {output_dir}")
        
        for i in tqdm(range(count), desc="Generating FITS files"):
            # Generate unique filename
            timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
            filename = f"sim_obs_{timestamp}_{i:04d}.fits"
            output_path = output_dir / filename
            
            # Generate random observation parameters
            ra = self.random_state.uniform(0, 360)
            dec = self.random_state.uniform(-30, 60)  # Observable from most sites
            exposure = self.random_state.choice([60, 120, 300, 600, 900])
            
            try:
                file_path = self.generate_fits_file(
                    str(output_path),
                    target_ra=ra,
                    target_dec=dec,
                    exposure_time=exposure
                )
                generated_files.append(file_path)
                
                # Upload to S3 if requested
                if upload_to_s3 and self.config.get('s3_bucket'):
                    self._upload_to_s3(file_path)
                    
            except Exception as e:
                logger.error(f"Failed to generate {filename}: {e}")
                
        logger.info(f"Successfully generated {len(generated_files)} FITS files")
        return generated_files

    def _upload_to_s3(self, file_path: str):
        """Upload generated FITS file to S3."""
        if not self.config.get('s3_bucket'):
            return
            
        try:
            s3_client = boto3.client('s3')
            file_path = Path(file_path)
            s3_key = f"raw-data/fits/{file_path.name}"
            
            s3_client.upload_file(
                str(file_path),
                self.config['s3_bucket'],
                s3_key
            )
            logger.debug(f"Uploaded {file_path.name} to s3://{self.config['s3_bucket']}/{s3_key}")
            
        except Exception as e:
            logger.error(f"Failed to upload {file_path} to S3: {e}")


def main():
    """Command-line interface for the FITS generator."""
    parser = argparse.ArgumentParser(description="Generate realistic astronomical FITS files")
    
    parser.add_argument('--output-dir', '-o', required=True,
                       help='Output directory for FITS files')
    parser.add_argument('--count', '-c', type=int, default=10,
                       help='Number of FITS files to generate')
    parser.add_argument('--config', help='Configuration YAML file')
    parser.add_argument('--upload-s3', action='store_true',
                       help='Upload generated files to S3')
    parser.add_argument('--verbose', '-v', action='store_true',
                       help='Enable verbose logging')
    
    args = parser.parse_args()
    
    if args.verbose:
        logging.getLogger().setLevel(logging.DEBUG)
    
    try:
        generator = AstronomicalFITSGenerator(args.config)
        generated_files = generator.generate_batch(
            args.output_dir,
            args.count,
            upload_to_s3=args.upload_s3
        )
        
        print(f"\nGenerated {len(generated_files)} FITS files:")
        for file_path in generated_files[:10]:  # Show first 10
            print(f"  {file_path}")
        if len(generated_files) > 10:
            print(f"  ... and {len(generated_files) - 10} more")
            
    except Exception as e:
        logger.error(f"Error generating FITS files: {e}")
        sys.exit(1)


if __name__ == '__main__':
    main()