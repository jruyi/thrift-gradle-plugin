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

package org.jruyi.thrift.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPlugin

class ThriftPlugin implements Plugin<Project> {

	public static final String COMPILE_THRIFT_TASK = 'compileThrift'

	@Override
	void apply(Project project) {

		def srcDir = 'src/main/thrift'
		def dstDir = new File(project.buildDir, 'generated-sources/thrift')

		CompileThrift compileThrift = project.tasks.create(COMPILE_THRIFT_TASK, CompileThrift)
		compileThrift.sourceDir(srcDir)
		compileThrift.outputDir(dstDir)

		if (project.plugins.hasPlugin('java'))
			makeAsDependency(project, compileThrift)
		else {
			project.plugins.whenPluginAdded { plugin ->
				if (plugin instanceof JavaPlugin)
					makeAsDependency(project, compileThrift)
			}
		}
	}

	private void makeAsDependency(Project project, CompileThrift compileThrift) {
		Task compileJava = project.tasks.getByName(JavaPlugin.COMPILE_JAVA_TASK_NAME);
		if (compileJava == null)
			return;

		compileThrift.generators['java'] = ''
		def genJava = new File(compileThrift.outputDir, 'gen-java').canonicalFile
		project.sourceSets.main.java.srcDir genJava.absolutePath
		compileJava.dependsOn compileThrift
	}
}
