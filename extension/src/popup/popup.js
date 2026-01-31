/**
 * Cloud Cost Optimizer - Popup Script
 *
 * Handles popup UI interactions and displays current resource status.
 * Includes robust initialization with retry logic for service worker communication.
 */

// Prevent duplicate event listener attachment
let listenersAttached = false;

// Initialize when DOM is ready
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', init);
} else {
  init();
}

/**
 * Send message to service worker with retry logic
 */
async function sendMessageWithRetry(message, maxRetries = 3, timeout = 2000) {
  for (let attempt = 1; attempt <= maxRetries; attempt++) {
    try {
      const response = await Promise.race([
        chrome.runtime.sendMessage(message),
        new Promise((_, reject) =>
          setTimeout(() => reject(new Error('Timeout')), timeout)
        )
      ]);
      return response;
    } catch (error) {
      console.warn(`Message attempt ${attempt}/${maxRetries} failed:`, error.message);
      if (attempt === maxRetries) {
        throw error;
      }
      // Wait before retry with exponential backoff
      await new Promise(resolve => setTimeout(resolve, 200 * attempt));
    }
  }
}

/**
 * Check if service worker is healthy
 */
async function checkServiceWorker() {
  try {
    const response = await sendMessageWithRetry({ type: 'PING' }, 2, 1000);
    return response?.success === true;
  } catch {
    return false;
  }
}

async function init() {
  console.log('Popup initializing...');

  // Show loading state immediately
  showLoadingState();

  // Attach event listeners (only once)
  attachEventListeners();

  try {
    // Check service worker health first
    const isHealthy = await checkServiceWorker();
    if (!isHealthy) {
      console.warn('Service worker not responding, showing fallback UI');
      hideLoadingState();
      showStatusSection();
      showEmptyState('Extension loading. Please wait or refresh.');
      return;
    }

    // Check authentication status
    const authStatus = await sendMessageWithRetry({ type: 'GET_AUTH_STATUS' });

    hideLoadingState();

    if (!authStatus || !authStatus.authenticated) {
      showAuthSection();
    } else {
      showStatusSection();
      await loadCurrentTabStatus();
    }

  } catch (error) {
    console.error('Popup initialization error:', error);
    hideLoadingState();
    showErrorState('Unable to connect to extension. Click to retry.');
  }
}

/**
 * Attach event listeners (prevents duplicates)
 */
function attachEventListeners() {
  if (listenersAttached) return;
  listenersAttached = true;

  document.getElementById('sign-in-btn')?.addEventListener('click', handleSignIn);
  document.getElementById('sign-out-btn')?.addEventListener('click', handleSignOut);
  document.getElementById('settings-btn')?.addEventListener('click', openSettings);
  document.getElementById('retry-btn')?.addEventListener('click', handleRetry);

  // Listen for updates from content scripts
  chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (message.type === 'RESOURCE_UPDATED') {
      loadCurrentTabStatus();
    }
    return true;
  });
}

function showLoadingState() {
  const loading = document.getElementById('loading-state');
  const statusSection = document.getElementById('status-section');
  const authSection = document.getElementById('auth-section');

  if (loading) loading.classList.remove('hidden');
  if (statusSection) statusSection.classList.add('hidden');
  if (authSection) authSection.classList.add('hidden');
}

function hideLoadingState() {
  const loading = document.getElementById('loading-state');
  if (loading) loading.classList.add('hidden');
}

function showErrorState(message) {
  const errorState = document.getElementById('error-state');
  const errorMessage = document.getElementById('error-message');

  if (errorState) {
    errorState.classList.remove('hidden');
  }
  if (errorMessage) {
    errorMessage.textContent = message;
  }

  document.getElementById('auth-section')?.classList.add('hidden');
  document.getElementById('status-section')?.classList.add('hidden');
}

function hideErrorState() {
  const errorState = document.getElementById('error-state');
  if (errorState) errorState.classList.add('hidden');
}

async function handleRetry() {
  hideErrorState();
  await init();
}

function showAuthSection() {
  document.getElementById('auth-section').classList.remove('hidden');
  document.getElementById('status-section').classList.add('hidden');
}

function showStatusSection() {
  document.getElementById('auth-section').classList.add('hidden');
  document.getElementById('status-section').classList.remove('hidden');
}

async function handleSignIn() {
  const btn = document.getElementById('sign-in-btn');
  btn.disabled = true;
  btn.textContent = 'Signing in...';

  try {
    const result = await sendMessageWithRetry({ type: 'AUTHENTICATE' });
    if (result.success) {
      showStatusSection();
      await loadCurrentTabStatus();
    } else {
      alert('Sign in failed: ' + (result.error || 'Unknown error'));
    }
  } catch (error) {
    alert('Sign in failed: ' + error.message);
  } finally {
    btn.disabled = false;
    btn.textContent = 'Sign in with Azure AD';
  }
}

async function handleSignOut() {
  try {
    await sendMessageWithRetry({ type: 'LOGOUT' });
    showAuthSection();
  } catch (error) {
    console.error('Sign out failed:', error);
  }
}

function openSettings() {
  chrome.runtime.openOptionsPage();
}

async function loadCurrentTabStatus() {
  try {
    // Get current tab
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });

    if (!tab?.url) {
      showEmptyState();
      return;
    }

    // Detect provider from URL
    const provider = detectProvider(tab.url);

    if (!provider) {
      showEmptyState();
      return;
    }

    // Update provider badge
    updateProviderBadge(provider);

    // Request current resource info from content script
    try {
      const response = await chrome.tabs.sendMessage(tab.id, { type: 'GET_CURRENT_RESOURCE' });

      if (response?.resourceId) {
        showResourceInfo(response);
      } else {
        showEmptyState('Navigate to a resource to see insights.');
      }
    } catch {
      // Content script might not be loaded yet
      showEmptyState('Content script not ready. Refresh the page.');
    }

  } catch (error) {
    console.error('Error loading tab status:', error);
    showEmptyState();
  }
}

function detectProvider(url) {
  if (url.includes('portal.azure.com')) return 'AZURE';
  if (url.includes('console.aws.amazon.com')) return 'AWS';
  if (url.includes('console.cloud.google.com')) return 'GCP';
  return null;
}

function updateProviderBadge(provider) {
  const badge = document.getElementById('provider-badge');
  const providerNames = {
    'AZURE': 'Azure',
    'AWS': 'AWS',
    'GCP': 'GCP'
  };

  badge.textContent = providerNames[provider] || 'Unknown';
  badge.className = 'provider-badge ' + provider.toLowerCase();
}

function showResourceInfo(resource) {
  document.getElementById('empty-state').classList.add('hidden');
  document.getElementById('resource-info').classList.remove('hidden');
  document.getElementById('stats-section').classList.remove('hidden');

  document.getElementById('resource-name').textContent = resource.resourceName || 'Resource';
  document.getElementById('resource-id').textContent = resource.resourceId;

  if (resource.analysis) {
    document.getElementById('stat-forecast').textContent =
      '$' + resource.analysis.monthlyCostForecast.toFixed(2);

    const totalSavings = (resource.analysis.recommendations || [])
      .reduce((sum, r) => sum + (r.estimatedSavings || 0), 0);
    document.getElementById('stat-savings').textContent = '$' + totalSavings.toFixed(2);

    if (resource.analysis.recommendations?.length > 0) {
      showRecommendations(resource.analysis.recommendations, resource.analysis.severityLevel);
    }
  }
}

/**
 * Map severity level to CSS class
 */
function getSeverityClass(severityLevel) {
  const severityMap = {
    'critical': 'severity-critical',
    'warning': 'severity-warning',
    'info': 'severity-info'
  };
  return severityMap[severityLevel?.toLowerCase()] || 'severity-info';
}

function showRecommendations(recommendations, severityLevel) {
  const section = document.getElementById('recommendations-section');
  const list = document.getElementById('recommendations-list');
  const count = document.getElementById('rec-count');

  section.classList.remove('hidden');
  count.textContent = recommendations.length;

  list.innerHTML = recommendations.map((rec, index) => {
    const severity = rec.severityLevel || severityLevel || 'info';
    const severityClass = getSeverityClass(severity);

    return `
    <div class="recommendation-item ${severityClass}"
         data-action="${rec.action}"
         role="button"
         tabindex="0"
         aria-label="${rec.actionDisplayName}: Save $${rec.estimatedSavings.toFixed(2)} per month"
         style="animation-delay: ${index * 0.05}s">
      <div class="rec-icon ${rec.riskLevel === 'HIGH' ? 'warning' : 'success'}">
        ${getActionIcon(rec.action)}
      </div>
      <div class="rec-content">
        <div class="rec-title">${rec.actionDisplayName}</div>
        <div class="rec-summary">${rec.summary || ''}</div>
        <div class="rec-savings">Save $${rec.estimatedSavings.toFixed(2)}/mo</div>
      </div>
      <div class="rec-arrow" aria-hidden="true">
        <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor">
          <path d="M6 3l5 5-5 5" stroke="currentColor" stroke-width="1.5" fill="none"/>
        </svg>
      </div>
    </div>
  `;
  }).join('');

  // Add keyboard navigation
  list.querySelectorAll('.recommendation-item').forEach(item => {
    item.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        item.click();
      }
    });
  });
}

function getActionIcon(action) {
  const icons = {
    'DOWNSIZE_INSTANCE': '↓',
    'UPSIZE_INSTANCE': '↑',
    'DELETE_RESOURCE': '✕',
    'PURCHASE_RESERVATION': '📋',
    'SCHEDULE_SHUTDOWN': '⏰',
    'CHANGE_REGION': '🌍',
  };
  return icons[action] || '💡';
}

function showEmptyState(message) {
  document.getElementById('empty-state').classList.remove('hidden');
  document.getElementById('resource-info').classList.add('hidden');
  document.getElementById('stats-section').classList.add('hidden');
  document.getElementById('recommendations-section').classList.add('hidden');

  if (message) {
    const p = document.querySelector('.empty-state p');
    if (p) p.textContent = message;
  }
}
