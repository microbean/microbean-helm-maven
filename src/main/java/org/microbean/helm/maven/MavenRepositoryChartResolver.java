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
package org.microbean.helm.maven;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;

import java.nio.file.Files;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

import hapi.chart.ChartOuterClass.Chart;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RepositorySystem;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

import org.eclipse.aether.repository.RemoteRepository;

import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

import org.kamranzafar.jtar.TarInputStream;

import org.microbean.helm.chart.AbstractChartLoader; // for javadoc only
import org.microbean.helm.chart.TapeArchiveChartLoader;
import org.microbean.helm.chart.ZipInputStreamChartLoader;

import org.microbean.helm.chart.resolver.AbstractChartResolver;
import org.microbean.helm.chart.resolver.ChartResolverException;

/**
 * An {@link AbstractChartResolver} capable of resolving Helm charts
 * from Maven repositories.
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see #resolve(String, String)
 */
public class MavenRepositoryChartResolver extends AbstractChartResolver {


  /*
   * Instance fields.
   */


  /**
   * The {@link RepositorySystem} responsible for performing the
   * actual resolution.
   *
   * <p>This field is never {@code null}.</p>
   *
   * @see #MavenRepositoryChartResolver(RepositorySystem,
   * RepositorySystemSession, List)
   *
   * @see RepositorySystem
   */
  private final RepositorySystem repositorySystem;

  /**
   * The {@link RepositorySystemSession} governing artifact resolution.
   *
   * <p>This field is never {@code null}.</p>
   *
   * @see #MavenRepositoryChartResolver(RepositorySystem,
   * RepositorySystemSession, List)
   *
   * @see RepositorySystemSession
   */
  private final RepositorySystemSession session;

  /**
   * A {@link List} of {@link RemoteRepository} instances from which
   * resolution will be attempted.
   *
   * <p>This field may be {@code null}.</p>
   *
   * @see #MavenRepositoryChartResolver(RepositorySystem,
   * RepositorySystemSession, List)
   *
   * @see RemoteRepository
   */
  private final List<RemoteRepository> remoteRepositories;


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link MavenRepositoryChartResolver}.
   *
   * @param repositorySystem the {@link RepositorySystem} responsible
   * for performing the actual resolution; must not be {@code null}
   *
   * @param repositorySystemSession the {@link
   * RepositorySystemSession} governing artifact resolution; must not
   * be {@code null}
   *
   * @param remoteRepositories a {@link List} of {@link
   * RemoteRepository} instances from which resolution will be
   * attempted; may be {@code null}
   *
   * @exception NullPointerException if {@code repositorySystem} or
   * {@code session} is {@code null}
   *
   * @see #getRepositorySystem()
   *
   * @see #getSession()
   *
   * @see #getRemoteRepositories()
   */
  public MavenRepositoryChartResolver(final RepositorySystem repositorySystem,
                                      final RepositorySystemSession session,
                                      final List<RemoteRepository> remoteRepositories) {
    super();
    this.repositorySystem = Objects.requireNonNull(repositorySystem);
    this.session = Objects.requireNonNull(session);
    this.remoteRepositories = remoteRepositories;
  }


  /*
   * Instance methods.
   */


  /**
   * Returns the {@link RepositorySystem} responsible for performing the
   * actual resolution.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>Overrides of this method must not return {@code null}.</p>
   *
   * @return the {@link RepositorySystem} responsible for performing the
   * actual resolution; never {@code null}
   *
   * @see #MavenRepositoryChartResolver(RepositorySystem,
   * RepositorySystemSession, List)
   */
  public RepositorySystem getRepositorySystem() {
    return this.repositorySystem;
  }

  /**
   * Returns the {@link RepositorySystemSession} governing resolution.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>Overrides of this method must not return {@code null}.</p>
   *
   * @return the {@link RepositorySystemSession} governing resolution;
   * never {@code null}
   *
   * @see #MavenRepositoryChartResolver(RepositorySystem,
   * RepositorySystemSession, List)
   */
  public RepositorySystemSession getSession() {
    return this.session;
  }

  /**
   * Returns the {@link List} of {@link RemoteRepository} instances
   * from which resolution will be attempted.
   *
   * <p>This method may return {@code null}.</p>
   *
   * <p>Overrides of this method are permitted to return {@code
   * null}.</p>
   *
   * @return the {@link List} of {@link RemoteRepository} instances
   * from which resolution will be attempted, or {@code null}
   *
   * @see #MavenRepositoryChartResolver(RepositorySystem,
   * RepositorySystemSession, List)
   */
  public List<RemoteRepository> getRemoteRepositories() {
    return this.remoteRepositories;
  }

  /**
   * Creates and returns a {@link
   * hapi.chart.ChartOuterClass.Chart.Builder} representing a Helm
   * chart resolvable at the given Maven repository coordinates.
   *
   * @param coordinatesWithoutVersion a {@link String} of one of the
   * following forms: {@code groupId:artifactId}, {@code
   * groupId:artifactId:packaging}, or {@code
   * groupId:artifactId:packaging:classifier}; must not be {@code
   * null}
   *
   * @param chartVersion the version of the Helm chart artifact to
   * resolve; may be {@code null} in which case {@code LATEST} will be
   * used instead
   *
   * @return a non-{@code null} {@link
   * hapi.chart.ChartOuterClass.Chart.Builder}
   *
   * @exception NullPointerException if {@code coordinatesWithoutVersion} is {@code null}
   *
   * @exception ChartResolverException if {@code
   * coordinatesWithoutVersion} is malformed, if either the {@link
   * #getRepositorySystem()} or {@link #getSession()} method returns
   * {@code null}, if an {@link ArtifactResolutionException} was
   * encountered during artifact resolution, or if the {@link
   * #loadChart(File, String)} method throws a {@link
   * ChartResolverException}
   *
   * @see #resolve(Artifact)
   *
   * @see #loadChart(File, String)
   */
  @Override
  public final Chart.Builder resolve(final String coordinatesWithoutVersion, String chartVersion) throws ChartResolverException {
    Objects.requireNonNull(coordinatesWithoutVersion);
    if (chartVersion == null) {
      chartVersion = "LATEST"; // TODO: not sure if this will work
    }
    
    final String[] parts = coordinatesWithoutVersion.split(":");
    assert parts != null;
    if (parts.length < 2 || parts.length > 4) {
      throw new ChartResolverException(new IllegalArgumentException("coordinatesWithoutVersion: " + coordinatesWithoutVersion));
    }
    final String groupId = parts[0];
    final String artifactId = parts[1];
    final String packaging;
    final String classifier;
    if (parts.length >= 3) {
      packaging = parts[2];
      if (parts.length == 4) {
        classifier = parts[3];
      } else {
        classifier = null;
      }
    } else {
      packaging = "tgz";
      classifier = null;
    }

    System.out.println("*** groupId: " + groupId);
    System.out.println("*** artifactId: " + artifactId);
    System.out.println("*** packaging: " + packaging);
    System.out.println("*** classifier: " + classifier);

    final Artifact chart = new DefaultArtifact(groupId, artifactId, classifier, packaging, chartVersion);
    return this.resolve(chart);
  }

  /**
   * Creates and returns a {@link
   * hapi.chart.ChartOuterClass.Chart.Builder} representing a Helm
   * chart resolvable at the Maven repository coordinates represented
   * by the supplied {@link Artifact}.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>Overrides of this method must not return {@code null}.</p>
   *
   * @param chart the {@link Artifact} representing the Helm chart to
   * resolve; must not be {@code null}
   *
   * @return a non-{@code null} {@link
   * hapi.chart.ChartOuterClass.Chart.Builder}
   *
   * @exception NullPointerException if {@code chart} is {@code null}
   *
   * @exception ChartResolverException if either the {@link
   * #getRepositorySystem()} or {@link #getSession()} method returns
   * {@code null}, if an {@link ArtifactResolutionException} was
   * encountered during artifact resolution, or if the {@link
   * #loadChart(File, String)} method throws a {@link
   * ChartResolverException}
   *
   * @see #loadChart(File, String)
   */
  public Chart.Builder resolve(final Artifact chart) throws ChartResolverException {
    Objects.requireNonNull(chart);

    final RepositorySystem repositorySystem = this.getRepositorySystem();
    if (repositorySystem == null) {
      throw new ChartResolverException(new IllegalStateException("getRepositorySystem() == null"));
    }

    final RepositorySystemSession session = this.getSession();
    if (session == null) {
      throw new ChartResolverException(new IllegalStateException("getSession() == null"));
    }
    
    List<RemoteRepository> remoteRepositories = this.getRemoteRepositories();
    if (remoteRepositories == null) {
      remoteRepositories = Collections.emptyList();
    }
    
    final ArtifactRequest request = new ArtifactRequest(chart, remoteRepositories, null);

    ArtifactResult result = null;
    try {
      result = repositorySystem.resolveArtifact(session, request);
    } catch (final ArtifactResolutionException artifactResolutionException) {
      throw new ChartResolverException(artifactResolutionException);
    }
    
    final Artifact resolvedChart;
    if (result.isResolved()) {
      resolvedChart = result.getArtifact();
    } else {
      resolvedChart = null;
    }
    
    if (resolvedChart == null) {
      final List<? extends Exception> exceptions = result.getExceptions();
      if (exceptions == null || exceptions.isEmpty()) {
        throw new ChartResolverException();
      } else if (exceptions.size() == 1) {
        throw new ChartResolverException(exceptions.get(0));
      } else {
        final Iterator<? extends Exception> iterator = exceptions.iterator();
        assert iterator != null;
        assert iterator.hasNext();
        final Exception root = iterator.next();
        assert root != null;
        final ChartResolverException throwMe = new ChartResolverException(root);
        assert iterator.hasNext();
        while (iterator.hasNext()) {
          throwMe.addSuppressed(iterator.next());
        }
        throw throwMe;
      }
    }
    assert resolvedChart != null;
    
    final File chartFile = resolvedChart.getFile();
    assert chartFile != null;

    final Chart.Builder returnValue = this.loadChart(chartFile, chart.getExtension());
    return returnValue;
  }

  /**
   * Returns a {@link hapi.chart.ChartOuterClass.Chart.Builder}
   * representing the Helm chart contained by the supplied {@linkplain
   * File#isFile() regular} {@link File}.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>Overrides of this method must not return {@code null}.</p>
   *
   * <p>This implementation will return a {@link
   * hapi.chart.ChartOuterClass.Chart.Builder} provided that the
   * supplied {@link File} is either a GZIP-encoded tape archive or a
   * ZIP archive.  Overrides should feel free to expand these
   * capabilities.</p>
   *
   * @param chartFile the {@link File} containing a Helm chart; must
   * not be {@code null}
   *
   * @param packaging the kind of archive or other packaging mechanism
   * that the supplied {@code chartFile} value is; must not be {@code
   * null}; examples include {@code tgz}, {@code tar.gz}, {@code zip}
   * and the like; overrides of this method may use this parameter to
   * help in determining how to read the supplied {@code chartFile}
   *
   * @return a non-{@code null} {@link
   * hapi.chart.ChartOuterClass.Chart.Builder}, usually but not
   * necessarily as produced by an {@link AbstractChartLoader}
   * implementation
   *
   * @exception NullPointerException if {@code chartFile} or {@code
   * packaging} is {@code null}
   *
   * @exception ChartResolverException if the chart could not be
   * loaded for any reason
   *
   * @see #resolve(String, String)
   */
  protected Chart.Builder loadChart(final File chartFile, final String packaging) throws ChartResolverException {
    Objects.requireNonNull(chartFile);
    Objects.requireNonNull(packaging);
    Chart.Builder returnValue = null;
    if ("tgz".equalsIgnoreCase(packaging) || "tar.gz".equalsIgnoreCase(packaging) || "helm.tar.gz".equalsIgnoreCase(packaging)) {
      try (final TapeArchiveChartLoader loader = new TapeArchiveChartLoader()) {
        returnValue = loader.load(new TarInputStream(new GZIPInputStream(new BufferedInputStream(Files.newInputStream(chartFile.toPath())))));
      } catch (final IOException exception) {
        throw new ChartResolverException(exception.getMessage(), exception);
      }
    } else if ("jar".equalsIgnoreCase(packaging) || "zip".equalsIgnoreCase(packaging)) {
      try (final ZipInputStreamChartLoader loader = new ZipInputStreamChartLoader()) {
        returnValue = loader.load(new ZipInputStream(new BufferedInputStream(Files.newInputStream(chartFile.toPath()))));
      } catch (final IOException exception) {
        throw new ChartResolverException(exception.getMessage(), exception);
      }
    } else {
      throw new ChartResolverException("Cannot load chart: " + chartFile + "; unhandled packaging: " + packaging);
    }
    return returnValue;
  }
  
}
