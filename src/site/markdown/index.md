# MicroBean Launcher

The MicroBean Launcher project enables the composition of a CDI
application by (a) providing a `main` method and (b) supporting an
effective classpath consisting of Maven-style artifact coordinates.

Given such an environment, a CDI application can be composed out of
its Maven-repository-addressable constituent parts by simply
specifying them on the command line.

## Command Line

To run a CDI SE application from the command line, ensure that this
project and its runtime dependencies are present on your classpath,
and then type:

    java org.microbean.launcher.main.Main --artifact-path com.foobar:foobar-frobnicator:1.0 com.bizbaw:bizbaw-caturgiator:2.0
    
&hellip;where `com.foobar` and `com.bizbaw` are Maven-style group
identifiers, and `foobar-frobnicator` and `bizbaw-caturgiator` are
Maven-style artifact identifiers, and `1.0` and `2.0` are Maven-style
version identifiers.

The end result will be that these dependencies will be [resolved][0]
to the local filesystem using
the [Maven Artifact Resolver componentry][1], and their local
filesystem representations will be assembled into a classpath.

Obviously, if the artifacts in question already exist in the local
Maven repository, then no resolution will take place.

Once the classpath has been thus established, the [MicroBean Main][2]
project's `Main` class' [`main` method][3] will be invoked with that
classpath.

[0]: https://maven.apache.org/components/resolver/maven-resolver-api/apidocs/org/eclipse/aether/RepositorySystem.html#resolveDependencies(org.eclipse.aether.RepositorySystemSession,%20org.eclipse.aether.resolution.DependencyRequest)
[1]: https://maven.apache.org/components/resolver/maven-resolver-api/apidocs/
[2]: https://ljnelson.github.io/microbean-main/
[3]: https://ljnelson.github.io/microbean-main/apidocs/org/microbean/main/Main.html#main-javax.enterprise.inject.se.SeContainerInitializer-java.lang.String:A-


