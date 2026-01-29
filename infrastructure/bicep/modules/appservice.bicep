// App Service Module for Spring Boot API

@description('Resource name prefix')
param prefix string

@description('Environment name')
param environment string

@description('Azure region')
param location string

@description('Resource tags')
param tags object

var appServicePlanName = '${prefix}-${environment}-plan'
var appServiceName = '${prefix}-${environment}-api'

// App Service Plan (Linux for Java)
resource appServicePlan 'Microsoft.Web/serverfarms@2023-01-01' = {
  name: appServicePlanName
  location: location
  tags: tags
  kind: 'linux'
  sku: {
    name: environment == 'prod' ? 'P2v3' : 'B2'
    tier: environment == 'prod' ? 'PremiumV3' : 'Basic'
  }
  properties: {
    reserved: true
  }
}

// App Service (Spring Boot)
resource appService 'Microsoft.Web/sites@2023-01-01' = {
  name: appServiceName
  location: location
  tags: tags
  identity: {
    type: 'SystemAssigned'
  }
  properties: {
    serverFarmId: appServicePlan.id
    httpsOnly: true
    siteConfig: {
      linuxFxVersion: 'JAVA|17-java17'
      alwaysOn: environment == 'prod'
      minTlsVersion: '1.2'
      http20Enabled: true
      healthCheckPath: '/actuator/health'
      appSettings: [
        {
          name: 'SPRING_PROFILES_ACTIVE'
          value: environment == 'prod' ? 'production' : 'default'
        }
        {
          name: 'WEBSITES_PORT'
          value: '8080'
        }
        {
          name: 'JAVA_OPTS'
          value: '-Xms512m -Xmx1024m'
        }
      ]
    }
  }
}

// Autoscale settings for production
resource autoscale 'Microsoft.Insights/autoscalesettings@2022-10-01' = if (environment == 'prod') {
  name: '${appServiceName}-autoscale'
  location: location
  tags: tags
  properties: {
    enabled: true
    targetResourceUri: appServicePlan.id
    profiles: [
      {
        name: 'Default'
        capacity: {
          minimum: '2'
          maximum: '10'
          default: '2'
        }
        rules: [
          {
            metricTrigger: {
              metricName: 'CpuPercentage'
              metricResourceUri: appServicePlan.id
              timeGrain: 'PT1M'
              statistic: 'Average'
              timeWindow: 'PT5M'
              timeAggregation: 'Average'
              operator: 'GreaterThan'
              threshold: 70
            }
            scaleAction: {
              direction: 'Increase'
              type: 'ChangeCount'
              value: '1'
              cooldown: 'PT5M'
            }
          }
          {
            metricTrigger: {
              metricName: 'CpuPercentage'
              metricResourceUri: appServicePlan.id
              timeGrain: 'PT1M'
              statistic: 'Average'
              timeWindow: 'PT5M'
              timeAggregation: 'Average'
              operator: 'LessThan'
              threshold: 30
            }
            scaleAction: {
              direction: 'Decrease'
              type: 'ChangeCount'
              value: '1'
              cooldown: 'PT10M'
            }
          }
        ]
      }
    ]
  }
}

output appServiceUrl string = 'https://${appService.properties.defaultHostName}'
output principalId string = appService.identity.principalId
