/*
 * Copyright 2013-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.rules;

import com.facebook.buck.step.AbstractExecutionStep;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.util.BuckConstant;
import com.facebook.buck.util.DirectoryTraversal;
import com.facebook.buck.util.ProjectFilesystem;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Not all build rules know the paths of the output files that their steps will generate. Such rules
 * often write their output to a directory under {@link BuckConstant#BIN_DIR}. This step traverses
 * the output directory, copies the appropriate files to {@link BuckConstant#GEN_DIR}, and records
 * these artifacts for upload via the {@link BuildableContext}.
 */
public class RecordArtifactsInDirectoryStep extends AbstractExecutionStep {

  private final BuildableContext buildableContext;
  private final String binDirectory;
  private final String genDirectory;
  private final Function<String, String> artifactPathTransform;

  /**
   * @param buildableContext Interface through which the outputs to include in the artifact should
   *     be recorded.
   * @param binDirectory Scratch directory where output files were generated.
   * @param genDirectory Output directory where files should be written. This should be of the form
   *     "buck-out/gen/build/target/path" (note there should not be a trailing slash).
   */
  public RecordArtifactsInDirectoryStep(BuildableContext buildableContext,
      String binDirectory,
      String genDirectory,
      Function<String, String> artifactPathTransform) {
    super("recording artifacts in " + binDirectory);
    this.buildableContext = Preconditions.checkNotNull(buildableContext);
    this.binDirectory = Preconditions.checkNotNull(binDirectory);
    this.genDirectory = Preconditions.checkNotNull(genDirectory);
    this.artifactPathTransform = Preconditions.checkNotNull(artifactPathTransform);
  }

  @Override
  public int execute(final ExecutionContext context) {
    final ProjectFilesystem projectFilesystem = context.getProjectFilesystem();
    final File binDir = projectFilesystem.getFileForRelativePath(binDirectory);
    ImmutableSet<String> ignorePaths = ImmutableSet.of();

    final AtomicBoolean isSuccess = new AtomicBoolean(true);
    new DirectoryTraversal(binDir, ignorePaths) {

      @Override
      public void visit(File file, String relativePath) {
        // Once there has been an IOException, ignore the rest of the traversal.
        if (!isSuccess.get()) {
          return;
        }

        String source = new File(binDir, relativePath).getPath();
        String target = genDirectory + "/" + relativePath;
        try {
          projectFilesystem.createParentDirs(target);
          projectFilesystem.copyFile(source, target);
        } catch (IOException e) {
          isSuccess.set(false);
          e.printStackTrace(context.getStdErr());
        }

        String artifactPath = artifactPathTransform.apply(relativePath);
        buildableContext.recordArtifact(artifactPath);
      }

    }.traverse();

    return isSuccess.get() ? 0 : 1;
  }

}
