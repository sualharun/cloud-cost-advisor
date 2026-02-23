/**
 * Azure Portal Content Script
 *
 * Detects Azure resources from the portal DOM and triggers analysis.
 *
 * DETECTION STRATEGY:
 * 1. Monitor URL changes for resource pages
 * 2. Parse resource ID from URL and breadcrumbs
 * 3. Extract configuration from the resource blade
 * 4. Handle SPA navigation without page reloads
 */

(function() {
  'use strict';

  const PROVIDER = 'AZURE';

  // Resource type mapping from Azure resource types
  const RESOURCE_TYPE_MAP = {
    'microsoft.compute/virtualmachines': 'COMPUTE',
    'microsoft.compute/virtualmachinescalesets': 'COMPUTE',
    'microsoft.storage/storageaccounts': 'STORAGE',
    'microsoft.sql/servers/databases': 'DATABASE',
    'microsoft.documentdb/databaseaccounts': 'DATABASE',
    'microsoft.containerservice/managedclusters': 'KUBERNETES',
    'microsoft.web/sites': 'SERVERLESS',
  };

  let currentResourceId = null;
  let overlayElement = null;
  let isAnalyzing = false;

  /**
   * Initialize the detector.
   */
  function init() {
    console.log('[CloudOptimizer] Azure detector initialized');
    console.log('[CloudOptimizer] Running on:', window.location.href);

    // Check if we're on a test page (local development)
    const isTestPage = window.location.href.includes('test-page.html') ||
                       window.location.href.includes('localhost') ||
                       window.location.protocol === 'file:';

    if (isTestPage) {
      console.log('[CloudOptimizer] 🧪 Test mode detected - scanning for data-* resource cards');
      detectTestPageResources();
      return;
    }

    // Initial detection (Azure Portal)
    detectResource();

    // Monitor URL changes (Azure Portal is an SPA)
    observeUrlChanges();

    // Monitor DOM changes for blade content
    observeBladeContent();
  }

  /**
   * Detect resources from test page data-* attributes.
   * Used for local development without cloud consoles.
   */
  function detectTestPageResources() {
    const resourceCards = document.querySelectorAll('[data-provider][data-resource-id]');
    console.log('[CloudOptimizer] Found', resourceCards.length, 'test resources');

    resourceCards.forEach((card, index) => {
      const provider = card.dataset.provider?.toUpperCase() || 'AZURE';
      const resourceId = card.dataset.resourceId;
      const resourceType = card.dataset.resourceType?.toUpperCase() || 'COMPUTE';
      const region = card.dataset.region || 'eastus';

      console.log('[CloudOptimizer] Test resource', index + 1, ':', {
        provider, resourceId, resourceType, region
      });

      // Add click handler to analyze on click
      card.style.cursor = 'pointer';
      card.addEventListener('click', async (e) => {
        e.preventDefault();
        console.log('[CloudOptimizer] Analyzing test resource:', resourceId);

        currentResourceId = resourceId;
        await analyzeResource({
          provider: provider,
          resourceId: resourceId,
          resourceType: resourceType,
          region: region,
          detectedConfig: { vcpu: 4, memoryGb: 16 },
        });
      });

      // Add visual indicator that extension is active
      const badge = document.createElement('div');
      badge.style.cssText = 'position:absolute;top:10px;right:10px;background:#0078d4;color:#fff;padding:4px 8px;border-radius:4px;font-size:11px;font-weight:600;z-index:1000;';
      badge.textContent = '💡 Click to Analyze';
      card.style.position = 'relative';
      card.appendChild(badge);
    });

    // Log status to console
    if (resourceCards.length > 0) {
      console.log('[CloudOptimizer] ✅ Extension active - click any resource card to analyze');
    }
  }

  /**
   * Detect current resource from URL and DOM.
   */
  function detectResource() {
    const url = window.location.href;

    // Azure resource URLs follow pattern:
    // /resource/subscriptions/{sub}/resourceGroups/{rg}/providers/{provider}/{type}/{name}
    const resourceMatch = url.match(/\/resource(\/subscriptions\/[^#?]+)/i);

    if (!resourceMatch) {
      hideOverlay();
      currentResourceId = null;
      return;
    }

    const resourceId = resourceMatch[1];

    // Skip if same resource
    if (resourceId === currentResourceId) {
      return;
    }

    currentResourceId = resourceId;
    console.log('[CloudOptimizer] Detected Azure resource:', resourceId);

    // Extract resource details
    const resourceInfo = parseResourceId(resourceId);

    if (resourceInfo && isOptimizableResource(resourceInfo.resourceType)) {
      // Get additional config from DOM
      const detectedConfig = extractConfiguration();

      // Trigger analysis
      analyzeResource({
        provider: PROVIDER,
        resourceId: resourceId,
        resourceType: mapResourceType(resourceInfo.resourceType),
        region: extractRegion(),
        detectedConfig: detectedConfig,
      });
    }
  }

  /**
   * Parse Azure resource ID into components.
   */
  function parseResourceId(resourceId) {
    // /subscriptions/{sub}/resourceGroups/{rg}/providers/{provider}/{type}/{name}
    const pattern = /\/subscriptions\/([^/]+)\/resourceGroups\/([^/]+)\/providers\/([^/]+\/[^/]+)\/(.+)/i;
    const match = resourceId.match(pattern);

    if (!match) {
      return null;
    }

    return {
      subscriptionId: match[1],
      resourceGroup: match[2],
      resourceType: match[3].toLowerCase(),
      resourceName: match[4],
    };
  }

  /**
   * Check if resource type is optimizable.
   */
  function isOptimizableResource(resourceType) {
    return Object.keys(RESOURCE_TYPE_MAP).some(
      type => resourceType.toLowerCase().includes(type)
    );
  }

  /**
   * Map Azure resource type to normalized type.
   */
  function mapResourceType(azureType) {
    const normalized = azureType.toLowerCase();
    for (const [key, value] of Object.entries(RESOURCE_TYPE_MAP)) {
      if (normalized.includes(key)) {
        return value;
      }
    }
    return 'UNKNOWN';
  }

  /**
   * Extract region from DOM.
   */
  function extractRegion() {
    // Look for region in resource properties
    const regionElements = document.querySelectorAll('[data-automation-key="location"]');
    if (regionElements.length > 0) {
      return regionElements[0].textContent.trim();
    }

    // Try essentials blade
    const essentials = document.querySelector('.fxc-essentials');
    if (essentials) {
      const locationItem = essentials.querySelector('[data-bind*="location"]');
      if (locationItem) {
        return locationItem.textContent.trim();
      }
    }

    return null;
  }

  /**
   * Extract resource configuration from DOM.
   */
  function extractConfiguration() {
    const config = {};

    // VM Size
    const sizeElement = document.querySelector('[data-automation-key="size"]');
    if (sizeElement) {
      const sizeText = sizeElement.textContent.trim();
      config.vmSize = sizeText;

      // Parse vCPU and memory from size description
      const specs = parseVmSizeSpecs(sizeText);
      if (specs) {
        config.vcpu = specs.vcpu;
        config.memoryGb = specs.memoryGb;
      }
    }

    // Storage size
    const storageElement = document.querySelector('[data-automation-key="diskSize"]');
    if (storageElement) {
      config.storageGb = parseInt(storageElement.textContent.trim(), 10);
    }

    // Status
    const statusElement = document.querySelector('[data-automation-key="status"]');
    if (statusElement) {
      config.status = statusElement.textContent.trim();
    }

    // SKU/Tier
    const skuElement = document.querySelector('[data-automation-key="pricingTier"]');
    if (skuElement) {
      config.sku = skuElement.textContent.trim();
    }

    return config;
  }

  /**
   * Parse VM size string into specs.
   */
  function parseVmSizeSpecs(sizeText) {
    // Standard_D4s_v3 (4 vCPUs, 16 GB)
    const match = sizeText.match(/(\d+)\s*vCPU[s]?,?\s*(\d+)\s*GB/i);
    if (match) {
      return {
        vcpu: parseInt(match[1], 10),
        memoryGb: parseInt(match[2], 10),
      };
    }

    // Try to parse from size name
    const sizeMatch = sizeText.match(/Standard_\w*?(\d+)/);
    if (sizeMatch) {
      return {
        vcpu: parseInt(sizeMatch[1], 10),
        memoryGb: null, // Would need lookup table
      };
    }

    return null;
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

  /**
   * Create and show the overlay UI.
   */
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
      <div class="cco-overlay-content" id="cco-content">
        <!-- Content injected dynamically -->
      </div>
    `;

    document.body.appendChild(overlayElement);

    // Close button handler
    overlayElement.querySelector('#cco-close').addEventListener('click', hideOverlay);

    // Make draggable
    makeDraggable(overlayElement);

    return overlayElement;
  }

  function showLoadingOverlay() {
    const overlay = createOverlay();
    const content = overlay.querySelector('#cco-content');
    content.innerHTML = `
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

    const statusClass = getStatusClass(result.severityLevel);
    const hasRecommendations = result.recommendations && result.recommendations.length > 0;

    content.innerHTML = `
      <div class="cco-result">
        <div class="cco-metric">
          <span class="cco-metric-label">Monthly Forecast</span>
          <span class="cco-metric-value">$${result.monthlyCostForecast.toFixed(2)}</span>
          <span class="cco-metric-confidence">${Math.round(result.confidence * 100)}% confidence</span>
        </div>

        <div class="cco-status ${statusClass}">
          <span class="cco-status-indicator"></span>
          <span class="cco-status-label">${result.utilizationStatus || 'Unknown'}</span>
          ${result.avgCpuUtilization ?
            `<span class="cco-status-detail">${Math.round(result.avgCpuUtilization * 100)}% CPU avg</span>` : ''}
        </div>

        <div class="cco-quick-actions">
          <button class="cco-btn cco-btn-secondary cco-btn-sm" onclick="window.ccoShowAlternatives()">
            Compare Alternatives
          </button>
          <button class="cco-btn cco-btn-secondary cco-btn-sm" onclick="window.ccoShowSimulator()">
            Cost Simulator
          </button>
          <button class="cco-btn cco-btn-secondary cco-btn-sm" onclick="window.ccoShowSavings()">
            Savings Tracker
          </button>
        </div>

        ${hasRecommendations ? `
          <div class="cco-recommendations">
            <h4>Recommendations</h4>
            ${result.recommendations.map((rec, index) => `
              <div class="cco-recommendation ${rec.riskLevel.toLowerCase()}" data-rec-id="${index}">
                <div class="cco-rec-header">
                  <span class="cco-rec-action">${rec.actionDisplayName}</span>
                  <span class="cco-rec-savings">Save $${rec.estimatedSavings.toFixed(2)}/mo</span>
                </div>
                <div class="cco-rec-summary">${rec.summary}</div>
                ${rec.suggestedConfig ? `
                  <div class="cco-rec-config">Suggested: ${rec.suggestedConfig}</div>
                ` : ''}
                <div class="cco-rec-actions">
                  <button class="cco-btn cco-btn-primary cco-btn-sm" onclick="window.ccoMarkImplemented('${rec.action}', ${index})">
                    Mark Implemented
                  </button>
                  <button class="cco-btn cco-btn-secondary cco-btn-sm" onclick="window.ccoDismiss('${rec.action}')">
                    Dismiss
                  </button>
                </div>
              </div>
            `).join('')}
          </div>
        ` : `
          <div class="cco-no-recommendations">
            <span>No optimization recommendations at this time.</span>
          </div>
        `}
      </div>
    `;

    overlay.classList.add('visible');
  }

  function showAuthOverlay() {
    const overlay = createOverlay();
    const content = overlay.querySelector('#cco-content');
    content.innerHTML = `
      <div class="cco-auth">
        <p>Please sign in to analyze this resource.</p>
        <button class="cco-btn cco-btn-primary" id="cco-signin">Sign in with Azure AD</button>
      </div>
    `;

    content.querySelector('#cco-signin').addEventListener('click', async () => {
      await chrome.runtime.sendMessage({ type: 'AUTHENTICATE' });
      detectResource(); // Retry analysis
    });

    overlay.classList.add('visible');
  }

  function showErrorOverlay(message) {
    const overlay = createOverlay();
    const content = overlay.querySelector('#cco-content');
    content.innerHTML = `
      <div class="cco-error">
        <span class="cco-error-icon">!</span>
        <span class="cco-error-message">${message}</span>
        <button class="cco-btn cco-btn-secondary" onclick="window.location.reload()">Retry</button>
      </div>
    `;
    overlay.classList.add('visible');
  }

  function hideOverlay() {
    if (overlayElement) {
      overlayElement.classList.remove('visible');
    }
  }

  function getStatusClass(severityLevel) {
    const mapping = {
      'success': 'cco-status-success',
      'warning': 'cco-status-warning',
      'danger': 'cco-status-danger',
      'critical': 'cco-status-critical',
      'neutral': 'cco-status-neutral',
    };
    return mapping[severityLevel] || 'cco-status-neutral';
  }

  /**
   * Make overlay draggable.
   */
  function makeDraggable(element) {
    const header = element.querySelector('.cco-overlay-header');
    let isDragging = false;
    let offsetX, offsetY;

    header.addEventListener('mousedown', (e) => {
      isDragging = true;
      offsetX = e.clientX - element.offsetLeft;
      offsetY = e.clientY - element.offsetTop;
    });

    document.addEventListener('mousemove', (e) => {
      if (!isDragging) return;
      element.style.left = (e.clientX - offsetX) + 'px';
      element.style.top = (e.clientY - offsetY) + 'px';
      element.style.right = 'auto';
    });

    document.addEventListener('mouseup', () => {
      isDragging = false;
    });
  }

  /**
   * Observe URL changes for SPA navigation.
   */
  function observeUrlChanges() {
    let lastUrl = window.location.href;

    const observer = new MutationObserver(() => {
      if (window.location.href !== lastUrl) {
        lastUrl = window.location.href;
        detectResource();
      }
    });

    observer.observe(document.body, {
      childList: true,
      subtree: true,
    });

    // Also listen for popstate
    window.addEventListener('popstate', detectResource);
  }

  /**
   * Observe blade content changes.
   */
  function observeBladeContent() {
    const observer = new MutationObserver((mutations) => {
      // Look for essentials blade being added
      for (const mutation of mutations) {
        for (const node of mutation.addedNodes) {
          if (node.classList && node.classList.contains('fxc-essentials')) {
            // Essentials blade loaded, update config
            if (currentResourceId) {
              setTimeout(detectResource, 500);
            }
          }
        }
      }
    });

    observer.observe(document.body, {
      childList: true,
      subtree: true,
    });
  }

  // Global handlers for overlay buttons
  window.ccoShowDetails = function(action) {
    console.log('[CloudOptimizer] Show details for:', action);
    // Open details panel or modal
  };

  window.ccoDismiss = async function(action) {
    try {
      await chrome.runtime.sendMessage({
        type: 'DISMISS_RECOMMENDATION',
        payload: { action, resourceId: currentResourceId },
      });
      detectResource(); // Refresh
    } catch (error) {
      console.error('[CloudOptimizer] Dismiss failed:', error);
    }
  };

  window.ccoMarkImplemented = async function(action, recId) {
    console.log('[CloudOptimizer] Mark implemented:', action, recId);
    try {
      const response = await chrome.runtime.sendMessage({
        type: 'MARK_IMPLEMENTED',
        payload: { action, resourceId: currentResourceId, recommendationId: recId },
      });
      if (response && response.success) {
        // Show success feedback
        const recEl = document.querySelector(`[data-rec-id="${recId}"]`);
        if (recEl) {
          recEl.classList.add('implemented');
          recEl.innerHTML = `
            <div class="cco-implemented-badge">
              <span>✓ Marked as Implemented</span>
              <span class="cco-implemented-note">Savings will be validated in 30 days</span>
            </div>
          `;
        }
      }
    } catch (error) {
      console.error('[CloudOptimizer] Mark implemented failed:', error);
    }
  };

  window.ccoShowAlternatives = async function() {
    console.log('[CloudOptimizer] Show alternatives for:', currentResourceId);
    showLoadingOverlay();
    try {
      const response = await chrome.runtime.sendMessage({
        type: 'GET_ALTERNATIVES',
        payload: {
          provider: PROVIDER,
          resourceId: currentResourceId,
          sku: 'Standard_D4s_v3', // Would come from detected config
          region: extractRegion() || 'eastus'
        },
      });

      if (response && !response.error && window.TradeoffPanel) {
        const overlay = createOverlay();
        const content = overlay.querySelector('#cco-content');
        window.TradeoffPanel.render(response, content);
        overlay.classList.add('visible');
      } else {
        showErrorOverlay(response?.error || 'Failed to load alternatives');
      }
    } catch (error) {
      console.error('[CloudOptimizer] Get alternatives failed:', error);
      showErrorOverlay('Failed to load alternatives');
    }
  };

  window.ccoShowSimulator = function() {
    console.log('[CloudOptimizer] Show simulator for:', currentResourceId);
    if (window.SimulationPanel) {
      const overlay = createOverlay();
      const content = overlay.querySelector('#cco-content');
      window.SimulationPanel.render({
        provider: PROVIDER,
        resourceId: currentResourceId,
        currentSku: 'Standard_D4s_v3', // Would come from detected config
        region: extractRegion() || 'eastus'
      }, content);
      overlay.classList.add('visible');
    }
  };

  window.ccoShowSavings = async function() {
    console.log('[CloudOptimizer] Show savings dashboard');
    showLoadingOverlay();
    try {
      const response = await chrome.runtime.sendMessage({
        type: 'GET_SAVINGS_METRICS'
      });

      if (response && !response.error && window.SavingsDashboard) {
        const overlay = createOverlay();
        const content = overlay.querySelector('#cco-content');
        window.SavingsDashboard.render(response, content);
        overlay.classList.add('visible');
      } else {
        showErrorOverlay(response?.error || 'Failed to load savings');
      }
    } catch (error) {
      console.error('[CloudOptimizer] Get savings failed:', error);
      showErrorOverlay('Failed to load savings data');
    }
  };

  // Initialize when DOM is ready
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

})();
