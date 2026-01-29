# Multi-Cloud AI Cost Optimization Advisor

**Version:** 1.0.0
**Team:** Azure Platform / FinOps Tooling
**Status:** Production-Ready Architecture

---

## Executive Summary

The Multi-Cloud AI Cost Optimization Advisor is an enterprise-grade platform that delivers real-time, ML-powered cost optimization recommendations directly within cloud console interfaces (Azure Portal, AWS Console, GCP Console) via a browser extension.

**Core Value Proposition:** "GitHub Copilot for cloud cost optimization."

This system augments engineer decision-making in real time, providing actionable insights at the moment of resource configuration—not hours later in a dashboard.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Why Browser Extension?](#why-browser-extension)
3. [Why Normalization is Critical](#why-normalization-is-critical)
4. [System Components](#system-components)
5. [Data Flow](#data-flow)
6. [ML Model Design](#ml-model-design)
7. [Security Model](#security-model)
8. [Scaling Strategy](#scaling-strategy)
9. [Enterprise Deployment](#enterprise-deployment)
10. [API Reference](#api-reference)
11. [Development Guide](#development-guide)
12. [Operational Runbook](#operational-runbook)

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           BROWSER EXTENSION                                  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌──────────────────┐  │
│  │   Azure     │  │    AWS      │  │    GCP      │  │    Background    │  │
│  │  Detector   │  │  Detector   │  │  Detector   │  │ Service Worker   │  │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └────────┬─────────┘  │
│         │                │                │                   │            │
│         └────────────────┴────────────────┴───────────────────┘            │
│                                    │                                        │
└────────────────────────────────────┼────────────────────────────────────────┘
                                     │ HTTPS + JWT
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         AZURE APP SERVICE                                    │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                     SPRING BOOT APPLICATION                          │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                 │   │
│  │  │  Extension  │  │    Rate     │  │    JWT      │                 │   │
│  │  │    API      │  │   Limiter   │  │   Filter    │                 │   │
│  │  └──────┬──────┘  └─────────────┘  └─────────────┘                 │   │
│  │         │                                                           │   │
│  │  ┌──────┴──────────────────────────────────────────────────────┐   │   │
│  │  │                  RECOMMENDATION ENGINE                       │   │   │
│  │  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │   │   │
│  │  │  │ Forecasting │  │  Clustering │  │   Decision  │         │   │   │
│  │  │  │   Service   │  │   Service   │  │    Logic    │         │   │   │
│  │  │  └──────┬──────┘  └──────┬──────┘  └─────────────┘         │   │   │
│  │  └─────────┼────────────────┼──────────────────────────────────┘   │   │
│  │            │                │                                       │   │
│  │  ┌─────────┴────────────────┴──────────────────────────────────┐   │   │
│  │  │               NORMALIZATION LAYER                            │   │   │
│  │  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │   │   │
│  │  │  │   Region    │  │  Resource   │  │   Cost      │         │   │   │
│  │  │  │ Normalizer  │  │   Mapper    │  │  Converter  │         │   │   │
│  │  │  └─────────────┘  └─────────────┘  └─────────────┘         │   │   │
│  │  └──────────────────────────────────────────────────────────────┘   │   │
│  │                                                                     │   │
│  │  ┌──────────────────────────────────────────────────────────────┐   │   │
│  │  │                    ADAPTER LAYER                              │   │   │
│  │  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐          │   │   │
│  │  │  │    Azure    │  │     AWS     │  │     GCP     │          │   │   │
│  │  │  │   Adapter   │  │   Adapter   │  │   Adapter   │          │   │   │
│  │  │  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘          │   │   │
│  │  └─────────┼────────────────┼────────────────┼──────────────────┘   │   │
│  └────────────┼────────────────┼────────────────┼──────────────────────┘   │
└───────────────┼────────────────┼────────────────┼──────────────────────────┘
                │                │                │
                ▼                ▼                ▼
    ┌───────────────┐  ┌───────────────┐  ┌───────────────┐
    │ Azure Cost    │  │  AWS CUR      │  │ GCP BigQuery  │
    │ Management    │  │  + CloudWatch │  │ Billing Export│
    └───────────────┘  └───────────────┘  └───────────────┘
```

---

## Why Browser Extension?

### The Problem with Traditional Dashboards

Traditional FinOps dashboards suffer from a fundamental limitation: **temporal disconnect**. Engineers make provisioning decisions in the cloud console, but cost feedback arrives hours or days later in a separate dashboard.

By the time cost anomalies surface:
- Resources are already provisioned
- Workloads are deployed
- Changing configuration requires change management

### The Browser Extension Advantage

| Aspect | Dashboard Approach | Extension Approach |
|--------|-------------------|-------------------|
| **Timing** | After-the-fact | Real-time |
| **Context** | Aggregated views | Resource-specific |
| **Action** | Navigate away to investigate | Inline recommendations |
| **Adoption** | Requires new workflow | Enhances existing workflow |
| **Learning** | Passive (read reports) | Active (see impact immediately) |

### Design Principles

1. **Non-intrusive**: Overlay UI does not block console functionality
2. **Contextual**: Recommendations specific to the viewed resource
3. **Actionable**: Provides concrete next steps, not just warnings
4. **Trust-building**: Shows confidence scores and reasoning

---

## Why Normalization is Critical

### The Multi-Cloud Data Challenge

Each cloud provider exposes billing and resource data differently:

| Aspect | Azure | AWS | GCP |
|--------|-------|-----|-----|
| **Resource ID** | ARM resource ID | ARN | Self-link |
| **Region naming** | "East US" | "us-east-1" | "us-east1" |
| **Cost granularity** | Per meter | Per line item | Per SKU |
| **Utilization source** | Azure Monitor | CloudWatch | Cloud Monitoring |
| **API authentication** | Azure AD | IAM/STS | Service Account |

### The Normalization Contract

Our `CostRecord` entity is the **canonical representation** that all ML models consume:

```java
class CostRecord {
    CloudProvider provider;      // Enum, not string
    ResourceType resourceType;   // Unified taxonomy
    String resourceId;           // Provider-native (for drill-down)
    int vcpu;                    // Standardized units
    double memoryGb;             // Standardized units
    double avgCpuUtilization;    // 0.0-1.0 range
    String region;               // Canonical format
    BigDecimal dailyCost;        // USD only
    LocalDate date;              // UTC
}
```

### Normalization Guarantees

1. **Currency**: All costs in USD (converted at ingestion)
2. **Time**: UTC timezone, daily granularity
3. **Metrics**: Utilization as 0.0-1.0 ratio
4. **Regions**: Canonical format (e.g., "us-east-1")
5. **Types**: Unified resource taxonomy

This contract ensures ML models are **never exposed to provider-specific schemas**.

---

## System Components

### Backend (Java 17 + Spring Boot 3)

```
backend/
├── src/main/java/com/microsoft/cloudoptimizer/
│   ├── adapters/           # Cloud provider integrations
│   │   ├── azure/          # Azure Cost Management, ARM, Monitor
│   │   ├── aws/            # AWS CUR, CloudWatch, Pricing API
│   │   └── gcp/            # BigQuery exports, Cloud Monitoring
│   ├── normalization/      # Data transformation layer
│   ├── ml/                 # ML service clients
│   │   ├── ForecastingService.java
│   │   └── UtilizationClusteringService.java
│   ├── recommendation/     # Decision engine
│   ├── extension/          # Browser extension API
│   ├── security/           # JWT, rate limiting, RBAC
│   ├── domain/             # Core entities
│   │   ├── model/          # CostRecord, Recommendation, etc.
│   │   └── repository/     # JPA repositories
│   └── config/             # Spring configuration
└── src/test/               # Unit and integration tests
```

### Browser Extension (Manifest V3)

```
extension/
├── manifest.json           # Extension manifest
├── src/
│   ├── background/         # Service worker
│   │   └── service-worker.js
│   ├── content/            # Content scripts per provider
│   │   ├── azure-detector.js
│   │   ├── aws-detector.js
│   │   └── gcp-detector.js
│   ├── popup/              # Extension popup UI
│   └── ui/                 # Overlay components
├── styles/
│   └── overlay.css
└── assets/                 # Icons and images
```

---

## Data Flow

### Real-Time Analysis (Extension Request)

```
1. User navigates to resource page
2. Content script detects resource
3. Background worker sends API request
4. Backend checks cache (5-min TTL)
5. If cache miss:
   a. Fetch historical cost data
   b. Run forecasting model
   c. Classify utilization
   d. Generate recommendations
   e. Cache result
6. Return analysis to extension
7. Extension renders overlay
```

### Batch Processing (Data Ingestion)

```
1. Azure Data Factory triggers daily
2. Fetch cost exports from each provider
3. Transform to normalized schema
4. Load to Azure SQL Database
5. Trigger ML batch inference
6. Store recommendations
7. Invalidate extension cache
```

---

## ML Model Design

### Forecasting Model

**Architecture**: Prophet-style decomposition with ARIMA fallback

**Input Features**:
- Daily cost time series (90 days minimum)
- Day-of-week seasonality
- Resource type coefficient
- Region pricing factor

**Output**:
- 30/60/90-day cost forecast
- Confidence interval
- Trend direction

**Deployment**: Azure ML managed endpoint

**Fallback**: Statistical exponential smoothing when:
- < 14 days of data
- Endpoint unavailable
- Anomalous patterns

### Utilization Clustering

**Architecture**: K-Means with pre-computed cluster centers

**Clusters**:
| Cluster | Avg CPU | Avg Memory | Action |
|---------|---------|------------|--------|
| IDLE | < 5% | < 10% | Delete/Stop |
| UNDERUTILIZED | 5-20% | 10-30% | Downsize |
| OPTIMIZED | 20-80% | 30-80% | No action |
| OVERUTILIZED | > 80% | > 80% | Upsize |

**Confidence Scoring**:
```
confidence = (data_quality × 0.4) + (cluster_separation × 0.4) + (trend_stability × 0.2)
```

### Model Retraining

- **Frequency**: Weekly
- **Trigger**: MAPE > 15% on validation set
- **Dataset**: Rolling 12-month window
- **Validation**: 70/15/15 train/val/test split

---

## Security Model

### Authentication Flow

```
┌──────────┐    ┌───────────┐    ┌──────────┐    ┌──────────┐
│ Extension │───▶│ Azure AD  │───▶│ Token    │───▶│  Backend │
│           │    │ (OAuth)   │    │ (JWT)    │    │   API    │
└──────────┘    └───────────┘    └──────────┘    └──────────┘
```

### Security Layers

1. **CORS**: Only allow cloud console origins
2. **JWT Validation**: Azure AD-issued tokens
3. **Rate Limiting**: 100 req/min per tenant
4. **RBAC**: Role-based endpoint access
5. **Data Isolation**: Tenant-scoped queries

### Secret Management

| Secret | Storage | Access |
|--------|---------|--------|
| JWT signing key | Azure Key Vault | Managed Identity |
| Database credentials | Key Vault | Managed Identity |
| Cloud provider credentials | Key Vault | Service Principal |
| ML endpoint keys | Key Vault | Managed Identity |

### Extension Security

- **No secrets stored**: All API calls via backend
- **Minimal permissions**: Only required host permissions
- **Content script isolation**: No access to page JavaScript
- **Manifest V3**: Modern security model

---

## Scaling Strategy

### Traffic Patterns

- **Peak**: 10 AM - 4 PM (business hours)
- **Low**: Nights and weekends
- **Spikes**: Month-end cost reviews

### Horizontal Scaling

| Component | Scaling Trigger | Max Instances |
|-----------|-----------------|---------------|
| App Service | CPU > 70% | 10 |
| Azure Functions | Queue depth > 1000 | 50 |
| Azure SQL | DTU > 80% | Elastic pool |

### Caching Strategy

| Layer | TTL | Eviction |
|-------|-----|----------|
| Extension local | 5 min | LRU |
| Backend in-memory | 5 min | Size-based |
| Azure Redis | 15 min | TTL + manual |

### Cost Optimization (Self-Referential)

- Auto-scale down during off-hours
- Reserved instances for baseline capacity
- Spot instances for batch processing

---

## Enterprise Deployment

### Multi-Tenant Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     CONTROL PLANE                            │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │   Tenant    │  │   Config    │  │   Billing   │         │
│  │  Registry   │  │   Store     │  │   Metering  │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
└─────────────────────────────────────────────────────────────┘
                             │
        ┌────────────────────┼────────────────────┐
        ▼                    ▼                    ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│  Tenant A    │    │  Tenant B    │    │  Tenant C    │
│  (Shared)    │    │  (Shared)    │    │  (Dedicated) │
└──────────────┘    └──────────────┘    └──────────────┘
```

### Deployment Options

| Tier | Description | SLA |
|------|-------------|-----|
| **Starter** | Shared infrastructure | 99.5% |
| **Professional** | Dedicated compute | 99.9% |
| **Enterprise** | Isolated environment | 99.99% |

### Integration Points

- **SSO**: Azure AD, Okta, Auth0
- **Ticketing**: ServiceNow, Jira
- **Alerting**: PagerDuty, OpsGenie
- **BI**: Power BI, Tableau

---

## API Reference

### Extension API

#### Analyze Resource

```http
POST /api/extension/analyze
Authorization: Bearer <JWT>
Content-Type: application/json

{
  "provider": "AZURE",
  "resourceId": "/subscriptions/.../virtualMachines/myvm",
  "resourceType": "COMPUTE",
  "region": "eastus",
  "detectedConfig": {
    "vcpu": 4,
    "memoryGb": 16
  }
}
```

**Response:**

```json
{
  "resourceId": "/subscriptions/.../virtualMachines/myvm",
  "resourceName": "myvm",
  "monthlyCostForecast": 143.22,
  "confidence": 0.91,
  "utilizationStatus": "UNDERUTILIZED",
  "severityLevel": "warning",
  "avgCpuUtilization": 0.15,
  "recommendations": [
    {
      "action": "DOWNSIZE_INSTANCE",
      "actionDisplayName": "Downsize instance",
      "summary": "Resource underutilized at 15% CPU",
      "suggestedConfig": "2 vCPU / 8 GB",
      "estimatedSavings": 62.40,
      "confidence": 0.91,
      "riskLevel": "MEDIUM"
    }
  ]
}
```

#### Batch Analysis

```http
POST /api/extension/analyze/batch
```

#### Dismiss Recommendation

```http
POST /api/extension/recommendations/{id}/dismiss
```

---

## Development Guide

### Prerequisites

- **Java 17+** (Java 24 works fine)
- **Maven 3.8+**
- **Chrome or Edge browser** (for extension testing)
- **No cloud credentials required** for local development!

---

## 🚀 Local Development Guide

This section provides **complete step-by-step instructions** to run and test the entire system locally on your machine without any cloud dependencies.

### Quick Start (5 Minutes)

```bash
# 1. Start the backend server
cd backend
mvn spring-boot:run

# Backend will start on http://localhost:8080
# ✅ No database setup needed (uses H2 in-memory)
# ✅ No cloud credentials needed
# ✅ Test data auto-generated
```

That's it! The backend is running and ready.

---

### Loading the Browser Extension

The browser extension connects to your local backend and can be tested immediately:

#### Step 1: Load Extension in Chrome/Edge

1. **Open Extension Management Page**
   - Chrome: Navigate to `chrome://extensions/`
   - Edge: Navigate to `edge://extensions/`

2. **Enable Developer Mode**
   - Toggle the "Developer mode" switch in the top-right corner

3. **Load Unpacked Extension**
   - Click the "Load unpacked" button
   - Navigate to your project folder
   - Select the `extension/` folder
   - Click "Select Folder"

4. **Verify Installation**
   - You should see "Cloud Cost Optimizer" in your extensions list
   - The extension icon should appear in your browser toolbar
   - Note the Extension ID (e.g., `abcdefghijklmnopqrstuvwxyz`)

#### Step 2: Verify Extension is Connected

1. Click the extension icon in your toolbar
2. Open browser DevTools (F12) → Console tab
3. You should see: `Cloud Cost Optimizer - Connected to http://localhost:8080`

---

### Testing the Extension Locally

Since the extension normally works on cloud console pages (Azure Portal, AWS Console, etc.), we need a way to test it locally. Here are **three methods**:

#### Method 1: Test Page (Recommended for Development)

Create a simple test page to simulate a cloud console:

```bash
# Create a test HTML file
cat > extension/test-page.html << 'EOF'
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Cloud Optimizer - Local Test Page</title>
    <style>
        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            max-width: 1200px;
            margin: 40px auto;
            padding: 20px;
            background: #f5f5f5;
        }
        .resource-card {
            background: white;
            border: 1px solid #ddd;
            border-radius: 8px;
            padding: 20px;
            margin: 20px 0;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }
        .resource-header {
            display: flex;
            justify-content: space-between;
            margin-bottom: 15px;
        }
        h1 { color: #0078d4; }
        h2 { color: #333; margin-bottom: 10px; }
        .badge {
            display: inline-block;
            padding: 4px 12px;
            border-radius: 12px;
            font-size: 12px;
            font-weight: bold;
        }
        .badge.running { background: #d1f0d1; color: #0c5c0c; }
        .badge.stopped { background: #ffd1d1; color: #8c0000; }
        code {
            background: #f0f0f0;
            padding: 2px 6px;
            border-radius: 3px;
            font-family: 'Courier New', monospace;
        }
    </style>
</head>
<body>
    <h1>🧪 Cloud Cost Optimizer - Local Test Environment</h1>
    <p>This page simulates a cloud console for testing the extension locally.</p>

    <!-- Test Resource 1: Underutilized VM -->
    <div class="resource-card" data-resource-type="compute" data-resource-id="test-vm-underutilized-01" data-provider="azure">
        <div class="resource-header">
            <div>
                <h2>Virtual Machine: test-vm-underutilized-01</h2>
                <p><code>Standard_D4s_v3</code> • 4 vCPU • 16 GB RAM • East US</p>
            </div>
            <span class="badge running">Running</span>
        </div>
        <p><strong>Resource ID:</strong> <code>/subscriptions/12345/resourceGroups/test-rg/providers/Microsoft.Compute/virtualMachines/test-vm-underutilized-01</code></p>
        <p><strong>Monthly Cost:</strong> ~$140/month</p>
        <p><strong>Simulated Issue:</strong> Low CPU utilization (avg 12%)</p>
    </div>

    <!-- Test Resource 2: Idle Database -->
    <div class="resource-card" data-resource-type="database" data-resource-id="test-db-idle-01" data-provider="azure">
        <div class="resource-header">
            <div>
                <h2>SQL Database: test-db-idle-01</h2>
                <p><code>Standard S3</code> • 100 DTU • 250 GB • West US 2</p>
            </div>
            <span class="badge running">Running</span>
        </div>
        <p><strong>Resource ID:</strong> <code>/subscriptions/12345/resourceGroups/test-rg/providers/Microsoft.Sql/servers/testserver/databases/test-db-idle-01</code></p>
        <p><strong>Monthly Cost:</strong> ~$300/month</p>
        <p><strong>Simulated Issue:</strong> Idle for 7+ days (no connections)</p>
    </div>

    <!-- Test Resource 3: Optimized Instance -->
    <div class="resource-card" data-resource-type="compute" data-resource-id="test-vm-optimized-01" data-provider="aws">
        <div class="resource-header">
            <div>
                <h2>EC2 Instance: test-vm-optimized-01</h2>
                <p><code>t3.large</code> • 2 vCPU • 8 GB RAM • us-east-1</p>
            </div>
            <span class="badge running">Running</span>
        </div>
        <p><strong>Instance ID:</strong> <code>i-0123456789abcdef0</code></p>
        <p><strong>Monthly Cost:</strong> ~$75/month</p>
        <p><strong>Status:</strong> Well-utilized (avg 65% CPU)</p>
    </div>

    <!-- Test Resource 4: Stopped Instance -->
    <div class="resource-card" data-resource-type="compute" data-resource-id="test-vm-stopped-01" data-provider="gcp">
        <div class="resource-header">
            <div>
                <h2>Compute Engine: test-vm-stopped-01</h2>
                <p><code>n1-standard-4</code> • 4 vCPU • 15 GB RAM • us-central1</p>
            </div>
            <span class="badge stopped">Stopped</span>
        </div>
        <p><strong>Instance:</strong> <code>projects/my-project/zones/us-central1-a/instances/test-vm-stopped-01</code></p>
        <p><strong>Monthly Cost:</strong> ~$5/month (storage only)</p>
        <p><strong>Simulated Issue:</strong> Stopped for 30+ days</p>
    </div>

    <hr style="margin: 40px 0;">
    <h3>📋 Instructions</h3>
    <ol>
        <li>Make sure the backend is running: <code>cd backend && mvn spring-boot:run</code></li>
        <li>Open Browser DevTools (F12) to see extension logs</li>
        <li>The extension should automatically detect the resources above</li>
        <li>Look for cost optimization overlays on each resource card</li>
        <li>Click the extension icon to see the summary popup</li>
    </ol>

    <h3>🐛 Debugging</h3>
    <ul>
        <li><strong>Backend Health:</strong> <a href="http://localhost:8080/actuator/health" target="_blank">http://localhost:8080/actuator/health</a></li>
        <li><strong>API Docs:</strong> <a href="http://localhost:8080/swagger-ui.html" target="_blank">http://localhost:8080/swagger-ui.html</a></li>
        <li><strong>Extension Console:</strong> DevTools → Console tab (filter by "Cloud Optimizer")</li>
        <li><strong>Network Requests:</strong> DevTools → Network tab (filter by "localhost:8080")</li>
    </ul>
</body>
</html>
EOF

# Open the test page
open extension/test-page.html  # macOS
# or
start extension/test-page.html  # Windows
# or  
xdg-open extension/test-page.html  # Linux
```

The extension will detect the simulated resources and show cost optimization recommendations!

#### Method 2: Test Against Real Cloud Consoles

If you have access to Azure/AWS/GCP consoles:

1. Navigate to any resource page (VM, database, storage account, etc.)
2. The extension will automatically detect it
3. Mock recommendations will appear based on resource type

**Note:** The backend uses mock data by default, so you don't need actual cloud credentials.

#### Method 3: Direct API Testing

Test the backend API directly with curl:

```bash
# Health check
curl http://localhost:8080/actuator/health

# Analyze a resource
curl -X POST http://localhost:8080/api/extension/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "provider": "AZURE",
    "resourceId": "test-vm-01",
    "resourceType": "COMPUTE",
    "region": "eastus",
    "detectedConfig": {
      "vcpu": 4,
      "memoryGb": 16
    }
  }'

# Expected response: JSON with cost forecast and recommendations
```

---

### Backend Local Development Features

The backend is configured for local development with these features:

#### 1. **H2 In-Memory Database**
- No installation required
- Data resets on restart (perfect for testing)
- Database console: http://localhost:8080/h2-console
  - JDBC URL: `jdbc:h2:mem:testdb`
  - Username: `sa`
  - Password: (leave empty)

#### 2. **Automatic Schema Creation**
- Tables created automatically from JPA entities
- No Flyway migrations needed locally
- Change `ddl-auto: update` in application.yml for schema updates

#### 3. **Mock Cloud Adapters**
- All cloud provider calls are mocked by default
- Returns realistic test data
- No Azure/AWS/GCP credentials needed

#### 4. **Disabled Authentication**
- JWT validation disabled in local profile
- No need to sign in during development
- Re-enabled automatically in production

#### 5. **CORS Enabled**
- Extension can call from `chrome-extension://` origins
- localhost:* allowed for test pages

#### 6. **Hot Reload with DevTools**
```bash
# Add to pom.xml if not present
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <optional>true</optional>
</dependency>

# Changes auto-reload without restart
```

---

### Useful Endpoints for Local Testing

Once the backend is running:

| Endpoint | Purpose |
|----------|---------|
| http://localhost:8080/actuator/health | Health check |
| http://localhost:8080/actuator/metrics | Application metrics |
| http://localhost:8080/swagger-ui.html | Interactive API documentation |
| http://localhost:8080/h2-console | Database console |
| http://localhost:8080/actuator/prometheus | Metrics in Prometheus format |

---

### Troubleshooting Local Development

#### Backend Won't Start

**Issue:** `Port 8080 is already in use`
```bash
# Find process using port 8080
lsof -i :8080  # macOS/Linux
netstat -ano | findstr :8080  # Windows

# Kill the process or change port
export SERVER_PORT=8081
mvn spring-boot:run
```

**Issue:** `java.lang.ClassNotFoundException: com.github.benmanes.caffeine.cache.Caffeine`
```bash
# Clean and rebuild
mvn clean install -DskipTests
```

**Issue:** `Cannot create bean 'cacheManager'`
- Check that CacheConfig.java exists in `src/main/java/com/microsoft/cloudoptimizer/config/`
- Verify Caffeine dependency in pom.xml

#### Extension Issues

**Issue:** Extension doesn't detect resources
- Check browser console (F12) for errors
- Verify backend is running: `curl http://localhost:8080/actuator/health`
- Make sure you're on the test page or a supported cloud console

**Issue:** API calls failing with CORS errors
```javascript
// Check extension console for:
// "Access to fetch at 'http://localhost:8080' has been blocked by CORS"

// Fix: Ensure backend has CORS configuration for chrome-extension://
// This should already be configured in LocalDevConfig.java
```

**Issue:** Extension doesn't load
- Reload extension: chrome://extensions → Click reload button
- Check for manifest errors in extension management page
- Verify all files are present in extension/ folder

#### Getting Help

Check the logs:
```bash
# Backend logs
# Look for startup errors in terminal where mvn spring-boot:run is running

# Extension logs
# Open DevTools (F12) → Console tab
# Filter by "Cloud Optimizer" or check the service worker console

# Network requests
# DevTools → Network tab → Filter by "localhost:8080"
```

---

### Development Workflow

A typical development session:

```bash
# 1. Start backend
cd backend
mvn spring-boot:run
# Leave this running...

# 2. Make changes to Java code
# Files auto-reload with Spring DevTools

# 3. Test via curl
curl http://localhost:8080/api/extension/analyze/...

# 4. Make changes to extension code
# Click reload button in chrome://extensions

# 5. Test on test page
open extension/test-page.html

# 6. Iterate!
```

---

### Next Steps: Deploying to Production

Once you've tested locally and are ready to deploy:

1. **Update Extension Configuration**
   ```javascript
   // extension/src/background/service-worker.js
   const CONFIG = {
     apiBaseUrl: 'https://your-api.azurewebsites.net', // Change from localhost
     // ...
   };
   ```

2. **Set Production Environment Variables**
   ```bash
   export AZURE_SQL_URL=jdbc:sqlserver://...
   export AZURE_KEYVAULT_URI=https://...
   export JWT_SECRET=...
   # See deployment guide for full list
   ```

3. **Build Production JAR**
   ```bash
   mvn clean package -Pproduction
   ```

4. **Package Extension**
   ```bash
   cd extension
   # Remove test files, minify JS, etc.
   # Upload to Chrome Web Store
   ```

See the [Enterprise Deployment](#enterprise-deployment) section for complete production setup.

---

### Environment Variables

```bash
# Backend
export AZURE_SUBSCRIPTION_ID=xxx
export AZURE_KEYVAULT_URI=https://xxx.vault.azure.net
export JWT_SECRET=your-32-char-secret

# For local development with H2
export SPRING_PROFILES_ACTIVE=local
```

### Testing

```bash
# Unit tests
mvn test

# Integration tests
mvn verify -P integration-tests

# Extension tests
cd extension && npm test
```

---

## Operational Runbook

### Health Checks

| Endpoint | Expected | Alert Threshold |
|----------|----------|-----------------|
| `/actuator/health` | 200 OK | Any non-200 |
| `/actuator/metrics` | 200 OK | Response > 5s |

### Common Issues

#### High Latency

1. Check Azure SQL DTU utilization
2. Verify cache hit ratio
3. Review ML endpoint response times

#### Authentication Failures

1. Verify Azure AD app registration
2. Check token expiration
3. Validate CORS configuration

#### Missing Recommendations

1. Verify data ingestion completed
2. Check ML endpoint health
3. Review confidence thresholds

### Monitoring Dashboards

- **Application Insights**: Request traces, exceptions
- **Azure Monitor**: Infrastructure metrics
- **Custom Dashboard**: Business KPIs

---

## License

Copyright (c) Microsoft Corporation. All rights reserved.

Licensed under the MIT License.
