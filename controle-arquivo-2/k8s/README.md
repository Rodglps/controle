# Kubernetes Deployment Guide - Controle de Arquivos

This directory contains Kubernetes manifests for deploying the distributed file control system.

## Architecture Overview

The system consists of two main components:

- **Orchestrator Pod**: Scheduled job that collects files from SFTP servers and publishes to RabbitMQ
- **Processor Pod**: Consumer that processes messages, identifies clients/layouts, and uploads to destinations

Both pods communicate via RabbitMQ and share an Oracle database for state management.

## Prerequisites

- Kubernetes cluster (v1.24+)
- kubectl configured to access your cluster
- Oracle Database (RDS or on-premises)
- RabbitMQ (AWS MQ or self-hosted)
- HashiCorp Vault for credential management
- AWS S3 bucket (for file storage)
- SFTP servers configured with known_hosts file

## Directory Structure

```
k8s/
├── orchestrator-deployment.yaml    # Orchestrator deployment with probes
├── processor-deployment.yaml       # Processor deployment with HPA
├── orchestrator-service.yaml       # ClusterIP service for orchestrator
├── processor-service.yaml          # ClusterIP service for processor
├── configmap-dev.yaml             # Development environment config
├── configmap-staging.yaml         # Staging environment config
├── configmap-prod.yaml            # Production environment config
├── secrets-template.yaml          # Template for sensitive data
└── README.md                      # This file
```

## Deployment Steps

### 1. Create Namespace (Optional)

```bash
kubectl create namespace controle-arquivos
kubectl config set-context --current --namespace=controle-arquivos
```

### 2. Create Secrets

**Option A: Using kubectl create secret**

```bash
kubectl create secret generic controle-arquivos-secrets \
  --from-literal=db-url='jdbc:oracle:thin:@oracle.example.com:1521:ORCL' \
  --from-literal=db-username='app_user' \
  --from-literal=db-password='YourSecurePassword' \
  --from-literal=rabbitmq-host='rabbitmq.example.com' \
  --from-literal=rabbitmq-username='app_user' \
  --from-literal=rabbitmq-password='YourSecurePassword' \
  --from-literal=vault-uri='https://vault.example.com' \
  --from-literal=vault-token='hvs.XXXXXXXXXXXXXX' \
  --from-literal=aws-access-key-id='AKIAIOSFODNN7EXAMPLE' \
  --from-literal=aws-secret-access-key='wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY'
```

**Option B: Using secrets-template.yaml**

1. Copy the template:
   ```bash
   cp secrets-template.yaml secrets.yaml
   ```

2. Edit `secrets.yaml` and replace all `<PLACEHOLDER>` values with actual credentials

3. Apply the secret:
   ```bash
   kubectl apply -f secrets.yaml
   ```

4. **IMPORTANT**: Do not commit `secrets.yaml` to version control!

**Option C: Using External Secrets Operator (Recommended for Production)**

If using AWS Secrets Manager or HashiCorp Vault:

```yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: controle-arquivos-secrets
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: aws-secrets-manager
    kind: SecretStore
  target:
    name: controle-arquivos-secrets
  data:
  - secretKey: db-password
    remoteRef:
      key: controle-arquivos/db-password
  # ... other secrets
```

### 3. Apply ConfigMap

Choose the appropriate environment:

**Development:**
```bash
kubectl apply -f configmap-dev.yaml
```

**Staging:**
```bash
kubectl apply -f configmap-staging.yaml
```

**Production:**
```bash
kubectl apply -f configmap-prod.yaml
```

### 4. Deploy Services

```bash
kubectl apply -f orchestrator-service.yaml
kubectl apply -f processor-service.yaml
```

### 5. Deploy Applications

```bash
kubectl apply -f orchestrator-deployment.yaml
kubectl apply -f processor-deployment.yaml
```

### 6. Verify Deployment

Check pod status:
```bash
kubectl get pods -l app=controle-arquivos
```

Check services:
```bash
kubectl get svc -l app=controle-arquivos
```

Check HPA status:
```bash
kubectl get hpa controle-arquivos-processor-hpa
```

View logs:
```bash
# Orchestrator logs
kubectl logs -l component=orchestrator -f

# Processor logs
kubectl logs -l component=processor -f
```

### 7. Health Checks

Test health endpoints:

```bash
# Orchestrator health
kubectl port-forward svc/controle-arquivos-orchestrator 8080:8080
curl http://localhost:8080/actuator/health

# Processor health
kubectl port-forward svc/controle-arquivos-processor 8081:8081
curl http://localhost:8081/actuator/health
```

## Configuration Details

### Resource Limits

**Orchestrator:**
- Requests: 512Mi memory, 250m CPU
- Limits: 1Gi memory, 1000m CPU
- Replicas: 1 (single instance due to scheduler)

**Processor:**
- Requests: 1Gi memory, 500m CPU
- Limits: 2Gi memory, 2000m CPU
- Replicas: 2-10 (auto-scaled via HPA)

### Health Probes

Both pods expose Spring Boot Actuator health endpoints:

**Liveness Probe:**
- Path: `/actuator/health/liveness`
- Initial Delay: 60s
- Period: 10s
- Timeout: 5s
- Failure Threshold: 3

**Readiness Probe:**
- Path: `/actuator/health/readiness`
- Initial Delay: 30s
- Period: 5s
- Timeout: 3s
- Failure Threshold: 3

### Horizontal Pod Autoscaler (HPA)

The Processor pod uses HPA with the following configuration:

- **Min Replicas**: 2
- **Max Replicas**: 10
- **CPU Target**: 70% utilization
- **Memory Target**: 80% utilization
- **Scale Up**: Fast (100% increase or 2 pods per 30s)
- **Scale Down**: Conservative (50% decrease per 60s, 5min stabilization)

## Environment-Specific Configurations

### Development (dev)

- Scheduler: Every 10 minutes
- RabbitMQ Prefetch: 5
- Concurrency: 2-10
- Chunk Size: 5MB
- Log Level: DEBUG

### Staging (staging)

- Scheduler: Every 7 minutes
- RabbitMQ Prefetch: 10
- Concurrency: 5-20
- Chunk Size: 10MB
- Log Level: INFO

### Production (prod)

- Scheduler: Every 5 minutes
- RabbitMQ Prefetch: 20
- Concurrency: 10-50
- Chunk Size: 10MB
- Log Level: INFO (app), WARN (root)

## Monitoring and Observability

### Metrics

Both pods expose Prometheus metrics at `/actuator/prometheus`:

```bash
kubectl port-forward svc/controle-arquivos-processor 8081:8081
curl http://localhost:8081/actuator/prometheus
```

### Logs

Logs are structured in JSON format with the following fields:
- `timestamp`: ISO 8601 timestamp
- `level`: Log level (INFO, WARN, ERROR)
- `logger`: Logger name
- `message`: Log message
- `context`: Additional context (file name, correlation ID, etc.)

View logs with kubectl:
```bash
# All logs
kubectl logs -l app=controle-arquivos --all-containers=true

# Follow logs
kubectl logs -l component=processor -f

# Filter by time
kubectl logs -l component=orchestrator --since=1h
```

### Troubleshooting

**Pod not starting:**
```bash
kubectl describe pod <pod-name>
kubectl logs <pod-name>
```

**Database connection issues:**
```bash
# Check secret values (base64 encoded)
kubectl get secret controle-arquivos-secrets -o yaml

# Test database connectivity from pod
kubectl exec -it <pod-name> -- bash
# Inside pod: test connection with SQL client
```

**RabbitMQ connection issues:**
```bash
# Check RabbitMQ configuration
kubectl get configmap controle-arquivos-config -o yaml

# Check RabbitMQ credentials
kubectl get secret controle-arquivos-secrets -o jsonpath='{.data.rabbitmq-password}' | base64 -d
```

**Health check failures:**
```bash
# Check health endpoint directly
kubectl port-forward <pod-name> 8080:8080
curl http://localhost:8080/actuator/health

# Check readiness/liveness specifically
curl http://localhost:8080/actuator/health/readiness
curl http://localhost:8080/actuator/health/liveness
```

## Scaling

### Manual Scaling

Scale the Processor deployment manually:
```bash
kubectl scale deployment controle-arquivos-processor --replicas=5
```

### HPA Scaling

The HPA will automatically scale based on CPU and memory:
```bash
# View HPA status
kubectl get hpa controle-arquivos-processor-hpa

# Describe HPA for details
kubectl describe hpa controle-arquivos-processor-hpa
```

### Orchestrator Scaling

**Note**: The Orchestrator should run as a single instance to avoid duplicate processing. The `job_concurrency_control` table ensures only one instance processes at a time.

## Updates and Rollouts

### Rolling Update

Update the image version:
```bash
kubectl set image deployment/controle-arquivos-processor \
  processor=controle-arquivos-processor:v2.0.0
```

### Rollout Status

Check rollout status:
```bash
kubectl rollout status deployment/controle-arquivos-processor
```

### Rollback

Rollback to previous version:
```bash
kubectl rollout undo deployment/controle-arquivos-processor
```

View rollout history:
```bash
kubectl rollout history deployment/controle-arquivos-processor
```

## Security Best Practices

1. **Secrets Management**:
   - Use External Secrets Operator or Sealed Secrets
   - Rotate credentials regularly
   - Never commit secrets to version control

2. **Network Policies**:
   - Implement network policies to restrict pod-to-pod communication
   - Allow only necessary ingress/egress traffic

3. **RBAC**:
   - Create service accounts with minimal permissions
   - Use RBAC to control access to resources

4. **Image Security**:
   - Use private container registry
   - Scan images for vulnerabilities
   - Use specific image tags (not `latest`)

5. **Pod Security**:
   - Run containers as non-root user
   - Use read-only root filesystem where possible
   - Drop unnecessary capabilities

## Backup and Disaster Recovery

### Database Backup

Ensure Oracle database has regular backups configured:
- Automated snapshots (RDS)
- Point-in-time recovery enabled
- Cross-region replication for DR

### Configuration Backup

Backup all Kubernetes manifests:
```bash
kubectl get all,configmap,secret -n controle-arquivos -o yaml > backup.yaml
```

### Recovery Procedure

1. Restore database from backup
2. Apply Kubernetes manifests
3. Verify health checks
4. Monitor logs for errors

## Performance Tuning

### Database Connection Pool

Adjust Hikari pool settings in ConfigMap:
- `db-hikari-max-pool-size`: Maximum connections
- `db-hikari-min-idle`: Minimum idle connections
- `db-hikari-connection-timeout`: Connection timeout

### RabbitMQ Consumer

Adjust consumer settings in ConfigMap:
- `rabbitmq-listener-prefetch`: Messages prefetched per consumer
- `rabbitmq-listener-concurrency`: Initial concurrent consumers
- `rabbitmq-listener-max-concurrency`: Maximum concurrent consumers

### Streaming

Adjust streaming settings in ConfigMap:
- `streaming-chunk-size`: Size of chunks for file transfer
- `streaming-buffer-size`: Buffer size for I/O operations

## Support and Maintenance

### Regular Maintenance Tasks

1. **Weekly**:
   - Review logs for errors
   - Check HPA metrics
   - Verify health check status

2. **Monthly**:
   - Review resource utilization
   - Update dependencies
   - Rotate credentials

3. **Quarterly**:
   - Performance testing
   - Disaster recovery drill
   - Security audit

### Contact

For issues or questions, contact the development team or create an issue in the project repository.

## Additional Resources

- [Spring Boot Actuator Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Horizontal Pod Autoscaler](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/)
- [External Secrets Operator](https://external-secrets.io/)
