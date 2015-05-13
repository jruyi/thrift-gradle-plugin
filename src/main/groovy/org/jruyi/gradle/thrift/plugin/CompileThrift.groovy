/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jruyi.gradle.thrift.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.incremental.IncrementalTaskInputs

class CompileThrift extends DefaultTask {

	@InputDirectory
	File sourceDir

	@OutputDirectory
	File outputDir

	@Input
	Set<File> includeDirs = []

	@Input
	String thriftExecutable = 'thrift'

	@Input
	final Map<String, String> generators = new LinkedHashMap<>()

	@Input
	boolean recurse

	@Input
	boolean allowNegKeys

	@Input
	boolean allow64bitsConsts

	boolean nowarn
	boolean strict
	boolean verbose
	boolean debug

	def sourceDir(String sourceDirPath) {
		sourceDir = project.file(sourceDirPath).canonicalFile
	}

	def sourceDir(File sourceDir) {
		this.sourceDir = sourceDir
	}

	def outputDir(String outputDirPath) {
		outputDir = project.file(outputDirPath).canonicalFile
	}

	def outputDir(File outputDir) {
		this.outputDir = outputDir
	}

	def includeDir(File includeDir) {
		includeDirs << includeDir
	}

	def includeDir(String includeDir) {
		includeDirs << project.file(includeDir).canonicalFile
	}

	def generator(String gen, String... args) {
		String options
		if (args == null || args.length < 1)
			options = ''
		else {
			final int n = args.length
			for (int i = 0; i < n; ++i)
				args[i] = args[i].trim()
			options = args.join(',')
		}
		generators.put(gen.trim(), options)
	}

	@TaskAction
	def compileThrift(IncrementalTaskInputs inputs) {

		if (!inputs.incremental) {
			compileAll()
			return
		}

		List<File> changedFiles = []
		inputs.outOfDate { change ->
			changedFiles.add(change.file)
		}

		boolean removed = false
		inputs.removed {
			removed = true
		}

		if (removed) {
			compileAll()
			return
		}

		if (!outputDir.exists() && !outputDir.mkdirs())
			throw new GradleException("Could not create thrift output directory: ${outputDir.absolutePath}")

		changedFiles.each { changedFile ->
			compile(changedFile.absolutePath)
		}
	}

	def compileAll() {
		if (!outputDir.deleteDir())
			throw new GradleException("Could not delete thrift output directory: ${outputDir.absolutePath}")

		if (!outputDir.mkdirs())
			throw new GradleException("Could not create thrift output directory: ${outputDir.absolutePath}")

		project.fileTree(sourceDir.canonicalPath) {
			include '**/*.thrift'
		}.each { it ->
			compile(it.absolutePath)
		}
	}

	def compile(String source) {
		def cmdLine = [thriftExecutable, '-o', outputDir.absolutePath]
		generators.each { generator ->
			cmdLine << '--gen'
			String cmd = generator.key.trim()
			String options = generator.value.trim()
			if (!options.isEmpty())
				cmd += ':' + options
			cmdLine << cmd
		}
		includeDirs.each { includeDir ->
			cmdLine << '-I'
			cmdLine << includeDir.absolutePath
		}
		if (recurse) cmdLine << '-r'
		if (nowarn) cmdLine << '-nowarn'
		if (strict) cmdLine << '-strict'
		if (verbose) cmdLine << '-v'
		if (debug) cmdLine << '-debug'
		cmdLine << source

		def result = project.exec {
			commandLine cmdLine
		}

		def exitCode = result.exitValue
		if (exitCode != 0)
			throw new GradleException("Failed to compile ${source}, exit=${exitCode}")
	}
}
