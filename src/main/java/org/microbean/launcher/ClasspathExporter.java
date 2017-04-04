/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2017 MicroBean.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.microbean.launcher;

import java.io.File;

import java.net.URI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;

import javax.enterprise.event.Observes;

import javax.enterprise.inject.Produces;

import javax.inject.Singleton;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import org.apache.maven.settings.Settings;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RepositorySystem;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

import org.eclipse.aether.collection.CollectRequest;

import org.eclipse.aether.graph.Dependency;

import org.eclipse.aether.repository.RemoteRepository;

import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;

import org.eclipse.aether.transfer.TransferListener;

import org.eclipse.aether.util.artifact.JavaScopes;

import org.eclipse.aether.util.filter.DependencyFilterUtils;

import org.microbean.maven.cdi.annotation.Resolution;

@Singleton
public final class ClasspathExporter {

  private final Set<URI> classpath;

  private String[] remainingCommandLineArguments;
  
  public ClasspathExporter() {
    super();
    this.classpath = new LinkedHashSet<>();
  }

  @Produces
  @ApplicationScoped
  private static final Options getOptions() {
    final Options options = new Options();

    final Option defaultGroupId = Option.builder()
      .longOpt("defaultGroupId")
      .hasArg(true)
      .required(false)
      .type(String.class)
      .argName("groupId")
      .desc("The default groupId to use in artifact coordinates.")
      .build();
    options.addOption(defaultGroupId);

    final Option artifact = Option.builder("ap")
      .longOpt("artifactPath")
      .hasArg()
      .required(false)
      .type(String.class)
      .argName("GAV coordinates")
      .desc("The double-colon separated GAV coordinates of the artifacts to resolve.")
      .build();
    options.addOption(artifact);

    return options;
  }

  @Produces
  @ApplicationScoped
  private final List<Dependency> produceDependencies(final CommandLine commandLine) {
    List<Dependency> returnValue = null;
    if (commandLine != null) {
      final String artifactPath = commandLine.getOptionValue("artifactPath");
      if (artifactPath != null) {
        final String[] gavs  = artifactPath.split("::");
        if (gavs != null && gavs.length > 0) {
          returnValue = new ArrayList<>();
          for (final String gav : gavs) {
            if (gav != null) {
              final String[] components = gav.split(":");
              assert components != null;
              assert components.length > 0;
              String groupId = null;
              String artifactId = null;
              String version = null;
              String classifier = null;
              String packaging = null;
              String scope = null;
              switch (components.length) {
              case 1:
                // artifact
                groupId = null; // will be set to default later
                artifactId = components[0];
                version = "LATEST";
                packaging = "jar";
                scope = JavaScopes.COMPILE;
                break;
              case 2:
                // group:artifact or artifact:version
                final String firstComponent = components[0];
                assert firstComponent != null;
                final String secondComponent = components[1];
                assert secondComponent != null;
                if (secondComponent.isEmpty()) {
                  throw new IllegalArgumentException("Unrecognized artifact coordinates: " + gav);
                } else if (Character.isDigit(secondComponent.charAt(0))) {
                  // artifact:version
                  groupId = null; // will default below
                  artifactId = firstComponent;
                  version = secondComponent;
                } else {
                  // group:artifact
                  groupId = firstComponent;
                  artifactId = secondComponent;
                  version = "LATEST";
                }
                packaging = "jar";
                scope = JavaScopes.COMPILE;
                break;
              case 3:
                // group:artifact:version
                groupId = components[0];
                artifactId = components[1];
                version = components[2];
                packaging = "jar";
                scope = JavaScopes.COMPILE;
                break;
              case 4:
                // group:artifact:version:packaging
                groupId = components[0];
                artifactId = components[1];
                version = components[2];
                packaging = components[3];
                scope = JavaScopes.COMPILE;
                break;
              case 5:
                // group:artifact:version:packaging:classifier
                groupId = components[0];
                artifactId = components[1];
                version = components[2];
                packaging = components[3];
                classifier = components[4];
                scope = JavaScopes.COMPILE;
                break;
              case 6:
                // group:artifact:version:packaging:classifier:scope
                groupId = components[0];
                artifactId = components[1];
                version = components[2];
                packaging = components[3];
                classifier = components[4];
                scope = components[5];
                break;
              default:
                throw new IllegalArgumentException("Unrecognized artifact coordinates: " + gav);
              }
              if (groupId == null || groupId.isEmpty()) {
                groupId = commandLine.getOptionValue("defaultGroupId");
              }
              if (groupId == null || groupId.isEmpty() ||
                  artifactId == null || artifactId.isEmpty()) {
                throw new IllegalArgumentException("Unrecognized artifact coordinates: " + gav);
              }
              if (version == null || version.isEmpty()) {
                version = "LATEST";
              }
              // note: classifier can be null
              if (packaging == null || packaging.isEmpty()) {
                packaging = "jar";
              }
              if (scope == null || scope.isEmpty()) {
                scope = JavaScopes.COMPILE;
              }
              assert scope != null;
              returnValue.add(new Dependency(new DefaultArtifact(groupId, artifactId, classifier, packaging, version), scope));
            }
          }
        }
      }
      this.remainingCommandLineArguments = commandLine.getArgs();
    }
    if (returnValue == null || returnValue.isEmpty()) {
      returnValue = Collections.emptyList();
    } else {
      returnValue = Collections.unmodifiableList(returnValue);
    }
    return returnValue;
  }
  
  private final void onStartup(@Observes @Initialized(ApplicationScoped.class) final Object event,
                               final RepositorySystem repositorySystem,
                               final RepositorySystemSession session,
                               @Resolution final List<RemoteRepository> remoteRepositories,
                               final List<Dependency> dependencies)
  throws DependencyResolutionException {
    Objects.requireNonNull(repositorySystem);
    Objects.requireNonNull(session);
    if (dependencies != null && !dependencies.isEmpty()) {
      final CollectRequest collectRequest = new CollectRequest((Dependency)null /* no root */, dependencies, remoteRepositories);
      final DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE)); // TODO: not sure about this filter if the user can specify individual scopes
      final DependencyResult dependencyResult = repositorySystem.resolveDependencies(session, dependencyRequest);
      assert dependencyResult != null;
      final List<ArtifactResult> artifactResults = dependencyResult.getArtifactResults();
      assert artifactResults != null;
      for (final ArtifactResult artifactResult : artifactResults) {
        if (artifactResult != null) {
          final Artifact resolvedArtifact = artifactResult.getArtifact();
          if (resolvedArtifact != null) {
            final File f = resolvedArtifact.getFile();
            assert f != null;
            assert f.isFile();
            assert f.canRead();
            this.classpath.add(f.toURI());
          }
        }
      }
    }
  }

  public final Set<URI> getClasspath() {
    return this.classpath;
  }

  public final String[] getRemainingCommandLineArguments() {
    return this.remainingCommandLineArguments;
  }
  
}
