/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2017-2018 microBean.
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
import java.util.Arrays;
import java.util.Collection; // for javadoc only
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

/**
 * A special-purpose class that converts a list of Maven-style
 * artifact coordinates into a {@link Set} of classpath {@link URI}s
 * when it is added to a CDI container.
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see #getClasspath()
 *
 * @see #getRemainingCommandLineArguments()
 */
@Singleton
public final class ClasspathExporter {


  /*
   * Instance fields.
   */


  /**
   * A {@link Set} of {@link URI}s each element of which represents a
   * Java classpath element.
   *
   * <p>This field is never {@code null}.</p>
   *
   * @see #getClasspath()
   */
  private final Set<URI> classpath;

  /**
   * A {@link String} array representing any command line arguments
   * that are "left over" after this class consumes any arguments that
   * supply it with a list of Maven-style artifact coordinates.
   *
   * <p>This field may be {@code null}.</p>
   *
   * @see #getRemainingCommandLineArguments()
   */
  private String[] remainingCommandLineArguments;

  /**
   * A {@link List} of {@link Dependency} instances that were either
   * {@linkplain #setDependencies(List) explicitly set} or {@linkplain
   * #produceDependencies(CommandLine) computed}.
   *
   * <p>This field may be {@code null}.</p>
   *
   * @see #getDependencies()
   *
   * @see #setDependencies(List)
   *
   * @see #produceDependencies(CommandLine)
   */
  private List<Dependency> dependencies;


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link ClasspathExporter}.
   */
  public ClasspathExporter() {
    super();
    this.classpath = new LinkedHashSet<>();
  }


  /*
   * Instance methods.
   */


  /**
   * A <a
   * href="http://docs.jboss.org/cdi/spec/2.0/cdi-spec.html#producer_method"
   * target="_parent">producer method</a> that {@linkplain Produces
   * produces} an {@link ApplicationScoped application-scoped} {@link
   * List} of {@link Dependency} instances that represent Maven-style
   * artifact coordinates from either {@linkplain
   * #setDependencies(List) explicitly-set <code>Dependency</code>
   * instances} or {@linkplain CommandLine command-line arguments} or
   * system properties.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * @param commandLine a {@link CommandLine} instance; may be {@code
   * null} in which case an {@linkplain Collection#isEmpty() empty}
   * {@link List} will be returned
   *
   * @return a non-{@code null} {@link List} of {@link Dependency}
   * instances
   *
   * @see #setDependencies(List)
   *
   * @see #getDependencies()
   */
  @Produces
  @ApplicationScoped
  public final List<Dependency> produceDependencies(final CommandLine commandLine) {
    final List<Dependency> returnValue;
    List<Dependency> dependencies = this.getDependencies();
    if (dependencies == null) {
      returnValue = new ArrayList<>();
      if (commandLine != null) {
        final String[] artifactPath;
        if (commandLine.hasOption("artifactPath")) {
          artifactPath = commandLine.getOptionValues("artifactPath");
        } else {
          final String artifactPathSystemProperty = System.getProperty("maven.artifact.path");
          if (artifactPathSystemProperty == null) {
            artifactPath = new String[0];
          } else {
            artifactPath = new String[] { artifactPathSystemProperty };
          }
        }
        if (artifactPath != null) {
          Arrays.stream(artifactPath)
            .flatMap(item -> Arrays.stream(item.split("[, ]+")))
            .forEach(gav -> {
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
              });
        }
        this.remainingCommandLineArguments = commandLine.getArgs();
      }
      this.dependencies = returnValue;
    } else {
      returnValue = dependencies;
    }
    if (returnValue == null || returnValue.isEmpty()) {
      return Collections.emptyList();
    } else {
      return Collections.unmodifiableList(returnValue);
    }
  }

  /**
   * Returns the {@link List} of {@link Dependency} instances that
   * will be used by this class to compute the eventual classpath
   * returned by the {@link #getClasspath()} method.
   *
   * <p>This method may return {@code null}.</p>
   *
   * @return a {@link List} of {@link Dependency} instances, or {@code
   * null}
   *
   * @see #setDependencies(List)
   */
  public final List<Dependency> getDependencies() {
    final List<Dependency> returnValue;
    if (this.dependencies == null) {
      returnValue = null;
    } else if (this.dependencies.isEmpty()) {
      returnValue = Collections.emptyList();
    } else {
      returnValue = Collections.unmodifiableList(this.dependencies);
    }
    return returnValue;
  }
  
  /**
   * Sets the {@link List} of {@link Dependency} instances that will
   * be used by this {@link ClasspathExporter} to compute the
   * classpath returned by the {@link #getClasspath()} method.
   *
   * <p><strong>Users of this class should not feel obligated to call
   * this method.</strong> If this method is not explicitly called,
   * this class will infer the list of dependencies from command line
   * arguments and system properties.</p>
   *
   * @param dependencies a {@link List} of {@link Dependency}
   * instances; may be {@code null}
   *
   * @see #getDependencies()
   *
   * @see #getClasspath()
   */
  public final void setDependencies(final List<Dependency> dependencies) {
    if (dependencies == null || dependencies.isEmpty()) {
      this.dependencies = Collections.emptyList();
    } else {
      this.dependencies = new ArrayList<>(dependencies);
    }
  }

  /**
   * Given a {@link List} of {@link Dependency} elements supplied as
   * the value of the {@code dependencies} parameter, uses the
   * supplied {@link RepositorySystem} to {@linkplain
   * RepositorySystem#resolveDependencies(RepositorySystemSession,
   * DependencyRequest) resolve} them in {@linkplain
   * JavaScopes#COMPILE compile scope}, and uses their {@linkplain
   * Artifact#getFile() associated <code>File</code> instances} to
   * build a {@linkplain #getClasspath() classpath} out of them.
   *
   * <p>The classpath so built will consist of {@link URI}s that
   * represent {@link File}s on the local filesystem.</p>
   *
   * @param event the event signalling container startup; ignored; may
   * be {@code null}
   *
   * @param repositorySystem the {@link RepositorySystem} used for
   * dependency resolution; must not be {@code null}
   *
   * @param session the {@link RepositorySystemSession} used to
   * interact with the supplied {@code repositorySystem}; must not be
   * {@code null}
   *
   * @param remoteRepositories a {@link List} of {@link
   * RemoteRepository} instances to use for {@linkplain Resolution
   * dependency resolution} (as opposed to, say, deployment or some
   * other repository operation); may be {@code null} but probably
   * shouldn't be
   *
   * @param dependencies a {@link List} of {@link Dependency}
   * instances to {@linkplain
   * RepositorySystem#resolveDependencies(RepositorySystemSession,
   * DependencyRequest) resolve}; may be {@code null} in which case no
   * action will be taken and the {@link #getClasspath()} method will
   * return an {@linkplain Collections#emptySet() empty
   * <code>Set</code>} of {@link URI}s
   *
   * @exception NullPointerException if {@code repositorySystem} or
   * {@code session} is {@code null}
   *
   * @exception DependencyResolutionException if there was a problem
   * with {@linkplain
   * RepositorySystem#resolveDependencies(RepositorySystemSession,
   * DependencyRequest) dependency resolution};
   *
   * @see #getClasspath()
   *
   * @see
   * RepositorySystem#resolveDependencies(RepositorySystemSession,
   * DependencyRequest)
   */
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
            final URI uri = f.toURI();
            assert uri != null;
            this.classpath.add(uri);
          }
        }
      }
    }
  }

  /**
   * Returns an {@linkplain Collections#unmodifiableSet(Set)
   * unmodifiable <code>Set</code>} of {@link URI}s representing Java
   * classpath elements that resulted from examining the user-supplied
   * <em>artifact path</em>&mdash;a list of Maven-style artifact
   * coordinates.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * @return a non-{@code null}, {@linkplain
   * Collections#unmodifiableSet(Set) unmodifiable <code>Set</code>}
   * of {@link URI}s representing Java classpath elements
   */
  public final Set<URI> getClasspath() {
    if (this.classpath == null || this.classpath.isEmpty()) {
      return Collections.emptySet();
    } else {
      return Collections.unmodifiableSet(this.classpath);
    }
  }

  /**
   * Returns a {@link String} array representing any command line
   * arguments that this class did <em>not</em> "consume" as it tried
   * to determine the user-supplied artifact path.
   *
   * <p>This method will never return {@code null}.</p>
   *
   * @return a non-{@code null} {@link String} array representing any
   * command line arguments that this class did <em>not</em> "consume"
   * as it tried to determine the user-supplied artifact path
   */
  public final String[] getRemainingCommandLineArguments() {
    if (this.remainingCommandLineArguments == null) {
      return new String[0];
    } else {
      return this.remainingCommandLineArguments.clone();
    }
  }
  

  /*
   * Static methods.
   */


  /**
   * A <a
   * href="http://docs.jboss.org/cdi/spec/2.0/cdi-spec.html#producer_method"
   * target="_parent">producer method</a> that {@linkplain Produces
   * produces} an {@link Options} instance representing the command
   * line options that this class looks for in its {@link
   * #produceDependencies(CommandLine)} method.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * @return a non-{@code null} {@link Options} representing the
   * command line options this class will look for in its {@link
   * #produceDependencies(CommandLine)} method
   *
   * @see #produceDependencies(CommandLine)
   */
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
      .hasArgs()
      .required(false)
      .type(String.class)
      .argName("GAV coordinates")
      .desc("The double-colon separated GAV coordinates of the artifacts to resolve.")
      .build();
    options.addOption(artifact);

    return options;
  }

  
}
