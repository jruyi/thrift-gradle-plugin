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

import org.gradle.api.Plugin
import org.gradle.api.Project

class ThriftPlugin implements Plugin<Project> {

	public static final String COMPILE_THRIFT_TASK = 'compileThrift'

	@Override
	void apply(Project project) {

		def srcDir = 'src/main/thrift'
		def dstDir = "${project.buildDir}/generated-sources/thrift"

		CompileThrift compileThrift = project.tasks.create(COMPILE_THRIFT_TASK, CompileThrift)
		compileThrift.sourceDir(srcDir)
		compileThrift.outputDir(dstDir)
		project.configure(project) {
			sourceSets {
				main {
					resources {
						srcDirs srcDir
						include '**/*.thrift'
					}
				}
			}
		}
	}
}
