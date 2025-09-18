package org.stsci.astro.processor.dto;

import lombok.Builder;
import lombok.Data;
import nom.tam.fits.Header;

import java.nio.file.Path;

@Data
@Builder
public class FitsImageData {
    
    private float[][] imageData;
    private Header header;
    private int width;
    private int height;
    private String instrument;
    private String filter;
    private double exposureTime;
    private long fileSizeBytes;
    private Path tempFilePath;
    
    // Additional astronomical metadata
    private String objectName;
    private String observer;
    private String telescope;
    private String dateObserved;
    private double rightAscension;
    private double declination;
    private double airmass;
    private double seeing;
    private String weatherConditions;
    
    // Image statistics
    private double meanValue;
    private double medianValue;
    private double standardDeviation;
    private double minimumValue;
    private double maximumValue;
    private long totalPixels;
    private long saturatedPixels;
    
    // World Coordinate System info
    private boolean hasWcsInfo;
    private double crval1; // Reference RA
    private double crval2; // Reference Dec
    private double crpix1; // Reference pixel X
    private double crpix2; // Reference pixel Y
    private double cdelt1; // Pixel scale X
    private double cdelt2; // Pixel scale Y
    private double crota2; // Rotation angle
    
    public boolean isValid() {
        return imageData != null && width > 0 && height > 0 && header != null;
    }
    
    public long getImageSizeInBytes() {
        return (long) width * height * 4; // 4 bytes per float pixel
    }
    
    public double getPixelScale() {
        if (hasWcsInfo && cdelt1 != 0) {
            return Math.abs(cdelt1) * 3600; // Convert to arcseconds per pixel
        }
        return 0.0;
    }
    
    public String getImageDimensions() {
        return width + "x" + height;
    }
}