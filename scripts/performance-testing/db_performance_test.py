#!/usr/bin/env python3
"""
Comprehensive Database Performance Testing Suite for Astronomical Catalog
Optimized for PostGIS spatial queries and large-scale astronomical data operations
"""

import psycopg2
import psycopg2.extras
import time
import random
import json
import statistics
import argparse
import sys
import logging
from datetime import datetime, timedelta
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, asdict
from typing import List, Dict, Any, Tuple
import numpy as np

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

@dataclass
class TestResult:
    """Test result data structure"""
    test_name: str
    iterations: int
    avg_time_ms: float
    median_time_ms: float
    p95_time_ms: float
    p99_time_ms: float
    min_time_ms: float
    max_time_ms: float
    throughput_ops_per_sec: float
    success_rate: float
    error_count: int

class AstronomicalDBPerformanceTester:
    def __init__(self, connection_string: str, max_connections: int = 20):
        self.connection_string = connection_string
        self.max_connections = max_connections
        self.results = []
        
    def get_connection(self):
        """Create database connection with optimized settings"""
        conn = psycopg2.connect(self.connection_string)
        conn.set_session(autocommit=False)
        return conn

    def test_cone_search_performance(self, iterations: int = 1000, concurrent_users: int = 10) -> TestResult:
        """
        Test spatial cone search performance with various parameters
        Simulates real astronomical queries for object detection
        """
        logger.info(f"Starting cone search performance test: {iterations} iterations, {concurrent_users} concurrent users")
        
        def single_cone_search() -> Tuple[float, bool]:
            """Single cone search operation"""
            try:
                with self.get_connection() as conn:
                    cursor = conn.cursor()
                    
                    # Generate realistic astronomical coordinates
                    ra = random.uniform(0, 360)
                    dec = random.uniform(-90, 90)
                    radius = random.choice([0.1, 0.5, 1.0, 2.0, 5.0])  # degrees
                    
                    start_time = time.perf_counter()
                    
                    # Optimized spatial query with proper indexing
                    cursor.execute("""
                        SELECT object_id, ra, dec, magnitude_v, object_type,
                               ST_Distance(position::geography, ST_SetSRID(ST_Point(%s, %s), 4326)::geography) / 111000 as distance_deg
                        FROM astronomical_objects 
                        WHERE ST_DWithin(
                            position::geography,
                            ST_SetSRID(ST_Point(%s, %s), 4326)::geography,
                            %s * 111000
                        )
                        ORDER BY distance_deg
                        LIMIT 1000
                    """, (ra, dec, ra, dec, radius))
                    
                    results = cursor.fetchall()
                    end_time = time.perf_counter()
                    
                    query_time = (end_time - start_time) * 1000
                    return query_time, True
                    
            except Exception as e:
                logger.error(f"Cone search error: {e}")
                return 0, False

        # Execute concurrent cone searches
        times = []
        errors = 0
        
        with ThreadPoolExecutor(max_workers=concurrent_users) as executor:
            futures = [executor.submit(single_cone_search) for _ in range(iterations)]
            
            for i, future in enumerate(as_completed(futures)):
                query_time, success = future.result()
                if success:
                    times.append(query_time)
                else:
                    errors += 1
                    
                if (i + 1) % 100 == 0:
                    logger.info(f"Completed {i + 1}/{iterations} cone searches")

        if not times:
            logger.error("No successful cone search operations completed")
            return TestResult("cone_search", iterations, 0, 0, 0, 0, 0, 0, 0, 0, errors)

        # Calculate statistics
        times.sort()
        total_time_seconds = sum(times) / 1000
        
        return TestResult(
            test_name="cone_search",
            iterations=len(times),
            avg_time_ms=statistics.mean(times),
            median_time_ms=statistics.median(times),
            p95_time_ms=times[int(0.95 * len(times))],
            p99_time_ms=times[int(0.99 * len(times))],
            min_time_ms=min(times),
            max_time_ms=max(times),
            throughput_ops_per_sec=len(times) / total_time_seconds if total_time_seconds > 0 else 0,
            success_rate=(len(times) / iterations) * 100,
            error_count=errors
        )

    def test_bulk_insert_performance(self, batch_size: int = 10000, batches: int = 5) -> TestResult:
        """
        Test bulk insert performance for astronomical objects
        Simulates large-scale catalog ingestion operations
        """
        logger.info(f"Starting bulk insert test: {batch_size} objects per batch, {batches} batches")
        
        times = []
        total_records = 0
        errors = 0
        
        for batch_num in range(batches):
            try:
                with self.get_connection() as conn:
                    cursor = conn.cursor()
                    
                    # Generate realistic astronomical test data
                    test_data = []
                    for i in range(batch_size):
                        object_id = f"test_batch_{batch_num}_obj_{i}_{int(time.time())}"
                        ra = random.uniform(0, 360)
                        dec = random.uniform(-90, 90)
                        magnitude_v = random.uniform(8.0, 28.0)
                        object_type = random.choice(['STAR', 'GALAXY', 'QUASAR', 'NEBULA'])
                        
                        test_data.append((
                            object_id, ra, dec, magnitude_v, object_type, 
                            datetime.now(), f'POINT({ra} {dec})'
                        ))
                    
                    start_time = time.perf_counter()
                    
                    # Use COPY for maximum insert performance
                    cursor.executemany("""
                        INSERT INTO astronomical_objects 
                        (object_id, ra, dec, magnitude_v, object_type, discovery_date, position)
                        VALUES (%s, %s, %s, %s, %s, %s, ST_GeomFromText(%s, 4326))
                        ON CONFLICT (object_id) DO NOTHING
                    """, test_data)
                    
                    conn.commit()
                    end_time = time.perf_counter()
                    
                    batch_time = (end_time - start_time) * 1000
                    times.append(batch_time)
                    total_records += batch_size
                    
                    logger.info(f"Completed batch {batch_num + 1}/{batches}: {batch_time:.2f}ms")
                    
            except Exception as e:
                logger.error(f"Bulk insert batch {batch_num} error: {e}")
                errors += 1

        if not times:
            return TestResult("bulk_insert", 0, 0, 0, 0, 0, 0, 0, 0, 0, errors)

        total_time_seconds = sum(times) / 1000
        
        return TestResult(
            test_name="bulk_insert",
            iterations=len(times),
            avg_time_ms=statistics.mean(times),
            median_time_ms=statistics.median(times),
            p95_time_ms=max(times) if len(times) < 20 else times[int(0.95 * len(times))],
            p99_time_ms=max(times) if len(times) < 100 else times[int(0.99 * len(times))],
            min_time_ms=min(times),
            max_time_ms=max(times),
            throughput_ops_per_sec=total_records / total_time_seconds if total_time_seconds > 0 else 0,
            success_rate=((len(times) / batches) * 100) if batches > 0 else 0,
            error_count=errors
        )

    def test_complex_query_performance(self, iterations: int = 100) -> TestResult:
        """
        Test complex astronomical queries including joins and aggregations
        """
        logger.info(f"Starting complex query performance test: {iterations} iterations")
        
        queries = [
            # Multi-table join with spatial filtering
            ("cross_match_query", """
                SELECT ao.object_id, ao.magnitude_v, ao.object_type, 
                       obs.observation_id, obs.telescope, obs.filter_name
                FROM astronomical_objects ao
                JOIN observations obs ON ST_DWithin(ao.position::geography, obs.pointing::geography, 1000)
                WHERE ao.magnitude_v < 20.0 
                  AND obs.observation_date > NOW() - INTERVAL '30 days'
                ORDER BY ao.magnitude_v
                LIMIT 100
            """, ()),
            
            # Magnitude distribution analysis
            ("magnitude_histogram", """
                SELECT 
                    FLOOR(magnitude_v) as mag_bin,
                    object_type,
                    COUNT(*) as object_count,
                    AVG(magnitude_v) as avg_magnitude
                FROM astronomical_objects
                WHERE magnitude_v BETWEEN 10 AND 25
                GROUP BY FLOOR(magnitude_v), object_type
                ORDER BY mag_bin, object_type
            """, ()),
            
            # Spatial density analysis
            ("spatial_density", """
                WITH grid AS (
                    SELECT generate_series(-180, 179, 10) as ra_bin,
                           generate_series(-89, 89, 10) as dec_bin
                ),
                density AS (
                    SELECT g.ra_bin, g.dec_bin,
                           COUNT(ao.object_id) as object_count
                    FROM grid g
                    LEFT JOIN astronomical_objects ao ON 
                        ao.ra BETWEEN g.ra_bin AND g.ra_bin + 10 AND
                        ao.dec BETWEEN g.dec_bin AND g.dec_bin + 10
                    GROUP BY g.ra_bin, g.dec_bin
                )
                SELECT ra_bin, dec_bin, object_count,
                       object_count / 100.0 as objects_per_sq_deg
                FROM density
                WHERE object_count > 0
                ORDER BY object_count DESC
                LIMIT 50
            """, ()),
            
            # Variability analysis
            ("variability_analysis", """
                SELECT ao.object_id, ao.object_type,
                       COUNT(d.detection_id) as detection_count,
                       STDDEV(d.magnitude) as mag_stddev,
                       MAX(d.magnitude) - MIN(d.magnitude) as mag_range
                FROM astronomical_objects ao
                JOIN detections d ON ao.object_id = d.object_id
                WHERE d.observation_date > NOW() - INTERVAL '90 days'
                GROUP BY ao.object_id, ao.object_type
                HAVING COUNT(d.detection_id) > 5
                   AND STDDEV(d.magnitude) > 0.1
                ORDER BY mag_stddev DESC
                LIMIT 100
            """, ())
        ]
        
        all_times = []
        errors = 0
        
        for query_name, sql, params in queries:
            query_times = []
            
            for i in range(iterations // len(queries)):
                try:
                    with self.get_connection() as conn:
                        cursor = conn.cursor()
                        
                        start_time = time.perf_counter()
                        cursor.execute(sql, params)
                        results = cursor.fetchall()
                        end_time = time.perf_counter()
                        
                        query_time = (end_time - start_time) * 1000
                        query_times.append(query_time)
                        all_times.append(query_time)
                        
                except Exception as e:
                    logger.error(f"Complex query {query_name} error: {e}")
                    errors += 1
            
            if query_times:
                logger.info(f"Query {query_name}: avg={statistics.mean(query_times):.2f}ms")

        if not all_times:
            return TestResult("complex_queries", 0, 0, 0, 0, 0, 0, 0, 0, 0, errors)

        all_times.sort()
        total_time_seconds = sum(all_times) / 1000
        
        return TestResult(
            test_name="complex_queries",
            iterations=len(all_times),
            avg_time_ms=statistics.mean(all_times),
            median_time_ms=statistics.median(all_times),
            p95_time_ms=all_times[int(0.95 * len(all_times))],
            p99_time_ms=all_times[int(0.99 * len(all_times))],
            min_time_ms=min(all_times),
            max_time_ms=max(all_times),
            throughput_ops_per_sec=len(all_times) / total_time_seconds if total_time_seconds > 0 else 0,
            success_rate=((len(all_times) / iterations) * 100),
            error_count=errors
        )

    def test_concurrent_load(self, concurrent_users: int = 50, duration_seconds: int = 300) -> TestResult:
        """
        Test database performance under concurrent load
        Simulates multiple users accessing the system simultaneously
        """
        logger.info(f"Starting concurrent load test: {concurrent_users} users, {duration_seconds}s duration")
        
        def user_workload(user_id: int) -> List[float]:
            """Simulate individual user workload"""
            user_times = []
            end_time = time.time() + duration_seconds
            
            while time.time() < end_time:
                try:
                    with self.get_connection() as conn:
                        cursor = conn.cursor()
                        
                        # Mix of operations (weighted by typical usage)
                        operation = random.choices(
                            ['cone_search', 'magnitude_query', 'recent_objects', 'health_check'],
                            weights=[40, 30, 20, 10]
                        )[0]
                        
                        start_time = time.perf_counter()
                        
                        if operation == 'cone_search':
                            cursor.execute("""
                                SELECT COUNT(*) FROM astronomical_objects
                                WHERE ST_DWithin(position::geography, 
                                                ST_SetSRID(ST_Point(%s, %s), 4326)::geography, 
                                                %s * 111000)
                            """, (random.uniform(0, 360), random.uniform(-90, 90), random.uniform(0.1, 2.0)))
                            
                        elif operation == 'magnitude_query':
                            cursor.execute("""
                                SELECT object_id, magnitude_v FROM astronomical_objects
                                WHERE magnitude_v BETWEEN %s AND %s
                                ORDER BY magnitude_v LIMIT 100
                            """, (random.uniform(10, 20), random.uniform(20, 25)))
                            
                        elif operation == 'recent_objects':
                            cursor.execute("""
                                SELECT object_id, discovery_date FROM astronomical_objects
                                WHERE discovery_date > %s
                                ORDER BY discovery_date DESC LIMIT 50
                            """, (datetime.now() - timedelta(days=random.randint(1, 365)),))
                            
                        else:  # health_check
                            cursor.execute("SELECT 1")
                        
                        cursor.fetchall()
                        end_time_op = time.perf_counter()
                        
                        operation_time = (end_time_op - start_time) * 1000
                        user_times.append(operation_time)
                        
                        # Brief pause between operations
                        time.sleep(random.uniform(0.1, 0.5))
                        
                except Exception as e:
                    logger.warning(f"User {user_id} operation error: {e}")
                    
            return user_times

        # Execute concurrent user workloads
        all_times = []
        errors = 0
        
        with ThreadPoolExecutor(max_workers=concurrent_users) as executor:
            futures = [executor.submit(user_workload, i) for i in range(concurrent_users)]
            
            for future in as_completed(futures):
                try:
                    user_times = future.result()
                    all_times.extend(user_times)
                except Exception as e:
                    logger.error(f"User workload error: {e}")
                    errors += 1

        if not all_times:
            return TestResult("concurrent_load", 0, 0, 0, 0, 0, 0, 0, 0, 0, errors)

        all_times.sort()
        total_operations = len(all_times)
        
        return TestResult(
            test_name="concurrent_load",
            iterations=total_operations,
            avg_time_ms=statistics.mean(all_times),
            median_time_ms=statistics.median(all_times),
            p95_time_ms=all_times[int(0.95 * len(all_times))],
            p99_time_ms=all_times[int(0.99 * len(all_times))],
            min_time_ms=min(all_times),
            max_time_ms=max(all_times),
            throughput_ops_per_sec=total_operations / duration_seconds,
            success_rate=100.0 - (errors / concurrent_users * 100),
            error_count=errors
        )

    def run_performance_suite(self, quick_mode: bool = False) -> Dict[str, Any]:
        """
        Run complete performance test suite
        """
        logger.info("Starting comprehensive database performance test suite")
        
        if quick_mode:
            logger.info("Running in quick mode with reduced iterations")
            test_config = {
                'cone_search': {'iterations': 100, 'concurrent_users': 5},
                'bulk_insert': {'batch_size': 1000, 'batches': 2},
                'complex_queries': {'iterations': 20},
                'concurrent_load': {'concurrent_users': 10, 'duration_seconds': 60}
            }
        else:
            test_config = {
                'cone_search': {'iterations': 1000, 'concurrent_users': 20},
                'bulk_insert': {'batch_size': 10000, 'batches': 5},
                'complex_queries': {'iterations': 100},
                'concurrent_load': {'concurrent_users': 50, 'duration_seconds': 300}
            }

        results = {}
        
        # Run individual tests
        results['cone_search'] = asdict(self.test_cone_search_performance(**test_config['cone_search']))
        results['bulk_insert'] = asdict(self.test_bulk_insert_performance(**test_config['bulk_insert']))
        results['complex_queries'] = asdict(self.test_complex_query_performance(**test_config['complex_queries']))
        results['concurrent_load'] = asdict(self.test_concurrent_load(**test_config['concurrent_load']))
        
        # Generate summary
        results['summary'] = {
            'total_tests': len([r for r in results.values() if isinstance(r, dict) and 'test_name' in r]),
            'successful_tests': len([r for r in results.values() if isinstance(r, dict) and r.get('success_rate', 0) > 90]),
            'timestamp': datetime.now().isoformat(),
            'test_mode': 'quick' if quick_mode else 'full'
        }
        
        # Performance assessment
        results['assessment'] = self._assess_performance(results)
        
        logger.info("Database performance test suite completed")
        return results

    def _assess_performance(self, results: Dict[str, Any]) -> Dict[str, str]:
        """
        Assess performance results against astronomical database benchmarks
        """
        assessment = {}
        
        # Cone search assessment
        if 'cone_search' in results:
            cs = results['cone_search']
            if cs['avg_time_ms'] < 50:
                assessment['cone_search'] = 'EXCELLENT - Sub-50ms average response time'
            elif cs['avg_time_ms'] < 100:
                assessment['cone_search'] = 'GOOD - Response time within acceptable range'
            elif cs['avg_time_ms'] < 500:
                assessment['cone_search'] = 'FAIR - Consider index optimization'
            else:
                assessment['cone_search'] = 'POOR - Significant optimization needed'
        
        # Bulk insert assessment
        if 'bulk_insert' in results:
            bi = results['bulk_insert']
            if bi['throughput_ops_per_sec'] > 5000:
                assessment['bulk_insert'] = 'EXCELLENT - High-performance ingestion capability'
            elif bi['throughput_ops_per_sec'] > 1000:
                assessment['bulk_insert'] = 'GOOD - Adequate ingestion performance'
            else:
                assessment['bulk_insert'] = 'FAIR - Consider batch size optimization'
        
        # Overall system assessment
        error_rates = [r.get('error_count', 0) for r in results.values() if isinstance(r, dict)]
        total_errors = sum(error_rates)
        
        if total_errors == 0:
            assessment['overall'] = 'SYSTEM STABLE - No errors detected'
        elif total_errors < 5:
            assessment['overall'] = 'SYSTEM STABLE - Minimal errors'
        else:
            assessment['overall'] = 'SYSTEM ISSUES - High error rate detected'
        
        return assessment

def main():
    parser = argparse.ArgumentParser(description='Astronomical Database Performance Testing Suite')
    parser.add_argument('--host', default='localhost', help='Database host')
    parser.add_argument('--port', default='5432', help='Database port')
    parser.add_argument('--database', default='astro_catalog', help='Database name')
    parser.add_argument('--user', default='astro_user', help='Database user')
    parser.add_argument('--password', default='password123', help='Database password')
    parser.add_argument('--quick', action='store_true', help='Run quick test mode')
    parser.add_argument('--output', default='db_performance_results.json', help='Output file')
    
    args = parser.parse_args()
    
    # Build connection string
    conn_string = f"postgresql://{args.user}:{args.password}@{args.host}:{args.port}/{args.database}"
    
    try:
        # Initialize tester
        tester = AstronomicalDBPerformanceTester(conn_string)
        
        # Run performance tests
        results = tester.run_performance_suite(quick_mode=args.quick)
        
        # Save results
        with open(args.output, 'w') as f:
            json.dump(results, f, indent=2, default=str)
        
        # Print summary
        print("\n" + "="*80)
        print("DATABASE PERFORMANCE TEST RESULTS")
        print("="*80)
        
        for test_name, result in results.items():
            if isinstance(result, dict) and 'test_name' in result:
                print(f"\n{test_name.upper()}:")
                print(f"  Average Response Time: {result['avg_time_ms']:.2f}ms")
                print(f"  95th Percentile: {result['p95_time_ms']:.2f}ms")
                print(f"  Throughput: {result['throughput_ops_per_sec']:.2f} ops/sec")
                print(f"  Success Rate: {result['success_rate']:.1f}%")
        
        if 'assessment' in results:
            print("\nPERFORMAN ASSESSMENT:")
            for component, assessment in results['assessment'].items():
                print(f"  {component}: {assessment}")
        
        print(f"\nDetailed results saved to: {args.output}")
        
    except Exception as e:
        logger.error(f"Performance test failed: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()