/**
 * Savings Dashboard Component
 *
 * Displays implemented recommendations and validated savings metrics.
 */

const SavingsDashboard = {
  /**
   * Render the savings dashboard.
   * @param {Object} metrics - SavingsMetricsResponse from API
   * @param {HTMLElement} container - Container element to render into
   */
  render(metrics, container) {
    if (!container) return;

    const hasData = metrics && metrics.implementedCount > 0;

    container.innerHTML = `
      <div class="cco-savings-dashboard">
        <div class="cco-savings-header">
          <h3>Savings Tracker</h3>
          <button class="cco-btn-icon" id="cco-savings-close" aria-label="Close">×</button>
        </div>

        <div class="cco-savings-summary">
          <div class="cco-savings-card total">
            <div class="cco-savings-icon">💰</div>
            <div class="cco-savings-value">$${hasData ? metrics.totalExpectedSavings.toFixed(2) : '0.00'}</div>
            <div class="cco-savings-label">Expected Monthly Savings</div>
          </div>

          <div class="cco-savings-card validated">
            <div class="cco-savings-icon">✓</div>
            <div class="cco-savings-value">$${hasData ? metrics.totalValidatedSavings.toFixed(2) : '0.00'}</div>
            <div class="cco-savings-label">Validated Savings</div>
          </div>
        </div>

        <div class="cco-savings-stats">
          <div class="cco-stat">
            <span class="cco-stat-value">${hasData ? metrics.implementedCount : 0}</span>
            <span class="cco-stat-label">Implemented</span>
          </div>
          <div class="cco-stat">
            <span class="cco-stat-value">${hasData ? metrics.validationSuccessRate.toFixed(0) : 0}%</span>
            <span class="cco-stat-label">Success Rate</span>
          </div>
        </div>

        ${hasData ? this.renderROIMetrics(metrics) : this.renderEmptyState()}

        <div class="cco-savings-actions">
          <button class="cco-btn cco-btn-primary" id="cco-view-all-savings">
            View All Implementations
          </button>
        </div>
      </div>
    `;

    this.attachEventListeners(container);
  },

  /**
   * Render ROI metrics section.
   */
  renderROIMetrics(metrics) {
    const annualSavings = metrics.totalValidatedSavings * 12;
    const roi = metrics.totalExpectedSavings > 0 ?
      (metrics.totalValidatedSavings / metrics.totalExpectedSavings * 100) : 0;

    return `
      <div class="cco-roi-section">
        <h4>ROI Metrics</h4>
        <div class="cco-roi-grid">
          <div class="cco-roi-item">
            <span class="cco-roi-value">$${annualSavings.toFixed(2)}</span>
            <span class="cco-roi-label">Projected Annual Savings</span>
          </div>
          <div class="cco-roi-item">
            <span class="cco-roi-value">${roi.toFixed(0)}%</span>
            <span class="cco-roi-label">Actual vs Expected</span>
          </div>
        </div>

        <div class="cco-savings-progress">
          <div class="cco-progress-label">
            <span>Savings Realization</span>
            <span>${roi.toFixed(0)}%</span>
          </div>
          <div class="cco-progress-bar">
            <div class="cco-progress-fill" style="width: ${Math.min(roi, 100)}%"></div>
          </div>
        </div>
      </div>
    `;
  },

  /**
   * Render empty state.
   */
  renderEmptyState() {
    return `
      <div class="cco-savings-empty">
        <div class="cco-empty-icon">📊</div>
        <p>No implemented recommendations yet.</p>
        <p class="cco-hint">Mark recommendations as implemented to track your savings.</p>
      </div>
    `;
  },

  /**
   * Render implemented recommendations list.
   */
  renderImplementationList(implementations, container) {
    if (!implementations || implementations.length === 0) {
      container.innerHTML = '<p class="cco-no-data">No implementations found.</p>';
      return;
    }

    container.innerHTML = `
      <div class="cco-implementations-list">
        ${implementations.map(impl => `
          <div class="cco-implementation-item ${impl.validationStatus.toLowerCase()}">
            <div class="cco-impl-header">
              <span class="cco-impl-resource">${impl.resourceId.split('/').pop()}</span>
              <span class="cco-impl-status ${impl.validationStatus.toLowerCase()}">
                ${this.formatStatus(impl.validationStatus)}
              </span>
            </div>
            <div class="cco-impl-details">
              <div class="cco-impl-savings">
                <span class="cco-impl-expected">Expected: $${impl.expectedMonthlySavings.toFixed(2)}/mo</span>
                ${impl.actualMonthlySavings ? `
                  <span class="cco-impl-actual">Actual: $${impl.actualMonthlySavings.toFixed(2)}/mo</span>
                ` : ''}
              </div>
              <div class="cco-impl-date">
                Implemented: ${new Date(impl.implementedAt).toLocaleDateString()}
              </div>
            </div>
          </div>
        `).join('')}
      </div>
    `;
  },

  /**
   * Format validation status for display.
   */
  formatStatus(status) {
    const labels = {
      'PENDING': '⏳ Pending',
      'VALIDATED': '✓ Validated',
      'PARTIAL': '◐ Partial',
      'FAILED': '✗ Not Realized'
    };
    return labels[status] || status;
  },

  /**
   * Attach event listeners.
   */
  attachEventListeners(container) {
    // Close button
    const closeBtn = container.querySelector('#cco-savings-close');
    if (closeBtn) {
      closeBtn.addEventListener('click', () => {
        container.classList.remove('visible');
      });
    }

    // View all button
    const viewAllBtn = container.querySelector('#cco-view-all-savings');
    if (viewAllBtn) {
      viewAllBtn.addEventListener('click', async () => {
        try {
          const response = await chrome.runtime.sendMessage({
            type: 'GET_IMPLEMENTATIONS'
          });
          if (response && !response.error) {
            const listContainer = document.createElement('div');
            listContainer.className = 'cco-implementations-modal';
            this.renderImplementationList(response, listContainer);
            container.appendChild(listContainer);
          }
        } catch (error) {
          console.error('[CloudOptimizer] Failed to load implementations:', error);
        }
      });
    }
  }
};

// Export for use in other modules
if (typeof window !== 'undefined') {
  window.SavingsDashboard = SavingsDashboard;
}
