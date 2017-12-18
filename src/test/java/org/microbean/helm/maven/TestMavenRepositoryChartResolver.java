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

import java.io.File;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import hapi.chart.ChartOuterClass.Chart;

import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Profile;
import org.apache.maven.settings.Repository;
import org.apache.maven.settings.Settings;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;

import org.apache.maven.settings.building.DefaultSettingsBuilderFactory;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuildingResult;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsProblem;

import org.eclipse.aether.DefaultRepositoryCache;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;

import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;

import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.impl.DefaultServiceLocator.ErrorHandler;

import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RemoteRepository.Builder;

import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;

import org.eclipse.aether.spi.connector.transport.TransporterFactory;

import org.eclipse.aether.transfer.AbstractTransferListener;
import org.eclipse.aether.transfer.TransferEvent;

import org.eclipse.aether.transport.file.FileTransporterFactory;

import org.eclipse.aether.transport.http.HttpTransporterFactory;

import org.eclipse.aether.util.repository.DefaultMirrorSelector;

import org.microbean.helm.chart.resolver.ChartResolverException;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class TestMavenRepositoryChartResolver {

  private static Settings settings;
  
  private static RepositorySystem repositorySystem;

  private MavenRepositoryChartResolver resolver;
  
  public TestMavenRepositoryChartResolver() {
    super();
  }

  @BeforeClass
  public static void setupClass() throws SettingsBuildingException {

    // See
    // https://github.com/eclipse/aether-demo/blob/master/aether-demo-snippets/src/main/java/org/eclipse/aether/examples/util/Booter.java
    // et al. for general (undocumented) recipe.
    
    final DefaultServiceLocator serviceLocator = MavenRepositorySystemUtils.newServiceLocator();
    assertNotNull(serviceLocator);
    serviceLocator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
    serviceLocator.addService(TransporterFactory.class, FileTransporterFactory.class);
    serviceLocator.addService(TransporterFactory.class, HttpTransporterFactory.class);
    serviceLocator.setErrorHandler(new ErrorHandler() {
        @Override
        public final void serviceCreationFailed(final Class<?> type, final Class<?> impl, final Throwable exception) {
          if (exception != null) {
            exception.printStackTrace();
          }
        }
      });

    final RepositorySystem repositorySystem = serviceLocator.getService(RepositorySystem.class);
    assertNotNull(repositorySystem);
    TestMavenRepositoryChartResolver.repositorySystem = repositorySystem;

    final Settings settings = getSettings();
    assertNotNull(settings);
    TestMavenRepositoryChartResolver.settings = settings;

  }

  @Before
  public void setup() {
    assertNotNull(settings);
    
    final DefaultRepositorySystemSession repositorySystemSession = MavenRepositorySystemUtils.newSession();
    assertNotNull(repositorySystemSession);
    // repositorySystemSession.setUpdatePolicy("always");
    repositorySystemSession.setTransferListener(new TransferListener());
    repositorySystemSession.setOffline(settings.isOffline());
    repositorySystemSession.setCache(new DefaultRepositoryCache());

    final Collection<? extends Mirror> mirrors = settings.getMirrors();
    if (mirrors != null && !mirrors.isEmpty()) {
      final DefaultMirrorSelector mirrorSelector = new DefaultMirrorSelector();
      for (final Mirror mirror : mirrors) {
        assert mirror != null;
        mirrorSelector.add(mirror.getId(),
                           mirror.getUrl(),
                           mirror.getLayout(),
                           false, /* not a repository manager; settings.xml does not encode this information */
                           mirror.getMirrorOf(),
                           mirror.getMirrorOfLayouts());
      }
      repositorySystemSession.setMirrorSelector(mirrorSelector);
    }

    String localRepositoryString = settings.getLocalRepository();
    if (localRepositoryString == null) {
      localRepositoryString = System.getProperty("user.home") + "/.m2/repository";
    }
    
    final LocalRepository localRepository = new LocalRepository(localRepositoryString);
    final LocalRepositoryManager localRepositoryManager = repositorySystem.newLocalRepositoryManager(repositorySystemSession, localRepository);
    assertNotNull(localRepositoryManager);    
    repositorySystemSession.setLocalRepositoryManager(localRepositoryManager);

     List<RemoteRepository> remoteRepositories = new ArrayList<>();
    final Map<String, Profile> profiles = settings.getProfilesAsMap();
    if (profiles != null && !profiles.isEmpty()) {
      final Collection<String> activeProfileKeys = settings.getActiveProfiles();
      if (activeProfileKeys != null && !activeProfileKeys.isEmpty()) {
        for (final String activeProfileKey : activeProfileKeys) {
          final Profile activeProfile = profiles.get(activeProfileKey);
          if (activeProfile != null) {
            final Collection<Repository> repositories = activeProfile.getRepositories();
            if (repositories != null && !repositories.isEmpty()) {
              for (final Repository repository : repositories) {
                if (repository != null) {
                  Builder builder = new Builder(repository.getId(), repository.getLayout(), repository.getUrl());

                  final org.apache.maven.settings.RepositoryPolicy settingsReleasePolicy = repository.getReleases();
                  if (settingsReleasePolicy != null) {
                    final org.eclipse.aether.repository.RepositoryPolicy releasePolicy = new org.eclipse.aether.repository.RepositoryPolicy(settingsReleasePolicy.isEnabled(), settingsReleasePolicy.getUpdatePolicy(), settingsReleasePolicy.getChecksumPolicy());
                    builder = builder.setReleasePolicy(releasePolicy);
                  }

                  final org.apache.maven.settings.RepositoryPolicy settingsSnapshotPolicy = repository.getSnapshots();
                  if (settingsSnapshotPolicy != null) {
                    final org.eclipse.aether.repository.RepositoryPolicy snapshotPolicy = new org.eclipse.aether.repository.RepositoryPolicy(settingsSnapshotPolicy.isEnabled(), settingsSnapshotPolicy.getUpdatePolicy(), settingsSnapshotPolicy.getChecksumPolicy());
                    builder = builder.setSnapshotPolicy(snapshotPolicy);
                  }
                  
                  final RemoteRepository remoteRepository = builder.build();
                  assert remoteRepository != null;
                  remoteRepositories.add(remoteRepository);
                }
              }
            }
          }
        }
      }
    }
    final RemoteRepository mavenCentral = new Builder("central", "default", "http://central.maven.org/maven2/").build();
    assert mavenCentral != null;
    remoteRepositories.add(mavenCentral);
    remoteRepositories = repositorySystem.newResolutionRepositories(repositorySystemSession, remoteRepositories);
    assertNotNull(remoteRepositories);

    this.resolver = new MavenRepositoryChartResolver(repositorySystem, repositorySystemSession, remoteRepositories);
  }

  @Test
  public void testArbitraryHelmChartResolution() throws ChartResolverException {
    assertNotNull(this.resolver);

    final Chart.Builder chart = this.resolver.resolve("io.fabric8.platform.packages:ingress:tar.gz:helm", "4.0.208");
    assertNotNull(chart);
  }
  

  private static final Settings getSettings() throws SettingsBuildingException {
    final SettingsBuilder settingsBuilder = new DefaultSettingsBuilderFactory().newInstance(); // this method should be static!
    assert settingsBuilder != null;
    final DefaultSettingsBuildingRequest settingsBuildingRequest = new DefaultSettingsBuildingRequest();
    settingsBuildingRequest.setSystemProperties(System.getProperties());
    // settingsBuildingRequest.setUserProperties(userProperties); // TODO: implement this
    settingsBuildingRequest.setGlobalSettingsFile(new File("/usr/local/maven/conf/settings.xml")); // TODO: do this for real
    settingsBuildingRequest.setUserSettingsFile(new File(new File(System.getProperty("user.home")), ".m2/settings.xml"));
    final SettingsBuildingResult settingsBuildingResult = settingsBuilder.build(settingsBuildingRequest);
    assert settingsBuildingResult != null;
    final List<SettingsProblem> settingsBuildingProblems = settingsBuildingResult.getProblems();
    if (settingsBuildingProblems != null && !settingsBuildingProblems.isEmpty()) {
      throw new SettingsBuildingException(settingsBuildingProblems);
    }
    return settingsBuildingResult.getEffectiveSettings();
  }
  
  private static final class TransferListener extends AbstractTransferListener {

    private TransferListener() {
      super();
    }

    @Override
    public void transferInitiated(final TransferEvent event) {
      System.out.println("*** transfer initiated: " + event);
    }

    @Override
    public void transferStarted(final TransferEvent event) {
      System.out.println("*** transfer started: " + event);
    }

    @Override
    public void transferProgressed(final TransferEvent event) {
      System.out.println("*** transfer progressed: " + event);
    }

    @Override
    public void transferSucceeded(final TransferEvent event) {
      System.out.println("*** transfer succeeded: " + event);
    }

    @Override
    public void transferCorrupted(final TransferEvent event) {
      System.out.println("*** transfer corrupted: " + event);
    }

    @Override
    public void transferFailed(final TransferEvent event) {
      System.out.println("*** transfer failed: " + event);
    }
    
  }
  
}

