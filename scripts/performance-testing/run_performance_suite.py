#!/usr/bin/env python3
"""
Master Performance Test Suite Orchestrator
Coordinates comprehensive performance testing across all components of the astronomical data pipeline
"""

import json
import os
import sys
import time
import subprocess
import argparse
import logging
import asyncio
from datetime import datetime, timedelta
from typing import Dict, List, Any, Optional
from concurrent.futures import ThreadPoolExecutor
import requests

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

class PerformanceTestSuite:
    def __init__(self, config_file: Optional[str] = None):
        self.config = self._load_config(config_file) if config_file else self._default_config()
        self.results = {}
        self.start_time = datetime.now()
        
    def _load_config(self, config_file: str) -> Dict[str, Any]:
        """Load configuration from file"""
        try:
            with open(config_file, 'r') as f:
                return json.load(f)
        except Exception as e:
            logger.error(f"Failed to load config file {config_file}: {e}")
            return self._default_config()
    
    def _default_config(self) -> Dict[str, Any]:
        """Default configuration for performance testing"""
        return {
            "database": {
                "host": "localhost",
                "port": "5432",
                "database": "astro_catalog", 
                "user": "astro_user",
                "password": "password123"
            },
            "applications": {
                "image_processor": "http://localhost:8080",
                "catalog_service": "http://localhost:8081"
            },
            "kubernetes": {
                "namespace": "astro-pipeline",
                "kubeconfig": None
            },
            "test_parameters": {
                "quick_mode": False,
                "load_test_duration": 300,
                "concurrent_users": 50,
                "database_iterations": 1000
            },
            "output": {
                "directory": "./performance_results",
                "timestamp": True,
                "formats": ["json", "html", "csv"]
            }
        }

    def prepare_environment(self) -> bool:
        """Prepare test environment and validate connectivity"""
        logger.info("Preparing performance test environment...")
        
        # Create output directory
        output_dir = self.config["output"]["directory"]
        if self.config["output"]["timestamp"]:
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            output_dir = f"{output_dir}_{timestamp}"
        
        os.makedirs(output_dir, exist_ok=True)
        self.output_dir = output_dir
        
        # Validate application connectivity
        for app_name, app_url in self.config["applications"].items():
            try:
                response = requests.get(f"{app_url}/actuator/health", timeout=10)
                if response.status_code == 200:
                    logger.info(f"✅ {app_name} connectivity verified")
                else:
                    logger.warning(f"⚠️ {app_name} health check returned {response.status_code}")
            except Exception as e:
                logger.error(f"❌ {app_name} connectivity failed: {e}")
                return False
        
        # Validate database connectivity
        try:
            db_config = self.config["database"]
            conn_string = f"postgresql://{db_config['user']}:{db_config['password']}@{db_config['host']}:{db_config['port']}/{db_config['database']}"
            
            import psycopg2
            with psycopg2.connect(conn_string) as conn:
                cursor = conn.cursor()
                cursor.execute("SELECT 1")
                logger.info("✅ Database connectivity verified")
        except Exception as e:
            logger.error(f"❌ Database connectivity failed: {e}")
            return False
        
        logger.info("✅ Environment preparation completed successfully")
        return True

    def run_database_performance_test(self) -> Dict[str, Any]:
        """Execute database performance testing"""
        logger.info("Starting database performance testing...")
        
        try:
            db_config = self.config["database"]
            test_params = self.config["test_parameters"]
            
            cmd = [
                sys.executable, "db_performance_test.py",
                "--host", db_config["host"],
                "--port", db_config["port"], 
                "--database", db_config["database"],
                "--user", db_config["user"],
                "--password", db_config["password"],
                "--output", f"{self.output_dir}/db_performance_results.json"
            ]
            
            if test_params["quick_mode"]:
                cmd.append("--quick")
            
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=3600)
            
            if result.returncode == 0:
                logger.info("✅ Database performance test completed successfully")
                
                # Load results
                with open(f"{self.output_dir}/db_performance_results.json", 'r') as f:
                    return json.load(f)
            else:
                logger.error(f"❌ Database performance test failed: {result.stderr}")
                return {"status": "failed", "error": result.stderr}
                
        except subprocess.TimeoutExpired:
            logger.error("❌ Database performance test timed out")
            return {"status": "timeout"}
        except Exception as e:
            logger.error(f"❌ Database performance test error: {e}")
            return {"status": "error", "error": str(e)}

    def run_application_performance_test(self) -> Dict[str, Any]:
        """Execute application performance testing"""
        logger.info("Starting application performance testing...")
        
        try:
            test_params = self.config["test_parameters"]
            app_results = {}
            
            for app_name, app_url in self.config["applications"].items():
                logger.info(f"Testing {app_name} at {app_url}")
                
                cmd = [
                    sys.executable, "app_performance_test.py",
                    "--url", app_url,
                    "--output", f"{self.output_dir}/app_performance_{app_name}.json",
                    "--timeout", "60"
                ]
                
                if test_params["quick_mode"]:
                    cmd.append("--quick")
                
                result = subprocess.run(cmd, capture_output=True, text=True, timeout=3600)
                
                if result.returncode == 0:
                    logger.info(f"✅ {app_name} performance test completed")
                    
                    # Load results
                    with open(f"{self.output_dir}/app_performance_{app_name}.json", 'r') as f:
                        app_results[app_name] = json.load(f)
                else:
                    logger.error(f"❌ {app_name} performance test failed: {result.stderr}")
                    app_results[app_name] = {"status": "failed", "error": result.stderr}
            
            return app_results
            
        except Exception as e:
            logger.error(f"❌ Application performance test error: {e}")
            return {"status": "error", "error": str(e)}

    def run_system_optimization_analysis(self) -> Dict[str, Any]:
        """Execute system optimization analysis"""
        logger.info("Starting system optimization analysis...")
        
        try:
            k8s_config = self.config["kubernetes"]
            app_urls = list(self.config["applications"].values())
            
            cmd = [
                sys.executable, "system_optimizer.py",
                "--namespace", k8s_config["namespace"],
                "--output", f"{self.output_dir}/system_optimization_report.json"
            ]
            
            if k8s_config.get("kubeconfig"):
                cmd.extend(["--kubeconfig", k8s_config["kubeconfig"]])
            
            # Add database URL
            db_config = self.config["database"]
            db_url = f"postgresql://{db_config['user']}:{db_config['password']}@{db_config['host']}:{db_config['port']}/{db_config['database']}"
            cmd.extend(["--db-url", db_url])
            
            # Add application URLs
            cmd.extend(["--app-urls"] + app_urls)
            
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=1800)
            
            if result.returncode == 0:
                logger.info("✅ System optimization analysis completed")
                
                # Load results
                with open(f"{self.output_dir}/system_optimization_report.json", 'r') as f:
                    return json.load(f)
            else:
                logger.error(f"❌ System optimization analysis failed: {result.stderr}")
                return {"status": "failed", "error": result.stderr}
                
        except Exception as e:
            logger.error(f"❌ System optimization analysis error: {e}")
            return {"status": "error", "error": str(e)}

    def run_load_testing(self) -> Dict[str, Any]:
        """Execute comprehensive load testing with Locust"""
        logger.info("Starting comprehensive load testing...")
        
        try:
            test_params = self.config["test_parameters"]
            
            # Use first application URL as primary target
            primary_app = list(self.config["applications"].values())[0]
            
            # Run Locust in headless mode
            cmd = [
                "locust", "-f", "locust_load_test.py",
                "--host", primary_app,
                "--users", str(test_params["concurrent_users"]),
                "--spawn-rate", "5",
                "--run-time", f"{test_params['load_test_duration']}s",
                "--headless",
                "--html", f"{self.output_dir}/load_test_report.html",
                "--csv", f"{self.output_dir}/load_test_results"
            ]
            
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=test_params["load_test_duration"] + 300)
            
            if result.returncode == 0:
                logger.info("✅ Load testing completed successfully")
                
                # Parse CSV results if available
                results = {"status": "completed", "output": result.stdout}
                
                # Try to load CSV statistics
                try:
                    import pandas as pd
                    stats_df = pd.read_csv(f"{self.output_dir}/load_test_results_stats.csv")
                    results["statistics"] = stats_df.to_dict('records')
                except Exception:
                    logger.warning("Could not parse load test CSV results")
                
                return results
            else:
                logger.error(f"❌ Load testing failed: {result.stderr}")
                return {"status": "failed", "error": result.stderr}
                
        except subprocess.TimeoutExpired:
            logger.error("❌ Load testing timed out")
            return {"status": "timeout"}
        except Exception as e:
            logger.error(f"❌ Load testing error: {e}")
            return {"status": "error", "error": str(e)}

    def generate_comprehensive_report(self) -> Dict[str, Any]:
        """Generate comprehensive performance analysis report"""
        logger.info("Generating comprehensive performance report...")
        
        end_time = datetime.now()
        duration = end_time - self.start_time
        
        # Compile all results
        comprehensive_report = {
            "metadata": {
                "test_suite_version": "1.0.0",
                "execution_start": self.start_time.isoformat(),
                "execution_end": end_time.isoformat(), 
                "total_duration_seconds": duration.total_seconds(),
                "configuration": self.config
            },
            "results": {
                "database_performance": self.results.get("database", {}),
                "application_performance": self.results.get("applications", {}),
                "system_optimization": self.results.get("optimization", {}),
                "load_testing": self.results.get("load_testing", {})
            },
            "summary": self._generate_executive_summary(),
            "recommendations": self._aggregate_recommendations(),
            "performance_gates": self._evaluate_performance_gates()
        }
        
        # Save comprehensive report
        report_file = f"{self.output_dir}/comprehensive_performance_report.json"
        with open(report_file, 'w') as f:
            json.dump(comprehensive_report, f, indent=2, default=str)
        
        logger.info(f"✅ Comprehensive report generated: {report_file}")
        return comprehensive_report

    def _generate_executive_summary(self) -> Dict[str, Any]:
        """Generate executive summary of performance testing"""
        summary = {
            "overall_status": "UNKNOWN",
            "key_metrics": {},
            "critical_issues": [],
            "top_recommendations": [],
            "performance_score": 0
        }
        
        # Database performance summary
        db_results = self.results.get("database", {})
        if "cone_search" in db_results:
            cs = db_results["cone_search"]
            summary["key_metrics"]["avg_cone_search_time_ms"] = cs.get("avg_time_ms", 0)
            summary["key_metrics"]["cone_search_throughput"] = cs.get("throughput_ops_per_sec", 0)
        
        # Application performance summary  
        app_results = self.results.get("applications", {})
        for app_name, app_data in app_results.items():
            if isinstance(app_data, dict) and "health_check" in app_data:
                hc = app_data["health_check"]
                summary["key_metrics"][f"{app_name}_health_check_time_ms"] = hc.get("avg_response_time_ms", 0)
                summary["key_metrics"][f"{app_name}_success_rate"] = hc.get("success_rate", 0)
        
        # System optimization summary
        opt_results = self.results.get("optimization", {})
        if "optimization_report" in opt_results:
            opt_summary = opt_results["optimization_report"].get("summary", {})
            summary["key_metrics"]["total_optimization_recommendations"] = opt_summary.get("total_recommendations", 0)
            summary["key_metrics"]["high_priority_issues"] = opt_summary.get("high_priority", 0)
            summary["key_metrics"]["potential_monthly_savings"] = opt_summary.get("potential_monthly_savings", 0)
        
        # Load testing summary
        load_results = self.results.get("load_testing", {})
        if "statistics" in load_results:
            stats = load_results["statistics"]
            if stats:
                avg_response_time = sum(s.get("Average Response Time", 0) for s in stats) / len(stats)
                failure_rate = sum(s.get("Failure Count", 0) for s in stats) / sum(s.get("Request Count", 1) for s in stats)
                summary["key_metrics"]["load_test_avg_response_time"] = avg_response_time
                summary["key_metrics"]["load_test_failure_rate"] = failure_rate * 100
        
        # Determine overall status
        critical_failures = 0
        if summary["key_metrics"].get("cone_search_throughput", 0) < 10:
            critical_failures += 1
            summary["critical_issues"].append("Database cone search performance below threshold")
        
        if summary["key_metrics"].get("load_test_failure_rate", 0) > 5:
            critical_failures += 1
            summary["critical_issues"].append("Load test failure rate exceeds 5%")
        
        if summary["key_metrics"].get("high_priority_issues", 0) > 5:
            critical_failures += 1  
            summary["critical_issues"].append("Multiple high-priority optimization issues")
        
        # Overall status determination
        if critical_failures == 0:
            summary["overall_status"] = "EXCELLENT"
            summary["performance_score"] = 95
        elif critical_failures == 1:
            summary["overall_status"] = "GOOD"
            summary["performance_score"] = 80
        elif critical_failures == 2:
            summary["overall_status"] = "FAIR"
            summary["performance_score"] = 60
        else:
            summary["overall_status"] = "POOR"
            summary["performance_score"] = 40
        
        return summary

    def _aggregate_recommendations(self) -> List[Dict[str, Any]]:
        """Aggregate recommendations from all testing components"""
        recommendations = []
        
        # System optimization recommendations
        opt_results = self.results.get("optimization", {})
        if "optimization_report" in opt_results:
            opt_recs = opt_results["optimization_report"].get("recommendations", [])
            recommendations.extend(opt_recs)
        
        # Database-specific recommendations
        db_results = self.results.get("database", {})
        if "assessment" in db_results:
            for component, assessment in db_results["assessment"].items():
                if "POOR" in assessment or "FAIR" in assessment:
                    recommendations.append({
                        "component": f"database/{component}",
                        "priority": "MEDIUM",
                        "category": "PERFORMANCE",
                        "issue": f"Database {component} performance issue",
                        "recommendation": assessment,
                        "implementation_effort": "MEDIUM"
                    })
        
        # Sort by priority
        priority_order = {"HIGH": 0, "MEDIUM": 1, "LOW": 2}
        recommendations.sort(key=lambda x: priority_order.get(x.get("priority", "LOW"), 2))
        
        return recommendations[:10]  # Top 10 recommendations

    def _evaluate_performance_gates(self) -> Dict[str, Any]:
        """Evaluate performance against defined gates/thresholds"""
        gates = {
            "database_cone_search_time": {"threshold": 100, "unit": "ms", "status": "UNKNOWN"},
            "application_health_check": {"threshold": 99, "unit": "%", "status": "UNKNOWN"}, 
            "load_test_failure_rate": {"threshold": 5, "unit": "%", "status": "UNKNOWN"},
            "system_optimization_critical": {"threshold": 3, "unit": "count", "status": "UNKNOWN"}
        }
        
        summary = self.results.get("summary", {}).get("key_metrics", {})
        
        # Evaluate each gate
        cone_search_time = summary.get("avg_cone_search_time_ms", 0)
        if cone_search_time > 0:
            gates["database_cone_search_time"]["actual"] = cone_search_time
            gates["database_cone_search_time"]["status"] = "PASS" if cone_search_time < 100 else "FAIL"
        
        # Application health check (use average across all apps)
        health_rates = [v for k, v in summary.items() if k.endswith("_success_rate")]
        if health_rates:
            avg_health = sum(health_rates) / len(health_rates)
            gates["application_health_check"]["actual"] = avg_health
            gates["application_health_check"]["status"] = "PASS" if avg_health >= 99 else "FAIL"
        
        # Load test failure rate
        failure_rate = summary.get("load_test_failure_rate", 0)
        if failure_rate > 0:
            gates["load_test_failure_rate"]["actual"] = failure_rate
            gates["load_test_failure_rate"]["status"] = "PASS" if failure_rate < 5 else "FAIL"
        
        # Critical optimization issues
        critical_issues = summary.get("high_priority_issues", 0)
        gates["system_optimization_critical"]["actual"] = critical_issues
        gates["system_optimization_critical"]["status"] = "PASS" if critical_issues < 3 else "FAIL"
        
        # Overall gate status
        passing_gates = sum(1 for gate in gates.values() if gate["status"] == "PASS")
        total_gates = len([gate for gate in gates.values() if gate["status"] != "UNKNOWN"])
        
        return {
            "gates": gates,
            "overall_status": "PASS" if passing_gates == total_gates and total_gates > 0 else "FAIL",
            "passing_gates": passing_gates,
            "total_gates": total_gates
        }

    def run_comprehensive_suite(self) -> Dict[str, Any]:
        """Execute the complete performance testing suite"""
        logger.info("🚀 Starting comprehensive performance testing suite...")
        
        # Prepare environment
        if not self.prepare_environment():
            logger.error("❌ Environment preparation failed")
            return {"status": "failed", "stage": "preparation"}
        
        # Execute tests in sequence with error handling
        test_stages = [
            ("database", self.run_database_performance_test),
            ("applications", self.run_application_performance_test),
            ("optimization", self.run_system_optimization_analysis),
            ("load_testing", self.run_load_testing)
        ]
        
        for stage_name, test_function in test_stages:
            logger.info(f"🔄 Executing {stage_name} testing stage...")
            try:
                start_time = time.time()
                self.results[stage_name] = test_function()
                duration = time.time() - start_time
                logger.info(f"✅ {stage_name} stage completed in {duration:.1f} seconds")
            except Exception as e:
                logger.error(f"❌ {stage_name} stage failed: {e}")
                self.results[stage_name] = {"status": "error", "error": str(e)}
        
        # Generate comprehensive report
        comprehensive_report = self.generate_comprehensive_report()
        
        # Print summary
        self._print_final_summary(comprehensive_report)
        
        logger.info(f"🎉 Performance testing suite completed! Results in: {self.output_dir}")
        return comprehensive_report

    def _print_final_summary(self, report: Dict[str, Any]):
        """Print final summary to console"""
        print("\n" + "="*80)
        print("🚀 ASTRONOMICAL DATA PIPELINE PERFORMANCE TEST RESULTS")
        print("="*80)
        
        summary = report.get("summary", {})
        print(f"Overall Status: {summary.get('overall_status', 'UNKNOWN')}")
        print(f"Performance Score: {summary.get('performance_score', 0)}/100")
        
        print("\n📊 KEY METRICS:")
        key_metrics = summary.get("key_metrics", {})
        for metric, value in key_metrics.items():
            print(f"  • {metric}: {value}")
        
        print("\n⚠️ CRITICAL ISSUES:")
        critical_issues = summary.get("critical_issues", [])
        if critical_issues:
            for issue in critical_issues:
                print(f"  • {issue}")
        else:
            print("  • No critical issues detected ✅")
        
        print("\n🎯 PERFORMANCE GATES:")
        gates = report.get("performance_gates", {}).get("gates", {})
        for gate_name, gate_data in gates.items():
            status_icon = "✅" if gate_data["status"] == "PASS" else "❌" if gate_data["status"] == "FAIL" else "⚪"
            actual = gate_data.get("actual", "N/A")
            threshold = gate_data["threshold"]
            unit = gate_data["unit"]
            print(f"  {status_icon} {gate_name}: {actual} (threshold: {threshold} {unit})")
        
        print("\n🔧 TOP RECOMMENDATIONS:")
        recommendations = report.get("recommendations", [])[:5]
        for i, rec in enumerate(recommendations, 1):
            print(f"  {i}. [{rec.get('priority', 'UNKNOWN')}] {rec.get('component', 'Unknown')}")
            print(f"     → {rec.get('recommendation', 'No recommendation')}")
        
        print("\n📁 DETAILED RESULTS:")
        print(f"  • Output Directory: {self.output_dir}")
        print(f"  • Comprehensive Report: {self.output_dir}/comprehensive_performance_report.json")
        print(f"  • Load Test Report: {self.output_dir}/load_test_report.html")

def main():
    parser = argparse.ArgumentParser(description='Comprehensive Performance Test Suite for Astronomical Data Pipeline')
    parser.add_argument('--config', help='Configuration file path')
    parser.add_argument('--quick', action='store_true', help='Run quick tests only')
    parser.add_argument('--output-dir', help='Output directory for results')
    parser.add_argument('--namespace', default='astro-pipeline', help='Kubernetes namespace')
    parser.add_argument('--app-url', help='Primary application URL')
    parser.add_argument('--db-host', help='Database host')
    
    args = parser.parse_args()
    
    try:
        # Initialize test suite
        suite = PerformanceTestSuite(config_file=args.config)
        
        # Override config with CLI arguments
        if args.quick:
            suite.config["test_parameters"]["quick_mode"] = True
            suite.config["test_parameters"]["load_test_duration"] = 60
            suite.config["test_parameters"]["concurrent_users"] = 10
        
        if args.output_dir:
            suite.config["output"]["directory"] = args.output_dir
            
        if args.namespace:
            suite.config["kubernetes"]["namespace"] = args.namespace
            
        if args.app_url:
            suite.config["applications"]["primary"] = args.app_url
            
        if args.db_host:
            suite.config["database"]["host"] = args.db_host
        
        # Run comprehensive test suite
        results = suite.run_comprehensive_suite()
        
        # Exit with appropriate code
        overall_status = results.get("summary", {}).get("overall_status", "UNKNOWN")
        if overall_status in ["EXCELLENT", "GOOD"]:
            return 0
        elif overall_status == "FAIR":
            return 1
        else:
            return 2
            
    except KeyboardInterrupt:
        logger.info("Performance testing interrupted by user")
        return 130
    except Exception as e:
        logger.error(f"Performance testing suite failed: {e}")
        return 1

if __name__ == "__main__":
    exit(main())