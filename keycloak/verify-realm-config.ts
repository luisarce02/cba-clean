import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

interface ProtocolMapper {
  id: string;
  name: string;
  protocol: string;
  protocolMapper: string;
  config: Record<string, string>;
}

interface Client {
  id: string;
  clientId: string;
  protocolMappers: ProtocolMapper[];
}

interface RealmUser {
  id: string;
  username: string;
  realmRoles: string[];
  clientRoles: Record<string, string[]>;
}

interface RealmConfig {
  realm: string;
  roles: { realm: { name: string }[] };
  clients: Client[];
  users: RealmUser[];
}

const realmPath = resolve(import.meta.dirname ?? '.', 'realm/cba-clean-realm.json');
const realm: RealmConfig = JSON.parse(readFileSync(realmPath, 'utf-8'));

let failures = 0;

function assert(condition: boolean, message: string): void {
  if (!condition) {
    console.error(`FAIL: ${message}`);
    failures++;
  } else {
    console.log(`PASS: ${message}`);
  }
}

// 1. Realm roles exist
const roleNames = realm.roles.realm.map((r) => r.name);
assert(roleNames.includes('REPORTER'), 'REPORTER realm role is defined');
assert(roleNames.includes('OPERATOR'), 'OPERATOR realm role is defined');

// 2. Client exists
const client = realm.clients.find((c) => c.clientId === 'cba-clean-web');
assert(client !== undefined, 'cba-clean-web client exists');

// 3. Realm role mapper exists on the client
const rolesMapper = client?.protocolMappers.find((m) => m.name === 'roles');
assert(rolesMapper !== undefined, 'roles mapper exists on cba-clean-web client');

// 4. Mapper type is realm-role-mapper (NOT client-role-mapper)
assert(
  rolesMapper?.protocolMapper === 'oidc-usermodel-realm-role-mapper',
  `mapper type is oidc-usermodel-realm-role-mapper (got: ${rolesMapper?.protocolMapper})`,
);
assert(
  rolesMapper?.protocolMapper !== 'oidc-usermodel-client-role-mapper',
  'mapper is NOT oidc-usermodel-client-role-mapper (old broken config)',
);

// 5. Mapper emits into access token
assert(rolesMapper?.config['access.token.claim'] === 'true', 'access.token.claim is true');
assert(rolesMapper?.config['id.token.claim'] === 'false', 'id.token.claim is false');
assert(rolesMapper?.config['token.claim.name'] === 'roles', 'token.claim.name is roles');
assert(rolesMapper?.config['multivalued'] === 'true', 'multivalued is true');

// 6. Users have realm roles assigned
const reporter = realm.users.find((u) => u.username === 'reporter');
assert(reporter?.realmRoles.includes('REPORTER'), 'reporter user has REPORTER realm role');

const operator = realm.users.find((u) => u.username === 'operator');
assert(operator?.realmRoles.includes('OPERATOR'), 'operator user has OPERATOR realm role');

// 7. Users do NOT rely on client roles for these roles
assert(
  Object.keys(reporter?.clientRoles ?? {}).length === 0,
  'reporter has no client roles (roles are realm-level)',
);

console.log('');
if (failures > 0) {
  console.error(`${failures} assertion(s) failed.`);
  process.exit(1);
} else {
  console.log('All Keycloak realm configuration assertions passed.');
}
