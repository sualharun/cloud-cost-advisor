// Multi-Cloud Cost Optimization Advisor - Azure Infrastructure
// Deploy with: az deployment sub create --location eastus --template-file main.bicep

targetScope = 'subscription'

@description('Environment name (dev, staging, prod)')
param environment string = 'dev'

@description('Azure region for resources')
param location string = 'eastus'

@description('Resource name prefix')
param prefix string = 'cloudopt'

var resourceGroupName = '${prefix}-${environment}-rg'
var tags = {
  Environment: environment
  Application: 'CloudCostOptimizer'
  ManagedBy: 'Bicep'
}

// Resource Group
resource rg 'Microsoft.Resources/resourceGroups@2023-07-01' = {
  name: resourceGroupName
  location: location
  tags: tags
}

// Deploy infrastructure modules
module appService 'modules/appservice.bicep' = {
  scope: rg
  name: 'appServiceDeployment'
  params: {
    prefix: prefix
    environment: environment
    location: location
    tags: tags
  }
}

module sql 'modules/sql.bicep' = {
  scope: rg
  name: 'sqlDeployment'
  params: {
    prefix: prefix
    environment: environment
    location: location
    tags: tags
  }
}

module keyVault 'modules/keyvault.bicep' = {
  scope: rg
  name: 'keyVaultDeployment'
  params: {
    prefix: prefix
    environment: environment
    location: location
    tags: tags
    appServicePrincipalId: appService.outputs.principalId
  }
}

module storage 'modules/storage.bicep' = {
  scope: rg
  name: 'storageDeployment'
  params: {
    prefix: prefix
    environment: environment
    location: location
    tags: tags
  }
}

module monitoring 'modules/monitoring.bicep' = {
  scope: rg
  name: 'monitoringDeployment'
  params: {
    prefix: prefix
    environment: environment
    location: location
    tags: tags
  }
}

// Outputs
output resourceGroupName string = rg.name
output appServiceUrl string = appService.outputs.appServiceUrl
output keyVaultUri string = keyVault.outputs.keyVaultUri
