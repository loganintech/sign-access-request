# SignAccessRequest Plugin

A Minecraft plugin that allows players to create signs that trigger ConductorOne access requests.

## Features

- Create signs with `[c1-req]` on line 1 to request **grant** access to ConductorOne entitlements
- Create signs with `[c1-drop]` on line 1 to request **revoke** access to ConductorOne entitlements
- Automatic detection of existing open tasks to prevent duplicate requests
- Clickable links to view access request tasks in ConductorOne
- OAuth2 client credentials authentication with automatic token refresh
- Permission-based sign creation and destruction
- All players can use signs (right-click) by default
- Valid signs display with blue text, invalid signs display with red text

## Installation

1. Build the plugin: `./gradlew build`
2. Copy the JAR from `build/libs/sign-access-request-1.0.0-SNAPSHOT.jar` to your server's `plugins` folder
3. Start your server to generate the default config
4. Edit `plugins/SignAccessRequest/config.yml` with your ConductorOne credentials
5. Restart your server

## Configuration

Edit `plugins/SignAccessRequest/config.yml`:

```yaml
conductorone:
  # The base URL for your ConductorOne instance
  base-url: "https://your-tenant.conductor.one"

  # OAuth2 Client Credentials
  client-id: "your-client-id-here"
  client-secret: "your-client-secret-here"

  # API endpoints (usually don't need to change)
  token-endpoint: "auth/v1/token"
  grant-task-endpoint: "api/v1/task/grant"
  revoke-task-endpoint: "api/v1/task/revoke"

# Debug Settings
debug:
  # Enable debug logging for API requests and responses
  enabled: false
```

## Usage

### Creating a Sign

#### Grant Access Sign
1. Place a sign
2. On line 1, type: `[c1-req]`
3. On line 2, type the entitlement alias (e.g., `prod-admin-access`)
4. Lines 3 and 4 can contain any text you want (e.g., description)

#### Revoke Access Sign
1. Place a sign
2. On line 1, type: `[c1-drop]`
3. On line 2, type the entitlement alias (e.g., `prod-admin-access`)
4. Lines 3 and 4 can contain any text you want (e.g., description)

If valid:
- Line 1 will turn **blue**
- The sign is now active

If invalid:
- Line 1 will turn **red**
- Check that line 2 has an entitlement alias

### Using a Sign

Right-click any valid C1 sign to submit a grant or revoke request for that entitlement.

**When successful, you'll receive:**
- A confirmation message
- A clickable link to view your task in ConductorOne

**If you have existing open tasks:**
- The plugin will detect them and show you clickable links to the existing tasks
- No duplicate task will be created

### Destroying a Sign

Valid signs (blue) require the `signaccessrequest.destroy` permission to break.
Invalid signs (red) can be broken by the creator or anyone with destroy permission.

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/c1reload` | Reloads the plugin configuration without restarting the server | `signaccessrequest.admin` |
| `/c1debug [on\|off]` | Enables or toggles debug mode for API requests | `signaccessrequest.admin` |

### Debug Mode

When debug mode is enabled, the plugin will log:
- All API requests (URL, method, body)
- All API responses (status code, body)
- Token requests (with masked tokens)
- Error responses

This is useful for troubleshooting authentication or API issues.

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `signaccessrequest.create` | Allows creating C1 access request signs | op |
| `signaccessrequest.destroy` | Allows destroying C1 access request signs | op |
| `signaccessrequest.use` | Allows using C1 access request signs | true (all players) |
| `signaccessrequest.admin` | Allows using admin commands (reload, debug) | op |

## Building

Requirements:
- Java 21 (Java 22+ not yet fully supported by Gradle 8.10)
- Gradle 8.10+

```bash
./gradlew build
```

The plugin JAR will be in `build/libs/`.

## API Compatibility

- Paper 1.21.3+
- Java 21+
- Minecraft 1.21+
