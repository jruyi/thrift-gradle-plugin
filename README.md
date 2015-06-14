## Gradle Thrift Plugin

[![Build Status](https://travis-ci.org/jruyi/thrift-gradle-plugin.svg?branch=master)](https://travis-ci.org/jruyi/thrift-gradle-plugin)

Gradle Thrift Plugin uses thrift compiler to compile Thrift IDL files.

### Usage
To use this plugin, add the following to your build script.

```groovy
buildscript {
	repositories {
		maven {
			url "https://plugins.gradle.org/m2/"
		}
	}
	dependencies {
		classpath "gradle.plugin.org.jruyi.gradle:thrift-gradle-plugin:0.3.0"
	}
}

apply plugin: "org.jruyi.thrift"
```

Or for gradle 2.1+

```groovy
plugins {
	id "org.jruyi.thrift" version "0.3.0"
}
```

### Implicitly Applied Plugins

None.

### Tasks

The Thrift plugin adds compileThrift task which compiles Thrift IDL files using thrift compiler.

##### Table-1 Task Properties of compileThrift

Task Property     | Type                | Default Value
------------------|---------------------|---------------------------------------------------
thriftExecutable  | String              | thrift
sourceDir         | File                | src/main/thrift
outputDir         | File                | _buildDir_/generated-sources/thrift
includeDirs       | Set<File>           | []
generators        | Map<String, String> | ['java':''] if JavaPlugin is applied, otherwise []
nowarn            | boolean             | false
strict            | boolean             | false
verbose           | boolean             | false
recurse           | boolean             | false
debug             | boolean             | false
allowNegKeys      | boolean             | false
allow64bitsConsts | boolean             | false
createGenFolder   | boolean             | true

If createGenFolder is set to false, no gen-* folder will be created.

##### Example

```groovy
compileThrift {
	recurse true

	generator 'html'
	generator 'java', 'private-members'
}
```

### Default Behaviors

When JavaPlugin is applied, generator 'java' will be created and the generated java code will be added to java source automatically.

## License

Gradle Thrift Plugin is licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html).
