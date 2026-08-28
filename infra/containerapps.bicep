// CBA Clean — Azure Foundation Phase 2: Container Apps
// Deploys the four application Container Apps (Keycloak, report-service,
// incident-service, web/Nginx) into the Phase 1 environment created by
// main.bicep. Resource names for the Phase 1 resources (ACR, Key Vault,
// Container Apps Environment, PostgreSQL) are recomputed deterministically
// from the same `env`/`baseName` inputs via `uniqueString(resourceGroup().id)`,
// so this module only needs to reference them by name — no manual copy/paste
// of Phase 1 outputs required.
//
// No secrets are hardcoded. Every ACA `secrets` entry is a Key Vault reference
// resolved at runtime via the shared user-assigned managed identity; the
// underlying Key Vault secrets (postgresAdminPassword, keycloak-admin-password,
// rabbitmq-password, mongo-uri) are seeded out-of-band via
// `az keyvault secret set` (see docs/azure-deployment.md §3).
//
// Validate: az bicep build --file infra/containerapps.bicep
// Deploy:   az deployment group create --resource-group cba-clean-rg \
//             --template-file infra/containerapps.bicep \
//             --parameters @infra/containerapps.parameters.azure.json

targetScope = 'resourceGroup'

@description('Environment suffix - dev/prod. Must match the value used for main.bicep.')
param env string = 'prod'

@description('Azure region. Must match the value used for main.bicep (Phase 1 resources are looked up by name).')
param location string = resourceGroup().location

@description('Base name prefix; must match the value used for main.bicep so Phase 1 resource names resolve.')
param baseName string = 'cbaclean'

@description('Public frontend origin (https://<web-app>.<domain>). Used for CORS_ALLOWED_ORIGINS and Keycloak redirect/web origins.')
param frontendOrigin string

@description('Keycloak issuer base URL (https://<keycloak-app>.<domain>/realms/cba-clean).')
param keycloakIssuerUri string

@description('OAuth2 audience expected by report-service/incident-service.')
param jwtAudience string = 'cba-clean-web'

@description('Container image reference for the web (Nginx/Angular) app.')
param webImage string

@description('Container image reference for report-service.')
param reportServiceImage string

@description('Container image reference for incident-service.')
param incidentServiceImage string

@description('Container image reference for the Keycloak app (built from keycloak/Dockerfile with the realm baked in).')
param keycloakImage string

@description('Minimum replicas for report-service and incident-service. Azure Container Apps Envoy ingress times out around 30s; cold start (~35s) causes 504s at minReplicas=0, so this must stay >= 1.')
@minValue(1)
param backendMinReplicas int = 1

@description('Maximum replicas for every app (portfolio/demo scale).')
@minValue(1)
param maxReplicas int = 1

@description('RabbitMQ (CloudAMQP) hostname. Non-secret.')
param rabbitmqHost string

@description('RabbitMQ (CloudAMQP) port. 5671 for AMQPS/TLS.')
param rabbitmqPort int = 5671

@description('RabbitMQ (CloudAMQP) username. Non-secret.')
param rabbitmqUsername string

@description('RabbitMQ (CloudAMQP) virtual host. Non-secret.')
param rabbitmqVirtualHost string

@description('Enable AMQPS/TLS for RabbitMQ. CloudAMQP requires TLS.')
param rabbitmqSslEnabled bool = true

@description('MongoDB database name used by incident-service. Non-secret.')
param mongoDatabase string = 'cbaclean'

@description('PostgreSQL database name used by Keycloak (separate DB on the same Flexible Server as report-service).')
param keycloakDbName string = 'keycloak'

// --- Recompute Phase 1 resource names deterministically (see main.bicep) ---
var unique = uniqueString(resourceGroup().id)
var uniqueShort = take(unique, 6)
var uniqueAcr = take(unique, 8)
var acrName = toLower('acr${baseName}${env}${uniqueAcr}')
var kvName = 'kv-${baseName}-${env}-${uniqueShort}'
var caeName = 'cae-${baseName}-${env}'
var pgServerName = 'pgsql-${baseName}-${env}-${uniqueShort}'
var identityName = 'id-${baseName}-${env}'

// --- Existing Phase 1 resources ---
resource acr 'Microsoft.ContainerRegistry/registries@2023-07-01' existing = {
  name: acrName
}

resource keyVault 'Microsoft.KeyVault/vaults@2023-07-01' existing = {
  name: kvName
}

resource cae 'Microsoft.App/managedEnvironments@2023-11-02-preview' existing = {
  name: caeName
}

resource pg 'Microsoft.DBforPostgreSQL/flexibleServers@2023-12-01-preview' existing = {
  name: pgServerName
}

// --- Shared managed identity: ACR pull + Key Vault secret references ---
resource identity 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' = {
  name: identityName
  location: location
}

var acrPullRoleId = subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '7f951dda-4ed3-4680-a7ca-43fe172d538d')
var kvSecretsUserRoleId = subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '4633458b-17de-408a-b874-0445c86b69e6')

resource acrPullRoleAssignment 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(acr.id, identity.id, acrPullRoleId)
  scope: acr
  properties: {
    roleDefinitionId: acrPullRoleId
    principalId: identity.properties.principalId
    principalType: 'ServicePrincipal'
  }
}

resource kvSecretsUserRoleAssignment 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(keyVault.id, identity.id, kvSecretsUserRoleId)
  scope: keyVault
  properties: {
    roleDefinitionId: kvSecretsUserRoleId
    principalId: identity.properties.principalId
    principalType: 'ServicePrincipal'
  }
}

// --- Key Vault secret references shared by every app below ---
var postgresPasswordSecret = {
  name: 'postgres-password'
  identity: identity.id
  keyVaultUrl: '${keyVault.properties.vaultUri}secrets/postgresAdminPassword'
}
var keycloakAdminPasswordSecret = {
  name: 'keycloak-admin-password'
  identity: identity.id
  keyVaultUrl: '${keyVault.properties.vaultUri}secrets/keycloak-admin-password'
}
var rabbitmqPasswordSecret = {
  name: 'rabbitmq-password'
  identity: identity.id
  keyVaultUrl: '${keyVault.properties.vaultUri}secrets/rabbitmq-password'
}
var mongoUriSecret = {
  name: 'mongo-uri'
  identity: identity.id
  keyVaultUrl: '${keyVault.properties.vaultUri}secrets/mongo-uri'
}

var registryCredential = {
  server: acr.properties.loginServer
  identity: identity.id
}

// --- Keycloak (external ingress, own realm-baked image, Postgres DB) ---
resource keycloakApp 'Microsoft.App/containerApps@2023-11-02-preview' = {
  name: 'cba-clean-keycloak'
  location: location
  identity: {
    type: 'UserAssigned'
    userAssignedIdentities: {
      '${identity.id}': {}
    }
  }
  properties: {
    environmentId: cae.id
    configuration: {
      activeRevisionsMode: 'Single'
      ingress: {
        external: true
        targetPort: 8080
        transport: 'Auto'
        allowInsecure: false
      }
      registries: [registryCredential]
      secrets: [postgresPasswordSecret, keycloakAdminPasswordSecret]
    }
    template: {
      containers: [
        {
          name: 'cba-clean-keycloak'
          image: keycloakImage
          resources: {
            cpu: json('0.5')
            memory: '1Gi'
          }
          env: [
            { name: 'KC_DB', value: 'postgres' }
            { name: 'KC_DB_URL', value: 'jdbc:postgresql://${pg.properties.fullyQualifiedDomainName}:5432/${keycloakDbName}?sslmode=require' }
            { name: 'KC_DB_USERNAME', value: 'cbaclean' }
            { name: 'KC_DB_PASSWORD', secretRef: 'postgres-password' }
            { name: 'KEYCLOAK_ADMIN', value: 'admin' }
            { name: 'KEYCLOAK_ADMIN_PASSWORD', secretRef: 'keycloak-admin-password' }
            { name: 'KC_HOSTNAME', value: keycloakIssuerUri }
            { name: 'KC_HOSTNAME_STRICT', value: 'true' }
            { name: 'KC_HOSTNAME_BACKCHANNEL_DYNAMIC', value: 'false' }
            { name: 'KC_HTTP_ENABLED', value: 'true' }
            { name: 'KC_PROXY_HEADERS', value: 'xforwarded' }
            { name: 'KC_HEALTH_ENABLED', value: 'true' }
            { name: 'KC_METRICS_ENABLED', value: 'true' }
            { name: 'KC_DB_SCHEMA', value: 'public' }
          ]
        }
      ]
      scale: {
        minReplicas: 1
        maxReplicas: maxReplicas
      }
    }
  }
  dependsOn: [acrPullRoleAssignment, kvSecretsUserRoleAssignment]
}

// --- report-service (external ingress: browser calls it directly, hence CORS_ALLOWED_ORIGINS) ---
resource reportServiceApp 'Microsoft.App/containerApps@2023-11-02-preview' = {
  name: 'cba-clean-report-service'
  location: location
  identity: {
    type: 'UserAssigned'
    userAssignedIdentities: {
      '${identity.id}': {}
    }
  }
  properties: {
    environmentId: cae.id
    configuration: {
      activeRevisionsMode: 'Single'
      ingress: {
        external: true
        targetPort: 8080
        transport: 'Auto'
        allowInsecure: false
      }
      registries: [registryCredential]
      secrets: [postgresPasswordSecret, rabbitmqPasswordSecret]
    }
    template: {
      containers: [
        {
          name: 'cba-clean-report-service'
          image: reportServiceImage
          resources: {
            cpu: json('0.5')
            memory: '1Gi'
          }
          env: [
            { name: 'DB_URL', value: 'jdbc:postgresql://${pg.properties.fullyQualifiedDomainName}:5432/cbaclean?sslmode=require' }
            { name: 'DB_USERNAME', value: 'cbaclean' }
            { name: 'DB_PASSWORD', secretRef: 'postgres-password' }
            { name: 'RABBITMQ_HOST', value: rabbitmqHost }
            { name: 'RABBITMQ_PORT', value: string(rabbitmqPort) }
            { name: 'RABBITMQ_USERNAME', value: rabbitmqUsername }
            { name: 'RABBITMQ_PASSWORD', secretRef: 'rabbitmq-password' }
            { name: 'RABBITMQ_VIRTUAL_HOST', value: rabbitmqVirtualHost }
            { name: 'RABBITMQ_SSL_ENABLED', value: string(rabbitmqSslEnabled) }
            { name: 'JWT_ISSUER_URI', value: keycloakIssuerUri }
            { name: 'JWT_AUDIENCE', value: jwtAudience }
            { name: 'CORS_ALLOWED_ORIGINS', value: frontendOrigin }
          ]
        }
      ]
      scale: {
        minReplicas: backendMinReplicas
        maxReplicas: maxReplicas
      }
    }
  }
  dependsOn: [acrPullRoleAssignment, kvSecretsUserRoleAssignment]
}

// --- incident-service (internal ingress only: consumed via Nginx proxy / ACA internal FQDN) ---
resource incidentServiceApp 'Microsoft.App/containerApps@2023-11-02-preview' = {
  name: 'cba-clean-incident-service'
  location: location
  identity: {
    type: 'UserAssigned'
    userAssignedIdentities: {
      '${identity.id}': {}
    }
  }
  properties: {
    environmentId: cae.id
    configuration: {
      activeRevisionsMode: 'Single'
      ingress: {
        external: false
        targetPort: 8081
        transport: 'Auto'
        allowInsecure: false
      }
      registries: [registryCredential]
      secrets: [rabbitmqPasswordSecret, mongoUriSecret]
    }
    template: {
      containers: [
        {
          name: 'cba-clean-incident-service'
          image: incidentServiceImage
          resources: {
            cpu: json('0.5')
            memory: '1Gi'
          }
          env: [
            { name: 'MONGO_URI', secretRef: 'mongo-uri' }
            { name: 'MONGO_DATABASE', value: mongoDatabase }
            { name: 'RABBITMQ_HOST', value: rabbitmqHost }
            { name: 'RABBITMQ_PORT', value: string(rabbitmqPort) }
            { name: 'RABBITMQ_USERNAME', value: rabbitmqUsername }
            { name: 'RABBITMQ_PASSWORD', secretRef: 'rabbitmq-password' }
            { name: 'RABBITMQ_VIRTUAL_HOST', value: rabbitmqVirtualHost }
            { name: 'RABBITMQ_SSL_ENABLED', value: string(rabbitmqSslEnabled) }
            { name: 'JWT_ISSUER_URI', value: keycloakIssuerUri }
            { name: 'JWT_AUDIENCE', value: jwtAudience }
            { name: 'CORS_ALLOWED_ORIGINS', value: frontendOrigin }
          ]
        }
      ]
      scale: {
        minReplicas: backendMinReplicas
        maxReplicas: maxReplicas
      }
    }
  }
  dependsOn: [acrPullRoleAssignment, kvSecretsUserRoleAssignment]
}

// --- web (external ingress: Angular + Nginx, proxies to the two backends + Keycloak) ---
resource webApp 'Microsoft.App/containerApps@2023-11-02-preview' = {
  name: 'cba-clean-web'
  location: location
  identity: {
    type: 'UserAssigned'
    userAssignedIdentities: {
      '${identity.id}': {}
    }
  }
  properties: {
    environmentId: cae.id
    configuration: {
      activeRevisionsMode: 'Single'
      ingress: {
        external: true
        targetPort: 80
        transport: 'Auto'
        allowInsecure: false
      }
      registries: [registryCredential]
    }
    template: {
      containers: [
        {
          name: 'cba-clean-web'
          image: webImage
          resources: {
            cpu: json('0.5')
            memory: '1Gi'
          }
          env: [
            { name: 'REPORT_SERVICE_URL', value: 'https://${reportServiceApp.properties.configuration.ingress.fqdn}' }
            { name: 'INCIDENT_SERVICE_URL', value: 'https://${incidentServiceApp.properties.configuration.ingress.fqdn}' }
            { name: 'KEYCLOAK_URL', value: 'https://${keycloakApp.properties.configuration.ingress.fqdn}' }
            { name: 'FRONTEND_API_BASE_URL', value: '/api/v1' }
            { name: 'FRONTEND_INCIDENT_API_BASE_URL', value: '/api/v1' }
            { name: 'FRONTEND_KEYCLOAK_ISSUER', value: keycloakIssuerUri }
            { name: 'FRONTEND_KEYCLOAK_REDIRECT_URI', value: frontendOrigin }
            { name: 'FRONTEND_KEYCLOAK_CLIENT_ID', value: 'cba-clean-web' }
            { name: 'FRONTEND_KEYCLOAK_SCOPE', value: 'openid' }
          ]
        }
      ]
      scale: {
        minReplicas: 1
        maxReplicas: maxReplicas
      }
    }
  }
  dependsOn: [acrPullRoleAssignment, kvSecretsUserRoleAssignment]
}

output identityId string = identity.id
output keycloakFqdn string = keycloakApp.properties.configuration.ingress.fqdn
output reportServiceFqdn string = reportServiceApp.properties.configuration.ingress.fqdn
output incidentServiceFqdn string = incidentServiceApp.properties.configuration.ingress.fqdn
output webFqdn string = webApp.properties.configuration.ingress.fqdn
