/**
 * Simulation Panel Component
 *
 * Interactive cost simulation with SKU selector and real-time calculations.
 */

const SimulationPanel = {
  currentSku: null,
  currentRegion: null,
  availableSkus: [],

  /**
   * Render the simulation panel.
   * @param {Object} options - Configuration options
   * @param {HTMLElement} container - Container element to render into
   */
  render(options, container) {
    if (!container) return;

    this.currentSku = options.currentSku;
    this.currentRegion = options.region || 'eastus';
    this.availableSkus = options.availableSkus || [];
    this.provider = options.provider || 'AZURE';
    this.resourceId = options.resourceId;

    container.innerHTML = `
      <div class="cco-simulation-panel">
        <div class="cco-simulation-header">
          <h3>Cost Simulator</h3>
          <button class="cco-btn-icon" id="cco-simulation-close" aria-label="Close">×</button>
        </div>

        <div class="cco-simulation-current">
          <div class="cco-sim-label">Current Configuration</div>
          <div class="cco-sim-current-sku">${this.currentSku}</div>
        </div>

        <div class="cco-simulation-form">
          <div class="cco-sim-section">
            <label class="cco-sim-label">Simulation Type</label>
            <div class="cco-sim-tabs">
              <button class="cco-sim-tab active" data-type="sku">SKU Change</button>
              <button class="cco-sim-tab" data-type="reservation">Reserved Instance</button>
              <button class="cco-sim-tab" data-type="spot">Spot Instance</button>
              <button class="cco-sim-tab" data-type="scheduling">Scheduling</button>
            </div>
          </div>

          <div class="cco-sim-content" id="cco-sim-content">
            ${this.renderSkuChangeForm()}
          </div>
        </div>

        <div class="cco-simulation-result" id="cco-sim-result">
          <div class="cco-sim-result-placeholder">
            <p>Select options and click "Calculate" to see estimated savings.</p>
          </div>
        </div>
      </div>
    `;

    this.attachEventListeners(container);
  },

  /**
   * Render SKU change form.
   */
  renderSkuChangeForm() {
    const skuOptions = this.getSkuOptions();

    return `
      <div class="cco-sim-form-content" data-form="sku">
        <div class="cco-form-group">
          <label>Target SKU</label>
          <select class="cco-select" id="cco-target-sku">
            <option value="">Select a SKU...</option>
            ${skuOptions}
          </select>
        </div>
        <button class="cco-btn cco-btn-primary cco-sim-calculate" data-type="sku">
          Calculate Savings
        </button>
      </div>
    `;
  },

  /**
   * Render reservation form.
   */
  renderReservationForm() {
    return `
      <div class="cco-sim-form-content" data-form="reservation">
        <div class="cco-form-group">
          <label>Commitment Term</label>
          <div class="cco-radio-group">
            <label class="cco-radio">
              <input type="radio" name="ri-term" value="1" checked>
              <span>1 Year (~30% savings)</span>
            </label>
            <label class="cco-radio">
              <input type="radio" name="ri-term" value="3">
              <span>3 Year (~60% savings)</span>
            </label>
          </div>
        </div>
        <div class="cco-form-note">
          <p>Reserved Instances require upfront commitment but offer significant savings for stable workloads.</p>
        </div>
        <button class="cco-btn cco-btn-primary cco-sim-calculate" data-type="reservation">
          Calculate Savings
        </button>
      </div>
    `;
  },

  /**
   * Render spot instance form.
   */
  renderSpotForm() {
    return `
      <div class="cco-sim-form-content" data-form="spot">
        <div class="cco-form-note warning">
          <p><strong>Note:</strong> Spot instances can be interrupted with short notice. Best for fault-tolerant or stateless workloads.</p>
        </div>
        <div class="cco-spot-savings">
          <span class="cco-spot-icon">⚡</span>
          <span>Typical savings: <strong>60-90%</strong></span>
        </div>
        <button class="cco-btn cco-btn-primary cco-sim-calculate" data-type="spot">
          Calculate Savings
        </button>
      </div>
    `;
  },

  /**
   * Render scheduling form.
   */
  renderSchedulingForm() {
    return `
      <div class="cco-sim-form-content" data-form="scheduling">
        <div class="cco-form-group">
          <label>Schedule Type</label>
          <div class="cco-radio-group">
            <label class="cco-radio">
              <input type="radio" name="schedule" value="business_hours" checked>
              <span>Business Hours (9 AM - 7 PM, Mon-Fri)</span>
            </label>
            <label class="cco-radio">
              <input type="radio" name="schedule" value="dev_hours">
              <span>Dev Hours (8 AM - 8 PM, Mon-Fri)</span>
            </label>
            <label class="cco-radio">
              <input type="radio" name="schedule" value="weekdays_only">
              <span>Weekdays Only (24/7, Mon-Fri)</span>
            </label>
          </div>
        </div>
        <div class="cco-form-note">
          <p>Automatically stop resources during off-hours to save costs on non-production workloads.</p>
        </div>
        <button class="cco-btn cco-btn-primary cco-sim-calculate" data-type="scheduling">
          Calculate Savings
        </button>
      </div>
    `;
  },

  /**
   * Get SKU options HTML.
   */
  getSkuOptions() {
    const defaultSkus = [
      { sku: 'Standard_D2s_v3', name: 'D2s v3 (2 vCPU, 8 GB)' },
      { sku: 'Standard_B2s', name: 'B2s (2 vCPU, 4 GB, Burstable)' },
      { sku: 'Standard_B1s', name: 'B1s (1 vCPU, 1 GB, Burstable)' }
    ];

    const skus = this.availableSkus.length > 0 ? this.availableSkus : defaultSkus;

    return skus.map(s =>
      `<option value="${s.sku}">${s.name || s.sku}</option>`
    ).join('');
  },

  /**
   * Render simulation result.
   */
  renderResult(result) {
    const resultEl = document.getElementById('cco-sim-result');
    if (!resultEl) return;

    if (!result || result.error) {
      resultEl.innerHTML = `
        <div class="cco-sim-error">
          <span class="cco-error-icon">!</span>
          <span>${result?.error || 'Unable to calculate savings'}</span>
        </div>
      `;
      return;
    }

    const savingsClass = result.savingsPercentage > 50 ? 'high' :
                         result.savingsPercentage > 25 ? 'medium' : 'low';

    resultEl.innerHTML = `
      <div class="cco-sim-result-content">
        <div class="cco-sim-comparison">
          <div class="cco-sim-before">
            <span class="cco-sim-cost-label">Current</span>
            <span class="cco-sim-cost-value">$${result.currentMonthlyCost.toFixed(2)}/mo</span>
          </div>
          <div class="cco-sim-arrow">→</div>
          <div class="cco-sim-after">
            <span class="cco-sim-cost-label">Projected</span>
            <span class="cco-sim-cost-value">$${result.projectedMonthlyCost.toFixed(2)}/mo</span>
          </div>
        </div>

        <div class="cco-sim-savings ${savingsClass}">
          <div class="cco-sim-savings-amount">
            <span class="cco-sim-savings-icon">💰</span>
            <span>Save $${result.savingsAmount.toFixed(2)}/mo</span>
          </div>
          <div class="cco-sim-savings-pct">
            ${result.savingsPercentage.toFixed(0)}% reduction
          </div>
        </div>

        <div class="cco-sim-confidence">
          <span class="cco-confidence-label">Confidence:</span>
          <span class="cco-confidence-value">${Math.round(result.confidence * 100)}%</span>
        </div>
      </div>
    `;
  },

  /**
   * Attach event listeners.
   */
  attachEventListeners(container) {
    // Close button
    const closeBtn = container.querySelector('#cco-simulation-close');
    if (closeBtn) {
      closeBtn.addEventListener('click', () => {
        container.classList.remove('visible');
      });
    }

    // Tab switching
    const tabs = container.querySelectorAll('.cco-sim-tab');
    tabs.forEach(tab => {
      tab.addEventListener('click', (e) => {
        tabs.forEach(t => t.classList.remove('active'));
        e.target.classList.add('active');

        const type = e.target.dataset.type;
        const content = container.querySelector('#cco-sim-content');

        switch (type) {
          case 'sku':
            content.innerHTML = this.renderSkuChangeForm();
            break;
          case 'reservation':
            content.innerHTML = this.renderReservationForm();
            break;
          case 'spot':
            content.innerHTML = this.renderSpotForm();
            break;
          case 'scheduling':
            content.innerHTML = this.renderSchedulingForm();
            break;
        }

        // Re-attach calculate button listeners
        this.attachCalculateListeners(container);
      });
    });

    // Calculate button listeners
    this.attachCalculateListeners(container);
  },

  /**
   * Attach calculate button listeners.
   */
  attachCalculateListeners(container) {
    const calculateBtns = container.querySelectorAll('.cco-sim-calculate');
    calculateBtns.forEach(btn => {
      btn.addEventListener('click', async (e) => {
        const type = e.target.dataset.type;
        await this.runSimulation(type, container);
      });
    });
  },

  /**
   * Run simulation.
   */
  async runSimulation(type, container) {
    const payload = {
      resourceId: this.resourceId,
      provider: this.provider,
      sku: this.currentSku,
      region: this.currentRegion
    };

    switch (type) {
      case 'sku':
        const targetSku = container.querySelector('#cco-target-sku')?.value;
        if (!targetSku) {
          this.renderResult({ error: 'Please select a target SKU' });
          return;
        }
        payload.proposedConfig = { sku: targetSku, currentSku: this.currentSku };
        break;

      case 'reservation':
        const term = container.querySelector('input[name="ri-term"]:checked')?.value || '1';
        payload.term = term;
        break;

      case 'scheduling':
        const schedule = container.querySelector('input[name="schedule"]:checked')?.value || 'dev_hours';
        payload.schedule = schedule;
        break;
    }

    try {
      const response = await chrome.runtime.sendMessage({
        type: `SIMULATE_${type.toUpperCase()}`,
        payload
      });

      this.renderResult(response);
    } catch (error) {
      console.error('[CloudOptimizer] Simulation error:', error);
      this.renderResult({ error: error.message });
    }
  }
};

// Export for use in other modules
if (typeof window !== 'undefined') {
  window.SimulationPanel = SimulationPanel;
}
