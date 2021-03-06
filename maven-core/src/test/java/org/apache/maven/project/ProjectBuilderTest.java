package org.apache.maven.project;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.apache.maven.AbstractCoreMavenComponentTestCase;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.building.FileModelSource;
import org.apache.maven.model.building.ModelSource;
import org.apache.maven.shared.utils.io.FileUtils;

import com.google.common.io.Files;

public class ProjectBuilderTest
    extends AbstractCoreMavenComponentTestCase
{
    @Override
    protected String getProjectsDirectory()
    {
        return "src/test/projects/project-builder";
    }

    public void testSystemScopeDependencyIsPresentInTheCompileClasspathElements()
        throws Exception
    {
        File pom = getProject( "it0063" );

        Properties eps = new Properties();
        eps.setProperty( "jre.home", new File( pom.getParentFile(), "jdk/jre" ).getPath() );

        MavenSession session = createMavenSession( pom, eps );
        MavenProject project = session.getCurrentProject();

        // Here we will actually not have any artifacts because the ProjectDependenciesResolver is not involved here. So
        // right now it's not valid to ask for artifacts unless plugins require the artifacts.

        project.getCompileClasspathElements();
    }

    public void testBuildFromModelSource()
        throws Exception
    {
        File pomFile = new File( "src/test/resources/projects/modelsource/module01/pom.xml" );
        MavenSession mavenSession = createMavenSession( pomFile );
        ProjectBuildingRequest configuration = new DefaultProjectBuildingRequest();
        configuration.setRepositorySession( mavenSession.getRepositorySession() );
        ModelSource modelSource = new FileModelSource( pomFile );
        ProjectBuildingResult result =
            lookup( org.apache.maven.project.ProjectBuilder.class ).build( modelSource, configuration );

        assertNotNull( result.getProject().getParentFile() );
    }

    public void testVersionlessManagedDependency()
        throws Exception
    {
        File pomFile = new File( "src/test/resources/projects/versionless-managed-dependency.xml" );
        MavenSession mavenSession = createMavenSession( null );
        ProjectBuildingRequest configuration = new DefaultProjectBuildingRequest();
        configuration.setRepositorySession( mavenSession.getRepositorySession() );

        try
        {
            lookup( org.apache.maven.project.ProjectBuilder.class ).build( pomFile, configuration );
            fail();
        }
        catch ( ProjectBuildingException e )
        {
            // this is expected
        }
    }

    public void testResolveDependencies()
        throws Exception
    {
        File pomFile = new File( "src/test/resources/projects/basic-resolveDependencies.xml" );
        MavenSession mavenSession = createMavenSession( null );
        ProjectBuildingRequest configuration = new DefaultProjectBuildingRequest();
        configuration.setRepositorySession( mavenSession.getRepositorySession() );
        configuration.setResolveDependencies( true );

        // single project build entry point
        ProjectBuildingResult result = lookup( org.apache.maven.project.ProjectBuilder.class ).build( pomFile, configuration );
        assertEquals( 1, result.getProject().getArtifacts().size() );
        // multi projects build entry point
        List<ProjectBuildingResult> results = lookup( org.apache.maven.project.ProjectBuilder.class ).build( Collections.singletonList( pomFile ), false, configuration );
        assertEquals( 1, results.size() );
        MavenProject mavenProject = results.get( 0 ).getProject();
        assertEquals( 1, mavenProject.getArtifacts().size() );
    }

    public void testDontResolveDependencies()
        throws Exception
    {
        File pomFile = new File( "src/test/resources/projects/basic-resolveDependencies.xml" );
        MavenSession mavenSession = createMavenSession( null );
        ProjectBuildingRequest configuration = new DefaultProjectBuildingRequest();
        configuration.setRepositorySession( mavenSession.getRepositorySession() );
        configuration.setResolveDependencies( false );

        // single project build entry point
        ProjectBuildingResult result = lookup( org.apache.maven.project.ProjectBuilder.class ).build( pomFile, configuration );
        assertEquals( 0, result.getProject().getArtifacts().size() );
        // multi projects build entry point
        List<ProjectBuildingResult> results = lookup( org.apache.maven.project.ProjectBuilder.class ).build( Collections.singletonList( pomFile ), false, configuration );
        assertEquals( 1, results.size() );
        MavenProject mavenProject = results.get( 0 ).getProject();
        assertEquals( 0, mavenProject.getArtifacts().size() );
    }

    public void testReadModifiedPoms() throws Exception {
        String initialValue = System.setProperty( DefaultProjectBuilder.DISABLE_GLOBAL_MODEL_CACHE_SYSTEM_PROPERTY, Boolean.toString( true ) );
        // TODO a similar test should be created to test the dependency management (basically all usages
        // of DefaultModelBuilder.getCache() are affected by MNG-6530
        File tempDir = Files.createTempDir();
        FileUtils.copyDirectoryStructure (new File( "src/test/resources/projects/grandchild-check"), tempDir );
        try
        {
            MavenSession mavenSession = createMavenSession( null );
            ProjectBuildingRequest configuration = new DefaultProjectBuildingRequest();
            configuration.setRepositorySession( mavenSession.getRepositorySession() );
            org.apache.maven.project.ProjectBuilder projectBuilder = lookup( org.apache.maven.project.ProjectBuilder.class );
            File child = new File( tempDir, "child/pom.xml" );
            // build project once
            projectBuilder.build( child, configuration );
            // modify parent
            File parent = new File( tempDir, "pom.xml" );
            String parentContent = FileUtils.fileRead( parent );
            parentContent = parentContent.replaceAll( "<packaging>pom</packaging>",
            		"<packaging>pom</packaging><properties><addedProperty>addedValue</addedProperty></properties>" );
            FileUtils.fileWrite( parent, "UTF-8", parentContent );
            // re-build pom with modified parent
            ProjectBuildingResult result = projectBuilder.build( child, configuration );
            assertTrue( result.getProject().getProperties().containsKey( "addedProperty" ) );
        }
        finally
        {
            if ( initialValue == null )
            {
                System.clearProperty( DefaultProjectBuilder.DISABLE_GLOBAL_MODEL_CACHE_SYSTEM_PROPERTY );
            }
            else
            {
                System.setProperty( DefaultProjectBuilder.DISABLE_GLOBAL_MODEL_CACHE_SYSTEM_PROPERTY, initialValue );
            }
            FileUtils.deleteDirectory( tempDir );
        }
    }
}
