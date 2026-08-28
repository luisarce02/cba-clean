// CBA Clean — Azure Foundation Phase 1 (Students)
// Resource Group scoped deployment; validates via `az bicep build --file infra/main.bicep`.
// Phase 1 creates only: Log Analytics, Key Vault, ACR, Container Apps Environment, PostgreSQL + DB.
// No secrets are hardcoded. See docs/azure-deployment.md §3 for secret strategy.
// Deploy to `centralus` (policy allows brazilsouth/centralus/westus/chilecentral/mexicocentral; eastus2 is blocked).

targetScope = 'resourceGroup'

@description('Environment suffix - dev/prod. Used in resource naming.')
param env string = 'prod'

@description('Azure region for Phase 1 resources. Must be one of the policy-allowed regions for Azure for Students. Default is resourceGroup().location but override to centralus when RG is eastus2.')
param location string = resourceGroup().location

@description('Base name prefix; resource names derived via uniqueString.')
param baseName string = 'cbaclean'

@description('PostgreSQL administrator password. Must satisfy Azure Flexible Server rules: 8-128 chars, include 3 of 4 categories (upper, lower, digit, non-alphanumeric). No default - never committed.')
@minLength(12)
@maxLength(128)
@secure()
param postgresAdminPassword string

@description('Public frontend origin for CORS / Keycloak redirectUris (https://...). Updated after frontend Container App gets its FQDN in Phase 2.')
param frontendOrigin string = 'https://frontend.example.com'

@description('Keycloak issuer base URL (https://<keycloak>/realms/cba-clean). Updated after Keycloak is deployed in Phase 2.')
param keycloakIssuerUri string = 'https://keycloak.example.com/realms/cba-clean'

// Naming: respect Azure limits (KV 3-24, ACR 5-50 alphanumeric lower, no hyphens for ACR).
// uniqueShort = 6 chars gives global uniqueness per RG while staying deterministic.
// KV: kv-cbaclean-prod-xxxxxx = 3+8+1+4+1+6 = 23 <=24, starts with letter, ends alphanumeric, globally unique.
// ACR: acrcbacleanprodxxxxxxxx = 3+8+4+8 = 23 <=50, alphanumeric lower only.
// PG server: 3-63, hyphens allowed; use longer unique for stability.
var unique = uniqueString(resourceGroup().id)
var uniqueShort = take(unique, 6)
var uniqueAcr = take(unique, 8)
var acrName = toLower('acr${baseName}${env}${uniqueAcr}') // 5-50, alphanumeric lower
var kvName = 'kv-${baseName}-${env}-${uniqueShort}' // 3-24
var laName = 'la-${baseName}-${env}'
var caeName = 'cae-${baseName}-${env}'
var pgServerName = 'pgsql-${baseName}-${env}-${uniqueShort}'
var pgDbName = 'cbaclean'

// Derive safe names (trim to Azure limits)
var acrLoginServer = '${acrName}.azurecr.io'

// Log Analytics for Container Apps
resource logAnalytics 'Microsoft.OperationalInsights/workspaces@2022-10-01' = {
  name: laName
  location: location
  properties: {
    sku: { name: 'PerGB2018' }
    retentionInDays: 30
  }
}

// Key Vault (RBAC) - secrets populated outside Bicep (Step 2)
resource keyVault 'Microsoft.KeyVault/vaults@2023-07-01' = {
  name: kvName
  location: location
  properties: {
    sku: { family: 'A', name: 'standard' }
    tenantId: subscription().tenantId
    enableRbacAuthorization: true
    enabledForTemplateDeployment: true
    softDeleteRetentionInDays: 7
  }
}

// ACR - GitHub OIDC pushes via federated credential; ACA pulls via managed identity
resource acr 'Microsoft.ContainerRegistry/registries@2023-07-01' = {
  name: acrName
  location: location
  sku: { name: 'Basic' }
  properties: {
    adminUserEnabled: false
    publicNetworkAccess: 'Enabled'
    policies: { quarantinePolicy: { status: 'disabled' } }
  }
}

// Container Apps Environment (shared, consumes LA workspace)
resource cae 'Microsoft.App/managedEnvironments@2023-11-02-preview' = {
  name: caeName
  location: location
  properties: {
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: logAnalytics.properties.customerId
        sharedKey: logAnalytics.listKeys().primarySharedKey
      }
    }
    zoneRedundant: false
  }
}

// PostgreSQL Flexible Server (burstable B1ms for portfolio — cheapest viable SKU for Students)
// Networking decision (Phase 1): publicNetworkAccess Enabled with no 0.0.0.0/0 firewall rule.
// Rationale: ACA Consumption without VNet integration cannot reach a Fully Disabled server. VNet/private endpoint would require
// ACA Environment VNet + subnet + NAT Gateway — unnecessary cost/complexity for portfolio demo. Public + firewall lets
// report-service (ACA) connect via FQDN + TLS (sslmode=require) while keeping cost minimal. See docs/azure-deployment.md §4.
// Security: strong 12+ char password (minLength), TLS enforced by JDBC, firewall tightened in Phase 2 after ACA outbound IPs are known
// (add firewallRules child `AllowAcaOutbound` with ACA egress ranges, or switch to VNet if budget allows). NOT opened to 0.0.0.0/0 in this commit.
resource pg 'Microsoft.DBforPostgreSQL/flexibleServers@2023-12-01-preview' = {
  name: pgServerName
  location: location
  sku: { name: 'Standard_B1ms', tier: 'Burstable' }
  properties: {
    version: '17'
    administratorLogin: 'cbaclean'
    administratorLoginPassword: postgresAdminPassword
    storage: { storageSizeGB: 32, autoGrow: 'Enabled' }
    backup: { backupRetentionDays: 7, geoRedundantBackup: 'Disabled' }
    highAvailability: { mode: 'Disabled' }
    network: { publicNetworkAccess: 'Enabled' }
  }
}

resource pgDb 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2023-12-01-preview' = {
  parent: pg
  name: pgDbName
  properties: { charset: 'UTF8', collation: 'en_US.utf8' }
}

// --- Outputs for GitHub Actions / next steps ---
output acrLoginServer string = acrLoginServer
output keyVaultName string = keyVault.name
output containerAppsEnvId string = cae.id
output postgresFqdn string = pg.properties.fullyQualifiedDomainName
output postgresDbName string = pgDbName
// Frontend Keycloak issuer / CORS derived from ACA ingress after deploy; provided here for docs
output frontendOrigin string = frontendOrigin
output keycloakIssuerUri string = keycloakIssuerUri
