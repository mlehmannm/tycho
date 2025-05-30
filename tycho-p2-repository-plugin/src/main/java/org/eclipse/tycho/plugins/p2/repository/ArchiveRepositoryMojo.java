/*******************************************************************************
 * Copyright (c) 2010, 2011 SAP SE and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     SAP SE - initial API and implementation
 *     Michael Pellaton (Netcetera) - add finalName mojo parameter
 *******************************************************************************/
package org.eclipse.tycho.plugins.p2.repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.attribute.FileTime;

import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.util.DefaultFileSet;
import org.eclipse.tycho.FileLockService;

/**
 * <p>
 * Creates a zip archive with the aggregated p2 repository.
 * </p>
 */
@Mojo(name = "archive-repository", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class ArchiveRepositoryMojo extends AbstractRepositoryMojo {

    @Component(role = Archiver.class, hint = "zip")
    private Archiver inflater;

    /**
     * <p>
     * Name of the generated zip file (without extension).
     * </p>
     */
    @Parameter(property = "project.build.finalName")
    private String finalName;

    /**
     * Whether or not to skip archiving the repository. False by default.
     */
    @Parameter(defaultValue = "false")
    private boolean skipArchive;

    /**
     * Timestamp for reproducible output archive entries, either formatted as ISO 8601 extended
     * offset date-time (e.g. in UTC such as '2011-12-03T10:15:30Z' or with an offset
     * '2019-10-05T20:37:42+06:00'), or as an int representing seconds since the epoch (like
     * <a href="https://reproducible-builds.org/docs/source-date-epoch/">SOURCE_DATE_EPOCH</a>).
     */
    @Parameter(defaultValue = "${project.build.outputTimestamp}")
    private String outputTimestamp;

    @Component
    private FileLockService fileLockService;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skipArchive) {
            return;
        }
        File repositoryLocation = getAssemblyRepositoryLocation();
        File destFile = getBuildDirectory().getChild(finalName + ".zip");
        try (var repoLock = fileLockService.lockVirtually(repositoryLocation);
                var destLock = fileLockService.lockVirtually(destFile);) {
            // configure for Reproducible Builds based on outputTimestamp value
            MavenArchiver.parseBuildOutputTimestamp(outputTimestamp).map(FileTime::from)
                    .ifPresent(modifiedTime -> inflater.configureReproducibleBuild(modifiedTime));
            inflater.addFileSet(DefaultFileSet.fileSet(repositoryLocation).prefixed(""));
            inflater.setDestFile(destFile);
            inflater.createArchive();
        } catch (ArchiverException | IOException e) {
            throw new MojoExecutionException("Error packing p2 repository", e);
        }
        getProject().getArtifact().setFile(destFile);
    }

}
