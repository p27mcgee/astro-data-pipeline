"""
PostGIS Extension Initializer for Astronomical Data Pipeline

BACKGROUND:
The astronomical data processing pipeline requires PostGIS spatial extensions
for efficient coordinate system transformations, cone searches, and spatial
indexing of astronomical objects.

INFRASTRUCTURE DECISION:
After evaluating complex infrastructure approaches (Lambda functions, ECS Fargate),
we chose application-level installation following AWS RDS PostgreSQL best practices.
This provides simplicity, reliability, and proper separation of concerns.

IMPLEMENTATION:
This module automatically installs PostGIS extensions during application startup.
The installation is idempotent and safe to run multiple times.

EXTENSIONS INSTALLED:
- postgis: Core spatial functionality for geometric data types and operations
- postgis_topology: Advanced topological operations for complex spatial relationships

REFERENCE:
https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Appendix.PostgreSQL.CommonDBATasks.PostGIS.html

Author: STScI Astronomical Data Pipeline Team
Since: 2025-09-26
"""

import logging
import psycopg2
from typing import Optional

logger = logging.getLogger(__name__)


class PostGISInitializer:
    """
    Handles PostGIS extension installation for astronomical data processing.

    This class provides methods to automatically install and verify PostGIS
    extensions required for spatial operations on astronomical coordinate data.
    """

    def __init__(self, connection_params: dict):
        """
        Initialize PostGIS installer with database connection parameters.

        Args:
            connection_params: Dictionary containing database connection details
                - host: RDS endpoint hostname
                - port: Database port (typically 5432)
                - database: Database name
                - user: Database username
                - password: Database password (from AWS Secrets Manager)
        """
        self.connection_params = connection_params

    def initialize_postgis(self) -> bool:
        """
        Initialize PostGIS extensions for astronomical data processing.

        This method connects to the PostgreSQL database and installs the required
        PostGIS extensions. It's designed to be called during application startup
        to ensure spatial capabilities are available before processing begins.

        TIMING: Should be called during application initialization, after:
        - Configuration loading (including database credentials)
        - Logging setup
        - Before any spatial data operations

        ERROR HANDLING: Returns False on failure to allow graceful degradation
        or application startup failure based on requirements.

        Returns:
            bool: True if PostGIS was successfully initialized, False otherwise
        """
        logger.info("Initializing PostGIS extensions for astronomical data processing...")

        connection: Optional[psycopg2.extensions.connection] = None

        try:
            # Establish database connection
            logger.debug("Connecting to PostgreSQL database...")
            connection = psycopg2.connect(**self.connection_params)

            with connection.cursor() as cursor:
                # Install core PostGIS extension for spatial data types and functions
                logger.debug("Installing PostGIS core extension...")
                cursor.execute("CREATE EXTENSION IF NOT EXISTS postgis;")

                # Install PostGIS topology extension for advanced spatial relationships
                logger.debug("Installing PostGIS topology extension...")
                cursor.execute("CREATE EXTENSION IF NOT EXISTS postgis_topology;")

                # Commit the extension installations
                connection.commit()

                # Verify installation by checking PostGIS version
                cursor.execute("SELECT PostGIS_version();")
                postgis_version = cursor.fetchone()[0]

                logger.info(f"PostGIS extensions installed successfully. Version: {postgis_version}")

                # Log available spatial reference systems for astronomical coordinates
                cursor.execute("SELECT COUNT(*) FROM spatial_ref_sys WHERE auth_name = 'EPSG';")
                srid_count = cursor.fetchone()[0]

                logger.info(f"Spatial reference systems available: {srid_count} EPSG codes for coordinate transformations")

                return True

        except psycopg2.Error as e:
            logger.error(f"Database error during PostGIS initialization: {e}")
            return False

        except Exception as e:
            logger.error(f"Unexpected error during PostGIS initialization: {e}")
            return False

        finally:
            if connection:
                connection.close()
                logger.debug("Database connection closed")


def initialize_postgis_from_secrets_manager(secret_name: str, region: str = 'us-east-1') -> bool:
    """
    Initialize PostGIS using database credentials from AWS Secrets Manager.

    This convenience function retrieves database connection parameters from
    AWS Secrets Manager and initializes PostGIS extensions. This is the
    recommended approach for production deployments.

    Args:
        secret_name: Name of the secret in AWS Secrets Manager containing database credentials
        region: AWS region where the secret is stored

    Returns:
        bool: True if PostGIS was successfully initialized, False otherwise

    Example:
        # During application startup
        success = initialize_postgis_from_secrets_manager(
            secret_name='astro-data-pipeline-staging-rds-credentials',
            region='us-east-1'
        )
        if not success:
            raise RuntimeError("Failed to initialize PostGIS - application cannot start")
    """
    import boto3
    import json

    try:
        # Retrieve database credentials from Secrets Manager
        logger.debug(f"Retrieving database credentials from Secrets Manager: {secret_name}")

        secrets_client = boto3.client('secretsmanager', region_name=region)
        secret_response = secrets_client.get_secret_value(SecretId=secret_name)

        # Parse JSON credentials
        credentials = json.loads(secret_response['SecretString'])

        # Extract connection parameters
        connection_params = {
            'host': credentials['endpoint'].split(':')[0],
            'port': int(credentials['port']),
            'database': credentials['dbname'],
            'user': credentials['username'],
            'password': credentials['password']
        }

        # Initialize PostGIS with retrieved credentials
        initializer = PostGISInitializer(connection_params)
        return initializer.initialize_postgis()

    except Exception as e:
        logger.error(f"Failed to retrieve credentials from Secrets Manager: {e}")
        return False


# Example usage for different Python frameworks
if __name__ == "__main__":
    """
    Example usage patterns for different Python application frameworks.
    """

    # Django example (add to apps.py or management command)
    """
    from django.apps import AppConfig
    from .postgis_initializer import initialize_postgis_from_secrets_manager

    class AstronomicalDataConfig(AppConfig):
        default_auto_field = 'django.db.models.BigAutoField'
        name = 'astronomical_data'

        def ready(self):
            # Initialize PostGIS during Django startup
            success = initialize_postgis_from_secrets_manager(
                secret_name='astro-data-pipeline-staging-rds-credentials'
            )
            if not success:
                raise RuntimeError("PostGIS initialization failed")
    """

    # Flask example (add to application factory)
    """
    from flask import Flask
    from .postgis_initializer import initialize_postgis_from_secrets_manager

    def create_app():
        app = Flask(__name__)

        # Initialize PostGIS during Flask startup
        with app.app_context():
            success = initialize_postgis_from_secrets_manager(
                secret_name='astro-data-pipeline-staging-rds-credentials'
            )
            if not success:
                raise RuntimeError("PostGIS initialization failed")

        return app
    """

    # Standalone script example
    logging.basicConfig(level=logging.INFO)
    success = initialize_postgis_from_secrets_manager(
        secret_name='astro-data-pipeline-staging-rds-credentials',
        region='us-east-1'
    )

    if success:
        print("PostGIS initialization completed successfully")
    else:
        print("PostGIS initialization failed")
        exit(1)