#!/usr/bin/env python3
"""
Comprehensive Load Testing Suite for Astronomical Data Processing Pipeline
Advanced Locust-based performance testing with astronomical workload simulation
"""

import random
import json
import time
import statistics
from datetime import datetime, timedelta
from typing import Dict, List, Any, Optional
import uuid

from locust import HttpUser, TaskSet, task, between, events, tag
from locust.runners import MasterRunner, WorkerRunner
from locust.env import Environment
import logging

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class AstronomicalDataGenerator:
    """Generate realistic astronomical test data"""
    
    @staticmethod
    def generate_coordinates() -> tuple:
        """Generate realistic astronomical coordinates"""
        # Favor certain sky regions (Galactic plane, LMC, SMC, etc.)
        if random.random() < 0.3:  # 30% chance for interesting regions
            region = random.choice([
                {'ra_range': (80, 100), 'dec_range': (-70, -60)},    # LMC
                {'ra_range': (10, 30), 'dec_range': (-75, -65)},     # SMC
                {'ra_range': (265, 275), 'dec_range': (-30, -25)},   # Galactic center
                {'ra_range': (83, 87), 'dec_range': (21, 23)},       # Pleiades
            ])
            ra = random.uniform(*region['ra_range'])
            dec = random.uniform(*region['dec_range'])
        else:
            ra = random.uniform(0, 360)
            dec = random.uniform(-90, 90)
        
        return ra, dec
    
    @staticmethod
    def generate_magnitude() -> float:
        """Generate realistic magnitude distribution"""
        # Astronomical magnitude distribution (more faint objects)
        return random.lognormvariate(2.5, 0.8) + 8.0
    
    @staticmethod
    def generate_object_type() -> str:
        """Generate object type with realistic distribution"""
        return random.choices(
            ['STAR', 'GALAXY', 'QUASAR', 'NEBULA', 'ASTEROID', 'VARIABLE_STAR'],
            weights=[70, 20, 3, 4, 2, 1]
        )[0]
    
    @staticmethod
    def generate_fits_filename() -> str:
        """Generate realistic FITS filename"""
        telescope = random.choice(['HST', 'JWST', 'VLT', 'Gemini', 'Keck'])
        instrument = random.choice(['WFC3', 'MIRI', 'FORS2', 'NIRI', 'DEIMOS'])
        filter_name = random.choice(['F606W', 'F814W', 'F160W', 'F475W', 'F850LP'])
        
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        sequence = random.randint(1, 999)
        
        return f"{telescope}_{instrument}_{filter_name}_{timestamp}_{sequence:03d}.fits"

class ImageProcessingTasks(TaskSet):
    """Image processing workload tasks"""
    
    def on_start(self):
        """Initialize user session"""
        self.user_id = str(uuid.uuid4())[:8]
        self.fits_files = [AstronomicalDataGenerator.generate_fits_filename() for _ in range(10)]
        self.active_jobs = []
        
    @task(3)
    @tag('processing')
    def submit_fits_processing_job(self):
        """Submit FITS file processing job"""
        fits_file = random.choice(self.fits_files)
        processing_config = {
            "inputBucket": "astro-raw-data",
            "inputObjectKey": f"fits/{fits_file}",
            "processingType": random.choice([
                "BASIC_CALIBRATION", 
                "FULL_CALIBRATION", 
                "ADVANCED_PROCESSING",
                "PHOTOMETRY",
                "ASTROMETRY"
            ]),
            "outputBucket": "astro-processed-data",
            "userId": self.user_id,
            "priority": random.choice(["LOW", "NORMAL", "HIGH"]),
            "parameters": {
                "darkSubtraction": True,
                "flatCorrection": True,
                "cosmicRayRemoval": random.choice([True, False]),
                "backgroundSubtraction": random.choice([True, False]),
                "qualityAssessment": True
            }
        }
        
        with self.client.post(
            "/api/v1/processing/jobs/s3",
            json=processing_config,
            headers={"Content-Type": "application/json"},
            catch_response=True,
            name="submit_processing_job"
        ) as response:
            if response.status_code == 202:
                job_data = response.json()
                self.active_jobs.append(job_data.get('jobId'))
                response.success()
            else:
                response.failure(f"Job submission failed: {response.status_code}")
    
    @task(2) 
    @tag('processing')
    def check_job_status(self):
        """Check processing job status"""
        if not self.active_jobs:
            return
            
        job_id = random.choice(self.active_jobs)
        
        with self.client.get(
            f"/api/v1/processing/jobs/{job_id}/status",
            catch_response=True,
            name="check_job_status"
        ) as response:
            if response.status_code == 200:
                status_data = response.json()
                if status_data.get('status') in ['COMPLETED', 'FAILED', 'CANCELLED']:
                    self.active_jobs.remove(job_id)
                response.success()
            else:
                response.failure(f"Status check failed: {response.status_code}")
    
    @task(1)
    @tag('processing')
    def list_user_jobs(self):
        """List jobs for the current user"""
        with self.client.get(
            f"/api/v1/processing/jobs?userId={self.user_id}&limit=20",
            catch_response=True,
            name="list_user_jobs"
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Job listing failed: {response.status_code}")
    
    @task(1)
    @tag('processing')
    def cancel_job(self):
        """Cancel a random active job (simulate user behavior)"""
        if not self.active_jobs or random.random() > 0.1:  # 10% chance
            return
            
        job_id = random.choice(self.active_jobs)
        
        with self.client.delete(
            f"/api/v1/processing/jobs/{job_id}",
            catch_response=True,
            name="cancel_job"
        ) as response:
            if response.status_code == 200:
                self.active_jobs.remove(job_id)
                response.success()
            else:
                response.failure(f"Job cancellation failed: {response.status_code}")

class CatalogQueryTasks(TaskSet):
    """Astronomical catalog query tasks"""
    
    def on_start(self):
        """Initialize catalog query session"""
        self.query_history = []
    
    @task(4)
    @tag('catalog')
    def cone_search_query(self):
        """Perform cone search around coordinates"""
        ra, dec = AstronomicalDataGenerator.generate_coordinates()
        radius = random.choice([0.1, 0.2, 0.5, 1.0, 2.0, 5.0])  # degrees
        
        params = {
            "ra": ra,
            "dec": dec,
            "radius": radius,
            "limit": random.randint(50, 1000),
            "minMagnitude": random.uniform(8, 15),
            "maxMagnitude": random.uniform(18, 25)
        }
        
        self.query_history.append(params)
        
        with self.client.get(
            "/api/v1/catalog/cone-search",
            params=params,
            catch_response=True,
            name="catalog_cone_search"
        ) as response:
            if response.status_code == 200:
                results = response.json()
                num_results = len(results.get('objects', []))
                if num_results > 10000:  # Very large result set
                    response.failure(f"Result set too large: {num_results} objects")
                else:
                    response.success()
            else:
                response.failure(f"Cone search failed: {response.status_code}")
    
    @task(2)
    @tag('catalog')
    def magnitude_range_query(self):
        """Query objects by magnitude range"""
        min_mag = random.uniform(8, 18)
        max_mag = min_mag + random.uniform(2, 8)
        
        params = {
            "minMagnitude": min_mag,
            "maxMagnitude": max_mag,
            "objectType": random.choice(["STAR", "GALAXY", "ALL"]),
            "limit": random.randint(100, 2000)
        }
        
        with self.client.get(
            "/api/v1/catalog/magnitude-search",
            params=params,
            catch_response=True,
            name="catalog_magnitude_search"
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Magnitude search failed: {response.status_code}")
    
    @task(2)
    @tag('catalog')
    def object_type_query(self):
        """Query objects by type"""
        object_type = AstronomicalDataGenerator.generate_object_type()
        
        params = {
            "objectType": object_type,
            "limit": random.randint(50, 500),
            "sortBy": random.choice(["magnitude", "discoveryDate", "ra"]),
            "sortOrder": random.choice(["asc", "desc"])
        }
        
        with self.client.get(
            "/api/v1/catalog/object-type-search",
            params=params,
            catch_response=True,
            name="catalog_object_type_search"
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Object type search failed: {response.status_code}")
    
    @task(1)
    @tag('catalog')
    def cross_match_query(self):
        """Perform cross-matching with external catalog"""
        ra, dec = AstronomicalDataGenerator.generate_coordinates()
        
        params = {
            "ra": ra,
            "dec": dec,
            "radius": random.uniform(0.001, 0.1),  # arcseconds to arcminutes
            "externalCatalog": random.choice(["GAIA", "2MASS", "SDSS", "WISE"]),
            "matchTolerance": random.uniform(0.5, 3.0)  # arcseconds
        }
        
        with self.client.get(
            "/api/v1/catalog/cross-match",
            params=params,
            catch_response=True,
            name="catalog_cross_match"
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Cross-match failed: {response.status_code}")
    
    @task(1)
    @tag('catalog')
    def variability_search(self):
        """Search for variable objects"""
        params = {
            "minVariability": random.uniform(0.1, 0.5),
            "maxVariability": random.uniform(0.5, 2.0),
            "timePeriod": random.randint(30, 365),  # days
            "minObservations": random.randint(5, 20),
            "limit": random.randint(50, 200)
        }
        
        with self.client.get(
            "/api/v1/catalog/variability-search",
            params=params,
            catch_response=True,
            name="catalog_variability_search"
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Variability search failed: {response.status_code}")

class SystemMonitoringTasks(TaskSet):
    """System monitoring and health check tasks"""
    
    @task(5)
    @tag('monitoring')
    def health_check(self):
        """Application health check"""
        with self.client.get(
            "/actuator/health",
            catch_response=True,
            name="health_check"
        ) as response:
            if response.status_code == 200:
                health_data = response.json()
                if health_data.get('status') != 'UP':
                    response.failure(f"Health check not UP: {health_data}")
                else:
                    response.success()
            else:
                response.failure(f"Health check failed: {response.status_code}")
    
    @task(2)
    @tag('monitoring')
    def metrics_endpoint(self):
        """Application metrics"""
        with self.client.get(
            "/actuator/metrics",
            catch_response=True,
            name="metrics_check"
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Metrics check failed: {response.status_code}")
    
    @task(1)
    @tag('monitoring')  
    def prometheus_metrics(self):
        """Prometheus metrics endpoint"""
        with self.client.get(
            "/actuator/prometheus",
            catch_response=True,
            name="prometheus_metrics"
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Prometheus metrics failed: {response.status_code}")

class AstronomicalUser(HttpUser):
    """Simulated astronomical data processing user"""
    
    wait_time = between(1, 5)  # Wait 1-5 seconds between tasks
    
    # Define user behavior patterns
    tasks = {
        ImageProcessingTasks: 30,  # 30% of time on processing tasks
        CatalogQueryTasks: 60,     # 60% of time on catalog queries  
        SystemMonitoringTasks: 10  # 10% of time on monitoring
    }
    
    def on_start(self):
        """Called when a user starts"""
        self.user_type = random.choice(['researcher', 'student', 'automated_pipeline'])
        self.session_start = time.time()
        
        # Adjust behavior based on user type
        if self.user_type == 'automated_pipeline':
            self.wait_time = between(0.5, 2)  # Faster automated requests
        elif self.user_type == 'student':
            self.wait_time = between(2, 8)   # Slower, more exploratory
    
    def on_stop(self):
        """Called when a user stops"""
        session_duration = time.time() - self.session_start
        logger.info(f"User {self.user_type} session ended after {session_duration:.1f} seconds")

class AstronomicalResearcher(HttpUser):
    """Heavy catalog query user (researcher pattern)"""
    
    wait_time = between(2, 10)
    tasks = [CatalogQueryTasks]
    weight = 3

class AutomatedPipeline(HttpUser):
    """Automated processing pipeline user"""
    
    wait_time = between(0.5, 2)
    tasks = [ImageProcessingTasks]
    weight = 1

class CasualUser(HttpUser):
    """Casual/student user with mixed light usage"""
    
    wait_time = between(5, 15)
    tasks = {
        CatalogQueryTasks: 70,
        ImageProcessingTasks: 20,
        SystemMonitoringTasks: 10
    }
    weight = 2

# Custom event handlers for detailed metrics
@events.request.add_listener
def request_handler(request_type, name, response_time, response_length, exception, context, **kwargs):
    """Custom request handler for astronomical-specific metrics"""
    
    # Log slow requests
    if response_time > 5000:  # > 5 seconds
        logger.warning(f"Slow request detected: {name} took {response_time}ms")
    
    # Track specific endpoint patterns
    if 'cone-search' in name and response_time > 1000:
        logger.info(f"Slow cone search: {response_time}ms")
    elif 'processing/jobs' in name and response_time > 10000:
        logger.info(f"Slow processing job: {response_time}ms")

@events.test_start.add_listener
def test_start_handler(environment, **kwargs):
    """Test start handler"""
    logger.info("Starting astronomical data processing load test")
    logger.info(f"Target host: {environment.host}")

@events.test_stop.add_listener  
def test_stop_handler(environment, **kwargs):
    """Test stop handler with astronomical-specific reporting"""
    logger.info("Astronomical load test completed")
    
    if hasattr(environment, 'stats') and environment.stats:
        stats = environment.stats
        
        # Calculate astronomical-specific metrics
        cone_search_stats = [s for s in stats.entries.values() if 'cone-search' in s.name]
        processing_stats = [s for s in stats.entries.values() if 'processing' in s.name]
        
        if cone_search_stats:
            avg_cone_search_time = statistics.mean([s.avg_response_time for s in cone_search_stats])
            logger.info(f"Average cone search time: {avg_cone_search_time:.2f}ms")
        
        if processing_stats:
            avg_processing_time = statistics.mean([s.avg_response_time for s in processing_stats])
            logger.info(f"Average processing time: {avg_processing_time:.2f}ms")
        
        # Overall system performance
        total_requests = sum(s.num_requests for s in stats.entries.values())
        total_failures = sum(s.num_failures for s in stats.entries.values())
        failure_rate = (total_failures / total_requests) * 100 if total_requests > 0 else 0
        
        logger.info(f"Total requests: {total_requests}")
        logger.info(f"Failure rate: {failure_rate:.2f}%")
        
        # Performance assessment
        if failure_rate < 1 and avg_cone_search_time < 500:
            logger.info("✅ PERFORMANCE: Excellent - System meets all performance targets")
        elif failure_rate < 5 and avg_cone_search_time < 1000:
            logger.info("⚠️ PERFORMANCE: Good - Minor optimization opportunities")
        else:
            logger.info("❌ PERFORMANCE: Poor - Significant optimization needed")

# Locust configuration for different test scenarios
class StressTestUser(HttpUser):
    """High-load stress testing user"""
    wait_time = between(0.1, 0.5)  # Very aggressive load
    tasks = [CatalogQueryTasks, ImageProcessingTasks]

if __name__ == "__main__":
    # This allows running specific test scenarios
    import sys
    
    if len(sys.argv) > 1:
        test_type = sys.argv[1]
        
        if test_type == "stress":
            # Override for stress testing
            AstronomicalUser.wait_time = between(0.1, 1)
            print("Running in STRESS TEST mode")
        elif test_type == "endurance":
            # Endurance testing configuration
            AstronomicalUser.wait_time = between(2, 8)
            print("Running in ENDURANCE TEST mode")
        elif test_type == "spike":
            # Spike testing with very high load
            StressTestUser.weight = 5
            print("Running in SPIKE TEST mode")
    
    print("Astronomical Data Processing Load Test Ready")
    print("Use: locust -f locust_load_test.py --host=http://your-app-url")