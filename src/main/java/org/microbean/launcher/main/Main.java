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
package org.microbean.launcher.main;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;

import java.security.PrivilegedAction;

import java.util.HashSet;
import java.util.Set;

import java.util.function.Consumer;

import org.microbean.maven.cdi.MavenExtension;

import org.microbean.launcher.ClasspathExporter;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;

import javax.inject.Provider;

import static java.security.AccessController.doPrivileged;

/**
 * A class whose {@link #main(String[])} method can be used to
 * {@linkplain SeContainerInitializer#initialize() start a CDI
 * application} given a collection of Maven Central artifact
 * coordinates.
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see #main(SeContainerInitializer, SeContainerInitializer, String[])
 *
 * @see ClasspathExporter
 */
public class Main {


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link Main}.
   */
  private Main() {
    super();
  }


  /*
   * Static methods.
   */
  
  
  /**
   * Calls the {@link #main(SeContainerInitializer,
   * SeContainerInitializer, Consumer, String[])} method with {@code
   * null}, {@code null}, {@code null} and the supplied {@code
   * commandLineArguments}.
   *
   * @param commandLineArguments the command line arguments; may be
   * {@code null}
   *
   * @exception MalformedURLException if there was a problem
   * formulating a classpath {@link URL}
   *
   * @see #main(SeContainerInitializer, SeContainerInitializer, Consumer, String[])
   */
  public static final void main(final String[] commandLineArguments) throws MalformedURLException {
    main(null, null, null, commandLineArguments);
  }

  /**
   * Calls the {@link #main(SeContainerInitializer,
   * SeContainerInitializer, Consumer, String[])} method with the
   * supplied {@code boostrapInitializer}, the supplied {@code
   * initializer}, {@code null} and the supplied {@code
   * commandLineArguments}.
   *
   * @param bootstrapInitializer the {@link SeContainerInitializer} to
   * use while performing dependency resolution; may be {@code null}
   * in which case the return value of {@link
   * SeContainerInitializer#newInstance()} will be used instead
   *
   * @param initializer the {@link SeContainerInitializer} to use to
   * {@linkplain SeContainerInitializer#initialize() create and run}
   * the "real" CDI container; may be {@code null}
   * in which case the return value of {@link
   * SeContainerInitializer#newInstance()} will be used instead
   *
   * @param commandLineArguments the command line arguments; may be
   * {@code null}
   *
   * @exception MalformedURLException if there was a problem
   * formulating a classpath {@link URL}
   *
   * @see #main(SeContainerInitializer, SeContainerInitializer, Consumer, String[])
   */
  public static final void main(SeContainerInitializer bootstrapInitializer, SeContainerInitializer initializer, final String[] commandLineArguments) throws MalformedURLException {
    main(bootstrapInitializer, initializer, null, commandLineArguments);
  }

  /**
   * {@linkplain SeContainerInitializer#initialize() Starts} a
   * {@linkplain SeContainer CDI container} with a classpath formed
   * from the existing classpath and the local filesystem locations of
   * Maven artifacts and their transitive dependencies resolvable from
   * the value of the {@code --artifactPath} command line option (or
   * its short form, {@code -ap}).
   *
   * <p>The supplied {@code bootstrapInitializer} is used to
   * {@linkplain SeContainerInitializer#initialize() start} a
   * {@linkplain SeContainer CDI container} that indirectly uses
   * components from the <a
   * href="https://ljnelson.github.io/microbean-maven-cdi/">MicroBean
   * Maven CDI</a>, <a
   * href="https://ljnelson.github.io/microbean-main/">MicroBean
   * Main</a> and <a
   * href="https://ljnelson.github.io/microbean-commons-cli/">MicroBean
   * Commons CLI</a> projects to parse the value of the {@code
   * --artifactPath} command line option and, by invoking the <a
   * href="https://ljnelson.github.io/microbean-maven-cdi/">MicroBean
   * Maven CDI</a> machinery, <a
   * href="https://maven.apache.org/resolver/maven-resolver-api/apidocs/org/eclipse/aether/RepositorySystem.html#resolveDependencies(org.eclipse.aether.RepositorySystemSession,%20org.eclipse.aether.resolution.DependencyRequest)">resolves
   * each such artifact mentioned, together with its transitive
   * compile- and runtime-scoped dependencies</a> into the user's <a
   * href="https://maven.apache.org/guides/introduction/introduction-to-repositories.html">local
   * Maven repository</a>.  A classpath is built from the (local
   * filesystem) locations of these artifacts and the current
   * classpath.</p>
   *
   * <p>The {@code --artifactPath} command line option (or its short
   * form, {@code -ap}) takes a whitespace- and/or comma-separated
   * list of Maven artifact coordinates of one of the following
   * forms:</p>
   *
   * <ul>
   *
   * <li>{@code groupId:artifactId:version:packaging:classifier:scope}</li>
   *
   * <li>{@code groupId:artifactId:version:packaging:classifier}</li>
   *
   * <li>{@code groupId:artifactId:version:packaging}</li>
   *
   * <li>{@code groupId:artifactId:version}</li>
   *
   * <li>{@code groupId:artifactId}</li>
   *
   * <li>{@code artifactId:version} (requires the {@code
   * --defaultGroupId} command line option to be specified; see
   * below)</li>
   *
   * </ul>
   *
   * <p>In all cases, {@code packaging} is the kind of artifact
   * desired, e.g. {@code jar}, {@code war}, {@code ear}, etc., and
   * usually translates to a three-character file extension suffix.
   * It defaults to {@code jar}.  {@code classifier} is <a
   * href="https://maven.apache.org/pom.html#Dependencies">Maven's
   * notion of a classifier</a>&mdash;an identifier to further
   * classify an artifact, and is usually not specified.  {@code
   * scope} is one of {@code compile}, {@code runtime}, or {@code
   * test} and is {@code compile} by default.</p>
   *
   * <p>If a {@code --defaultGroupId} command line option is present,
   * then its value is taken to be a <a
   * href="https://maven.apache.org/pom.html#Dependencies">Maven group
   * identifier</a>, and elements in the artifact path that have that
   * group identifier may be specified as simply {@code
   * artifactId:version}.</p>
   *
   * <p>Examples of valid command line options described here include:</p>
   *
   * <ul>
   *
   * <li>The most common style of specification:
   * {@code --artifactPath com.foobar:frobnicator:1.0,com.foobar:caturgiator:2.0}</li>
   *
   * <li>Using {@code --defaultGroupId}:
   * {@code --defaultGroupId com.foobar -ap frobnicator:1.0,caturgiator:2.0}</li>
   *
   * <li>Maximally specific (note the double-colon eliminating the
   * {@code classifier} component):
   * {@code --artifactPath com.foobar:frobnicator:1.0:jar::compile,com.foobar:caturgiator:2.0:jar::compile}</li>
   *
   * </ul>
   *
   * <p>The "bootstrap" dependency resolution CDI container is then
   * {@linkplain SeContainer#close() closed} and discarded.</p>
   *
   * <p>The classpath constructed according to the prior description
   * is the classpath used by the supplied {@code initializer}, which
   * is then used to {@linkplain SeContainerInitializer#initialize()
   * start} the "real" {@linkplain SeContainer CDI container}.</p>
   *
   * <p>If there is a {@code consumer}, then the {@link SeContainer}
   * so started is supplied to it.</p>
   *
   * <p>Finally the {@link SeContainer} is {@linkplain
   * SeContainer#close() closed} and this method returns.
   *
   * @param bootstrapInitializer the {@link SeContainerInitializer} to
   * use while performing dependency resolution; may be {@code null}
   * in which case the return value of {@link
   * SeContainerInitializer#newInstance()} will be used instead
   *
   * @param initializer the {@link SeContainerInitializer} to use to
   * {@linkplain SeContainerInitializer#initialize() create and run}
   * the "real" CDI container; may be {@code null}
   * in which case the return value of {@link
   * SeContainerInitializer#newInstance()} will be used instead
   *
   * @param consumer a {@link Consumer} of an {@link SeContainer}; may
   * be {@code null}
   *
   * @param commandLineArguments the command line arguments; may be
   * {@code null}
   *
   * @exception MalformedURLException if there was a problem
   * formulating a classpath {@link URL}
   * 
   * @see org.microbean.main.Main
   *
   * @see SeContainerInitializer
   *
   * @see SeContainer
   *
   * @see <a
   * href="https://ljnelson.github.io/microbean-maven-cdi/">MicroBean
   * Maven CDI</a>
   *
   * @see <a
   * href="https://ljnelson.github.io/microbean-main/">MicroBean
   * Main</a>
   *
   * @see <a
   * href="https://ljnelson.github.io/microbean-commons-cli/">MicroBean
   * Commons CLI</a>
   */
  public static final void main(SeContainerInitializer bootstrapInitializer, SeContainerInitializer initializer, final Consumer<? super SeContainer> consumer, final String[] commandLineArguments) throws MalformedURLException {
    if (bootstrapInitializer == null) {
      bootstrapInitializer = SeContainerInitializer.newInstance();
    }
    assert bootstrapInitializer != null;
    bootstrapInitializer.addExtensions(new MavenExtension());
    bootstrapInitializer.addBeanClasses(ClasspathExporter.class, org.microbean.main.Main.class);

    final ClasspathExporter[] exporterHolder = new ClasspathExporter[1];
    org.microbean.main.Main.main(bootstrapInitializer, c -> exporterHolder[0] = c.select(ClasspathExporter.class).get(), commandLineArguments);
    final ClasspathExporter exporter = exporterHolder[0];
    assert exporter != null;
    final Set<URI> classpathAdditions = exporter.getClasspath();
    
    if (initializer == null) {
      initializer = SeContainerInitializer.newInstance();
    }
    final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
    URLClassLoader urlClassLoader = null;
    if (classpathAdditions != null && !classpathAdditions.isEmpty()) {
      final URL[] urls = new URL[classpathAdditions.size()];
      int i = 0;
      for (final URI uri : classpathAdditions) {
        urls[i++] = uri.toURL();
      }
      urlClassLoader = doPrivileged((PrivilegedAction<URLClassLoader>)() -> new URLClassLoader(urls, contextClassLoader));
      initializer.setClassLoader(urlClassLoader);
    }
    
    try {
      if (urlClassLoader != null) {      
        Thread.currentThread().setContextClassLoader(urlClassLoader);
      }
      org.microbean.main.Main.main(initializer, null, exporter.getRemainingCommandLineArguments());
    } finally {
      Thread.currentThread().setContextClassLoader(contextClassLoader);
    }
  }
  
}
