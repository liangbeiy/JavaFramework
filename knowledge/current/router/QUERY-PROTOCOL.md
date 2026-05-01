# Query Protocol for Code Router Index

## Overview

This document describes how to query the generated code router index files to navigate the JavaFramework codebase efficiently.

## Index Files

| File | Format | Purpose |
|------|--------|---------|
| `symbol-table.tsv` | ClassName\tModuleName\tFilePath\tType\tVisibility\tDescription | Class/interface/enum lookup |
| `method-index.tsv` | ClassName.methodName\tModule\tFilePath\tVisibility\tKind\tSignature | Method lookup |
| `call-graph.tsv` | Caller\tCallee\tType\tConfidence | Method call relationships |
| `module-deps.tsv` | FromModule\tToModule\tType\tEvidence | Module dependencies |

## Query Methods

### 1. Symbol Lookup (symbol-table.tsv)

**Goal**: Find class information by name or module.

**Examples**:

```bash
# Find all classes in framework/eventbus module
grep "framework/eventbus" symbol-table.tsv

# Find a specific class
grep "^EventBus" symbol-table.tsv

# List all interfaces
grep "\tinterface\t" symbol-table.tsv
```

### 2. Method Lookup (method-index.tsv)

**Goal**: Find method signatures and locations.

**Examples**:

```bash
# Find all methods in a class
grep "^EventBus\." method-index.tsv

# Find static methods
grep "\tstatic\t" method-index.tsv

# Find methods in a module
grep "framework/eventbus" method-index.tsv
```

### 3. Call Graph Analysis (call-graph.tsv)

**Goal**: Understand method call relationships.

**Examples**:

```bash
# Find who calls a specific method
grep "Callee.*EventBus\.post" call-graph.tsv

# Find all calls from a class
grep "^ClassName\.\*\t" call-graph.tsv

# High confidence calls only
grep "\thigh$" call-graph.tsv
```

### 4. Module Dependency Analysis (module-deps.tsv)

**Goal**: Understand module dependencies.

**Examples**:

```bash
# Find all dependencies of handler module
grep "^handler\t" module-deps.tsv

# Find src-level dependencies
grep "\tsrc\t" module-deps.tsv

# What depends on framework/eventbus
grep "\tframework/eventbus\t" module-deps.tsv
```

## Query Patterns by Use Case

### Finding where a method is defined

```bash
grep "ClassName.methodName" method-index.tsv
```

### Finding all classes implementing an interface

```bash
# First find the interface
grep "^InterfaceName\t" symbol-table.tsv
# Then search for classes that might implement it in the same module
```

### Understanding module architecture

```bash
# List all module dependencies
cat module-deps.tsv | cut -f1,2 | sort -u
```

### Finding public API surface

```bash
# All public classes
grep "\tpublic\t" symbol-table.tsv

# All public methods
grep "\tpublic\t" method-index.tsv
```

## Notes

- All files are TSV (Tab-Separated Values) for easy parsing
- Confidence levels in call-graph.tsv: high (same class), medium (cross-class), low (uncertain)
- Caller in call-graph.tsv uses `ClassName.*` for wildcard matching
- Module names use `framework/submodule` format for framework submodules
- Use `cut`, `grep`, `awk` for complex queries

## Automation

For programmatic access, consider loading TSV files into:
- Spreadsheet applications
- Database tables
- Custom scripts using Python/Ruby/Node.js
