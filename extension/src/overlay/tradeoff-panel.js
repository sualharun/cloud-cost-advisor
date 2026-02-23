/**
 * Tradeoff Panel Component
 *
 * Displays alternative comparison with dimension scores and weight adjustment.
 */

const TradeoffPanel = {
  /**
   * Render the tradeoff comparison panel.
   * @param {Object} data - AlternativeComparisonResponse from API
   * @param {HTMLElement} container - Container element to render into
   */
  render(data, container) {
    if (!data || !container) return;

    const { currentResource, alternatives, userPreferences } = data;

    container.innerHTML = `
      <div class="cco-tradeoff-panel">
        <div class="cco-tradeoff-header">
          <h3>Alternative Options</h3>
          <button class="cco-btn-icon" id="cco-tradeoff-close" aria-label="Close">×</button>
        </div>

        <div class="cco-current-resource">
          <div class="cco-current-label">Current Configuration</div>
          <div class="cco-current-sku">${currentResource.sku}</div>
          <div class="cco-current-specs">
            ${currentResource.vcpu} vCPU / ${currentResource.memoryGb} GB
          </div>
          <div class="cco-current-cost">$${currentResource.estimatedMonthlyCost.toFixed(2)}/mo</div>
        </div>

        ${alternatives.length > 0 ? `
          <div class="cco-alternatives-list">
            ${alternatives.map((alt, index) => this.renderAlternative(alt, index)).join('')}
          </div>

          <div class="cco-weight-adjuster">
            <div class="cco-weight-header">
              <span>Adjust Priorities</span>
              <button class="cco-btn-text" id="cco-reset-weights">Reset</button>
            </div>
            ${this.renderWeightSliders(userPreferences)}
          </div>
        ` : `
          <div class="cco-no-alternatives">
            <p>No alternatives found for this configuration.</p>
            <p class="cco-hint">Try adjusting the minimum savings threshold in settings.</p>
          </div>
        `}
      </div>
    `;

    // Attach event listeners
    this.attachEventListeners(container, data);
  },

  /**
   * Render a single alternative card.
   */
  renderAlternative(alt, index) {
    const savingsClass = alt.savingsPercentage > 50 ? 'high' :
                         alt.savingsPercentage > 25 ? 'medium' : 'low';

    return `
      <div class="cco-alternative-card" data-alt-id="${alt.alternativeId}">
        <div class="cco-alt-header">
          <div class="cco-alt-rank">#${index + 1}</div>
          <div class="cco-alt-info">
            <div class="cco-alt-sku">${alt.displayName}</div>
            <div class="cco-alt-specs">${alt.vcpu} vCPU / ${alt.memoryGb} GB</div>
          </div>
          <div class="cco-alt-savings ${savingsClass}">
            <div class="cco-alt-savings-amount">-$${alt.estimatedMonthlySavings.toFixed(2)}/mo</div>
            <div class="cco-alt-savings-pct">${alt.savingsPercentage.toFixed(0)}% savings</div>
          </div>
        </div>

        <div class="cco-alt-score">
          <div class="cco-score-bar">
            <div class="cco-score-fill" style="width: ${alt.overallScore * 100}%"></div>
          </div>
          <span class="cco-score-label">${(alt.overallScore * 100).toFixed(0)}% match</span>
        </div>

        <div class="cco-tradeoffs">
          ${alt.tradeoffs.map(t => this.renderTradeoff(t)).join('')}
        </div>

        <div class="cco-alt-category">
          <span class="cco-category-badge ${alt.category}">${this.formatCategory(alt.category)}</span>
          ${alt.provider !== 'AZURE' ? `<span class="cco-provider-badge">${alt.provider}</span>` : ''}
        </div>
      </div>
    `;
  },

  /**
   * Render a tradeoff dimension bar.
   */
  renderTradeoff(tradeoff) {
    const directionIcon = tradeoff.direction === 'improvement' ? '↑' :
                          tradeoff.direction === 'degradation' ? '↓' : '→';
    const directionClass = tradeoff.direction;

    return `
      <div class="cco-tradeoff-item ${directionClass}">
        <div class="cco-tradeoff-label">
          <span class="cco-tradeoff-name">${tradeoff.displayName}</span>
          <span class="cco-tradeoff-direction">${directionIcon}</span>
        </div>
        <div class="cco-tradeoff-bar">
          <div class="cco-tradeoff-fill" style="width: ${tradeoff.score * 100}%"></div>
        </div>
        <div class="cco-tradeoff-tooltip">
          <strong>${tradeoff.explanation}</strong><br>
          Current: ${tradeoff.currentValue}<br>
          Alternative: ${tradeoff.alternativeValue}
        </div>
      </div>
    `;
  },

  /**
   * Render weight adjustment sliders.
   */
  renderWeightSliders(preferences) {
    // Default weights if not set
    const defaultWeights = {
      cost: 0.35,
      performance: 0.25,
      availability: 0.15,
      migration_effort: 0.15,
      vendor_lock_in: 0.05,
      environmental_impact: 0.05
    };

    const weights = preferences.weights || defaultWeights;
    const dimensions = [
      { name: 'cost', label: 'Cost Savings', icon: '$' },
      { name: 'performance', label: 'Performance', icon: '⚡' },
      { name: 'availability', label: 'Availability', icon: '🛡' },
      { name: 'migration_effort', label: 'Migration Ease', icon: '🔄' },
      { name: 'vendor_lock_in', label: 'Portability', icon: '🔓' },
      { name: 'environmental_impact', label: 'Sustainability', icon: '🌱' }
    ];

    return `
      <div class="cco-weight-sliders">
        ${dimensions.map(dim => `
          <div class="cco-weight-slider">
            <label>
              <span class="cco-weight-icon">${dim.icon}</span>
              <span class="cco-weight-name">${dim.label}</span>
            </label>
            <input type="range" min="0" max="100" value="${(weights[dim.name] || 0) * 100}"
                   data-dimension="${dim.name}" class="cco-slider">
            <span class="cco-weight-value">${Math.round((weights[dim.name] || 0) * 100)}%</span>
          </div>
        `).join('')}
      </div>
    `;
  },

  /**
   * Format category for display.
   */
  formatCategory(category) {
    const labels = {
      'downsize': 'Downsize',
      'upsize': 'Upsize',
      'different_family': 'Different Family',
      'cross_cloud': 'Multi-Cloud'
    };
    return labels[category] || category;
  },

  /**
   * Attach event listeners to the panel.
   */
  attachEventListeners(container, data) {
    // Close button
    const closeBtn = container.querySelector('#cco-tradeoff-close');
    if (closeBtn) {
      closeBtn.addEventListener('click', () => {
        container.classList.remove('visible');
      });
    }

    // Reset weights button
    const resetBtn = container.querySelector('#cco-reset-weights');
    if (resetBtn) {
      resetBtn.addEventListener('click', () => {
        const sliders = container.querySelectorAll('.cco-slider');
        const defaults = [35, 25, 15, 15, 5, 5];
        sliders.forEach((slider, i) => {
          slider.value = defaults[i];
          const valueSpan = slider.parentElement.querySelector('.cco-weight-value');
          if (valueSpan) valueSpan.textContent = `${defaults[i]}%`;
        });
        this.updateWeights(container);
      });
    }

    // Weight sliders
    const sliders = container.querySelectorAll('.cco-slider');
    sliders.forEach(slider => {
      slider.addEventListener('input', (e) => {
        const valueSpan = e.target.parentElement.querySelector('.cco-weight-value');
        if (valueSpan) valueSpan.textContent = `${e.target.value}%`;
      });

      slider.addEventListener('change', () => {
        this.updateWeights(container);
      });
    });
  },

  /**
   * Update weights and refresh alternatives.
   */
  async updateWeights(container) {
    const sliders = container.querySelectorAll('.cco-slider');
    const weights = {};

    sliders.forEach(slider => {
      weights[slider.dataset.dimension] = parseInt(slider.value) / 100;
    });

    // Normalize weights to sum to 1
    const total = Object.values(weights).reduce((a, b) => a + b, 0);
    for (const key in weights) {
      weights[key] = weights[key] / total;
    }

    // Send update to backend
    try {
      await chrome.runtime.sendMessage({
        type: 'UPDATE_PREFERENCES',
        payload: { weights }
      });

      // Refresh alternatives with new weights
      const refreshEvent = new CustomEvent('cco-refresh-alternatives');
      document.dispatchEvent(refreshEvent);
    } catch (error) {
      console.error('[CloudOptimizer] Failed to update weights:', error);
    }
  }
};

// Export for use in other modules
if (typeof window !== 'undefined') {
  window.TradeoffPanel = TradeoffPanel;
}
