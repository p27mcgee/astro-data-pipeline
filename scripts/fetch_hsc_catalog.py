#!/usr/bin/env -S bash -c '"$(dirname "$0")/../venv/bin/python" "$0" "$@"'
# Alternative: Run as: ./venv/bin/python scripts/fetch_hsc_catalog.py
"""
Fetch Hubble Source Catalog data for test FITS files

This script queries the STScI MAST API to retrieve catalog data
for astronomical objects in the HST WFPC2 test images.

Usage:
    python fetch_hsc_catalog.py --ra 150.123 --dec 2.345 --radius 0.1
    python fetch_hsc_catalog.py --target "M51" --radius 0.5
"""

import argparse
import requests
import sys
from pathlib import Path


def query_hsc_catalog(ra=None, dec=None, target=None, radius=0.1, max_records=100):
    """
    Query Hubble Source Catalog via MAST API

    Args:
        ra: Right Ascension in degrees
        dec: Declination in degrees
        target: Target name (alternative to RA/Dec)
        radius: Search radius in degrees
        max_records: Maximum number of records to return

    Returns:
        CSV string with catalog data
    """
    base_url = "https://catalogs.mast.stsci.edu/api/v0.1/hsc/summary.csv"

    params = {
        "radius": radius,
        "nr": max_records,
        "columns": "MatchID,RA,Dec,MagAper2,CI,Flags,NumFilters,NumVisits,NumImages",
    }

    if target:
        params["target"] = target
    elif ra is not None and dec is not None:
        params["ra"] = ra
        params["dec"] = dec
    else:
        raise ValueError("Must provide either target name or RA/Dec coordinates")

    print(f"Querying HSC catalog...")
    print(f"  Parameters: {params}")

    try:
        response = requests.get(base_url, params=params, timeout=30)
        response.raise_for_status()

        if len(response.text.strip()) == 0:
            print("⚠️  No results found for this query")
            return None

        lines = response.text.strip().split('\n')
        print(f"✓ Retrieved {len(lines) - 1} catalog entries")

        return response.text

    except requests.exceptions.RequestException as e:
        print(f"❌ Error querying HSC catalog: {e}", file=sys.stderr)
        return None


def save_catalog(catalog_csv, output_path):
    """Save catalog data to file"""
    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    with open(output_path, 'w') as f:
        f.write(catalog_csv)

    print(f"✓ Saved catalog to: {output_path}")


def fetch_wfpc2_test_catalog():
    """
    Fetch catalog for the HST WFPC2 test observation u5780205r

    Note: Since we don't have the exact coordinates, we'll query
    a known region with HST observations as a demonstration
    """
    print("\nFetching catalog for HST WFPC2 test region...")
    print("=" * 60)

    # Example coordinates (NGC 4258 / M106 - well-observed with HST)
    # Replace with actual coordinates if known
    catalog = query_hsc_catalog(
        ra=184.7397,
        dec=47.3042,
        radius=0.02,  # ~1.2 arcminutes
        max_records=50
    )

    if catalog:
        output_dir = Path(__file__).parent.parent / "test-data" / "catalogs"
        save_catalog(catalog, output_dir / "hst_wfpc2_catalog.csv")
        return True

    return False


def main():
    parser = argparse.ArgumentParser(
        description="Fetch Hubble Source Catalog data for test images"
    )
    parser.add_argument(
        "--ra",
        type=float,
        help="Right Ascension in degrees"
    )
    parser.add_argument(
        "--dec",
        type=float,
        help="Declination in degrees"
    )
    parser.add_argument(
        "--target",
        type=str,
        help="Target name (e.g., 'M51', 'NGC 4258')"
    )
    parser.add_argument(
        "--radius",
        type=float,
        default=0.1,
        help="Search radius in degrees (default: 0.1)"
    )
    parser.add_argument(
        "--max-records",
        type=int,
        default=100,
        help="Maximum number of records (default: 100)"
    )
    parser.add_argument(
        "--output",
        type=str,
        default=None,
        help="Output file path (default: test-data/catalogs/hsc_catalog.csv)"
    )
    parser.add_argument(
        "--fetch-test-data",
        action="store_true",
        help="Fetch catalog for HST WFPC2 test observation"
    )

    args = parser.parse_args()

    # Fetch test data catalog
    if args.fetch_test_data:
        success = fetch_wfpc2_test_catalog()
        sys.exit(0 if success else 1)

    # Custom query
    if not args.target and (args.ra is None or args.dec is None):
        parser.print_help()
        print("\n❌ Error: Must provide either --target or both --ra and --dec")
        sys.exit(1)

    catalog = query_hsc_catalog(
        ra=args.ra,
        dec=args.dec,
        target=args.target,
        radius=args.radius,
        max_records=args.max_records
    )

    if catalog:
        output_path = args.output
        if not output_path:
            project_root = Path(__file__).parent.parent
            output_path = project_root / "test-data" / "catalogs" / "hsc_catalog.csv"

        save_catalog(catalog, output_path)
        print("\n✓ Catalog fetch complete!")
    else:
        print("\n❌ Failed to fetch catalog data")
        sys.exit(1)


if __name__ == "__main__":
    main()