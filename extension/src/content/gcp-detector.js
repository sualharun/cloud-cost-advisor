/**
 * Google Cloud Console Content Script
 *
 * Detects GCP resources from the console DOM and triggers analysis.
 *
 * DETECTION STRATEGY:
 * 1. Parse resource name from URL path
 * 2. Detect service from URL (compute, sql, storage, etc.)
 * 3. Extract configuration from Angular-based UI components
 * 4. Handle GCP's single-page application navigation
 */

(function() {
  'use strict';

  const PROVIDER = 'GCP';

  // GCP service to resource type mapping
  const SERVICE_TYPE_MAP = {
    'compute': 'COMPUTE',
    'sql': 'DATABASE',
    'storage': 'STORAGE',
    'functions': 'SERVERLESS',
    'run': 'SERVERLESS',
    'kubernetes': 'KUBERNETES',
    'container': 'KUBERNETES',
    'memorystore': 'CACHING',
    'bigquery': 'ANALYTICS',
    'spanner': 'DATABASE',
    'bigtable': 'DATABASE',
    'filestore': 'STORAGE',
  };

  let currentResourceId = null;
  let overlayElement = null;
  let isAnalyzing = false;

  /**
   * Initialize the detector.
   */
  function init() {
    console.log('[CloudOptimizer] GCP detector initialized');

    // Initial detection
    detectResource();

    // Monitor URL changes (GCP Console is an Angular SPA)
    observeUrlChanges();

    // Monitor for resource detail panels
    observeResourceDetails();
  }

  /**
   * Detect current resource from URL and DOM.
   */
  function detectResource() {
    const url = window.location.href;
    const pathname = window.location.pathname;

    // Extract project ID
    const projectId = extractProjectId();
    if (!projectId) {
      hideOverlay();
      return;
    }

    // Detect service type
    const service = detectService(pathname);
    if (!service || !SERVICE_TYPE_MAP[service]) {
      hideOverlay();
      return;
    }

    // Detect resource based on service
    let resourceInfo = null;

    switch (service) {
      case 'compute':
        resourceInfo = detectComputeResource(projectId, pathname);
        break;
      case 'sql':
        resourceInfo = detectSqlResource(projectId, pathname);
        break;
      case 'storage':
        resourceInfo = detectStorageResource(projectId, pathname);
        break;
      case 'functions':
        resourceInfo = detectFunctionsResource(projectId, pathname);
        break;
      case 'run':
        resourceInfo = detectCloudRunResource(projectId, pathname);
        break;
      case 'kubernetes':
      case 'container':
        resourceInfo = detectGkeResource(projectId, pathname);
        break;
      default:
        resourceInfo = detectGenericResource(projectId, service, pathname);
    }

    if (resourceInfo && resourceInfo.resourceId) {
      if (resourceInfo.resourceId === currentResourceId) {
        return;
      }

      currentResourceId = resourceInfo.resourceId;
      console.log('[CloudOptimizer] Detected GCP resource:', currentResourceId);

      analyzeResource({
        provider: PROVIDER,
        resourceId: resourceInfo.resourceId,
        resourceType: SERVICE_TYPE_MAP[service],
        region: resourceInfo.zone || resourceInfo.region,
        detectedConfig: resourceInfo.config,
      });
    } else {
      hideOverlay();
      currentResourceId = null;
    }
  }

  /**
   * Extract project ID from URL.
   */
  function extractProjectId() {
    // URL pattern: console.cloud.google.com/compute/instances?project=my-project
    const projectMatch = window.location.href.match(/[?&]project=([^&]+)/);
    if (projectMatch) {
      return decodeURIComponent(projectMatch[1]);
    }

    // Also check pathname: /project/{project-id}/...
    const pathMatch = window.location.pathname.match(/\/project\/([^/]+)/);
    if (pathMatch) {
      return decodeURIComponent(pathMatch[1]);
    }

    return null;
  }

  /**
   * Detect GCP service from pathname.
   */
  function detectService(pathname) {
    // URL patterns:
    // /compute/instances -> compute
    // /sql/instances -> sql
    // /storage/browser -> storage
    const match = pathname.match(/^\/([a-z-]+)/i);
    return match ? match[1].toLowerCase() : null;
  }

  /**
   * Detect Compute Engine instance.
   */
  function detectComputeResource(projectId, pathname) {
    // Pattern: /compute/instancesDetail/zones/{zone}/instances/{name}
    const instanceMatch = pathname.match(
      /\/compute\/instancesDetail\/zones\/([^/]+)\/instances\/([^/?]+)/i
    );

    if (instanceMatch) {
      const zone = instanceMatch[1];
      const instanceName = decodeURIComponent(instanceMatch[2]);
      const resourceId = `projects/${projectId}/zones/${zone}/instances/${instanceName}`;
      const config = extractComputeConfig();

      return { resourceId, zone, config };
    }

    // Try to detect from selected instance in list
    const selectedRow = document.querySelector('.mat-row.cdk-row.selected');
    if (selectedRow) {
      const nameCell = selectedRow.querySelector('[data-column="name"]');
      const zoneCell = selectedRow.querySelector('[data-column="zone"]');

      if (nameCell && zoneCell) {
        const instanceName = nameCell.textContent.trim();
        const zone = zoneCell.textContent.trim();
        const resourceId = `projects/${projectId}/zones/${zone}/instances/${instanceName}`;

        return { resourceId, zone, config: extractComputeConfigFromRow(selectedRow) };
      }
    }

    return null;
  }

  /**
   * Detect Cloud SQL instance.
   */
  function detectSqlResource(projectId, pathname) {
    // Pattern: /sql/instances/{instance-name}/overview
    const sqlMatch = pathname.match(/\/sql\/instances\/([^/]+)/i);

    if (sqlMatch) {
      const instanceName = decodeURIComponent(sqlMatch[1]);
      const resourceId = `projects/${projectId}/instances/${instanceName}`;
      const config = extractSqlConfig();

      return { resourceId, region: extractSqlRegion(), config };
    }

    return null;
  }

  /**
   * Detect Cloud Storage bucket.
   */
  function detectStorageResource(projectId, pathname) {
    // Pattern: /storage/browser/{bucket-name}
    const bucketMatch = pathname.match(/\/storage\/browser\/([^/?]+)/i);

    if (bucketMatch) {
      const bucketName = decodeURIComponent(bucketMatch[1]);
      const resourceId = `projects/${projectId}/buckets/${bucketName}`;

      return { resourceId, config: {} };
    }

    return null;
  }

  /**
   * Detect Cloud Functions function.
   */
  function detectFunctionsResource(projectId, pathname) {
    // Pattern: /functions/details/{region}/{function-name}
    const fnMatch = pathname.match(/\/functions\/details\/([^/]+)\/([^/?]+)/i);

    if (fnMatch) {
      const region = fnMatch[1];
      const functionName = decodeURIComponent(fnMatch[2]);
      const resourceId = `projects/${projectId}/locations/${region}/functions/${functionName}`;
      const config = extractFunctionsConfig();

      return { resourceId, region, config };
    }

    return null;
  }

  /**
   * Detect Cloud Run service.
   */
  function detectCloudRunResource(projectId, pathname) {
    // Pattern: /run/detail/{region}/{service-name}
    const runMatch = pathname.match(/\/run\/detail\/([^/]+)\/([^/?]+)/i);

    if (runMatch) {
      const region = runMatch[1];
      const serviceName = decodeURIComponent(runMatch[2]);
      const resourceId = `projects/${projectId}/locations/${region}/services/${serviceName}`;
      const config = extractCloudRunConfig();

      return { resourceId, region, config };
    }

    return null;
  }

  /**
   * Detect GKE cluster.
   */
  function detectGkeResource(projectId, pathname) {
    // Pattern: /kubernetes/clusters/details/{location}/{cluster-name}
    const gkeMatch = pathname.match(
      /\/kubernetes\/clusters\/details\/([^/]+)\/([^/?]+)/i
    );

    if (gkeMatch) {
      const location = gkeMatch[1];
      const clusterName = decodeURIComponent(gkeMatch[2]);
      const resourceId = `projects/${projectId}/locations/${location}/clusters/${clusterName}`;
      const config = extractGkeConfig();

      return { resourceId, region: location, config };
    }

    return null;
  }

  /**
   * Generic resource detection.
   */
  function detectGenericResource(projectId, service, pathname) {
    // Look for resource name in common patterns
    const nameMatch = pathname.match(/\/([^/]+)\/details\/[^/]+\/([^/?]+)/i) ||
                     pathname.match(/\/([^/]+)\/([^/?]+)$/i);

    if (nameMatch) {
      const resourceName = decodeURIComponent(nameMatch[2]);
      const resourceId = `projects/${projectId}/${service}/${resourceName}`;
      return { resourceId, config: {} };
    }

    return null;
  }

  /**
   * Extract Compute Engine instance configuration.
   */
  function extractComputeConfig() {
    const config = {};

    // Machine type
    const machineTypeElement = document.querySelector('[data-label="Machine type"]');
    if (machineTypeElement) {
      const text = machineTypeElement.textContent.trim();
      config.machineType = text;
      const specs = parseMachineTypeSpecs(text);
      if (specs) {
        config.vcpu = specs.vcpu;
        config.memoryGb = specs.memoryGb;
      }
    }

    // Zone
    const zoneElement = document.querySelector('[data-label="Zone"]');
    if (zoneElement) {
      config.zone = zoneElement.textContent.trim();
    }

    // Status
    const statusElement = document.querySelector('.instance-status');
    if (statusElement) {
      config.status = statusElement.textContent.trim();
    }

    return config;
  }

  /**
   * Extract config from table row.
   */
  function extractComputeConfigFromRow(row) {
    const config = {};

    const machineTypeCell = row.querySelector('[data-column="machineType"]');
    if (machineTypeCell) {
      config.machineType = machineTypeCell.textContent.trim();
    }

    const statusCell = row.querySelector('[data-column="status"]');
    if (statusCell) {
      config.status = statusCell.textContent.trim();
    }

    return config;
  }

  /**
   * Extract Cloud SQL configuration.
   */
  function extractSqlConfig() {
    const config = {};

    const tierElement = document.querySelector('[data-label="Machine type"]');
    if (tierElement) {
      config.tier = tierElement.textContent.trim();
    }

    const engineElement = document.querySelector('[data-label="Database version"]');
    if (engineElement) {
      config.engine = engineElement.textContent.trim();
    }

    return config;
  }

  /**
   * Extract Cloud SQL region.
   */
  function extractSqlRegion() {
    const regionElement = document.querySelector('[data-label="Region"]');
    if (regionElement) {
      return regionElement.textContent.trim();
    }
    return null;
  }

  /**
   * Extract Cloud Functions configuration.
   */
  function extractFunctionsConfig() {
    const config = {};

    const memoryElement = document.querySelector('[data-label="Memory allocated"]');
    if (memoryElement) {
      const memoryMatch = memoryElement.textContent.match(/(\d+)\s*MB/i);
      if (memoryMatch) {
        config.memoryMb = parseInt(memoryMatch[1], 10);
        config.memoryGb = config.memoryMb / 1024;
      }
    }

    return config;
  }

  /**
   * Extract Cloud Run configuration.
   */
  function extractCloudRunConfig() {
    const config = {};

    const cpuElement = document.querySelector('[data-label="CPU"]');
    if (cpuElement) {
      config.cpu = cpuElement.textContent.trim();
    }

    const memoryElement = document.querySelector('[data-label="Memory"]');
    if (memoryElement) {
      config.memory = memoryElement.textContent.trim();
    }

    return config;
  }

  /**
   * Extract GKE cluster configuration.
   */
  function extractGkeConfig() {
    const config = {};

    const nodeCountElement = document.querySelector('[data-label="Total nodes"]');
    if (nodeCountElement) {
      config.nodeCount = parseInt(nodeCountElement.textContent.trim(), 10);
    }

    return config;
  }

  /**
   * Parse GCP machine type into specs.
   */
  function parseMachineTypeSpecs(machineType) {
    // Common GCP machine types
    const specs = {
      'e2-micro': { vcpu: 0.25, memoryGb: 1 },
      'e2-small': { vcpu: 0.5, memoryGb: 2 },
      'e2-medium': { vcpu: 1, memoryGb: 4 },
      'e2-standard-2': { vcpu: 2, memoryGb: 8 },
      'e2-standard-4': { vcpu: 4, memoryGb: 16 },
      'n2-standard-2': { vcpu: 2, memoryGb: 8 },
      'n2-standard-4': { vcpu: 4, memoryGb: 16 },
      'n2-standard-8': { vcpu: 8, memoryGb: 32 },
      'c2-standard-4': { vcpu: 4, memoryGb: 16 },
    };

    const normalized = machineType.toLowerCase().trim();
    return specs[normalized] || null;
  }

  /**
   * Send analysis request.
   */
  async function analyzeResource(resourceData) {
    if (isAnalyzing) return;
    isAnalyzing = true;

    showLoadingOverlay();

    try {
      const response = await chrome.runtime.sendMessage({
        type: 'ANALYZE_RESOURCE',
        payload: resourceData,
      });

      if (response.error) {
        if (response.requiresAuth) {
          showAuthOverlay();
        } else {
          showErrorOverlay(response.error);
        }
      } else {
        showResultsOverlay(response);
      }
    } catch (error) {
      console.error('[CloudOptimizer] Analysis error:', error);
      showErrorOverlay('Analysis failed');
    } finally {
      isAnalyzing = false;
    }
  }

  // Overlay functions (same pattern as other detectors)
  function createOverlay() {
    if (overlayElement) return overlayElement;

    overlayElement = document.createElement('div');
    overlayElement.id = 'cloud-optimizer-overlay';
    overlayElement.className = 'cco-overlay';
    overlayElement.innerHTML = `
      <div class="cco-overlay-header">
        <span class="cco-overlay-title">Cost Optimizer</span>
        <button class="cco-overlay-close" id="cco-close">&times;</button>
      </div>
      <div class="cco-overlay-content" id="cco-content"></div>
    `;

    document.body.appendChild(overlayElement);
    overlayElement.querySelector('#cco-close').addEventListener('click', hideOverlay);

    return overlayElement;
  }

  function showLoadingOverlay() {
    const overlay = createOverlay();
    overlay.querySelector('#cco-content').innerHTML = `
      <div class="cco-loading">
        <div class="cco-spinner"></div>
        <span>Analyzing resource...</span>
      </div>
    `;
    overlay.classList.add('visible');
  }

  function showResultsOverlay(result) {
    const overlay = createOverlay();
    const content = overlay.querySelector('#cco-content');
    const hasRecommendations = result.recommendations?.length > 0;

    content.innerHTML = `
      <div class="cco-result">
        <div class="cco-metric">
          <span class="cco-metric-label">Monthly Forecast</span>
          <span class="cco-metric-value">$${result.monthlyCostForecast.toFixed(2)}</span>
        </div>
        <div class="cco-status cco-status-${result.severityLevel}">
          ${result.utilizationStatus || 'Unknown'}
        </div>
        ${hasRecommendations ? result.recommendations.map(rec => `
          <div class="cco-recommendation">
            <strong>${rec.actionDisplayName}</strong>
            <span>Save $${rec.estimatedSavings.toFixed(2)}/mo</span>
          </div>
        `).join('') : '<div class="cco-no-recommendations">No recommendations</div>'}
      </div>
    `;
    overlay.classList.add('visible');
  }

  function showAuthOverlay() {
    const overlay = createOverlay();
    overlay.querySelector('#cco-content').innerHTML = `
      <div class="cco-auth">
        <p>Please sign in to analyze this resource.</p>
        <button class="cco-btn cco-btn-primary" id="cco-signin">Sign In</button>
      </div>
    `;
    overlay.querySelector('#cco-signin').addEventListener('click', async () => {
      await chrome.runtime.sendMessage({ type: 'AUTHENTICATE' });
      detectResource();
    });
    overlay.classList.add('visible');
  }

  function showErrorOverlay(message) {
    const overlay = createOverlay();
    overlay.querySelector('#cco-content').innerHTML = `
      <div class="cco-error">${message}</div>
    `;
    overlay.classList.add('visible');
  }

  function hideOverlay() {
    if (overlayElement) {
      overlayElement.classList.remove('visible');
    }
  }

  function observeUrlChanges() {
    let lastUrl = window.location.href;
    const observer = new MutationObserver(() => {
      if (window.location.href !== lastUrl) {
        lastUrl = window.location.href;
        currentResourceId = null;
        detectResource();
      }
    });
    observer.observe(document.body, { childList: true, subtree: true });
    window.addEventListener('popstate', detectResource);
  }

  function observeResourceDetails() {
    const observer = new MutationObserver(() => {
      // Detect when detail panels load
    });
    observer.observe(document.body, { childList: true, subtree: true });
  }

  // Initialize
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

})();
