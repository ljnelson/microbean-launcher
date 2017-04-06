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

  
  private Main() {
    super();
  }


  /*
   * Static methods.
   */
  
  
  public static final void main(final String[] commandLineArguments) throws MalformedURLException {
    main(null, null, commandLineArguments);
  }
  
  public static final void main(SeContainerInitializer bootstrapInitializer, SeContainerInitializer initializer, final String[] commandLineArguments) throws MalformedURLException {
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
