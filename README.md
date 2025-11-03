# nonscenes

A cutscene plugin for Minecraft 1.20.x - 1.21.x servers.

> **Note**: This plugin is currently in alpha development stage but is actively maintained and improved.

## Features

- Record player movements to create cutscenes
- Play back recorded cutscenes
- Visualize cutscene paths with particles
- Multiple database support (SQLite, MySQL, PostgreSQL, MongoDB, Redis)

## Installation

1. Download the latest JAR from releases
2. Place in your server's `plugins/` folder
3. Install LuckPerms (required for permissions)
4. Restart your server

## Commands

- `/nonscene start <name> <frames>` - Start recording a cutscene
- `/nonscene play <name>` - Play a cutscene
- `/nonscene all` - List all cutscenes
- `/nonscene delete <name>` - Delete a cutscene
- `/nonscene showpath <name>` - Show cutscene path

## Permissions

- `nonscene.use` - Basic usage
- `nonscene.start` - Recording permission
- `nonscene.play` - Playback permission
- `nonscene.delete` - Delete permission
- `nonscene.list` - List permission
- `nonscene.showpath` - Path visualization permission
- `nonscene.admin` - All permissions

## Configuration

The plugin creates `config.yml` and `messages.yml` in `plugins/nonscenes/` on first run.

### Database Setup

Default is SQLite. For other databases, edit `config.yml`:

```yaml
storage:
  type: MYSQL  # or POSTGRESQL, MONGODB, REDIS
  mysql:
    host: localhost
    port: 3306
    database: minecraft
    username: your_username
    password: your_password
```

## Support

This is an alpha release. Report issues on GitHub.

## License

MIT License
