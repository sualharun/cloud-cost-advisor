/**
 * Options/Settings page for Cloud Cost Optimizer extension
 */

// Load saved settings when page loads
document.addEventListener('DOMContentLoaded', loadSettings);

// Save button
document.getElementById('save').addEventListener('click', saveSettings);

// Reset button
document.getElementById('reset').addEventListener('click', resetSettings);

/**
 * Load settings from Chrome storage
 */
async function loadSettings() {
  try {
    const settings = await chrome.storage.sync.get({
      // Default values
      enabled: true,
      showOverlay: true,
      minimumSavings: 10,
      autoRefresh: 5,
      enableAzure: true,
      enableAWS: true,
      enableGCP: true,
      notifications: true,
      notificationThreshold: 100,
      apiUrl: 'http://localhost:8080',
      debugMode: false
    });

    // Populate form with saved settings
    document.getElementById('enabled').checked = settings.enabled;
    document.getElementById('showOverlay').checked = settings.showOverlay;
    document.getElementById('minimumSavings').value = settings.minimumSavings;
    document.getElementById('autoRefresh').value = settings.autoRefresh;
    document.getElementById('enableAzure').checked = settings.enableAzure;
    document.getElementById('enableAWS').checked = settings.enableAWS;
    document.getElementById('enableGCP').checked = settings.enableGCP;
    document.getElementById('notifications').checked = settings.notifications;
    document.getElementById('notificationThreshold').value = settings.notificationThreshold;
    document.getElementById('apiUrl').value = settings.apiUrl;
    document.getElementById('debugMode').checked = settings.debugMode;

    // Update backend URL display
    document.getElementById('backend-url').textContent = settings.apiUrl;

    console.log('Settings loaded:', settings);
  } catch (error) {
    console.error('Error loading settings:', error);
  }
}

/**
 * Save settings to Chrome storage
 */
async function saveSettings() {
  try {
    const settings = {
      enabled: document.getElementById('enabled').checked,
      showOverlay: document.getElementById('showOverlay').checked,
      minimumSavings: parseInt(document.getElementById('minimumSavings').value),
      autoRefresh: parseInt(document.getElementById('autoRefresh').value),
      enableAzure: document.getElementById('enableAzure').checked,
      enableAWS: document.getElementById('enableAWS').checked,
      enableGCP: document.getElementById('enableGCP').checked,
      notifications: document.getElementById('notifications').checked,
      notificationThreshold: parseInt(document.getElementById('notificationThreshold').value),
      apiUrl: document.getElementById('apiUrl').value.trim(),
      debugMode: document.getElementById('debugMode').checked
    };

    await chrome.storage.sync.set(settings);

    // Show success message
    const status = document.getElementById('status');
    status.style.display = 'block';
    setTimeout(() => {
      status.style.display = 'none';
    }, 3000);

    console.log('Settings saved:', settings);

    // Notify background worker of settings change
    chrome.runtime.sendMessage({ 
      type: 'SETTINGS_UPDATED', 
      settings 
    });

  } catch (error) {
    console.error('Error saving settings:', error);
    alert('Failed to save settings. Please try again.');
  }
}

/**
 * Reset to default settings
 */
async function resetSettings() {
  if (confirm('Are you sure you want to reset all settings to defaults?')) {
    try {
      const defaults = {
        enabled: true,
        showOverlay: true,
        minimumSavings: 10,
        autoRefresh: 5,
        enableAzure: true,
        enableAWS: true,
        enableGCP: true,
        notifications: true,
        notificationThreshold: 100,
        apiUrl: 'http://localhost:8080',
        debugMode: false
      };

      await chrome.storage.sync.set(defaults);
      
      // Reload the page to show default values
      location.reload();

      console.log('Settings reset to defaults');
    } catch (error) {
      console.error('Error resetting settings:', error);
      alert('Failed to reset settings. Please try again.');
    }
  }
}
