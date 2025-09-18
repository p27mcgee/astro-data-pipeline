-- Enable PostGIS extension for spatial operations
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;

-- Create enum types for astronomical classifications
CREATE TYPE object_type AS ENUM (
    'STAR',
    'GALAXY',
    'NEBULA',
    'QUASAR',
    'ASTEROID',
    'COMET',
    'PLANET',
    'SATELLITE',
    'COSMIC_RAY',
    'ARTIFACT',
    'UNKNOWN'
);

CREATE TYPE photometric_system AS ENUM (
    'JOHNSON_UBV',
    'SDSS_ugriz',
    'HST_WFC3',
    'JWST_NIRCam',
    'GAIA_GBP_G_GRP',
    'AB_MAGNITUDE',
    'VEGA_MAGNITUDE'
);

CREATE TYPE observation_mode AS ENUM (
    'IMAGING',
    'SPECTROSCOPY',
    'PHOTOMETRY',
    'ASTROMETRY',
    'SURVEY'
);

-- Main observations table
CREATE TABLE observations (
    id BIGSERIAL PRIMARY KEY,
    observation_id VARCHAR(64) UNIQUE NOT NULL,
    
    -- Basic observation metadata
    telescope VARCHAR(100) NOT NULL,
    instrument VARCHAR(100) NOT NULL,
    filter VARCHAR(50),
    observation_date TIMESTAMPTZ NOT NULL,
    observation_mode observation_mode NOT NULL,
    exposure_time DOUBLE PRECISION,
    
    -- Coordinates and pointing
    target_name VARCHAR(200),
    ra DOUBLE PRECISION NOT NULL, -- Right Ascension in degrees
    dec DOUBLE PRECISION NOT NULL, -- Declination in degrees
    galactic_l DOUBLE PRECISION, -- Galactic longitude
    galactic_b DOUBLE PRECISION, -- Galactic latitude
    position GEOMETRY(POINT, 4326), -- Spatial index for efficient querying
    
    -- Field of view
    field_width_arcmin DOUBLE PRECISION,
    field_height_arcmin DOUBLE PRECISION,
    field_area_sq_arcmin DOUBLE PRECISION,
    
    -- Environmental conditions
    airmass DOUBLE PRECISION,
    seeing_arcsec DOUBLE PRECISION,
    sky_brightness DOUBLE PRECISION,
    weather_conditions TEXT,
    moon_phase DOUBLE PRECISION,
    moon_separation_deg DOUBLE PRECISION,
    
    -- Processing information
    processing_pipeline VARCHAR(100),
    processing_version VARCHAR(50),
    processing_date TIMESTAMPTZ,
    calibration_level INTEGER DEFAULT 1, -- 1=raw, 2=processed, 3=science-ready
    quality_flag INTEGER DEFAULT 0, -- Bitmask for quality issues
    
    -- Data files
    raw_file_path TEXT,
    processed_file_path TEXT,
    thumbnail_path TEXT,
    
    -- Metadata
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT valid_coordinates CHECK (ra >= 0 AND ra < 360 AND dec >= -90 AND dec <= 90),
    CONSTRAINT valid_exposure_time CHECK (exposure_time > 0),
    CONSTRAINT valid_airmass CHECK (airmass >= 1.0)
);

-- Astronomical objects catalog
CREATE TABLE astronomical_objects (
    id BIGSERIAL PRIMARY KEY,
    object_id VARCHAR(64) UNIQUE NOT NULL,
    
    -- Classification
    object_type object_type NOT NULL,
    classification_confidence DOUBLE PRECISION DEFAULT 0.5,
    
    -- Position (J2000 epoch)
    ra DOUBLE PRECISION NOT NULL,
    dec DOUBLE PRECISION NOT NULL,
    ra_error_mas DOUBLE PRECISION, -- milliarcseconds
    dec_error_mas DOUBLE PRECISION,
    position GEOMETRY(POINT, 4326),
    
    -- Proper motion (mas/year)
    pm_ra DOUBLE PRECISION DEFAULT 0,
    pm_dec DOUBLE PRECISION DEFAULT 0,
    pm_ra_error DOUBLE PRECISION,
    pm_dec_error DOUBLE PRECISION,
    
    -- Parallax
    parallax_mas DOUBLE PRECISION,
    parallax_error_mas DOUBLE PRECISION,
    distance_pc DOUBLE PRECISION, -- Derived from parallax
    
    -- Photometry
    magnitude DOUBLE PRECISION,
    magnitude_error DOUBLE PRECISION,
    photometric_system photometric_system,
    
    -- Multi-band photometry (JSON for flexibility)
    photometry JSONB,
    
    -- Physical properties
    effective_temperature DOUBLE PRECISION, -- Kelvin
    surface_gravity DOUBLE PRECISION, -- log g
    metallicity DOUBLE PRECISION, -- [Fe/H]
    
    -- Variability
    is_variable BOOLEAN DEFAULT FALSE,
    variability_period_days DOUBLE PRECISION,
    variability_amplitude DOUBLE PRECISION,
    variability_type VARCHAR(50),
    
    -- Cross-matching with catalogs
    gaia_source_id BIGINT,
    simbad_name VARCHAR(200),
    ned_name VARCHAR(200),
    usno_id VARCHAR(50),
    tycho_id VARCHAR(50),
    
    -- Detection and measurement details
    detection_significance DOUBLE PRECISION,
    flux_auto DOUBLE PRECISION, -- Automatic aperture flux
    flux_aper DOUBLE PRECISION[], -- Aperture photometry array
    flux_psf DOUBLE PRECISION, -- PSF photometry
    
    -- Shape parameters
    fwhm_arcsec DOUBLE PRECISION,
    elongation DOUBLE PRECISION,
    ellipticity DOUBLE PRECISION,
    position_angle_deg DOUBLE PRECISION,
    
    -- Quality flags
    quality_flags INTEGER DEFAULT 0,
    source_flags INTEGER DEFAULT 0,
    
    -- Metadata
    first_observed TIMESTAMPTZ,
    last_observed TIMESTAMPTZ,
    observation_count INTEGER DEFAULT 1,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT valid_coordinates CHECK (ra >= 0 AND ra < 360 AND dec >= -90 AND dec <= 90),
    CONSTRAINT valid_magnitude CHECK (magnitude BETWEEN -30 AND 50),
    CONSTRAINT valid_temperature CHECK (effective_temperature IS NULL OR effective_temperature > 0)
);

-- Detection events linking objects to observations
CREATE TABLE detections (
    id BIGSERIAL PRIMARY KEY,
    detection_id VARCHAR(64) UNIQUE NOT NULL,
    
    -- Foreign keys
    observation_id BIGINT NOT NULL REFERENCES observations(id) ON DELETE CASCADE,
    object_id BIGINT NOT NULL REFERENCES astronomical_objects(id) ON DELETE CASCADE,
    
    -- Detection position in image
    pixel_x DOUBLE PRECISION NOT NULL,
    pixel_y DOUBLE PRECISION NOT NULL,
    
    -- World coordinates for this detection
    ra DOUBLE PRECISION NOT NULL,
    dec DOUBLE PRECISION NOT NULL,
    ra_error_mas DOUBLE PRECISION,
    dec_error_mas DOUBLE PRECISION,
    
    -- Photometry for this detection
    magnitude DOUBLE PRECISION,
    magnitude_error DOUBLE PRECISION,
    flux DOUBLE PRECISION,
    flux_error DOUBLE PRECISION,
    
    -- Source extraction parameters
    background DOUBLE PRECISION,
    threshold DOUBLE PRECISION,
    peak_value DOUBLE PRECISION,
    
    -- Shape measurements
    fwhm_arcsec DOUBLE PRECISION,
    elongation DOUBLE PRECISION,
    ellipticity DOUBLE PRECISION,
    position_angle_deg DOUBLE PRECISION,
    
    -- Quality assessments
    detection_significance DOUBLE PRECISION,
    crowding_factor DOUBLE PRECISION,
    saturation_flag BOOLEAN DEFAULT FALSE,
    cosmic_ray_flag BOOLEAN DEFAULT FALSE,
    
    -- Flags
    extraction_flags INTEGER DEFAULT 0,
    photometry_flags INTEGER DEFAULT 0,
    
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT valid_pixel_coords CHECK (pixel_x >= 0 AND pixel_y >= 0),
    CONSTRAINT valid_detection_coords CHECK (ra >= 0 AND ra < 360 AND dec >= -90 AND dec <= 90)
);

-- Catalog cross-matches for external reference
CREATE TABLE catalog_crossmatches (
    id BIGSERIAL PRIMARY KEY,
    object_id BIGINT NOT NULL REFERENCES astronomical_objects(id) ON DELETE CASCADE,
    
    catalog_name VARCHAR(100) NOT NULL,
    catalog_id VARCHAR(100) NOT NULL,
    separation_arcsec DOUBLE PRECISION NOT NULL,
    match_probability DOUBLE PRECISION DEFAULT 1.0,
    
    -- Catalog-specific data (flexible JSON)
    catalog_data JSONB,
    
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(object_id, catalog_name, catalog_id)
);

-- Image quality metrics
CREATE TABLE image_quality (
    id BIGSERIAL PRIMARY KEY,
    observation_id BIGINT NOT NULL REFERENCES observations(id) ON DELETE CASCADE,
    
    -- Basic quality metrics
    seeing_fwhm_arcsec DOUBLE PRECISION,
    ellipticity DOUBLE PRECISION,
    sky_background DOUBLE PRECISION,
    sky_noise DOUBLE PRECISION,
    
    -- Photometric quality
    zeropoint DOUBLE PRECISION,
    zeropoint_error DOUBLE PRECISION,
    limiting_magnitude DOUBLE PRECISION,
    saturation_level DOUBLE PRECISION,
    
    -- Astrometric quality
    astrometric_rms_mas DOUBLE PRECISION,
    number_reference_stars INTEGER,
    wcs_fit_rms_arcsec DOUBLE PRECISION,
    
    -- Overall quality score (0-100)
    quality_score DOUBLE PRECISION,
    
    -- Quality flags and issues
    quality_issues TEXT[],
    
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Performance indexes for efficient queries

-- Spatial indexes
CREATE INDEX idx_observations_position ON observations USING GIST (position);
CREATE INDEX idx_objects_position ON astronomical_objects USING GIST (position);

-- Coordinate indexes for cone searches
CREATE INDEX idx_observations_radec ON observations (ra, dec);
CREATE INDEX idx_objects_radec ON astronomical_objects (ra, dec);

-- Time-based indexes
CREATE INDEX idx_observations_date ON observations (observation_date);
CREATE INDEX idx_objects_first_observed ON astronomical_objects (first_observed);
CREATE INDEX idx_objects_last_observed ON astronomical_objects (last_observed);

-- Classification and photometry indexes
CREATE INDEX idx_objects_type ON astronomical_objects (object_type);
CREATE INDEX idx_objects_magnitude ON astronomical_objects (magnitude);
CREATE INDEX idx_objects_variability ON astronomical_objects (is_variable);

-- Foreign key indexes
CREATE INDEX idx_detections_observation_id ON detections (observation_id);
CREATE INDEX idx_detections_object_id ON detections (object_id);
CREATE INDEX idx_crossmatches_object_id ON catalog_crossmatches (object_id);
CREATE INDEX idx_crossmatches_catalog ON catalog_crossmatches (catalog_name, catalog_id);
CREATE INDEX idx_quality_observation_id ON image_quality (observation_id);

-- Composite indexes for common queries
CREATE INDEX idx_observations_telescope_instrument ON observations (telescope, instrument);
CREATE INDEX idx_observations_date_quality ON observations (observation_date, quality_flag);
CREATE INDEX idx_objects_type_magnitude ON astronomical_objects (object_type, magnitude);
CREATE INDEX idx_detections_obs_ra_dec ON detections (observation_id, ra, dec);

-- JSONB indexes for efficient querying of flexible data
CREATE INDEX idx_objects_photometry ON astronomical_objects USING GIN (photometry);
CREATE INDEX idx_crossmatches_catalog_data ON catalog_crossmatches USING GIN (catalog_data);

-- Trigger to automatically update position geometry from ra/dec
CREATE OR REPLACE FUNCTION update_position_geometry()
RETURNS TRIGGER AS $$
BEGIN
    NEW.position = ST_Point(NEW.ra, NEW.dec);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER observations_position_trigger
    BEFORE INSERT OR UPDATE ON observations
    FOR EACH ROW
    EXECUTE FUNCTION update_position_geometry();

CREATE TRIGGER objects_position_trigger
    BEFORE INSERT OR UPDATE ON astronomical_objects
    FOR EACH ROW
    EXECUTE FUNCTION update_position_geometry();

-- Function for cone search queries
CREATE OR REPLACE FUNCTION cone_search(
    center_ra DOUBLE PRECISION,
    center_dec DOUBLE PRECISION,
    radius_arcsec DOUBLE PRECISION
)
RETURNS TABLE(
    object_id VARCHAR,
    ra DOUBLE PRECISION,
    dec DOUBLE PRECISION,
    separation_arcsec DOUBLE PRECISION
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        ao.object_id,
        ao.ra,
        ao.dec,
        ST_Distance(
            ST_Point(center_ra, center_dec)::geography,
            ao.position::geography
        ) / 1000.0 * 206265 AS separation_arcsec -- Convert to arcseconds
    FROM astronomical_objects ao
    WHERE ST_DWithin(
        ST_Point(center_ra, center_dec)::geography,
        ao.position::geography,
        radius_arcsec / 206265.0 * 1000 -- Convert arcsec to meters
    )
    ORDER BY separation_arcsec;
END;
$$ LANGUAGE plpgsql;

-- Create view for commonly used object summary
CREATE VIEW object_summary AS
SELECT 
    ao.object_id,
    ao.object_type,
    ao.ra,
    ao.dec,
    ao.magnitude,
    ao.photometric_system,
    ao.is_variable,
    ao.gaia_source_id,
    ao.observation_count,
    ao.first_observed,
    ao.last_observed,
    COUNT(d.id) as detection_count,
    AVG(d.magnitude) as mean_magnitude,
    STDDEV(d.magnitude) as magnitude_stddev
FROM astronomical_objects ao
LEFT JOIN detections d ON ao.id = d.object_id
GROUP BY ao.id;

-- Insert metadata about this schema version
CREATE TABLE schema_version (
    version VARCHAR(20) PRIMARY KEY,
    description TEXT,
    applied_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO schema_version (version, description) 
VALUES ('1.0.0', 'Initial astronomical catalog schema with PostGIS spatial support');