#!/usr/bin/env -S bash -c '"$(dirname "$0")/../venv/bin/python" "$0" "$@"'
# Alternative: Run as: ./venv/bin/python scripts/test_catalog_evaluation.py
"""
End-to-End Catalog Evaluation Test

Tests the astronomical data pipeline by:
1. Processing publicly available FITS files
2. Extracting generated catalog
3. Comparing with expected results (HSC catalog or simulated ground truth)
"""

import argparse
import json
import requests
import time
import sys
from pathlib import Path
from typing import Dict, List, Optional

try:
    from astropy.io import fits
    from astropy.wcs import WCS
    import numpy as np
except ImportError:
    print("Error: astropy not installed. Run: pip install astropy")
    sys.exit(1)


class CatalogEvaluator:
    """Evaluate catalog generation against ground truth."""

    def __init__(self,
                 image_processor_url: str = "http://localhost:8082",
                 catalog_service_url: str = "http://localhost:8081",
                 s3_endpoint: str = "http://localhost:4566"):
        self.image_processor_url = image_processor_url
        self.catalog_service_url = catalog_service_url
        self.s3_endpoint = s3_endpoint

    def inspect_fits(self, fits_path: str) -> Dict:
        """Extract information from FITS file."""
        print(f"\n📊 Inspecting FITS file: {fits_path}")
        print("=" * 60)

        with fits.open(fits_path) as hdul:
            hdul.info()

            # Get primary header
            header = hdul[0].header

            # Extract key information
            info = {
                'filename': Path(fits_path).name,
                'instrument': header.get('INSTRUME', 'UNKNOWN'),
                'telescope': header.get('TELESCOP', 'UNKNOWN'),
                'filter': header.get('FILTNAM1', header.get('FILTER', 'UNKNOWN')),
                'exptime': header.get('EXPTIME', 0.0),
                'ra': header.get('RA_TARG', header.get('CRVAL1', 0.0)),
                'dec': header.get('DEC_TARG', header.get('CRVAL2', 0.0)),
                'date_obs': header.get('DATE-OBS', 'UNKNOWN'),
            }

            # Get image dimensions
            if len(hdul[0].data.shape) == 3:
                # Multi-extension (e.g., WFPC2 with 4 chips)
                info['dimensions'] = f"{hdul[0].data.shape} (multi-chip)"
                info['n_chips'] = hdul[0].data.shape[0]
            else:
                info['dimensions'] = f"{hdul[0].data.shape}"
                info['n_chips'] = 1

            print(f"\n  Telescope: {info['telescope']}")
            print(f"  Instrument: {info['instrument']}")
            print(f"  Filter: {info['filter']}")
            print(f"  Exposure: {info['exptime']}s")
            print(f"  RA/Dec: {info['ra']:.4f}, {info['dec']:.4f}")
            print(f"  Dimensions: {info['dimensions']}")
            print(f"  Observation Date: {info['date_obs']}")

            return info

    def upload_to_s3(self, fits_path: str, bucket: str = "astro-raw-data") -> str:
        """Upload FITS file to LocalStack S3."""
        import boto3

        print(f"\n📤 Uploading to S3: s3://{bucket}/{Path(fits_path).name}")

        s3_client = boto3.client(
            's3',
            endpoint_url=self.s3_endpoint,
            aws_access_key_id='test',
            aws_secret_access_key='test',
            region_name='us-east-1'
        )

        object_key = Path(fits_path).name
        s3_client.upload_file(fits_path, bucket, object_key)

        print(f"  ✓ Uploaded: s3://{bucket}/{object_key}")
        return object_key

    def trigger_processing(self, bucket: str, object_key: str) -> str:
        """Trigger processing job via API."""
        print(f"\n🚀 Triggering processing job...")

        url = f"{self.image_processor_url}/api/v1/processing/jobs"
        payload = {
            "inputBucket": bucket,
            "inputObjectKey": object_key,
            "processingType": "FULL_CALIBRATION",
            "priority": 5,
            "userId": "test-user",
            "description": "Catalog evaluation test - publicly available FITS file",
            "enableDarkSubtraction": True,
            "enableFlatCorrection": True,
            "enableCosmicRayRemoval": True,
            "enableQualityAssessment": True
        }

        print(f"  URL: {url}")
        print(f"  Payload: {json.dumps(payload, indent=2)}")

        response = requests.post(url, json=payload, auth=('admin', 'admin'))

        if response.status_code == 200 or response.status_code == 202:
            job_data = response.json()
            job_id = job_data.get('jobId')
            print(f"  ✓ Job created: {job_id}")
            print(f"  Status: {job_data.get('status')}")
            return job_id
        else:
            print(f"  ✗ Error: {response.status_code}")
            print(f"  Response: {response.text}")
            return None

    def monitor_job(self, job_id: str, timeout: int = 300) -> Dict:
        """Monitor job until completion."""
        print(f"\n⏳ Monitoring job: {job_id}")

        url = f"{self.image_processor_url}/api/v1/processing/jobs/{job_id}"

        start_time = time.time()
        while time.time() - start_time < timeout:
            response = requests.get(url, auth=('admin', 'admin'))

            if response.status_code == 200:
                job_data = response.json()
                status = job_data.get('status')

                print(f"  Status: {status}", end='\r')

                if status == 'COMPLETED':
                    print(f"\n  ✓ Job completed successfully!")
                    return job_data
                elif status == 'FAILED':
                    print(f"\n  ✗ Job failed: {job_data.get('errorMessage')}")
                    return job_data

                time.sleep(5)
            else:
                print(f"\n  ✗ Error checking status: {response.status_code}")
                return None

        print(f"\n  ⏱️ Timeout after {timeout}s")
        return None

    def get_generated_catalog(self, job_id: str) -> Optional[List[Dict]]:
        """Retrieve generated catalog from catalog service."""
        print(f"\n📋 Retrieving generated catalog...")

        # Query catalog service for sources from this job
        url = f"{self.catalog_service_url}/api/v1/catalog/sources"
        params = {'jobId': job_id, 'limit': 1000}

        response = requests.get(url, params=params, auth=('admin', 'admin'))

        if response.status_code == 200:
            catalog = response.json()
            sources = catalog.get('sources', [])
            print(f"  ✓ Retrieved {len(sources)} sources from catalog")
            return sources
        else:
            print(f"  ✗ Error retrieving catalog: {response.status_code}")
            return None

    def fetch_hsc_catalog(self, ra: float, dec: float, radius: float = 0.1) -> Optional[List[Dict]]:
        """Fetch Hubble Source Catalog data for comparison."""
        print(f"\n🔍 Fetching HSC catalog for comparison...")
        print(f"  RA/Dec: {ra:.4f}, {dec:.4f}")
        print(f"  Radius: {radius}°")

        url = "https://catalogs.mast.stsci.edu/api/v0.1/hsc/summary.csv"
        params = {
            'ra': ra,
            'dec': dec,
            'radius': radius,
            'nr': 100,
            'columns': 'MatchID,RA,Dec,MagAper2,CI,NumFilters'
        }

        try:
            response = requests.get(url, params=params, timeout=30)
            if response.status_code == 200 and len(response.text.strip()) > 0:
                # Parse CSV
                lines = response.text.strip().split('\n')
                headers = lines[0].split(',')

                catalog = []
                for line in lines[1:]:
                    values = line.split(',')
                    source = dict(zip(headers, values))
                    catalog.append(source)

                print(f"  ✓ Retrieved {len(catalog)} sources from HSC")
                return catalog
            else:
                print(f"  ⚠️  No HSC data available for this region")
                return []
        except Exception as e:
            print(f"  ⚠️  HSC query failed: {e}")
            return []

    def compare_catalogs(self, generated: List[Dict], ground_truth: Optional[List[Dict]]) -> Dict:
        """Compare generated catalog with ground truth."""
        print(f"\n📊 Catalog Comparison")
        print("=" * 60)

        results = {
            'n_generated': len(generated),
            'n_ground_truth': len(ground_truth) if ground_truth else 0,
            'comparison_available': ground_truth is not None and len(ground_truth) > 0
        }

        print(f"  Generated sources: {results['n_generated']}")

        if results['comparison_available']:
            print(f"  Ground truth sources: {results['n_ground_truth']}")

            # Basic statistics
            if results['n_generated'] > 0:
                gen_ra = [float(s.get('ra', 0)) for s in generated if s.get('ra')]
                gen_dec = [float(s.get('dec', 0)) for s in generated if s.get('dec')]

                if gen_ra and gen_dec:
                    print(f"\n  Generated Catalog Statistics:")
                    print(f"    RA range: {min(gen_ra):.4f} - {max(gen_ra):.4f}")
                    print(f"    Dec range: {min(gen_dec):.4f} - {max(gen_dec):.4f}")

            if results['n_ground_truth'] > 0:
                gt_ra = [float(s.get('RA', 0)) for s in ground_truth if s.get('RA')]
                gt_dec = [float(s.get('Dec', 0)) for s in ground_truth if s.get('Dec')]

                if gt_ra and gt_dec:
                    print(f"\n  Ground Truth Statistics:")
                    print(f"    RA range: {min(gt_ra):.4f} - {max(gt_ra):.4f}")
                    print(f"    Dec range: {min(gt_dec):.4f} - {max(gt_dec):.4f}")

            # Calculate detection rate (rough estimate)
            if results['n_ground_truth'] > 0:
                detection_rate = (results['n_generated'] / results['n_ground_truth']) * 100
                results['detection_rate'] = detection_rate
                print(f"\n  Detection Rate: {detection_rate:.1f}%")
        else:
            print(f"  ⚠️  No ground truth available for comparison")
            print(f"  Generated {results['n_generated']} sources (unvalidated)")

        return results


def main():
    parser = argparse.ArgumentParser(
        description="Test catalog generation with publicly available FITS data"
    )
    parser.add_argument(
        '--fits-file',
        required=True,
        help='Path to FITS file'
    )
    parser.add_argument(
        '--fetch-hsc',
        action='store_true',
        help='Fetch HSC catalog for comparison'
    )
    parser.add_argument(
        '--image-processor-url',
        default='http://localhost:8082',
        help='Image processor service URL'
    )
    parser.add_argument(
        '--catalog-service-url',
        default='http://localhost:8081',
        help='Catalog service URL'
    )

    args = parser.parse_args()

    # Initialize evaluator
    evaluator = CatalogEvaluator(
        image_processor_url=args.image_processor_url,
        catalog_service_url=args.catalog_service_url
    )

    print("\n" + "=" * 60)
    print("  CATALOG EVALUATION TEST")
    print("=" * 60)

    # Step 1: Inspect FITS
    fits_info = evaluator.inspect_fits(args.fits_file)

    # Step 2: Upload to S3
    object_key = evaluator.upload_to_s3(args.fits_file)

    # Step 3: Trigger processing
    job_id = evaluator.trigger_processing("astro-raw-data", object_key)
    if not job_id:
        print("\n❌ Failed to trigger processing job")
        sys.exit(1)

    # Step 4: Monitor job
    job_result = evaluator.monitor_job(job_id, timeout=300)
    if not job_result or job_result.get('status') != 'COMPLETED':
        print("\n❌ Job did not complete successfully")
        sys.exit(1)

    # Step 5: Get generated catalog
    generated_catalog = evaluator.get_generated_catalog(job_id)
    if generated_catalog is None:
        print("\n❌ Failed to retrieve generated catalog")
        sys.exit(1)

    # Step 6: Fetch ground truth (if requested)
    ground_truth = None
    if args.fetch_hsc:
        ground_truth = evaluator.fetch_hsc_catalog(
            fits_info['ra'],
            fits_info['dec'],
            radius=0.05
        )

    # Step 7: Compare catalogs
    comparison = evaluator.compare_catalogs(generated_catalog, ground_truth)

    # Summary
    print("\n" + "=" * 60)
    print("  TEST RESULTS")
    print("=" * 60)
    print(f"  FITS File: {fits_info['filename']}")
    print(f"  Job ID: {job_id}")
    print(f"  Status: ✅ COMPLETED")
    print(f"  Generated Sources: {comparison['n_generated']}")

    if comparison['comparison_available']:
        print(f"  Ground Truth Sources: {comparison['n_ground_truth']}")
        print(f"  Detection Rate: {comparison.get('detection_rate', 0):.1f}%")

    print("\n✅ Catalog evaluation test completed successfully!")


if __name__ == '__main__':
    main()