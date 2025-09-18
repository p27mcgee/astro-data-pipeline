#!/usr/bin/env python3
"""
System Optimization Tool for Astronomical Data Processing Pipeline
Analyzes performance bottlenecks and provides actionable optimization recommendations
"""

import json
import yaml
import subprocess
import psutil
import requests
import time
import statistics
import argparse
import logging
from datetime import datetime, timedelta
from typing import Dict, List, Any, Optional, Tuple
from dataclasses import dataclass, asdict
import kubernetes
from kubernetes import client, config

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

@dataclass
class OptimizationRecommendation:
    """Optimization recommendation structure"""
    component: str
    priority: str  # HIGH, MEDIUM, LOW
    category: str  # PERFORMANCE, COST, RELIABILITY, SECURITY
    issue: str
    recommendation: str
    expected_impact: str
    implementation_effort: str  # LOW, MEDIUM, HIGH
    estimated_cost_savings: Optional[float] = None

class SystemOptimizer:
    def __init__(self, namespace: str = 'astro-pipeline', kubeconfig_path: Optional[str] = None):
        self.namespace = namespace
        self.recommendations = []
        
        # Initialize Kubernetes client
        try:
            if kubeconfig_path:
                config.load_kube_config(config_file=kubeconfig_path)
            else:
                config.load_incluster_config()
        except:
            logger.warning("Could not load Kubernetes configuration")
        
        self.k8s_apps_v1 = client.AppsV1Api()
        self.k8s_core_v1 = client.CoreV1Api()
        self.k8s_metrics = client.CustomObjectsApi()

    def analyze_kubernetes_resources(self) -> Dict[str, Any]:
        """Analyze Kubernetes resource utilization and configuration"""
        logger.info("Analyzing Kubernetes resources...")
        
        analysis = {
            'deployments': {},
            'pods': {},
            'services': {},
            'resource_usage': {},
            'recommendations': []
        }
        
        try:
            # Analyze deployments
            deployments = self.k8s_apps_v1.list_namespaced_deployment(namespace=self.namespace)
            for deployment in deployments.items:
                deployment_analysis = self._analyze_deployment(deployment)
                analysis['deployments'][deployment.metadata.name] = deployment_analysis
            
            # Analyze pods
            pods = self.k8s_core_v1.list_namespaced_pod(namespace=self.namespace)
            for pod in pods.items:
                pod_analysis = self._analyze_pod(pod)
                analysis['pods'][pod.metadata.name] = pod_analysis
            
            # Analyze resource usage
            analysis['resource_usage'] = self._get_resource_metrics()
            
        except Exception as e:
            logger.error(f"Kubernetes analysis error: {e}")
            
        return analysis

    def _analyze_deployment(self, deployment) -> Dict[str, Any]:
        """Analyze individual deployment configuration"""
        spec = deployment.spec
        status = deployment.status
        
        analysis = {
            'replicas': {
                'desired': spec.replicas,
                'ready': status.ready_replicas or 0,
                'available': status.available_replicas or 0
            },
            'resource_requests': {},
            'resource_limits': {},
            'health_checks': {},
            'issues': []
        }
        
        if spec.template.spec.containers:
            container = spec.template.spec.containers[0]  # Primary container
            
            # Resource analysis
            if container.resources:
                if container.resources.requests:
                    analysis['resource_requests'] = dict(container.resources.requests)
                if container.resources.limits:
                    analysis['resource_limits'] = dict(container.resources.limits)
            
            # Health check analysis
            analysis['health_checks'] = {
                'liveness_probe': container.liveness_probe is not None,
                'readiness_probe': container.readiness_probe is not None,
                'startup_probe': container.startup_probe is not None
            }
            
            # Identify issues and recommendations
            if not container.resources or not container.resources.limits:
                analysis['issues'].append("Missing resource limits")
                self.recommendations.append(OptimizationRecommendation(
                    component=f"deployment/{deployment.metadata.name}",
                    priority="HIGH",
                    category="PERFORMANCE",
                    issue="Missing resource limits can cause resource contention",
                    recommendation="Set appropriate CPU and memory limits based on profiling",
                    expected_impact="Improved stability and resource utilization",
                    implementation_effort="LOW"
                ))
            
            if not container.readiness_probe:
                analysis['issues'].append("Missing readiness probe")
                self.recommendations.append(OptimizationRecommendation(
                    component=f"deployment/{deployment.metadata.name}",
                    priority="MEDIUM",
                    category="RELIABILITY",
                    issue="Missing readiness probe can cause traffic routing issues",
                    recommendation="Implement readiness probe for health-based traffic routing",
                    expected_impact="Improved service reliability and user experience",
                    implementation_effort="LOW"
                ))
        
        return analysis

    def _analyze_pod(self, pod) -> Dict[str, Any]:
        """Analyze individual pod performance and resource usage"""
        analysis = {
            'phase': pod.status.phase,
            'restart_count': 0,
            'resource_usage': {},
            'issues': []
        }
        
        # Calculate restart count
        if pod.status.container_statuses:
            for container_status in pod.status.container_statuses:
                analysis['restart_count'] += container_status.restart_count
        
        # High restart count indicates issues
        if analysis['restart_count'] > 5:
            analysis['issues'].append("High restart count")
            self.recommendations.append(OptimizationRecommendation(
                component=f"pod/{pod.metadata.name}",
                priority="HIGH", 
                category="RELIABILITY",
                issue=f"Pod has restarted {analysis['restart_count']} times",
                recommendation="Investigate logs for crash causes, adjust resource limits, improve error handling",
                expected_impact="Reduced downtime and improved reliability",
                implementation_effort="MEDIUM"
            ))
        
        return analysis

    def _get_resource_metrics(self) -> Dict[str, Any]:
        """Get resource utilization metrics from Kubernetes metrics server"""
        metrics = {
            'cpu_utilization': {},
            'memory_utilization': {},
            'network_io': {},
            'storage_io': {}
        }
        
        try:
            # Get pod metrics (requires metrics-server)
            pod_metrics = self.k8s_metrics.list_namespaced_custom_object(
                group="metrics.k8s.io",
                version="v1beta1", 
                namespace=self.namespace,
                plural="pods"
            )
            
            for pod_metric in pod_metrics.get('items', []):
                pod_name = pod_metric['metadata']['name']
                
                if 'containers' in pod_metric:
                    for container in pod_metric['containers']:
                        usage = container.get('usage', {})
                        metrics['cpu_utilization'][f"{pod_name}/{container['name']}"] = usage.get('cpu', '0')
                        metrics['memory_utilization'][f"{pod_name}/{container['name']}"] = usage.get('memory', '0')
        
        except Exception as e:
            logger.warning(f"Could not retrieve metrics: {e}")
        
        return metrics

    def analyze_database_performance(self, db_connection_string: str) -> Dict[str, Any]:
        """Analyze database performance and configuration"""
        logger.info("Analyzing database performance...")
        
        analysis = {
            'connection_pool': {},
            'query_performance': {},
            'index_usage': {},
            'table_statistics': {},
            'recommendations': []
        }
        
        try:
            import psycopg2
            
            with psycopg2.connect(db_connection_string) as conn:
                cursor = conn.cursor()
                
                # Connection pool analysis
                cursor.execute("""
                    SELECT state, count(*) 
                    FROM pg_stat_activity 
                    WHERE datname = current_database()
                    GROUP BY state
                """)
                connection_states = dict(cursor.fetchall())
                analysis['connection_pool'] = connection_states
                
                # Query performance analysis
                cursor.execute("""
                    SELECT query, calls, total_time, mean_time, rows,
                           100.0 * shared_blks_hit / nullif(shared_blks_hit + shared_blks_read, 0) AS hit_percent
                    FROM pg_stat_statements 
                    WHERE query NOT LIKE '%pg_stat_statements%'
                    ORDER BY total_time DESC 
                    LIMIT 10
                """)
                slow_queries = cursor.fetchall()
                analysis['query_performance'] = {
                    'slow_queries': [
                        {
                            'query': q[0][:100] + '...' if len(q[0]) > 100 else q[0],
                            'calls': q[1],
                            'total_time_ms': q[2],
                            'avg_time_ms': q[3],
                            'rows': q[4],
                            'cache_hit_ratio': q[5]
                        }
                        for q in slow_queries
                    ]
                }
                
                # Index usage analysis
                cursor.execute("""
                    SELECT schemaname, tablename, indexname, idx_tup_read, idx_tup_fetch
                    FROM pg_stat_user_indexes 
                    WHERE idx_tup_read > 0
                    ORDER BY idx_tup_read DESC
                    LIMIT 20
                """)
                index_stats = cursor.fetchall()
                analysis['index_usage'] = [
                    {
                        'schema': idx[0], 'table': idx[1], 'index': idx[2],
                        'reads': idx[3], 'fetches': idx[4]
                    }
                    for idx in index_stats
                ]
                
                # Table statistics
                cursor.execute("""
                    SELECT schemaname, tablename, n_tup_ins, n_tup_upd, n_tup_del, n_live_tup, n_dead_tup
                    FROM pg_stat_user_tables
                    ORDER BY n_live_tup DESC
                """)
                table_stats = cursor.fetchall()
                analysis['table_statistics'] = [
                    {
                        'schema': t[0], 'table': t[1], 'inserts': t[2], 
                        'updates': t[3], 'deletes': t[4], 'live_tuples': t[5], 'dead_tuples': t[6]
                    }
                    for t in table_stats
                ]
                
                # Generate database-specific recommendations
                self._generate_database_recommendations(analysis)
                
        except Exception as e:
            logger.error(f"Database analysis error: {e}")
            
        return analysis

    def _generate_database_recommendations(self, analysis: Dict[str, Any]):
        """Generate database optimization recommendations"""
        
        # Check for high connection usage
        connections = analysis.get('connection_pool', {})
        total_connections = sum(connections.values())
        if total_connections > 80:  # Assuming max_connections = 100
            self.recommendations.append(OptimizationRecommendation(
                component="database/connections",
                priority="HIGH",
                category="PERFORMANCE", 
                issue="High database connection usage detected",
                recommendation="Implement connection pooling optimization, review connection timeouts",
                expected_impact="Reduced connection contention and improved response times",
                implementation_effort="MEDIUM"
            ))
        
        # Check for slow queries
        slow_queries = analysis.get('query_performance', {}).get('slow_queries', [])
        for query in slow_queries:
            if query['avg_time_ms'] > 1000:  # Queries taking > 1 second
                self.recommendations.append(OptimizationRecommendation(
                    component="database/queries",
                    priority="HIGH",
                    category="PERFORMANCE",
                    issue=f"Slow query detected: {query['avg_time_ms']:.2f}ms average",
                    recommendation="Optimize query with proper indexing, query rewriting, or table partitioning",
                    expected_impact="Significant response time improvement",
                    implementation_effort="MEDIUM"
                ))
        
        # Check for unused indexes
        indexes = analysis.get('index_usage', [])
        for index in indexes:
            if index['reads'] < 100:  # Very low usage
                self.recommendations.append(OptimizationRecommendation(
                    component="database/indexes",
                    priority="LOW",
                    category="COST",
                    issue=f"Potentially unused index: {index['index']}",
                    recommendation="Consider dropping unused indexes to reduce storage and maintenance overhead",
                    expected_impact="Reduced storage costs and faster write operations",
                    implementation_effort="LOW",
                    estimated_cost_savings=50.0  # Estimated monthly savings
                ))

    def analyze_application_metrics(self, app_urls: List[str]) -> Dict[str, Any]:
        """Analyze application performance metrics"""
        logger.info("Analyzing application metrics...")
        
        analysis = {
            'health_status': {},
            'response_times': {},
            'jvm_metrics': {},
            'custom_metrics': {},
            'recommendations': []
        }
        
        for url in app_urls:
            try:
                service_name = url.split('//')[1].split('.')[0] if '//' in url else url
                
                # Health check
                health_response = requests.get(f"{url}/actuator/health", timeout=5)
                analysis['health_status'][service_name] = {
                    'status': health_response.json().get('status', 'UNKNOWN'),
                    'response_time_ms': health_response.elapsed.total_seconds() * 1000
                }
                
                # JVM metrics
                metrics_response = requests.get(f"{url}/actuator/metrics", timeout=10)
                if metrics_response.status_code == 200:
                    metrics_list = metrics_response.json().get('names', [])
                    
                    # Get key JVM metrics
                    jvm_metrics = {}
                    for metric in ['jvm.memory.used', 'jvm.gc.pause', 'http.server.requests']:
                        if metric in metrics_list:
                            metric_response = requests.get(f"{url}/actuator/metrics/{metric}", timeout=5)
                            if metric_response.status_code == 200:
                                jvm_metrics[metric] = metric_response.json()
                    
                    analysis['jvm_metrics'][service_name] = jvm_metrics
                    
                    # Generate JVM-specific recommendations
                    self._generate_jvm_recommendations(service_name, jvm_metrics)
                
            except Exception as e:
                logger.warning(f"Could not analyze {url}: {e}")
                analysis['health_status'][service_name] = {'status': 'ERROR', 'error': str(e)}
        
        return analysis

    def _generate_jvm_recommendations(self, service_name: str, jvm_metrics: Dict[str, Any]):
        """Generate JVM optimization recommendations"""
        
        # Memory usage analysis
        memory_metric = jvm_metrics.get('jvm.memory.used', {})
        memory_measurements = memory_metric.get('measurements', [])
        
        for measurement in memory_measurements:
            if measurement.get('statistic') == 'VALUE':
                memory_tags = {tag['key']: tag['value'] for tag in measurement.get('availableTags', [])}
                if memory_tags.get('area') == 'heap':
                    memory_used_mb = measurement['value'] / (1024 * 1024)
                    
                    if memory_used_mb > 1000:  # > 1GB heap usage
                        self.recommendations.append(OptimizationRecommendation(
                            component=f"jvm/{service_name}",
                            priority="MEDIUM",
                            category="PERFORMANCE",
                            issue=f"High heap memory usage: {memory_used_mb:.0f}MB",
                            recommendation="Consider increasing heap size or optimizing memory usage patterns",
                            expected_impact="Reduced GC pressure and improved response times",
                            implementation_effort="LOW"
                        ))
        
        # GC analysis
        gc_metric = jvm_metrics.get('jvm.gc.pause', {})
        gc_measurements = gc_metric.get('measurements', [])
        
        for measurement in gc_measurements:
            if measurement.get('statistic') == 'TOTAL_TIME':
                gc_time_seconds = measurement['value']
                if gc_time_seconds > 30:  # > 30 seconds total GC time
                    self.recommendations.append(OptimizationRecommendation(
                        component=f"jvm/{service_name}",
                        priority="HIGH",
                        category="PERFORMANCE",
                        issue=f"High GC time: {gc_time_seconds:.1f}s total",
                        recommendation="Tune GC settings, consider G1GC or ZGC for better performance",
                        expected_impact="Significantly reduced GC pauses and improved throughput",
                        implementation_effort="MEDIUM"
                    ))

    def analyze_cost_optimization(self) -> Dict[str, Any]:
        """Analyze cost optimization opportunities"""
        logger.info("Analyzing cost optimization opportunities...")
        
        analysis = {
            'resource_rightsizing': {},
            'unused_resources': {},
            'scheduling_optimization': {},
            'storage_optimization': {},
            'potential_savings': 0.0
        }
        
        try:
            # Analyze pod resource usage vs requests/limits
            pods = self.k8s_core_v1.list_namespaced_pod(namespace=self.namespace)
            
            for pod in pods.items:
                if not pod.spec.containers:
                    continue
                    
                container = pod.spec.containers[0]
                pod_name = pod.metadata.name
                
                # Check for over-provisioned resources
                if container.resources and container.resources.limits:
                    limits = container.resources.limits
                    requests = container.resources.requests or {}
                    
                    cpu_limit = limits.get('cpu', '0')
                    memory_limit = limits.get('memory', '0')
                    
                    # Simplified cost analysis (would be more complex in reality)
                    if cpu_limit.replace('m', '').isdigit():
                        cpu_cores = int(cpu_limit.replace('m', '')) / 1000
                        if cpu_cores > 2:  # > 2 cores might be over-provisioned
                            monthly_savings = cpu_cores * 30 * 0.05  # $0.05 per core-hour
                            analysis['potential_savings'] += monthly_savings
                            
                            self.recommendations.append(OptimizationRecommendation(
                                component=f"pod/{pod_name}",
                                priority="MEDIUM",
                                category="COST",
                                issue=f"Potentially over-provisioned CPU: {cpu_cores} cores",
                                recommendation="Right-size CPU requests based on actual usage patterns",
                                expected_impact="Reduced compute costs without performance impact",
                                implementation_effort="LOW",
                                estimated_cost_savings=monthly_savings
                            ))
            
            # Check for unused persistent volumes
            pvs = self.k8s_core_v1.list_persistent_volume()
            for pv in pvs.items:
                if pv.status.phase == 'Available':  # Unused PV
                    storage_size = pv.spec.capacity.get('storage', '0Gi')
                    size_gb = int(storage_size.replace('Gi', '')) if 'Gi' in storage_size else 0
                    monthly_savings = size_gb * 0.10  # $0.10 per GB per month
                    analysis['potential_savings'] += monthly_savings
                    
                    self.recommendations.append(OptimizationRecommendation(
                        component=f"storage/{pv.metadata.name}",
                        priority="LOW",
                        category="COST",
                        issue=f"Unused persistent volume: {size_gb}GB",
                        recommendation="Delete unused persistent volumes to reduce storage costs",
                        expected_impact="Direct storage cost reduction",
                        implementation_effort="LOW",
                        estimated_cost_savings=monthly_savings
                    ))
        
        except Exception as e:
            logger.error(f"Cost analysis error: {e}")
        
        return analysis

    def generate_optimization_report(self) -> Dict[str, Any]:
        """Generate comprehensive optimization report"""
        logger.info("Generating comprehensive optimization report...")
        
        report = {
            'summary': {
                'total_recommendations': len(self.recommendations),
                'high_priority': len([r for r in self.recommendations if r.priority == 'HIGH']),
                'medium_priority': len([r for r in self.recommendations if r.priority == 'MEDIUM']),
                'low_priority': len([r for r in self.recommendations if r.priority == 'LOW']),
                'categories': {},
                'potential_monthly_savings': sum(r.estimated_cost_savings or 0 for r in self.recommendations),
                'generated_at': datetime.now().isoformat()
            },
            'recommendations': [asdict(r) for r in self.recommendations],
            'implementation_plan': self._create_implementation_plan()
        }
        
        # Categorize recommendations
        categories = {}
        for rec in self.recommendations:
            categories[rec.category] = categories.get(rec.category, 0) + 1
        report['summary']['categories'] = categories
        
        return report

    def _create_implementation_plan(self) -> Dict[str, List[Dict[str, Any]]]:
        """Create prioritized implementation plan"""
        
        # Sort recommendations by priority and effort
        priority_order = {'HIGH': 0, 'MEDIUM': 1, 'LOW': 2}
        effort_order = {'LOW': 0, 'MEDIUM': 1, 'HIGH': 2}
        
        sorted_recs = sorted(
            self.recommendations,
            key=lambda r: (priority_order[r.priority], effort_order[r.implementation_effort])
        )
        
        plan = {
            'immediate_actions': [],  # High priority, low effort
            'short_term': [],         # Medium priority or high priority with medium effort  
            'long_term': []           # Low priority or high effort items
        }
        
        for rec in sorted_recs:
            rec_dict = asdict(rec)
            
            if rec.priority == 'HIGH' and rec.implementation_effort == 'LOW':
                plan['immediate_actions'].append(rec_dict)
            elif rec.priority in ['HIGH', 'MEDIUM']:
                plan['short_term'].append(rec_dict)
            else:
                plan['long_term'].append(rec_dict)
        
        return plan

    def run_comprehensive_analysis(self, db_connection_string: Optional[str] = None, 
                                 app_urls: Optional[List[str]] = None) -> Dict[str, Any]:
        """Run comprehensive system analysis and optimization"""
        logger.info("Starting comprehensive system optimization analysis...")
        
        analysis_results = {
            'kubernetes': self.analyze_kubernetes_resources(),
            'cost_optimization': self.analyze_cost_optimization()
        }
        
        if db_connection_string:
            analysis_results['database'] = self.analyze_database_performance(db_connection_string)
        
        if app_urls:
            analysis_results['applications'] = self.analyze_application_metrics(app_urls)
        
        # Generate final report
        optimization_report = self.generate_optimization_report()
        analysis_results['optimization_report'] = optimization_report
        
        logger.info(f"Analysis complete. Generated {len(self.recommendations)} recommendations.")
        
        return analysis_results

def main():
    parser = argparse.ArgumentParser(description='System Optimization Tool for Astronomical Pipeline')
    parser.add_argument('--namespace', default='astro-pipeline', help='Kubernetes namespace')
    parser.add_argument('--kubeconfig', help='Path to kubeconfig file')
    parser.add_argument('--db-url', help='Database connection string')
    parser.add_argument('--app-urls', nargs='+', help='Application URLs for metrics analysis')
    parser.add_argument('--output', default='system_optimization_report.json', help='Output file')
    
    args = parser.parse_args()
    
    try:
        # Initialize optimizer
        optimizer = SystemOptimizer(namespace=args.namespace, kubeconfig_path=args.kubeconfig)
        
        # Run comprehensive analysis
        results = optimizer.run_comprehensive_analysis(
            db_connection_string=args.db_url,
            app_urls=args.app_urls
        )
        
        # Save results
        with open(args.output, 'w') as f:
            json.dump(results, f, indent=2, default=str)
        
        # Print summary
        report = results.get('optimization_report', {})
        summary = report.get('summary', {})
        
        print("\n" + "="*80)
        print("SYSTEM OPTIMIZATION ANALYSIS RESULTS")
        print("="*80)
        print(f"Total Recommendations: {summary.get('total_recommendations', 0)}")
        print(f"High Priority: {summary.get('high_priority', 0)}")
        print(f"Medium Priority: {summary.get('medium_priority', 0)}")
        print(f"Low Priority: {summary.get('low_priority', 0)}")
        print(f"Potential Monthly Savings: ${summary.get('potential_monthly_savings', 0):.2f}")
        
        # Show top recommendations
        implementation_plan = report.get('implementation_plan', {})
        immediate_actions = implementation_plan.get('immediate_actions', [])
        
        if immediate_actions:
            print(f"\nTOP {min(5, len(immediate_actions))} IMMEDIATE ACTIONS:")
            for i, action in enumerate(immediate_actions[:5]):
                print(f"{i+1}. {action['component']}: {action['issue']}")
                print(f"   → {action['recommendation']}")
        
        print(f"\nDetailed report saved to: {args.output}")
        
    except Exception as e:
        logger.error(f"System optimization analysis failed: {e}")
        return 1
    
    return 0

if __name__ == "__main__":
    exit(main())