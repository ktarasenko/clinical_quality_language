package org.cqframework.fhir.npm;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.hl7.cql.model.ModelIdentifier;
import org.hl7.elm.r1.VersionedIdentifier;
import org.hl7.elm_modelinfo.r1.ModelInfo;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.model.Enumerations.FHIRVersion;
import org.hl7.fhir.r5.model.Enumerations.PublicationStatus;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideDependsOnComponent;
import org.hl7.fhir.utilities.npm.BasePackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.npm.PackageGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Ignore;
import org.testng.annotations.Test;

public class NpmPackageManagerTests implements IWorkerContext.ILoggingService {

    private Logger logger = LoggerFactory.getLogger(NpmPackageManagerTests.class);
    /*
    NOTE: This test depends on the dev package cache for the [sample-ig](https://github.com/FHIR/sample-ig)
    Running the IG publisher on a clone of this IG locally will create and cache the package

    TODO: Update this so that it can download and cache packages by itself
     */
    @Ignore("Currently requires running the IG Publisher first to cache npm packages.")
    @Test
    public void TestSampleIG() throws IOException {
        NpmPackageManager pm = NpmPackageManager.fromStream(NpmPackageManagerTests.class.getResourceAsStream("myig.xml"), "4.0.1");
        assertTrue(pm.getNpmList().size() >= 1);
    }

    /*
    NOTE: This test depends on the dev package cache for the [sample-content-ig](https://github.com/cqframework/sample-content-ig)
    Running the IG publisher on a clone of this IG locally will create and cache the package

    NOTE: Temporarily @Ignore because it's causing the CI build to fail.
    TODO: Update this so that it can download and cache packages by itself
     */
    @Ignore("Currently requires running the IG Publisher first to cache npm packages.")
    @Test
    public void TestSampleContentIG() throws IOException {
        NpmPackageManager pm = NpmPackageManager.fromStream(NpmPackageManagerTests.class.getResourceAsStream("mycontentig.xml"), "4.0.1");
        assertTrue(pm.getNpmList().size() >= 1);
    }

    /*
    Ignoring this test because even though it passes locally in all reviewers environments, it fails when running in the Travis build,
    best guess is that the Travis environment doesn't allow access to the cache directory for some reason.
     */
    @Ignore
    @Test
    public void TestOpioidMMEIG() throws IOException {
        NpmPackageManager pm = NpmPackageManager.fromStream(NpmPackageManagerTests.class.getResourceAsStream("opioid-mme-r4.xml"), "4.0.1");
        assertTrue(pm.getNpmList().size() >= 1);
    }

    /*
    NOTE: This test depends on the dev package cache for the [sample-content-ig](https://github.com/cqframework/sample-content-ig)
    Running the IG publisher on a clone of this IG locally will create and cache the package

    NOTE: Temporarily @Ignore because it's causing the CI build to fail.
     */
    @Ignore
    @Test
    public void TestLibrarySourceProvider() throws IOException {
        NpmPackageManager pm = NpmPackageManager.fromStream(NpmPackageManagerTests.class.getResourceAsStream("mycontentig.xml"), "4.0.1");
        assertTrue(pm.getNpmList().size() >= 1);

        LibraryLoader reader = new LibraryLoader("4.0.1");
        NpmLibrarySourceProvider sp = new NpmLibrarySourceProvider(pm.getNpmList(), reader, this);
        InputStream is = sp.getLibrarySource(new VersionedIdentifier().withSystem("http://somewhere.org/fhir/uv/myig").withId("example"));
        assertTrue(is != null);
        is = sp.getLibrarySource(new VersionedIdentifier().withSystem("http://somewhere.org/fhir/uv/myig").withId("example").withVersion("0.2.0"));
        assertTrue(is != null);
    }

    /*
    Ignoring this test because even though it passes locally in all reviewers environments, it fails when running in the Travis build,
    best guess is that the Travis environment doesn't allow access to the cache directory for some reason.
     */
    @Ignore
    @Test
    public void TestModelInfoProvider() throws IOException {
        InputStream is = NpmPackageManagerTests.class.getResourceAsStream("testig.xml");
        assertNotNull(is);
        NpmPackageManager pm = NpmPackageManager.fromStream(is, "4.0.1");
        assertTrue(pm.getNpmList().size() >= 1);

        LibraryLoader reader = new LibraryLoader("4.0.1");
        NpmModelInfoProvider mp = new NpmModelInfoProvider(pm.getNpmList(), reader, this);
        ModelInfo mi = mp.load(new ModelIdentifier().withSystem("http://hl7.org/fhir/us/qicore").withId("QICore"));
        assertNotNull(mi);
        assertTrue(mi.getName().equals("QICore"));
    }

  @Test
  public void TestNestedDependencies() throws IOException {
    ImplementationGuide ig = new ImplementationGuide()
        .setPackageId("test.source")
        .addDependsOn(new ImplementationGuideDependsOnComponent()
            .setPackageId("test.dep1").setVersion("1.0.0"))
        .addDependsOn(new ImplementationGuideDependsOnComponent()
            .setPackageId("test.dep2").setVersion("1.0.0"));

    NpmPackageManager pm = new NpmPackageManager(ig, "4.0.1", new FakePackageManager());

    List<String> packageNames = pm.getNpmList().stream().map(NpmPackage::name)
        .collect(Collectors.toList());
    List<String> expectedDependencies = Arrays.asList("hl7.fhir.r4.core", "test.dep1", "test.dep3",
        "test.dep2", "test.dep4");
    assertEquals(packageNames.toString(), expectedDependencies.toString());

  }

  private static class FakePackageManager extends BasePackageCacheManager {

    @Override
    public NpmPackage loadPackageFromCacheOnly(String id, @Nullable String version)
        throws IOException {
      return loadPackage(id, version);
    }

    @Override
    public NpmPackage addPackageToCache(String id, String version,
        InputStream packageTgzInputStream, String sourceDesc) throws IOException {
      return loadPackage(id, version);
    }

    @Override
    public NpmPackage loadPackage(String id, String version) throws FHIRException, IOException {
      PackageGenerator packageGenerator = new PackageGenerator().name(id).version(version);
      switch (id) {
        case "test.dep1":
          return NpmPackage.empty(packageGenerator.dependency("test.dep3", version));
        case "test.dep2":
          return NpmPackage.empty(packageGenerator.dependency("test.dep4", version));
        default:
          return NpmPackage.empty(packageGenerator);
      }
    }

    @Override
    public String getPackageUrl(String packageId) throws IOException {
      return String.format("http://%s/ImplmentationGuide/", packageId);
    }
  }

    @Override
    public void logMessage(String msg) {
        logger.info(msg);
    }

    @Override
    public void logDebugMessage(IWorkerContext.ILoggingService.LogCategory category, String msg) {
        logMessage(msg);
    }

}
