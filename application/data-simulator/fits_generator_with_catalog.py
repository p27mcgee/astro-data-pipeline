#!/usr/bin/env python3
"""
Enhanced FITS Generator with Catalog Export

Extension to the original fits_generator.py that tracks and exports
source catalogs (ground truth) for testing the processing pipeline.

Usage:
    from fits_generator_with_catalog import AstronomicalFITSGeneratorWithCatalog

    generator = AstronomicalFITSGeneratorWithCatalog()
    fits_file, catalog = generator.generate_with_catalog("test.fits")
"""

import sys
from pathlib import Path
import pandas as pd
import numpy as np
from typing import Dict, List, Tuple

# Import the base generator
sys.path.insert(0, str(Path(__file__).parent))
from fits_generator import AstronomicalFITSGenerator


class AstronomicalFITSGeneratorWithCatalog(AstronomicalFITSGenerator):
    """Extended FITS generator that tracks and exports source catalogs."""

    def __init__(self, config_path: str = None):
        super().__init__(config_path)
        self.source_catalog = []

    def generate_with_catalog(
        self,
        output_path: str,
        target_ra: float = None,
        target_dec: float = None,
        exposure_time: float = 300.0,
        telescope: str = None,
        instrument: str = None,
        filter_name: str = None,
        export_catalog: bool = True
    ) -> Tuple[str, pd.DataFrame]:
        """
        Generate FITS file with accompanying source catalog.

        Returns:
            Tuple of (fits_path, catalog_dataframe)
        """
        # Reset catalog for new image
        self.source_catalog = []

        # Generate FITS file (this will populate self.source_catalog)
        fits_path = self.generate_fits_file(
            output_path=output_path,
            target_ra=target_ra,
            target_dec=target_dec,
            exposure_time=exposure_time,
            telescope=telescope,
            instrument=instrument,
            filter_name=filter_name
        )

        # Create DataFrame from collected sources
        catalog_df = pd.DataFrame(self.source_catalog)

        if export_catalog and len(catalog_df) > 0:
            # Export catalog to CSV
            catalog_path = Path(output_path).with_suffix('.csv')
            catalog_df.to_csv(catalog_path, index=False)
            print(f"✓ Exported catalog: {catalog_path} ({len(catalog_df)} sources)")

        return fits_path, catalog_df

    def _add_stars(self, image: np.ndarray, center_ra: float, center_dec: float):
        """Add stars and record them in catalog."""
        height, width = image.shape
        pixel_scale = self.config['pixel_scale'] / 3600.0
        field_area = (height * pixel_scale) * (width * pixel_scale) * 3600

        star_density = self.config['star_density']
        num_stars = self.random_state.poisson(star_density * field_area)

        for star_id in range(num_stars):
            # Random position
            x = self.random_state.uniform(50, width - 50)
            y = self.random_state.uniform(50, height - 50)

            # Stellar magnitude
            magnitude = self._generate_stellar_magnitude()
            flux = self._magnitude_to_flux(magnitude)

            # FWHM
            fwhm = self.random_state.normal(2.0, 0.3)
            fwhm = max(1.0, fwhm)

            # Convert pixel position to RA/Dec
            pixel_scale_deg = self.config['pixel_scale'] / 3600.0
            ra = center_ra + (x - width/2) * pixel_scale_deg
            dec = center_dec + (y - height/2) * pixel_scale_deg

            # Record in catalog
            self.source_catalog.append({
                'source_id': f'STAR_{star_id:05d}',
                'source_type': 'STAR',
                'x_pixel': x,
                'y_pixel': y,
                'ra': ra,
                'dec': dec,
                'magnitude': magnitude,
                'flux': flux,
                'fwhm': fwhm,
                'is_extended': False,
                'sersic_n': None,
                'effective_radius': None,
                'axis_ratio': None,
                'position_angle': None
            })

            # Add to image
            self._add_stellar_psf(image, x, y, flux, fwhm)

    def _add_galaxies(self, image: np.ndarray, center_ra: float, center_dec: float):
        """Add galaxies and record them in catalog."""
        height, width = image.shape
        pixel_scale = self.config['pixel_scale'] / 3600.0
        field_area = (height * pixel_scale) * (width * pixel_scale) * 3600

        galaxy_density = self.config['galaxy_density']
        num_galaxies = self.random_state.poisson(galaxy_density * field_area)

        galaxy_id_offset = len([s for s in self.source_catalog if s['source_type'] == 'STAR'])

        for galaxy_id in range(num_galaxies):
            x = self.random_state.uniform(100, width - 100)
            y = self.random_state.uniform(100, height - 100)

            magnitude = self._generate_galaxy_magnitude()
            flux = self._magnitude_to_flux(magnitude)

            effective_radius = self.random_state.exponential(3.0) + 1.0
            axis_ratio = self.random_state.uniform(0.3, 1.0)
            position_angle = self.random_state.uniform(0, 180)
            sersic_n = self.random_state.choice([1.0, 4.0], p=[0.7, 0.3])

            # Convert to RA/Dec
            pixel_scale_deg = self.config['pixel_scale'] / 3600.0
            ra = center_ra + (x - width/2) * pixel_scale_deg
            dec = center_dec + (y - height/2) * pixel_scale_deg

            # Record in catalog
            self.source_catalog.append({
                'source_id': f'GALAXY_{galaxy_id:05d}',
                'source_type': 'GALAXY',
                'x_pixel': x,
                'y_pixel': y,
                'ra': ra,
                'dec': dec,
                'magnitude': magnitude,
                'flux': flux,
                'fwhm': None,
                'is_extended': True,
                'sersic_n': sersic_n,
                'effective_radius': effective_radius,
                'axis_ratio': axis_ratio,
                'position_angle': position_angle
            })

            # Add to image
            self._add_galaxy_profile(
                image, x, y, flux, effective_radius,
                axis_ratio, position_angle, sersic_n
            )

    def _add_cosmic_rays(self, image: np.ndarray, exposure_time: float):
        """Add cosmic rays and record them in catalog."""
        height, width = image.shape
        total_pixels = height * width

        cr_rate = self.config['cosmic_ray_rate']
        expected_hits = cr_rate * total_pixels * (exposure_time / 3600.0)
        num_cosmic_rays = self.random_state.poisson(expected_hits)

        for cr_id in range(num_cosmic_rays):
            x = self.random_state.randint(0, width)
            y = self.random_state.randint(0, height)

            # Cosmic ray energy (exponential distribution)
            energy = self.random_state.exponential(1000.0) + 100.0

            # Record in catalog
            self.source_catalog.append({
                'source_id': f'CR_{cr_id:05d}',
                'source_type': 'COSMIC_RAY',
                'x_pixel': x,
                'y_pixel': y,
                'ra': None,
                'dec': None,
                'magnitude': None,
                'flux': energy,
                'fwhm': None,
                'is_extended': False,
                'sersic_n': None,
                'effective_radius': None,
                'axis_ratio': None,
                'position_angle': None
            })

            # Add to image
            image[y, x] += energy

            # Add random trail
            if self.random_state.random() > 0.5:
                trail_length = self.random_state.randint(2, 8)
                angle = self.random_state.uniform(0, 2 * np.pi)

                for step in range(1, trail_length):
                    trail_x = int(x + step * np.cos(angle))
                    trail_y = int(y + step * np.sin(angle))

                    if 0 <= trail_x < width and 0 <= trail_y < height:
                        trail_energy = energy * (1.0 - step / trail_length) * 0.5
                        image[trail_y, trail_x] += trail_energy


def main():
    """Example usage of the enhanced generator."""
    import argparse

    parser = argparse.ArgumentParser(
        description="Generate FITS files with source catalogs"
    )
    parser.add_argument('--output-dir', '-o', required=True,
                       help='Output directory')
    parser.add_argument('--count', '-c', type=int, default=1,
                       help='Number of files to generate')
    parser.add_argument('--config', help='Configuration YAML file')

    args = parser.parse_args()

    generator = AstronomicalFITSGeneratorWithCatalog(args.config)
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    print(f"\nGenerating {args.count} FITS files with catalogs...")
    print("=" * 60)

    for i in range(args.count):
        fits_path = output_dir / f"observation_{i:04d}.fits"

        fits_file, catalog = generator.generate_with_catalog(
            str(fits_path),
            export_catalog=True
        )

        print(f"\n{i+1}/{args.count}: {fits_path.name}")
        print(f"  Sources: {len(catalog)} total")
        print(f"    - Stars: {len(catalog[catalog['source_type'] == 'STAR'])}")
        print(f"    - Galaxies: {len(catalog[catalog['source_type'] == 'GALAXY'])}")
        print(f"    - Cosmic Rays: {len(catalog[catalog['source_type'] == 'COSMIC_RAY'])}")

    print("\n" + "=" * 60)
    print(f"✓ Complete! Generated {args.count} FITS files with catalogs")


if __name__ == '__main__':
    main()
