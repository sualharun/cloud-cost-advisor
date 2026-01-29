/**
 * Cloud Cost Optimizer - Popup Script
 *
 * Handles popup UI interactions and displays current resource status.
 */

document.addEventListener('DOMContentLoaded', init);

async function init() {
  console.log('Popup initializing...');
  
  try {
    // Check authentication status
    const authStatus = await chrome.runtime.sendMessage({ type: 'GET_AUTH_STATUS' });

    if (!authStatus || !authStatus.authenticated) {
      showAuthSection();
    } else {
      showStatusSection();
      await loadCurrentTabStatus();
    }

    // Set up event listeners
    document.getElementById('sign-in-btn')?.addEventListener('click', handleSignIn);
    document.getElementById('sign-out-btn')?.addEventListener('click', handleSignOut);
    document.getElementById('settings-btn')?.addEventListener('click', openSettings);
  } catch (error) {
    console.error('Popup initialization error:', error);
    // Show status section with error message for local dev
    showStatusSection();
    showEmptyState('Extension loaded. Navigate to a cloud resource to see insights.');
  }
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
  const result = await chrome.runtime.sendMessage({ type: 'AUTHENTICATE' });
  if (result.success) {
    showStatusSection();
    await loadCurrentTabStatus();
  } else {
    alert('Sign in failed: ' + (result.error || 'Unknown error'));
  }
}

async function handleSignOut() {
  await chrome.runtime.sendMessage({ type: 'LOGOUT' });
  showAuthSection();
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
      showRecommendations(resource.analysis.recommendations);
    }
  }
}

function showRecommendations(recommendations) {
  const section = document.getElementById('recommendations-section');
  const list = document.getElementById('recommendations-list');
  const count = document.getElementById('rec-count');

  section.classList.remove('hidden');
  count.textContent = recommendations.length;

  list.innerHTML = recommendations.map(rec => `
    <div class="recommendation-item" data-action="${rec.action}">
      <div class="rec-icon ${rec.riskLevel === 'HIGH' ? 'warning' : 'success'}">
        ${getActionIcon(rec.action)}
      </div>
      <div class="rec-content">
        <div class="rec-title">${rec.actionDisplayName}</div>
        <div class="rec-savings">Save $${rec.estimatedSavings.toFixed(2)}/mo</div>
      </div>
    </div>
  `).join('');
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
    document.querySelector('.empty-state p').textContent = message;
  }
}

// Listen for updates from content scripts
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message.type === 'RESOURCE_UPDATED') {
    loadCurrentTabStatus();
  }
  return true;
});
