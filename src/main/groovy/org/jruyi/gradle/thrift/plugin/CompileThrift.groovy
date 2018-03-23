/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	 http://www.apache.org/licenses/LICENSE-2.0
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
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.incremental.IncrementalTaskInputs

import java.nio.file.Paths

class CompileThrift extends DefaultTask {

	@InputFiles
	Set<File> sourceItems = []

	@OutputDirectory
	File outputDir

	@Input
	Set<File> includeDirs = []

	@Input
	Set<String> configurations = []

	@Input
	String thriftExecutable = 'thrift'

	@Input
	String tmpInBuildDir = 'thriftTmp'

	@Input
	final Map<String, String> generators = new LinkedHashMap<>()

	@Input
	boolean createGenFolder = true

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

	def thriftExecutable(Object thriftExecutable) {
		this.thriftExecutable = String.valueOf(thriftExecutable)
	}

	def sourceDir(Object sourceDir) {
		sourceItems(sourceDir)
	}

	def sourceItems(Object... sourceItems) {
		sourceItems.each { sourceItem ->
			this.sourceItems.add(convertToFile(sourceItem))
		}
	}

	def outputDir(Object outputDir) {
		if (!(outputDir instanceof File))
			outputDir = project.file(outputDir)
		if (this.outputDir == outputDir)
			return
		def oldOutputDir = currentOutputDir()
		this.outputDir = outputDir
		addSourceDir(oldOutputDir)
	}

	def tmpDir() {
		project.getBuildDir().toPath().resolve(Paths.get(tmpInBuildDir)).toFile()
	}

	def includeDir(Object includeDir) {
		if (!(includeDir instanceof File))
			includeDir = project.file(includeDir)

		includeDirs << includeDir
	}

	def configuration(Object configuration) {
		if (!configurations.contains(configuration)) {
			String confAsString = (String) configuration
			configurations << confAsString
			this.dependsOn(project.configurations.getByName(confAsString))
		}
	}

	def generator(Object gen, Object... args) {
		String options
		if (args == null || args.length < 1)
			options = ''
		else {
			final int n = args.length
			for (int i = 0; i < n; ++i)
				args[i] = args[i].trim()
			options = args.join(',')
		}
		generators.put(String.valueOf(gen).trim(), options)
	}

	def createGenFolder(boolean createGenFolder) {
		if (this.createGenFolder == createGenFolder)
			return
		def oldOutputDir = currentOutputDir()
		this.createGenFolder = createGenFolder
		addSourceDir(oldOutputDir)
	}

	@TaskAction
	def compileThrift(IncrementalTaskInputs inputs) {
		extractThriftFilesInJars().each {
			includeDir(it)
		}
		if (!inputs.incremental) {
			compileAll()
			return
		}

		List<File> changedFiles = []
		inputs.outOfDate { change ->
			if (change.file.name.endsWith('.thrift'))
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

	/**
	 * Extract thrift files present in the jars of the configurations into a hierarchy inside tmpDir.
	 * @return the list of directories where thrift files were extracted.
	 */
	List<File> extractThriftFilesInJars() {
		def res = []
		def currTmpDir = tmpDir()
		for (String configuration: configurations) {
			List<File> files = project.configurations.getByName(configuration).findAll {
				it instanceof File
			}.collect {
				(File) it
			}
			ExtractJarFromConfiguration.makeThriftPathFromJars(currTmpDir, files, logger).each {
				res << it
			}
		}
		res
	}

	def compileAll() {
		if (!outputDir.deleteDir())
			throw new GradleException("Could not delete thrift output directory: ${outputDir.absolutePath}")

		if (!outputDir.mkdirs())
			throw new GradleException("Could not create thrift output directory: ${outputDir.absolutePath}")

		// expand all items.
		Set<String> resolvedSourceItems = []
		sourceItems.each {
			sourceItem -> if(sourceItem.file) {
				resolvedSourceItems.add(sourceItem.absolutePath)
			} else if (sourceItem.directory) {
				project.fileTree(sourceItem.canonicalPath) {
					include '**/*.thrift'
				}.each { foundItem ->
					resolvedSourceItems.add(foundItem.absolutePath)
				}

			} else if (!sourceItem.exists()) {
				logger.warn("Could not find {}. Will ignore it", sourceItem)
			} else {
				logger.warn("Unable to handle {}. Will ignore it", sourceItem)
			}
		}

		logger.info("Items to be generated for: {}", resolvedSourceItems)

		resolvedSourceItems.each {
			compile(it)
		}
	}

	def compile(String source) {
		def cmdLine = [thriftExecutable, createGenFolder ? '-o' : '-out', outputDir.absolutePath]
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

	def makeAsDependency(File oldOutputDir) {
		Task compileJava = project.tasks.getByName(JavaPlugin.COMPILE_JAVA_TASK_NAME);
		if (compileJava == null)
			return

		generators['java'] = ''
		def genJava = currentOutputDir().canonicalFile
		if (genJava == oldOutputDir)
			return

		if (oldOutputDir != null)
			project.sourceSets.main.java.srcDirs -= oldOutputDir
		project.sourceSets.main.java.srcDir genJava.absolutePath

		compileJava.dependsOn this
	}

	private def addSourceDir(File oldOutputDir) {
		if (project.plugins.hasPlugin('java'))
			makeAsDependency(oldOutputDir)
		else {
			project.plugins.whenPluginAdded { plugin ->
				if (plugin instanceof JavaPlugin)
					makeAsDependency(oldOutputDir)
			}
		}
	}

	def convertToFile(Object item) {
		if (item instanceof File) {
			return item
		}

		def result = new File(item.toString());
		if(result.exists()) {
			return result;
		}

		project.file(item)
	}

	private def currentOutputDir() {
		def currentOutputDir = outputDir
		if (currentOutputDir == null)
			return null
		if (createGenFolder)
			currentOutputDir = new File(currentOutputDir, 'gen-java')
		return currentOutputDir
	}
}