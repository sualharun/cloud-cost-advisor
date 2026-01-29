/**
 * AWS Console Content Script
 *
 * Detects AWS resources from the console DOM and triggers analysis.
 *
 * DETECTION STRATEGY:
 * 1. Parse ARN/resource ID from URL and page elements
 * 2. Detect service type from URL path (ec2, rds, s3, etc.)
 * 3. Extract instance configuration from tables and detail views
 * 4. Handle the variety of AWS console UI patterns
 */

(function() {
  'use strict';

  const PROVIDER = 'AWS';

  // AWS service to resource type mapping
  const SERVICE_TYPE_MAP = {
    'ec2': 'COMPUTE',
    'rds': 'DATABASE',
    's3': 'STORAGE',
    'lambda': 'SERVERLESS',
    'eks': 'KUBERNETES',
    'ecs': 'KUBERNETES',
    'elasticache': 'CACHING',
    'redshift': 'ANALYTICS',
    'dynamodb': 'DATABASE',
    'elb': 'NETWORK',
    'elbv2': 'NETWORK',
  };

  let currentResourceId = null;
  let overlayElement = null;
  let isAnalyzing = false;

  /**
   * Initialize the detector.
   */
  function init() {
    console.log('[CloudOptimizer] AWS detector initialized');

    // Initial detection
    detectResource();

    // Monitor URL changes
    observeUrlChanges();

    // Monitor DOM for resource details
    observeResourceDetails();
  }

  /**
   * Detect current resource from URL and DOM.
   */
  function detectResource() {
    const url = window.location.href;
    const service = detectService(url);

    if (!service || !SERVICE_TYPE_MAP[service]) {
      hideOverlay();
      return;
    }

    // Detect resource based on service type
    let resourceInfo = null;

    switch (service) {
      case 'ec2':
        resourceInfo = detectEc2Resource();
        break;
      case 'rds':
        resourceInfo = detectRdsResource();
        break;
      case 's3':
        resourceInfo = detectS3Resource();
        break;
      case 'lambda':
        resourceInfo = detectLambdaResource();
        break;
      default:
        resourceInfo = detectGenericResource(service);
    }

    if (resourceInfo && resourceInfo.resourceId) {
      // Skip if same resource
      if (resourceInfo.resourceId === currentResourceId) {
        return;
      }

      currentResourceId = resourceInfo.resourceId;
      console.log('[CloudOptimizer] Detected AWS resource:', currentResourceId);

      analyzeResource({
        provider: PROVIDER,
        resourceId: resourceInfo.resourceId,
        resourceType: SERVICE_TYPE_MAP[service],
        region: extractRegion(),
        detectedConfig: resourceInfo.config,
      });
    } else {
      hideOverlay();
      currentResourceId = null;
    }
  }

  /**
   * Detect AWS service from URL.
   */
  function detectService(url) {
    // URL pattern: console.aws.amazon.com/{service}/...
    const match = url.match(/console\.aws\.amazon\.com\/([a-z0-9-]+)/i);
    return match ? match[1].toLowerCase() : null;
  }

  /**
   * Detect EC2 instance details.
   */
  function detectEc2Resource() {
    const url = window.location.href;

    // Instance detail page: /ec2/home?region=xxx#InstanceDetails:instanceId=i-xxx
    const instanceMatch = url.match(/instanceId=([i-][a-z0-9]+)/i) ||
                         url.match(/InstanceDetails.*?([i-][a-z0-9]+)/i);

    if (instanceMatch) {
      const instanceId = instanceMatch[1];
      const arn = buildArn('ec2', 'instance', instanceId);
      const config = extractEc2Config();

      return { resourceId: arn, config };
    }

    // Try to detect from selected row in table
    const selectedRow = document.querySelector('tr.awsui-table-row-selected');
    if (selectedRow) {
      const instanceCell = selectedRow.querySelector('[data-column-id="instanceId"]');
      if (instanceCell) {
        const instanceId = instanceCell.textContent.trim();
        if (instanceId.startsWith('i-')) {
          const arn = buildArn('ec2', 'instance', instanceId);
          const config = extractEc2ConfigFromRow(selectedRow);
          return { resourceId: arn, config };
        }
      }
    }

    return null;
  }

  /**
   * Detect RDS instance details.
   */
  function detectRdsResource() {
    const url = window.location.href;

    // RDS detail page: /rds/home?region=xxx#database:id=mydb
    const dbMatch = url.match(/database:id=([^&;]+)/i) ||
                   url.match(/databases\/([^/?]+)/i);

    if (dbMatch) {
      const dbId = decodeURIComponent(dbMatch[1]);
      const arn = buildArn('rds', 'db', dbId);
      const config = extractRdsConfig();

      return { resourceId: arn, config };
    }

    return null;
  }

  /**
   * Detect S3 bucket details.
   */
  function detectS3Resource() {
    const url = window.location.href;

    // S3 bucket page: /s3/buckets/bucket-name
    const bucketMatch = url.match(/\/s3\/buckets\/([^/?]+)/i);

    if (bucketMatch) {
      const bucketName = decodeURIComponent(bucketMatch[1]);
      const arn = `arn:aws:s3:::${bucketName}`;
      const config = extractS3Config();

      return { resourceId: arn, config };
    }

    return null;
  }

  /**
   * Detect Lambda function details.
   */
  function detectLambdaResource() {
    const url = window.location.href;

    // Lambda detail page: /lambda/home?region=xxx#/functions/myfunction
    const fnMatch = url.match(/\/functions\/([^/?]+)/i);

    if (fnMatch) {
      const functionName = decodeURIComponent(fnMatch[1]);
      const arn = buildArn('lambda', 'function', functionName);
      const config = extractLambdaConfig();

      return { resourceId: arn, config };
    }

    return null;
  }

  /**
   * Generic resource detection.
   */
  function detectGenericResource(service) {
    // Look for ARN in the page
    const arnElements = document.querySelectorAll('[data-testid*="arn"], .arn-field');
    for (const el of arnElements) {
      const text = el.textContent.trim();
      if (text.startsWith('arn:aws:')) {
        return { resourceId: text, config: {} };
      }
    }

    return null;
  }

  /**
   * Build AWS ARN.
   */
  function buildArn(service, resourceType, resourceId) {
    const region = extractRegion() || 'us-east-1';
    const account = extractAccountId() || '123456789012';
    return `arn:aws:${service}:${region}:${account}:${resourceType}/${resourceId}`;
  }

  /**
   * Extract region from URL or page.
   */
  function extractRegion() {
    // From URL parameter
    const urlMatch = window.location.href.match(/region=([a-z]+-[a-z]+-\d+)/i);
    if (urlMatch) return urlMatch[1];

    // From region selector
    const regionSelector = document.querySelector('[data-testid="awsc-nav-region-selector"]');
    if (regionSelector) {
      const text = regionSelector.textContent.trim();
      const regionMatch = text.match(/([a-z]+-[a-z]+-\d+)/i);
      if (regionMatch) return regionMatch[1];
    }

    return null;
  }

  /**
   * Extract AWS account ID from page.
   */
  function extractAccountId() {
    // From account menu
    const accountElement = document.querySelector('[data-testid="awsc-nav-account-menu-button"]');
    if (accountElement) {
      const text = accountElement.textContent.trim();
      const accountMatch = text.match(/(\d{12})/);
      if (accountMatch) return accountMatch[1];
    }

    return null;
  }

  /**
   * Extract EC2 instance configuration from detail page.
   */
  function extractEc2Config() {
    const config = {};

    // Instance type
    const typeElement = document.querySelector('[data-testid="instance-type"]') ||
                       document.querySelector('.instance-type-value');
    if (typeElement) {
      config.instanceType = typeElement.textContent.trim();
      const specs = parseInstanceTypeSpecs(config.instanceType);
      if (specs) {
        config.vcpu = specs.vcpu;
        config.memoryGb = specs.memoryGb;
      }
    }

    // State
    const stateElement = document.querySelector('[data-testid="instance-state"]');
    if (stateElement) {
      config.status = stateElement.textContent.trim();
    }

    return config;
  }

  /**
   * Extract EC2 config from table row.
   */
  function extractEc2ConfigFromRow(row) {
    const config = {};

    const typeCell = row.querySelector('[data-column-id="instanceType"]');
    if (typeCell) {
      config.instanceType = typeCell.textContent.trim();
    }

    const stateCell = row.querySelector('[data-column-id="instanceState"]');
    if (stateCell) {
      config.status = stateCell.textContent.trim();
    }

    return config;
  }

  /**
   * Extract RDS configuration.
   */
  function extractRdsConfig() {
    const config = {};

    // DB instance class
    const classElements = document.querySelectorAll('[data-test-selector="database-class"]');
    if (classElements.length > 0) {
      config.instanceClass = classElements[0].textContent.trim();
    }

    // Engine
    const engineElements = document.querySelectorAll('[data-test-selector="engine"]');
    if (engineElements.length > 0) {
      config.engine = engineElements[0].textContent.trim();
    }

    return config;
  }

  /**
   * Extract S3 configuration.
   */
  function extractS3Config() {
    const config = {};

    // Storage class distribution would require more complex parsing
    // For now, return empty config
    return config;
  }

  /**
   * Extract Lambda configuration.
   */
  function extractLambdaConfig() {
    const config = {};

    // Memory
    const memoryElement = document.querySelector('[data-testid="function-memory"]');
    if (memoryElement) {
      const memoryMatch = memoryElement.textContent.match(/(\d+)\s*MB/i);
      if (memoryMatch) {
        config.memoryMb = parseInt(memoryMatch[1], 10);
        config.memoryGb = config.memoryMb / 1024;
      }
    }

    // Timeout
    const timeoutElement = document.querySelector('[data-testid="function-timeout"]');
    if (timeoutElement) {
      config.timeoutSeconds = parseInt(timeoutElement.textContent, 10);
    }

    return config;
  }

  /**
   * Parse instance type into specs.
   * AWS types: t3.micro, m5.large, c5.xlarge, etc.
   */
  function parseInstanceTypeSpecs(instanceType) {
    // Common AWS instance specs
    const specs = {
      't3.micro': { vcpu: 2, memoryGb: 1 },
      't3.small': { vcpu: 2, memoryGb: 2 },
      't3.medium': { vcpu: 2, memoryGb: 4 },
      't3.large': { vcpu: 2, memoryGb: 8 },
      'm5.large': { vcpu: 2, memoryGb: 8 },
      'm5.xlarge': { vcpu: 4, memoryGb: 16 },
      'm5.2xlarge': { vcpu: 8, memoryGb: 32 },
      'c5.large': { vcpu: 2, memoryGb: 4 },
      'c5.xlarge': { vcpu: 4, memoryGb: 8 },
      'r5.large': { vcpu: 2, memoryGb: 16 },
      'r5.xlarge': { vcpu: 4, memoryGb: 32 },
    };

    return specs[instanceType] || null;
  }

  /**
   * Send analysis request to background script.
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

  // Overlay functions - reused from azure-detector.js pattern
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
      if (!isAnalyzing && currentResourceId) {
        // Re-extract config when details change
      }
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
