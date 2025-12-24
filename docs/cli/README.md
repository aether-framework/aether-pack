# APACK CLI Overview

The APACK command-line interface provides tools for creating, extracting, listing, verifying, and inspecting APACK archives from the terminal.

## Installation

### From Maven Build

After building the project, the CLI is packaged as a fat JAR with all dependencies:

```bash
# Build the project
mvn clean package

# The CLI JAR is located at:
# aether-pack-cli/target/aether-pack-cli-0.1.0-SNAPSHOT-fat.jar
```

### Running the CLI

```bash
# Direct execution
java -jar aether-pack-cli-0.1.0-SNAPSHOT-fat.jar [command] [options]

# Create an alias for convenience (Unix/Linux/macOS)
alias apack='java -jar /path/to/aether-pack-cli-0.1.0-SNAPSHOT-fat.jar'

# Windows batch file (apack.bat)
@echo off
java -jar C:\path\to\aether-pack-cli-0.1.0-SNAPSHOT-fat.jar %*
```

---

## Commands

| Command | Aliases | Description |
|---------|---------|-------------|
| `create` | `c` | Create a new APACK archive |
| `extract` | `x` | Extract files from an archive |
| `list` | `l`, `ls` | List archive contents |
| `info` | `i` | Display archive information |
| `verify` | `v` | Verify archive integrity |
| `help` | - | Show help for a command |

---

## Global Options

These options are available for all commands:

| Option | Description |
|--------|-------------|
| `-h, --help` | Show help message and exit |
| `-V, --version` | Print version information and exit |
| `-v, --verbose` | Enable verbose output with detailed error messages |

---

## Exit Codes

The CLI uses standard exit codes to indicate operation results:

| Code | Meaning |
|------|---------|
| `0` | Success |
| `1` | Error (invalid arguments, operation failed, verification failed) |
| `2` | Archive error (format error, file not found, corruption) |

**Usage in scripts:**
```bash
# Check if operation succeeded
if apack verify archive.apack; then
    echo "Archive is valid"
else
    echo "Archive verification failed"
fi
```

---

## Quick Reference

### Create Archive

```bash
# Create with default ZSTD compression
apack create archive.apack file1.txt file2.txt

# Create from directory with LZ4
apack create -c lz4 archive.apack ./mydir/

# Create encrypted archive
apack create -c zstd -e aes-256-gcm archive.apack ./sensitive/
```

### Extract Archive

```bash
# Extract to current directory
apack extract archive.apack

# Extract to specific directory
apack extract -o ./output/ archive.apack

# Extract encrypted archive
apack extract -p archive.apack -o ./output/
```

### List Contents

```bash
# Simple listing
apack list archive.apack

# Long format with details
apack list -l archive.apack

# JSON output
apack list --json archive.apack
```

### Show Information

```bash
# Display archive info
apack info archive.apack

# JSON format
apack info --json archive.apack
```

### Verify Integrity

```bash
# Full verification
apack verify archive.apack

# Quick header check
apack verify --quick archive.apack

# Verbose output
apack verify -v archive.apack
```

---

## Environment Variables

Currently, the CLI does not use environment variables. All configuration is passed via command-line options.

## Configuration Files

Currently, the CLI does not support configuration files. All settings must be specified on the command line.

---

## Getting Help

```bash
# Show main help
apack --help

# Show help for a specific command
apack help create
apack create --help
```

---

*Next: [Create Command](create.md) | Back to: [Documentation](../README.md)*
