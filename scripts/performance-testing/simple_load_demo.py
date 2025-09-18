#!/usr/bin/env python3
"""
Simple Load Testing Demo for Astronomical Pipeline
Demonstrates load testing concepts without complex framework dependencies
"""

import random
import time
import threading
import queue
from datetime import datetime
from dataclasses import dataclass
from typing import List, Dict, Any
import json

@dataclass
class RequestResult:
    """Result of a simulated request"""
    endpoint: str
    response_time_ms: float
    success: bool
    status_code: int
    response_size: int

class AstronomicalWorkloadSimulator:
    """Simulates astronomical pipeline workload"""
    
    def __init__(self):
        self.results = queue.Queue()
        self.running = False
    
    def simulate_request(self, endpoint: str, complexity: str = 'medium') -> RequestResult:
        """Simulate a request to an astronomical service endpoint"""
        
        # Simulate different response times based on endpoint and complexity
        response_times = {
            '/actuator/health': (10, 50),
            '/api/v1/processing/jobs': (100, 500),
            '/api/v1/catalog/cone_search': (50, 300),
            '/api/v1/processing/jobs/status': (20, 100),
            '/api/v1/metrics': (30, 150),
            '/api/v1/processing/jobs/batch': (200, 1000)
        }
        
        complexity_multiplier = {
            'simple': 0.5,
            'medium': 1.0,
            'complex': 2.0
        }
        
        base_min, base_max = response_times.get(endpoint, (50, 200))
        multiplier = complexity_multiplier.get(complexity, 1.0)
        
        response_time = random.uniform(base_min * multiplier, base_max * multiplier)
        
        # Simulate processing delay
        time.sleep(response_time / 1000)
        
        # Simulate occasional failures (5% failure rate)
        success = random.random() > 0.05
        status_code = 200 if success else random.choice([400, 500, 503, 504])
        
        # Simulate response size
        response_size = random.randint(100, 5000) if success else random.randint(50, 200)
        
        return RequestResult(
            endpoint=endpoint,
            response_time_ms=response_time,
            success=success,
            status_code=status_code,
            response_size=response_size
        )
    
    def user_session(self, user_id: int, duration_seconds: int):
        """Simulate a user session"""
        print(f"User {user_id} starting session")
        
        start_time = time.time()
        request_count = 0
        
        while time.time() - start_time < duration_seconds and self.running:
            # Choose a random endpoint based on realistic usage patterns
            endpoint_weights = [
                ('/actuator/health', 30),           # Frequent health checks
                ('/api/v1/processing/jobs', 20),    # Job submissions
                ('/api/v1/catalog/cone_search', 25), # Catalog queries
                ('/api/v1/processing/jobs/status', 20), # Status checks
                ('/api/v1/metrics', 3),             # Metrics queries
                ('/api/v1/processing/jobs/batch', 2) # Batch operations
            ]
            
            # Weighted random selection
            total_weight = sum(weight for _, weight in endpoint_weights)
            r = random.randint(1, total_weight)
            cumulative = 0
            
            for endpoint, weight in endpoint_weights:
                cumulative += weight
                if r <= cumulative:
                    chosen_endpoint = endpoint
                    break
            
            # Simulate request complexity based on endpoint
            if 'batch' in chosen_endpoint:
                complexity = 'complex'
            elif 'catalog' in chosen_endpoint:
                complexity = 'medium'
            else:
                complexity = 'simple'
            
            # Make request
            result = self.simulate_request(chosen_endpoint, complexity)
            self.results.put(result)
            
            request_count += 1
            
            # Wait between requests (simulate user think time)
            think_time = random.uniform(0.5, 3.0)
            time.sleep(think_time)
        
        print(f"User {user_id} completed session: {request_count} requests")
    
    def run_load_test(self, concurrent_users: int = 10, duration_seconds: int = 30):
        """Run a load test with multiple concurrent users"""
        print(f"Starting load test: {concurrent_users} users for {duration_seconds} seconds")
        print("=" * 60)
        
        self.running = True
        
        # Start user threads
        threads = []
        for user_id in range(1, concurrent_users + 1):
            thread = threading.Thread(
                target=self.user_session,
                args=(user_id, duration_seconds)
            )
            threads.append(thread)
            thread.start()
            
            # Stagger user starts
            time.sleep(0.1)
        
        # Wait for all threads to complete
        for thread in threads:
            thread.join()
        
        self.running = False
        
        # Collect and analyze results
        results = []
        while not self.results.empty():
            results.append(self.results.get())
        
        return self.analyze_results(results, duration_seconds)
    
    def analyze_results(self, results: List[RequestResult], duration: int) -> Dict[str, Any]:
        """Analyze load test results"""
        if not results:
            return {'error': 'No results to analyze'}
        
        # Overall statistics
        total_requests = len(results)
        successful_requests = sum(1 for r in results if r.success)
        failed_requests = total_requests - successful_requests
        
        response_times = [r.response_time_ms for r in results]
        avg_response_time = sum(response_times) / len(response_times)
        
        # Sort for percentiles
        sorted_times = sorted(response_times)
        p50 = sorted_times[int(0.5 * len(sorted_times))]
        p95 = sorted_times[int(0.95 * len(sorted_times))]
        p99 = sorted_times[int(0.99 * len(sorted_times))]
        
        # Throughput
        requests_per_second = total_requests / duration
        
        # Per-endpoint analysis
        endpoint_stats = {}
        for result in results:
            if result.endpoint not in endpoint_stats:
                endpoint_stats[result.endpoint] = {
                    'count': 0,
                    'success_count': 0,
                    'response_times': []
                }
            
            stats = endpoint_stats[result.endpoint]
            stats['count'] += 1
            if result.success:
                stats['success_count'] += 1
            stats['response_times'].append(result.response_time_ms)
        
        # Calculate per-endpoint metrics
        for endpoint, stats in endpoint_stats.items():
            if stats['response_times']:
                stats['avg_response_time'] = sum(stats['response_times']) / len(stats['response_times'])
                stats['success_rate'] = (stats['success_count'] / stats['count']) * 100
            else:
                stats['avg_response_time'] = 0
                stats['success_rate'] = 0
        
        return {
            'summary': {
                'duration_seconds': duration,
                'total_requests': total_requests,
                'successful_requests': successful_requests,
                'failed_requests': failed_requests,
                'success_rate_percent': (successful_requests / total_requests) * 100,
                'avg_response_time_ms': avg_response_time,
                'p50_response_time_ms': p50,
                'p95_response_time_ms': p95,
                'p99_response_time_ms': p99,
                'requests_per_second': requests_per_second
            },
            'endpoint_stats': endpoint_stats,
            'assessment': self.assess_performance(requests_per_second, avg_response_time, successful_requests / total_requests)
        }
    
    def assess_performance(self, rps: float, avg_response: float, success_rate: float) -> str:
        """Assess overall performance"""
        if success_rate < 0.95:
            return "POOR - High error rate detected"
        elif avg_response > 1000:
            return "POOR - Very slow response times"
        elif rps < 5:
            return "NEEDS_OPTIMIZATION - Low throughput"
        elif rps < 20:
            return "GOOD - Adequate performance"
        else:
            return "EXCELLENT - High performance achieved"

def main():
    """Main function to run load test demo"""
    print("Astronomical Pipeline Load Testing Demo")
    print("=" * 60)
    
    simulator = AstronomicalWorkloadSimulator()
    
    # Run load test
    results = simulator.run_load_test(concurrent_users=8, duration_seconds=20)
    
    # Print results
    print("\nLOAD TEST RESULTS")
    print("=" * 60)
    
    summary = results['summary']
    print(f"Duration: {summary['duration_seconds']} seconds")
    print(f"Total Requests: {summary['total_requests']}")
    print(f"Success Rate: {summary['success_rate_percent']:.1f}%")
    print(f"Average Response Time: {summary['avg_response_time_ms']:.1f}ms")
    print(f"95th Percentile: {summary['p95_response_time_ms']:.1f}ms")
    print(f"Throughput: {summary['requests_per_second']:.1f} requests/second")
    
    print("\nPER-ENDPOINT STATISTICS:")
    for endpoint, stats in results['endpoint_stats'].items():
        print(f"\n{endpoint}:")
        print(f"  Requests: {stats['count']}")
        print(f"  Success Rate: {stats['success_rate']:.1f}%")
        print(f"  Avg Response Time: {stats['avg_response_time']:.1f}ms")
    
    print(f"\nPerformance Assessment: {results['assessment']}")
    
    # Save detailed results
    output_file = 'simple_load_test_results.json'
    with open(output_file, 'w') as f:
        json.dump(results, f, indent=2)
    
    print(f"\nDetailed results saved to: {output_file}")
    print("\nNote: This demonstrates load testing concepts with simulated requests.")

if __name__ == "__main__":
    main()