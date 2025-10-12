FROM apache/airflow:2.7.1

# Switch to root to install system dependencies if needed
USER root

# Install any system-level dependencies
# (Add here if you need additional system packages)

# Switch back to airflow user
USER airflow

# Copy requirements file
COPY requirements.txt /tmp/requirements.txt

# Install Airflow providers and Python dependencies
RUN pip install --no-cache-dir -r /tmp/requirements.txt

# Copy DAGs and plugins into image (can be overridden by volume mounts)
COPY dags/ /opt/airflow/dags/
COPY plugins/ /opt/airflow/plugins/

# Set proper ownership
USER root
RUN chown -R airflow:root /opt/airflow/dags /opt/airflow/plugins
USER airflow