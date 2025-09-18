#!/usr/bin/env python3
"""
Demo Locust Load Testing for Astronomical Pipeline
Demonstrates load testing framework without requiring actual services
"""

import random
import json
import time
from datetime import datetime
from locust import HttpUser, TaskSet, task, between
import requests

class MockAPITester:
    """Mock API for testing purposes"""
    
    @staticmethod
    def simulate_response(endpoint: str, delay_range: tuple = (0.1, 0.5)):
        """Simulate API response with realistic delays"""
        time.sleep(random.uniform(*delay_range))
        
        # Simulate different response patterns based on endpoint
        if 'health' in endpoint:
            return {'status': 'healthy', 'timestamp': datetime.now().isoformat()}
        elif 'processing' in endpoint:
            return {
                'job_id': f'job-{random.randint(1000, 9999)}',
                'status': 'queued',
                'estimated_duration': random.randint(30, 300)
            }
        elif 'catalog' in endpoint:
            return {
                'objects': [
                    {
                        'id': i,
                        'ra': random.uniform(0, 360),
                        'dec': random.uniform(-90, 90),
                        'magnitude': random.uniform(8, 20)
                    } for i in range(random.randint(10, 100))
                ]
            }
        else:
            return {'message': 'mock response', 'endpoint': endpoint}

class AstronomicalWorkloadTasks(TaskSet):
    """Astronomical pipeline workload simulation"""
    
    def on_start(self):
        """Initialize user session"""
        self.session_id = f"session-{random.randint(1000, 9999)}"
        print(f"Starting session: {self.session_id}")
    
    @task(3)
    def health_check(self):
        """Health check endpoint (frequent)"""
        try:
            # Simulate health check
            response_data = MockAPITester.simulate_response('health', (0.01, 0.05))
            
            # Record successful request
            self.client.request_success(
                request_type="GET",
                name="/actuator/health",
                response_time=random.uniform(10, 50),
                response_length=len(json.dumps(response_data))
            )
            
        except Exception as e:
            self.client.request_failure(
                request_type="GET",
                name="/actuator/health",
                response_time=random.uniform(100, 500),
                response_length=0,
                exception=e
            )
    
    @task(2)
    def submit_processing_job(self):
        """Submit FITS processing job"""
        try:
            # Simulate job submission
            job_data = {
                'input_file': f'fits/observation_{random.randint(1000, 9999)}.fits',
                'processing_type': random.choice(['calibration', 'photometry', 'astrometry']),
                'priority': random.randint(1, 10)
            }
            
            response_data = MockAPITester.simulate_response('processing', (0.1, 0.3))
            
            self.client.request_success(
                request_type="POST",
                name="/api/v1/processing/jobs",
                response_time=random.uniform(100, 300),
                response_length=len(json.dumps(response_data))
            )
            
        except Exception as e:
            self.client.request_failure(
                request_type="POST",
                name="/api/v1/processing/jobs",
                response_time=random.uniform(200, 1000),
                response_length=0,
                exception=e
            )
    
    @task(1)
    def query_catalog(self):
        """Query astronomical catalog"""
        try:
            # Simulate catalog query
            ra = random.uniform(0, 360)
            dec = random.uniform(-90, 90)
            radius = random.uniform(0.1, 5.0)
            
            query_params = {
                'ra': ra,
                'dec': dec,
                'radius': radius,
                'magnitude_limit': random.uniform(15, 22)
            }
            
            response_data = MockAPITester.simulate_response('catalog', (0.05, 0.2))
            
            self.client.request_success(
                request_type="GET",
                name="/api/v1/catalog/cone_search",
                response_time=random.uniform(50, 200),
                response_length=len(json.dumps(response_data))
            )
            
        except Exception as e:
            self.client.request_failure(
                request_type="GET",
                name="/api/v1/catalog/cone_search",
                response_time=random.uniform(100, 500),
                response_length=0,
                exception=e
            )
    
    @task(1)
    def check_job_status(self):
        """Check processing job status"""
        try:
            job_id = f"job-{random.randint(1000, 9999)}"
            
            status_data = {
                'job_id': job_id,
                'status': random.choice(['queued', 'processing', 'completed', 'failed']),
                'progress': random.uniform(0, 100),
                'estimated_completion': datetime.now().isoformat()
            }
            
            MockAPITester.simulate_response('status', (0.02, 0.1))
            
            self.client.request_success(
                request_type="GET",
                name=f"/api/v1/processing/jobs/{job_id}/status",
                response_time=random.uniform(20, 100),
                response_length=len(json.dumps(status_data))
            )
            
        except Exception as e:
            self.client.request_failure(
                request_type="GET",
                name="/api/v1/processing/jobs/*/status",
                response_time=random.uniform(50, 200),
                response_length=0,
                exception=e
            )

class AstronomicalUser(HttpUser):
    """Simulated astronomical pipeline user"""
    
    host = "http://mock-astro-api:8080"
    tasks = [AstronomicalWorkloadTasks]
    wait_time = between(1, 5)  # Wait 1-5 seconds between tasks
    
    def __init__(self, environment):
        super().__init__(environment)
        # Use a mock client that doesn't make real HTTP requests
        self.client = MockHttpClient()

class MockHttpClient:
    """Mock HTTP client for demonstration purposes"""
    
    def __init__(self):
        self.host = "http://mock-astro-api:8080"
    
    def request_success(self, request_type, name, response_time, response_length):
        """Record a successful request"""
        print(f"✓ {request_type} {name} - {response_time:.1f}ms ({response_length} bytes)")
    
    def request_failure(self, request_type, name, response_time, response_length, exception):
        """Record a failed request"""
        print(f"✗ {request_type} {name} - {response_time:.1f}ms - Error: {exception}")

def run_demo_load_test():
    """Run a demonstration load test"""
    print("Starting Astronomical Pipeline Load Test Demo")
    print("=" * 60)
    
    # Simulate multiple users
    user_count = 5
    duration = 30  # seconds
    
    print(f"Simulating {user_count} concurrent users for {duration} seconds")
    print("Tasks: Health checks, job submissions, catalog queries, status checks")
    print()
    
    # Create mock users
    users = []
    for i in range(user_count):
        user = AstronomicalUser(None)
        user.user_id = i + 1
        users.append(user)
    
    # Run simulation
    start_time = time.time()
    total_requests = 0
    successful_requests = 0
    
    while time.time() - start_time < duration:
        for user in users:
            try:
                # Execute a random task
                task_set = AstronomicalWorkloadTasks(user)
                task_set.client = user.client
                
                # Run a random task
                task_methods = [
                    task_set.health_check,
                    task_set.submit_processing_job,
                    task_set.query_catalog,
                    task_set.check_job_status
                ]
                
                random.choice(task_methods)()
                total_requests += 1
                successful_requests += 1
                
                # Small delay between requests
                time.sleep(random.uniform(0.1, 0.5))
                
            except Exception as e:
                total_requests += 1
                print(f"User {user.user_id} task failed: {e}")
        
        # Brief pause between user iterations
        time.sleep(0.1)
    
    elapsed_time = time.time() - start_time
    
    # Generate summary
    print("\n" + "=" * 60)
    print("LOAD TEST RESULTS SUMMARY")
    print("=" * 60)
    print(f"Duration: {elapsed_time:.1f} seconds")
    print(f"Total Requests: {total_requests}")
    print(f"Successful Requests: {successful_requests}")
    print(f"Success Rate: {(successful_requests/total_requests)*100:.1f}%")
    print(f"Average RPS: {total_requests/elapsed_time:.1f} requests/second")
    print(f"Concurrent Users: {user_count}")
    
    # Performance assessment
    avg_rps = total_requests / elapsed_time
    if avg_rps > 20:
        assessment = "EXCELLENT - High throughput achieved"
    elif avg_rps > 10:
        assessment = "GOOD - Adequate performance"
    else:
        assessment = "NEEDS_OPTIMIZATION - Low throughput"
    
    print(f"Performance Assessment: {assessment}")
    print()
    print("Note: This is a demonstration using mock HTTP client.")
    print("Real load testing would use actual HTTP endpoints.")

if __name__ == "__main__":
    run_demo_load_test()