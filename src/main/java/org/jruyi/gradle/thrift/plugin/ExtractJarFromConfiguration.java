package org.jruyi.gradle.thrift.plugin;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Sets.newHashSet;
import static java.lang.String.format;
import static java.util.Collections.list;
import static org.codehaus.plexus.util.FileUtils.cleanDirectory;
import static org.codehaus.plexus.util.FileUtils.copyStreamToFile;

import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.codehaus.plexus.util.io.RawInputStreamFacade;
import org.gradle.api.logging.Logger;

/**
 * Mostly taken from
 * https://github.com/apache/thrift/blob/master/contrib/thrift-maven-plugin/src/main/java/org/apache/thrift/maven/AbstractThriftMojo.java#L264
 */

public class ExtractJarFromConfiguration {
  private static final String THRIFT_FILE_SUFFIX = ".thrift";

  public static ImmutableSet<File> makeThriftPathFromJars(File temporaryThriftFileDirectory, Iterable<File> classpathElementFiles, Logger logger)
      throws IOException {
    checkNotNull(classpathElementFiles, "classpathElementFiles");
    // clean the temporary directory to ensure that stale files aren't used
    if (temporaryThriftFileDirectory.exists()) {
      cleanDirectory(temporaryThriftFileDirectory);
    }

    Set<File> thriftDirectories = newHashSet();

    for (File classpathElementFile : classpathElementFiles) {
      // for some reason under IAM, we receive poms as dependent files
      // I am excluding .xml rather than including .jar as there may be other extensions in use (sar, har, zip)
      if (classpathElementFile.isFile() && classpathElementFile.canRead() &&
          !classpathElementFile.getName().endsWith(".xml")) {

        // create the jar file. the constructor validates.
        JarFile classpathJar;
        try {
          classpathJar = new JarFile(classpathElementFile);
        } catch (IOException e) {
          throw new IllegalArgumentException(format(
              "%s was not a readable artifact", classpathElementFile));
        }

        /**
         * Copy each .thrift file found in the JAR into a temporary directory, preserving the
         * directory path it had relative to its containing JAR. Add the resulting root directory
         * (unique for each JAR processed) to the set of thrift include directories to use when
         * compiling.
         */
        for (JarEntry jarEntry : list(classpathJar.entries())) {
          final String jarEntryName = jarEntry.getName();
          if (jarEntry.getName().endsWith(THRIFT_FILE_SUFFIX)) {
            final String truncatedJarPath = truncatePath(classpathJar.getName(), logger);
            /*
             * MODIFICATION: skip unsupported classpathJar instead of raising an exception.
             */
            if (truncatedJarPath == null) {
              continue;
            }
            final File thriftRootDirectory = new File(temporaryThriftFileDirectory, truncatedJarPath);
            final File uncompressedCopy =
                new File(thriftRootDirectory, jarEntryName);
            File parent = uncompressedCopy.getParentFile();
            parent.mkdirs();
            copyStreamToFile(new RawInputStreamFacade(classpathJar
                .getInputStream(jarEntry)), uncompressedCopy);
            /* MODIFICATION: when we have a non flat hierarchy in the jar
             * we have an issue in the algorithm from the maven plugin.
             * Ex: path_to_some_jar.jar/userdata/LTFCookie_Thrift.thrift
             * In the maven plugin the directory some_jar.jar is added, which will make the generated thrift
             * command line be:
             * $ thrift -I path_to_some_jar.jar
             * with thrift 0.9.3 though,
             * $ thrift -I path_to_some_jar.jar/userdata
             * should be included so that LFTCookie_Thrift.thrift is found by the compiler
             */
            thriftDirectories.add(thriftRootDirectory.toPath().resolve(parent.toPath()).toFile());
          }
        }

      } else if (classpathElementFile.isDirectory()) {
        File[] thriftFiles = classpathElementFile.listFiles(new FilenameFilter() {
          public boolean accept(File dir, String name) {
            return name.endsWith(THRIFT_FILE_SUFFIX);
          }
        });

        if (thriftFiles.length > 0) {
          thriftDirectories.add(classpathElementFile);
        }
      }
    }

    return ImmutableSet.copyOf(thriftDirectories);
  }

  /**
   * MODIFICATION: gradle does not take jars from local repositories. The jar can be found either
   * - in the downloaded jars cache, or
   * - in the build dir (when building from source)
   * Drastically de-feature and simplify the algorithm from the maven plugin to return
   * the name of the jar if jarPath ends with .jar and return null else.
   * @param jarPath the full path of a jar file.
   * @return the truncated path relative to the local repository or root of the drive.
   */
  private static String truncatePath(final String jarPath, Logger logger) {
    if (jarPath.endsWith(".jar")) {
      return Paths.get(jarPath).getFileName().toString();
    } else {
      logger.debug(String.format("%s does not end with .jar, thrift files will not be included", jarPath));
      return null;
    }
  }
}
