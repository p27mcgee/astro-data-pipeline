#!/usr/bin/env python3
"""
Application Performance Testing Suite for Astronomical Image Processing Pipeline
Tests FITS processing performance, throughput, and scalability
"""

import asyncio
import aiohttp
import time
import json
import random
import statistics
import argparse
import logging
from datetime import datetime, timedelta
from dataclasses import dataclass, asdict
from typing import List, Dict, Any, Optional
import numpy as np
from concurrent.futures import ThreadPoolExecutor, as_completed
import requests
import os

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

@dataclass
class PerformanceMetric:
    """Performance metric data structure"""
    test_name: str
    total_requests: int
    successful_requests: int
    failed_requests: int
    avg_response_time_ms: float
    median_response_time_ms: float
    p95_response_time_ms: float
    p99_response_time_ms: float
    min_response_time_ms: float
    max_response_time_ms: float
    requests_per_second: float
    errors_per_second: float
    success_rate: float
    throughput_mb_per_sec: float

class AstroApplicationPerformanceTester:
    def __init__(self, base_url: str, timeout: int = 30):
        self.base_url = base_url.rstrip('/')
        self.timeout = timeout
        self.session = None
        
    async def __aenter__(self):
        connector = aiohttp.TCPConnector(limit=100, limit_per_host=50)
        timeout = aiohttp.ClientTimeout(total=self.timeout)
        self.session = aiohttp.ClientSession(connector=connector, timeout=timeout)
        return self
        
    async def __aexit__(self, exc_type, exc_val, exc_tb):
        if self.session:
            await self.session.close()

    async def test_health_check_performance(self, iterations: int = 1000, concurrent: int = 20) -> PerformanceMetric:
        """Test health check endpoint performance"""
        logger.info(f"Testing health check performance: {iterations} requests, {concurrent} concurrent")
        
        async def single_health_check():
            start_time = time.perf_counter()
            try:
                async with self.session.get(f"{self.base_url}/actuator/health") as response:
                    await response.text()
                    end_time = time.perf_counter()
                    return (end_time - start_time) * 1000, response.status == 200
            except Exception as e:
                end_time = time.perf_counter()
                logger.warning(f"Health check error: {e}")
                return (end_time - start_time) * 1000, False

        # Execute concurrent health checks
        semaphore = asyncio.Semaphore(concurrent)
        
        async def limited_health_check():
            async with semaphore:
                return await single_health_check()

        tasks = [limited_health_check() for _ in range(iterations)]
        results = await asyncio.gather(*tasks)
        
        response_times = [r[0] for r in results]
        successes = [r[1] for r in results]
        
        return self._calculate_metrics("health_check", response_times, successes, 0)

    async def test_fits_processing_performance(self, iterations: int = 100, concurrent: int = 10) -> PerformanceMetric:
        """Test FITS file processing performance"""
        logger.info(f"Testing FITS processing performance: {iterations} requests, {concurrent} concurrent")
        
        # Sample FITS processing payloads
        processing_configs = [
            {
                "inputBucket": "astro-test-data",
                "inputObjectKey": "fits/test_small.fits",
                "processingType": "BASIC_CALIBRATION",
                "outputBucket": "astro-processed-test"
            },
            {
                "inputBucket": "astro-test-data", 
                "inputObjectKey": "fits/test_medium.fits",
                "processingType": "FULL_CALIBRATION",
                "outputBucket": "astro-processed-test"
            },
            {
                "inputBucket": "astro-test-data",
                "inputObjectKey": "fits/test_large.fits", 
                "processingType": "ADVANCED_PROCESSING",
                "outputBucket": "astro-processed-test"
            }
        ]
        
        async def single_fits_processing():
            config = random.choice(processing_configs)
            
            start_time = time.perf_counter()
            try:
                async with self.session.post(
                    f"{self.base_url}/api/v1/processing/jobs/s3",
                    json=config,
                    headers={"Content-Type": "application/json"}
                ) as response:
                    response_data = await response.json()
                    end_time = time.perf_counter()
                    
                    # For accepted jobs, monitor completion
                    if response.status == 202 and 'jobId' in response_data:
                        job_completion_time = await self._monitor_job_completion(response_data['jobId'])
                        return (end_time - start_time) * 1000 + job_completion_time, True
                    
                    return (end_time - start_time) * 1000, response.status in [200, 202]
                    
            except Exception as e:
                end_time = time.perf_counter()
                logger.warning(f"FITS processing error: {e}")
                return (end_time - start_time) * 1000, False

        # Execute concurrent FITS processing
        semaphore = asyncio.Semaphore(concurrent)
        
        async def limited_fits_processing():
            async with semaphore:
                return await single_fits_processing()

        tasks = [limited_fits_processing() for _ in range(iterations)]
        results = await asyncio.gather(*tasks)
        
        response_times = [r[0] for r in results]
        successes = [r[1] for r in results]
        
        # Estimate throughput (assuming average 100MB FITS files)
        avg_file_size_mb = 100
        total_data_processed = iterations * avg_file_size_mb
        total_time_seconds = sum(response_times) / 1000
        throughput_mb_per_sec = total_data_processed / total_time_seconds if total_time_seconds > 0 else 0
        
        metric = self._calculate_metrics("fits_processing", response_times, successes, throughput_mb_per_sec)
        return metric

    async def _monitor_job_completion(self, job_id: str, max_wait_time: int = 300) -> float:
        """Monitor job completion and return additional time"""
        start_time = time.perf_counter()
        
        while (time.perf_counter() - start_time) < max_wait_time:
            try:
                async with self.session.get(f"{self.base_url}/api/v1/processing/jobs/{job_id}/status") as response:
                    if response.status == 200:
                        status_data = await response.json()
                        if status_data.get('status') in ['COMPLETED', 'FAILED']:
                            return (time.perf_counter() - start_time) * 1000
                    
                await asyncio.sleep(2)  # Poll every 2 seconds
                
            except Exception as e:
                logger.warning(f"Job monitoring error: {e}")
                break
                
        return (time.perf_counter() - start_time) * 1000

    async def test_catalog_query_performance(self, iterations: int = 500, concurrent: int = 25) -> PerformanceMetric:
        """Test catalog query performance"""
        logger.info(f"Testing catalog query performance: {iterations} requests, {concurrent} concurrent")
        
        # Various catalog query types
        query_types = [
            ("cone_search", "/api/v1/catalog/cone-search", {
                "ra": lambda: random.uniform(0, 360),
                "dec": lambda: random.uniform(-90, 90),
                "radius": lambda: random.uniform(0.1, 5.0),
                "limit": lambda: random.randint(50, 500)
            }),
            ("magnitude_search", "/api/v1/catalog/magnitude-search", {
                "minMagnitude": lambda: random.uniform(10, 20),
                "maxMagnitude": lambda: random.uniform(20, 25),
                "limit": lambda: random.randint(100, 1000)
            }),
            ("object_type_search", "/api/v1/catalog/object-type-search", {
                "objectType": lambda: random.choice(["STAR", "GALAXY", "QUASAR", "NEBULA"]),
                "limit": lambda: random.randint(50, 200)
            })
        ]
        
        async def single_catalog_query():
            query_type, endpoint, param_generators = random.choice(query_types)
            params = {key: generator() for key, generator in param_generators.items()}
            
            start_time = time.perf_counter()
            try:
                async with self.session.get(f"{self.base_url}{endpoint}", params=params) as response:
                    response_data = await response.json()
                    end_time = time.perf_counter()
                    
                    # Calculate data transfer size
                    data_size = len(json.dumps(response_data).encode('utf-8'))
                    
                    return (end_time - start_time) * 1000, response.status == 200, data_size
                    
            except Exception as e:
                end_time = time.perf_counter()
                logger.warning(f"Catalog query error: {e}")
                return (end_time - start_time) * 1000, False, 0

        # Execute concurrent catalog queries
        semaphore = asyncio.Semaphore(concurrent)
        
        async def limited_catalog_query():
            async with semaphore:
                return await single_catalog_query()

        tasks = [limited_catalog_query() for _ in range(iterations)]
        results = await asyncio.gather(*tasks)
        
        response_times = [r[0] for r in results]
        successes = [r[1] for r in results]
        data_sizes = [r[2] for r in results]
        
        # Calculate data throughput
        total_data_mb = sum(data_sizes) / (1024 * 1024)
        total_time_seconds = sum(response_times) / 1000
        throughput_mb_per_sec = total_data_mb / total_time_seconds if total_time_seconds > 0 else 0
        
        return self._calculate_metrics("catalog_queries", response_times, successes, throughput_mb_per_sec)

    async def test_concurrent_mixed_workload(self, duration_seconds: int = 300, concurrent_users: int = 50) -> PerformanceMetric:
        """Test mixed workload performance under concurrent load"""
        logger.info(f"Testing concurrent mixed workload: {concurrent_users} users, {duration_seconds}s duration")
        
        async def user_workload(user_id: int):
            """Simulate individual user workload with mixed operations"""
            user_metrics = []
            end_time = time.time() + duration_seconds
            
            while time.time() < end_time:
                # Weighted operation selection (realistic usage pattern)
                operation = random.choices(
                    ['health_check', 'catalog_query', 'fits_processing', 'status_check'],
                    weights=[10, 60, 20, 10]
                )[0]
                
                start_time = time.perf_counter()
                success = False
                data_size = 0
                
                try:
                    if operation == 'health_check':
                        async with self.session.get(f"{self.base_url}/actuator/health") as response:
                            await response.text()
                            success = response.status == 200
                            
                    elif operation == 'catalog_query':
                        params = {
                            "ra": random.uniform(0, 360),
                            "dec": random.uniform(-90, 90),
                            "radius": random.uniform(0.1, 2.0),
                            "limit": random.randint(10, 100)
                        }
                        async with self.session.get(f"{self.base_url}/api/v1/catalog/cone-search", params=params) as response:
                            response_data = await response.json()
                            success = response.status == 200
                            data_size = len(json.dumps(response_data).encode('utf-8'))
                            
                    elif operation == 'fits_processing':
                        payload = {
                            "inputBucket": "astro-test-data",
                            "inputObjectKey": f"fits/user_{user_id}_test.fits",
                            "processingType": "BASIC_CALIBRATION"
                        }
                        async with self.session.post(f"{self.base_url}/api/v1/processing/jobs/s3", json=payload) as response:
                            await response.json()
                            success = response.status in [200, 202]
                            
                    else:  # status_check
                        async with self.session.get(f"{self.base_url}/actuator/metrics") as response:
                            await response.text()
                            success = response.status == 200
                
                except Exception as e:
                    logger.debug(f"User {user_id} operation {operation} error: {e}")
                
                end_time_op = time.perf_counter()
                operation_time = (end_time_op - start_time) * 1000
                user_metrics.append((operation_time, success, data_size))
                
                # Realistic user think time
                await asyncio.sleep(random.uniform(0.5, 2.0))
            
            return user_metrics

        # Execute concurrent user workloads
        tasks = [user_workload(i) for i in range(concurrent_users)]
        all_user_metrics = await asyncio.gather(*tasks)
        
        # Flatten results
        all_response_times = []
        all_successes = []
        all_data_sizes = []
        
        for user_metrics in all_user_metrics:
            for response_time, success, data_size in user_metrics:
                all_response_times.append(response_time)
                all_successes.append(success)
                all_data_sizes.append(data_size)
        
        # Calculate throughput
        total_data_mb = sum(all_data_sizes) / (1024 * 1024)
        throughput_mb_per_sec = total_data_mb / duration_seconds
        
        return self._calculate_metrics("mixed_workload", all_response_times, all_successes, throughput_mb_per_sec)

    def _calculate_metrics(self, test_name: str, response_times: List[float], 
                          successes: List[bool], throughput_mb_per_sec: float = 0) -> PerformanceMetric:
        """Calculate performance metrics from test results"""
        
        if not response_times:
            return PerformanceMetric(
                test_name=test_name, total_requests=0, successful_requests=0, failed_requests=0,
                avg_response_time_ms=0, median_response_time_ms=0, p95_response_time_ms=0,
                p99_response_time_ms=0, min_response_time_ms=0, max_response_time_ms=0,
                requests_per_second=0, errors_per_second=0, success_rate=0, throughput_mb_per_sec=0
            )
        
        response_times.sort()
        successful_requests = sum(successes)
        failed_requests = len(successes) - successful_requests
        total_time_seconds = sum(response_times) / 1000
        
        return PerformanceMetric(
            test_name=test_name,
            total_requests=len(response_times),
            successful_requests=successful_requests,
            failed_requests=failed_requests,
            avg_response_time_ms=statistics.mean(response_times),
            median_response_time_ms=statistics.median(response_times),
            p95_response_time_ms=response_times[int(0.95 * len(response_times))] if len(response_times) > 20 else max(response_times),
            p99_response_time_ms=response_times[int(0.99 * len(response_times))] if len(response_times) > 100 else max(response_times),
            min_response_time_ms=min(response_times),
            max_response_time_ms=max(response_times),
            requests_per_second=len(response_times) / total_time_seconds if total_time_seconds > 0 else 0,
            errors_per_second=failed_requests / total_time_seconds if total_time_seconds > 0 else 0,
            success_rate=(successful_requests / len(successes)) * 100 if successes else 0,
            throughput_mb_per_sec=throughput_mb_per_sec
        )

    async def run_performance_suite(self, quick_mode: bool = False) -> Dict[str, Any]:
        """Run complete application performance test suite"""
        logger.info("Starting comprehensive application performance test suite")
        
        if quick_mode:
            test_config = {
                'health_check': {'iterations': 100, 'concurrent': 10},
                'fits_processing': {'iterations': 10, 'concurrent': 3},
                'catalog_queries': {'iterations': 50, 'concurrent': 10},
                'mixed_workload': {'duration_seconds': 60, 'concurrent_users': 10}
            }
        else:
            test_config = {
                'health_check': {'iterations': 1000, 'concurrent': 50},
                'fits_processing': {'iterations': 100, 'concurrent': 10},
                'catalog_queries': {'iterations': 500, 'concurrent': 25},
                'mixed_workload': {'duration_seconds': 300, 'concurrent_users': 50}
            }

        results = {}
        
        # Run individual performance tests
        results['health_check'] = asdict(await self.test_health_check_performance(**test_config['health_check']))
        results['fits_processing'] = asdict(await self.test_fits_processing_performance(**test_config['fits_processing']))
        results['catalog_queries'] = asdict(await self.test_catalog_query_performance(**test_config['catalog_queries']))
        results['mixed_workload'] = asdict(await self.test_concurrent_mixed_workload(**test_config['mixed_workload']))
        
        # Generate summary and assessment
        results['summary'] = {
            'total_tests': len([r for r in results.values() if isinstance(r, dict) and 'test_name' in r]),
            'timestamp': datetime.now().isoformat(),
            'test_mode': 'quick' if quick_mode else 'full',
            'base_url': self.base_url
        }
        
        results['assessment'] = self._assess_application_performance(results)
        
        logger.info("Application performance test suite completed")
        return results

    def _assess_application_performance(self, results: Dict[str, Any]) -> Dict[str, str]:
        """Assess application performance against astronomical processing benchmarks"""
        assessment = {}
        
        # Health check assessment
        if 'health_check' in results:
            hc = results['health_check']
            if hc['avg_response_time_ms'] < 10 and hc['success_rate'] > 99:
                assessment['health_check'] = 'EXCELLENT - Fast and reliable health checks'
            elif hc['avg_response_time_ms'] < 50 and hc['success_rate'] > 95:
                assessment['health_check'] = 'GOOD - Adequate health check performance'
            else:
                assessment['health_check'] = 'POOR - Health check issues detected'
        
        # FITS processing assessment
        if 'fits_processing' in results:
            fp = results['fits_processing']
            if fp['avg_response_time_ms'] < 5000 and fp['success_rate'] > 95:
                assessment['fits_processing'] = 'EXCELLENT - Fast FITS processing capability'
            elif fp['avg_response_time_ms'] < 15000 and fp['success_rate'] > 90:
                assessment['fits_processing'] = 'GOOD - Acceptable processing performance'
            else:
                assessment['fits_processing'] = 'POOR - FITS processing optimization needed'
        
        # Catalog query assessment
        if 'catalog_queries' in results:
            cq = results['catalog_queries']
            if cq['avg_response_time_ms'] < 100 and cq['success_rate'] > 99:
                assessment['catalog_queries'] = 'EXCELLENT - High-performance catalog queries'
            elif cq['avg_response_time_ms'] < 500 and cq['success_rate'] > 95:
                assessment['catalog_queries'] = 'GOOD - Adequate query performance'
            else:
                assessment['catalog_queries'] = 'POOR - Query optimization required'
        
        # Overall system assessment
        success_rates = [r.get('success_rate', 0) for r in results.values() if isinstance(r, dict) and 'success_rate' in r]
        avg_success_rate = statistics.mean(success_rates) if success_rates else 0
        
        if avg_success_rate > 98:
            assessment['overall'] = 'SYSTEM EXCELLENT - High reliability and performance'
        elif avg_success_rate > 95:
            assessment['overall'] = 'SYSTEM GOOD - Meets performance requirements'
        elif avg_success_rate > 90:
            assessment['overall'] = 'SYSTEM FAIR - Some optimization recommended'
        else:
            assessment['overall'] = 'SYSTEM POOR - Significant issues require attention'
        
        return assessment

def run_sync_test(base_url: str, quick_mode: bool = False, output_file: str = 'app_performance_results.json'):
    """Synchronous wrapper for async performance tests"""
    
    async def async_main():
        async with AstroApplicationPerformanceTester(base_url) as tester:
            return await tester.run_performance_suite(quick_mode)
    
    # Run async tests
    results = asyncio.run(async_main())
    
    # Save results
    with open(output_file, 'w') as f:
        json.dump(results, f, indent=2, default=str)
    
    return results

def main():
    parser = argparse.ArgumentParser(description='Astronomical Application Performance Testing Suite')
    parser.add_argument('--url', default='http://localhost:8080', help='Application base URL')
    parser.add_argument('--quick', action='store_true', help='Run quick test mode')
    parser.add_argument('--output', default='app_performance_results.json', help='Output file')
    parser.add_argument('--timeout', type=int, default=30, help='Request timeout in seconds')
    
    args = parser.parse_args()
    
    try:
        # Test connectivity first
        response = requests.get(f"{args.url}/actuator/health", timeout=5)
        if response.status_code != 200:
            logger.error(f"Service not available at {args.url}")
            return 1
            
        logger.info(f"Testing application at {args.url}")
        
        # Run performance tests
        results = run_sync_test(args.url, args.quick, args.output)
        
        # Print summary
        print("\n" + "="*80)
        print("APPLICATION PERFORMANCE TEST RESULTS")
        print("="*80)
        
        for test_name, result in results.items():
            if isinstance(result, dict) and 'test_name' in result:
                print(f"\n{test_name.upper()}:")
                print(f"  Total Requests: {result['total_requests']}")
                print(f"  Success Rate: {result['success_rate']:.1f}%")
                print(f"  Average Response Time: {result['avg_response_time_ms']:.2f}ms")
                print(f"  95th Percentile: {result['p95_response_time_ms']:.2f}ms")
                print(f"  Throughput: {result['requests_per_second']:.2f} req/s")
                if result['throughput_mb_per_sec'] > 0:
                    print(f"  Data Throughput: {result['throughput_mb_per_sec']:.2f} MB/s")
        
        if 'assessment' in results:
            print("\nPERFORMANCE ASSESSMENT:")
            for component, assessment in results['assessment'].items():
                print(f"  {component}: {assessment}")
        
        print(f"\nDetailed results saved to: {args.output}")
        
        return 0
        
    except Exception as e:
        logger.error(f"Performance test failed: {e}")
        return 1

if __name__ == "__main__":
    exit(main())