/**
 * Background Service Worker for Cloud Cost Optimizer Extension
 *
 * RESPONSIBILITIES:
 * 1. Handle authentication with Azure AD
 * 2. Proxy API requests to backend
 * 3. Manage token refresh
 * 4. Cache responses for offline support
 * 5. Badge updates for savings opportunities
 */

// Configuration
const CONFIG = {
  apiBaseUrl: 'http://localhost:8080',
  // For production: 'https://cloud-optimizer-api.azurewebsites.net'
  tokenRefreshInterval: 50 * 60 * 1000, // 50 minutes
  cacheExpiration: 5 * 60 * 1000, // 5 minutes
};

// State
let authToken = null;
let tokenExpiry = null;

// Initialize on install
chrome.runtime.onInstalled.addListener(async (details) => {
  console.log('Cloud Cost Optimizer installed:', details.reason);

  // Set default settings
  await chrome.storage.sync.set({
    enabled: true,
    showOverlay: true,
    minimumSavingsToShow: 10, // $10 minimum
    autoRefreshInterval: 5, // minutes
  });
});

// Message handling from content scripts
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  handleMessage(message, sender).then(sendResponse);
  return true; // Keep channel open for async response
});

async function handleMessage(message, sender) {
  console.log('Background received message:', message.type);

  switch (message.type) {
    case 'ANALYZE_RESOURCE':
      return analyzeResource(message.payload);

    case 'ANALYZE_BATCH':
      return analyzeBatch(message.payload);

    case 'GET_AUTH_STATUS':
      // In local dev mode, always return authenticated
      const isLocalDev = CONFIG.apiBaseUrl.includes('localhost');
      return { authenticated: isLocalDev || !!authToken, expiry: tokenExpiry };

    case 'AUTHENTICATE':
      return authenticate();

    case 'LOGOUT':
      return logout();

    case 'DISMISS_RECOMMENDATION':
      return dismissRecommendation(message.payload);

    case 'GET_SETTINGS':
      return chrome.storage.sync.get(null);

    case 'UPDATE_SETTINGS':
      return chrome.storage.sync.set(message.payload);

    default:
      console.warn('Unknown message type:', message.type);
      return { error: 'Unknown message type' };
  }
}

/**
 * Analyze a single resource via backend API.
 */
async function analyzeResource(payload) {
  const { provider, resourceId, resourceType, region, detectedConfig } = payload;

  try {
    // Check cache first
    const cacheKey = `analysis:${provider}:${resourceId}`;
    const cached = await getCachedResponse(cacheKey);
    if (cached) {
      console.log('Returning cached analysis for:', resourceId);
      return cached;
    }

    // For local development, skip authentication
    const isLocalDev = CONFIG.apiBaseUrl.includes('localhost');

    // Call backend API
    const headers = {
      'Content-Type': 'application/json',
    };
    
    // Only add auth header if not local dev and token exists
    if (!isLocalDev && authToken) {
      headers['Authorization'] = `Bearer ${authToken}`;
    }

    console.log('Calling backend API:', CONFIG.apiBaseUrl + '/api/extension/analyze');
    
    const response = await fetch(`${CONFIG.apiBaseUrl}/api/extension/analyze`, {
      method: 'POST',
      headers: headers,
      body: JSON.stringify({
        provider,
        resourceId,
        resourceType,
        region,
        detectedConfig,
      }),
    });

    if (response.status === 401 && !isLocalDev) {
      authToken = null;
      return { error: 'Authentication expired', requiresAuth: true };
    }

    if (!response.ok) {
      const errorText = await response.text();
      console.error('API error response:', errorText);
      throw new Error(`API error: ${response.status} - ${errorText}`);
    }

    const result = await response.json();
    console.log('Analysis result:', result);

    // Cache successful response
    await cacheResponse(cacheKey, result);

    // Update badge if there are significant savings
    updateBadge(result);

    return result;

  } catch (error) {
    console.error('Analysis failed:', error);
    // Return mock data for local testing if API fails
    if (CONFIG.apiBaseUrl.includes('localhost')) {
      console.log('Returning mock data for local testing');
      return getMockAnalysis(payload);
    }
    return { error: error.message };
  }
}

/**
 * Analyze multiple resources in batch.
 */
async function analyzeBatch(payload) {
  const { provider, resources } = payload;

  try {
    if (!authToken) {
      return { error: 'Not authenticated', requiresAuth: true };
    }

    const response = await fetch(`${CONFIG.apiBaseUrl}/api/extension/analyze/batch`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${authToken}`,
      },
      body: JSON.stringify({ provider, resources }),
    });

    if (!response.ok) {
      throw new Error(`API error: ${response.status}`);
    }

    return await response.json();

  } catch (error) {
    console.error('Batch analysis failed:', error);
    return { error: error.message };
  }
}

/**
 * Authenticate with Azure AD.
 * Uses Chrome identity API for OAuth flow.
 */
async function authenticate() {
  try {
    // In production, this would use Azure AD OAuth
    // For now, we'll use a simplified flow

    // Option 1: Use chrome.identity.launchWebAuthFlow for Azure AD
    // const authUrl = buildAzureAdAuthUrl();
    // const redirectUrl = await chrome.identity.launchWebAuthFlow({
    //   url: authUrl,
    //   interactive: true,
    // });
    // authToken = extractTokenFromRedirect(redirectUrl);

    // Development placeholder - in production, implement full Azure AD flow
    console.log('Authentication requested - implement Azure AD flow');

    // Store token info
    await chrome.storage.local.set({ authToken, tokenExpiry });

    return { success: true };

  } catch (error) {
    console.error('Authentication failed:', error);
    return { error: error.message };
  }
}

/**
 * Clear authentication state.
 */
async function logout() {
  authToken = null;
  tokenExpiry = null;
  await chrome.storage.local.remove(['authToken', 'tokenExpiry']);
  return { success: true };
}

/**
 * Dismiss a recommendation.
 */
async function dismissRecommendation(payload) {
  const { recommendationId, reason } = payload;

  try {
    if (!authToken) {
      return { error: 'Not authenticated' };
    }

    const response = await fetch(
      `${CONFIG.apiBaseUrl}/api/extension/recommendations/${recommendationId}/dismiss`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${authToken}`,
        },
        body: JSON.stringify({ reason }),
      }
    );

    return { success: response.ok };

  } catch (error) {
    console.error('Dismiss failed:', error);
    return { error: error.message };
  }
}

/**
 * Cache management functions.
 */
async function getCachedResponse(key) {
  const result = await chrome.storage.local.get(key);
  const cached = result[key];

  if (cached && Date.now() - cached.timestamp < CONFIG.cacheExpiration) {
    return cached.data;
  }

  return null;
}

async function cacheResponse(key, data) {
  await chrome.storage.local.set({
    [key]: {
      data,
      timestamp: Date.now(),
    },
  });
}

/**
 * Update extension badge with savings indicator.
 */
function updateBadge(analysisResult) {
  if (!analysisResult.recommendations) return;

  const totalSavings = analysisResult.recommendations.reduce(
    (sum, r) => sum + (r.estimatedSavings || 0),
    0
  );

  if (totalSavings >= 100) {
    chrome.action.setBadgeText({ text: '$$$' });
    chrome.action.setBadgeBackgroundColor({ color: '#FF6B6B' });
  } else if (totalSavings >= 50) {
    chrome.action.setBadgeText({ text: '$$' });
    chrome.action.setBadgeBackgroundColor({ color: '#FFE66D' });
  } else if (totalSavings >= 10) {
    chrome.action.setBadgeText({ text: '$' });
    chrome.action.setBadgeBackgroundColor({ color: '#4ECDC4' });
  } else {
    chrome.action.setBadgeText({ text: '' });
  }
}

/**
 * Restore auth state on startup.
 */
chrome.storage.local.get(['authToken', 'tokenExpiry'], (result) => {
  if (result.authToken && result.tokenExpiry > Date.now()) {
    authToken = result.authToken;
    tokenExpiry = result.tokenExpiry;
    console.log('Restored auth state');
  }
});

/**
 * Get mock analysis data for local testing
 */
function getMockAnalysis(payload) {
  const { resourceId, resourceType, detectedConfig } = payload;
  
  // Determine recommendation based on resource ID
  const isIdle = resourceId.includes('idle');
  const isUnderutilized = resourceId.includes('underutilized');
  const isOptimized = resourceId.includes('optimized');
  
  if (isOptimized) {
    return {
      resourceId,
      resourceName: resourceId,
      monthlyCostForecast: 75.00,
      confidence: 0.95,
      utilizationStatus: 'OPTIMIZED',
      severityLevel: 'info',
      avgCpuUtilization: 0.65,
      recommendations: []
    };
  }
  
  if (isIdle) {
    return {
      resourceId,
      resourceName: resourceId,
      monthlyCostForecast: 300.00,
      confidence: 0.92,
      utilizationStatus: 'IDLE',
      severityLevel: 'critical',
      avgCpuUtilization: 0.02,
      recommendations: [
        {
          action: 'DELETE_RESOURCE',
          actionDisplayName: 'Delete resource',
          summary: 'Resource has been idle for 7+ days',
          suggestedConfig: null,
          estimatedSavings: 300.00,
          confidence: 0.92,
          riskLevel: 'LOW'
        }
      ]
    };
  }
  
  // Default: underutilized
  return {
    resourceId,
    resourceName: resourceId,
    monthlyCostForecast: 143.22,
    confidence: 0.91,
    utilizationStatus: 'UNDERUTILIZED',
    severityLevel: 'warning',
    avgCpuUtilization: 0.15,
    recommendations: [
      {
        action: 'DOWNSIZE_INSTANCE',
        actionDisplayName: 'Downsize instance',
        summary: `Resource underutilized at 15% CPU`,
        suggestedConfig: detectedConfig ? `${Math.floor(detectedConfig.vcpu / 2)} vCPU / ${Math.floor(detectedConfig.memoryGb / 2)} GB` : '2 vCPU / 8 GB',
        estimatedSavings: 62.40,
        confidence: 0.91,
        riskLevel: 'MEDIUM'
      }
    ]
  };
}

/**
 * Handle PING messages for health checks
 */
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message.type === 'PING') {
    sendResponse({ success: true, timestamp: Date.now() });
    return true;
  }
});

/**
 * Token refresh alarm.
 */
chrome.alarms.create('tokenRefresh', { periodInMinutes: 50 });
chrome.alarms.onAlarm.addListener((alarm) => {
  if (alarm.name === 'tokenRefresh' && authToken) {
    console.log('Refreshing auth token...');
    // Implement token refresh logic
  }
});

console.log('Cloud Cost Optimizer service worker initialized');
