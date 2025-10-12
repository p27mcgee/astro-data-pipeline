#!/bin/bash
#
# Fetch FITS test files and associated catalog data for E2E testing
# Usage: ./fetch_test_data.sh
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

TEST_DATA_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/test-data"
FITS_DIR="${TEST_DATA_DIR}/fits"
CATALOG_DIR="${TEST_DATA_DIR}/catalogs"

echo -e "${GREEN}Creating test data directories...${NC}"
mkdir -p "${FITS_DIR}"
mkdir -p "${CATALOG_DIR}"

# ============================================
# Download FITS Sample Files
# ============================================

echo -e "${GREEN}Downloading HST WFPC2 sample FITS file...${NC}"
if [ ! -f "${FITS_DIR}/hst_wfpc2_sample.fits" ]; then
    wget -q --show-progress \
        http://fits.gsfc.nasa.gov/samples/WFPC2u5780205r_c0fx.fits \
        -O "${FITS_DIR}/hst_wfpc2_sample.fits"
    echo -e "${GREEN}✓ Downloaded HST WFPC2 sample (800x800x4)${NC}"
else
    echo -e "${YELLOW}⊙ HST WFPC2 sample already exists${NC}"
fi

echo -e "${GREEN}Downloading HST WFPC2 mosaic FITS file...${NC}"
if [ ! -f "${FITS_DIR}/hst_wfpc2_mosaic.fits" ]; then
    wget -q --show-progress \
        http://fits.gsfc.nasa.gov/samples/WFPC2ASSNu5780205bx.fits \
        -O "${FITS_DIR}/hst_wfpc2_mosaic.fits"
    echo -e "${GREEN}✓ Downloaded HST WFPC2 mosaic (1600x1600)${NC}"
else
    echo -e "${YELLOW}⊙ HST WFPC2 mosaic already exists${NC}"
fi

echo -e "${GREEN}Downloading test FITS with standard keywords...${NC}"
if [ ! -f "${FITS_DIR}/test_keywords.fits" ]; then
    wget -q --show-progress \
        http://fits.gsfc.nasa.gov/samples/testkeys.fits \
        -O "${FITS_DIR}/test_keywords.fits"
    echo -e "${GREEN}✓ Downloaded test keywords FITS${NC}"
else
    echo -e "${YELLOW}⊙ Test keywords FITS already exists${NC}"
fi

# ============================================
# Download Associated Catalog Data
# ============================================

echo -e "${GREEN}Fetching HSC catalog data for test observations...${NC}"

# Query HSC for catalog data around the WFPC2 observation
# Note: This requires the observation coordinates or target name
# For demonstration, we'll create a sample catalog format

cat > "${CATALOG_DIR}/hst_wfpc2_catalog.csv" <<'EOF'
MatchID,RA,Dec,MagAper2,CI,Flags,NumFilters,NumVisits,NumImages
1,150.12345,2.34567,18.5,1.2,0,3,5,15
2,150.12456,2.34678,19.2,0.8,0,3,5,15
3,150.12567,2.34789,20.1,1.5,0,3,5,15
4,150.12678,2.34890,17.8,0.6,0,3,5,15
5,150.12789,2.35001,21.3,1.8,0,3,5,15
EOF

echo -e "${GREEN}✓ Created sample HSC catalog${NC}"

# ============================================
# Generate Metadata Summary
# ============================================

cat > "${TEST_DATA_DIR}/README.md" <<'EOF'
# Test Data for Astronomical Data Pipeline

## FITS Files

### 1. HST WFPC2 Sample (`fits/hst_wfpc2_sample.fits`)
- **Source**: NASA FITS Support Office
- **URL**: http://fits.gsfc.nasa.gov/samples/WFPC2u5780205r_c0fx.fits
- **Format**: 800 × 800 × 4 primary array (4 CCD images)
- **Instrument**: Hubble Space Telescope Wide Field Planetary Camera 2
- **Observation ID**: u5780205r
- **Use Case**: Calibration pipeline testing (dark, flat, cosmic rays)

### 2. HST WFPC2 Mosaic (`fits/hst_wfpc2_mosaic.fits`)
- **Source**: NASA FITS Support Office
- **URL**: http://fits.gsfc.nasa.gov/samples/WFPC2ASSNu5780205bx.fits
- **Format**: 1600 × 1600 primary array mosaic
- **Description**: Mosaic constructed from 4 individual CCD chips
- **Use Case**: Large image processing and memory optimization testing

### 3. Test Keywords FITS (`fits/test_keywords.fits`)
- **Source**: NASA FITS Support Office
- **URL**: http://fits.gsfc.nasa.gov/samples/testkeys.fits
- **Use Case**: FITS header parsing and metadata validation

## Catalog Data

### HSC Catalog (`catalogs/hst_wfpc2_catalog.csv`)
- **Format**: CSV with HSC standard columns
- **Columns**: MatchID, RA, Dec, MagAper2, CI, Flags, NumFilters, NumVisits, NumImages
- **Source**: Sample data in HSC format (for testing catalog ingestion)

## Usage in Tests

### Docker Compose
Mount this directory as a volume:
```yaml
volumes:
  - ./test-data:/test-data:ro
```

### Integration Tests
```java
@Test
public void testFitsProcessing() {
    String testFits = "/test-data/fits/hst_wfpc2_sample.fits";
    ProcessingResult result = fitsProcessingService.process(testFits);
    assertThat(result.getStatus()).isEqualTo(Status.SUCCESS);
}
```

### End-to-End Tests
```bash
# Upload test FITS to S3
aws s3 cp test-data/fits/hst_wfpc2_sample.fits \
  s3://astro-test-bucket/input/

# Trigger processing
curl -X POST http://localhost:8080/api/v1/processing/jobs \
  -d '{"inputPath": "s3://astro-test-bucket/input/hst_wfpc2_sample.fits"}'
```

## Updating Test Data

To refresh test data:
```bash
./scripts/fetch_test_data.sh
```

## Attribution

Data provided by:
- NASA Goddard Space Flight Center FITS Support Office
- Space Telescope Science Institute (STScI)
- Hubble Legacy Archive

When publishing results using this data, please acknowledge:
"Based on observations made with the NASA/ESA Hubble Space Telescope,
obtained from the data archive at the Space Telescope Science Institute."
EOF

echo -e "${GREEN}✓ Created test data README${NC}"

# ============================================
# Summary
# ============================================

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Test Data Fetch Complete!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "FITS files location: ${YELLOW}${FITS_DIR}/${NC}"
echo -e "Catalog data location: ${YELLOW}${CATALOG_DIR}/${NC}"
echo ""
echo "Files downloaded:"
ls -lh "${FITS_DIR}" | tail -n +2 | awk '{printf "  - %-40s %5s\n", $9, $5}'
echo ""
echo -e "${GREEN}Ready for end-to-end testing!${NC}"
echo ""
echo "Next steps:"
echo "  1. Review ${TEST_DATA_DIR}/README.md for usage examples"
echo "  2. Update docker-compose.yml to mount test-data volume"
echo "  3. Run integration tests with real FITS data"