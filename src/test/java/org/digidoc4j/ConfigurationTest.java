/* DigiDoc4J library
*
* This software is released under either the GNU Library General Public
* License (see LICENSE.LGPL).
*
* Note that the only valid version of the LGPL license as far as this
* project is concerned is the original GNU Library General Public License
* Version 2.1, February 1999
*/

package org.digidoc4j;

import static org.digidoc4j.Configuration.Mode;
import static org.digidoc4j.Configuration.Mode.PROD;
import static org.digidoc4j.Configuration.Mode.TEST;
import static org.digidoc4j.Constant.BDOC_CONTAINER_TYPE;
import static org.hamcrest.core.IsCollectionContaining.hasItem;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.digidoc4j.exceptions.ConfigurationException;
import org.digidoc4j.exceptions.DigiDoc4JException;
import org.digidoc4j.exceptions.TslCertificateSourceInitializationException;
import org.digidoc4j.exceptions.TslKeyStoreNotFoundException;
import org.digidoc4j.impl.asic.asice.bdoc.BDocContainer;
import org.digidoc4j.impl.asic.tsl.TSLCertificateSourceImpl;
import org.digidoc4j.impl.asic.tsl.TslLoader;
import org.digidoc4j.testutils.TSLHelper;
import org.digidoc4j.utils.Helper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import eu.europa.esig.dss.DSSUtils;
import eu.europa.esig.dss.tsl.Condition;
import eu.europa.esig.dss.tsl.KeyUsageBit;
import eu.europa.esig.dss.tsl.ServiceInfo;
import eu.europa.esig.dss.tsl.ServiceInfoStatus;
import eu.europa.esig.dss.x509.CertificateToken;

public class ConfigurationTest extends AbstractTest {

  private static final String SIGN_OCSP_REQUESTS = "SIGN_OCSP_REQUESTS";
  private static final String OCSP_PKCS12_CONTAINER = "DIGIDOC_PKCS12_CONTAINER";
  private static final String OCSP_PKCS_12_PASSWD = "DIGIDOC_PKCS12_PASSWD";
  private Configuration configuration;

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Before
  public void setUp() {
    System.clearProperty("digidoc4j.mode");
    configuration = new Configuration(Mode.TEST);
  }

  @Test
  public void getTSLLocationWhenNotFileURL() {
    Configuration configuration = new Configuration();
    String tslLocation = "URL:test";
    configuration.setTslLocation(tslLocation);

    assertEquals(tslLocation, configuration.getTslLocation());
  }

  @Test
  public void TSLIsLoadedOnlyOnceForGlobalConfiguration() {
    TSLCertificateSource tsl = configuration.getTSL();
    assertEquals(tsl, configuration.getTSL());
  }

  @Test
  public void addTSL() throws IOException, CertificateException {
    TSLCertificateSource tsl = configuration.getTSL();
    int numberOfTSLCertificates = tsl.getCertificates().size();
    addFromFileToTSLCertificate("src/test/resources/testFiles/certs/Juur-SK.pem.crt");

    assertEquals(numberOfTSLCertificates + 1, configuration.getTSL().getCertificates().size());
  }

  @Test
  public void addingCertificateToTsl() throws Exception {
    TSLCertificateSource certificateSource = new TSLCertificateSourceImpl();
    addFromFileToTSLCertificate("src/test/resources/testFiles/certs/Juur-SK.pem.crt", certificateSource);
    CertificateToken certificateToken = certificateSource.getCertificates().get(0);
    assertThat(certificateToken.getKeyUsageBits(), hasItem(KeyUsageBit.nonRepudiation));
    assertTrue(certificateToken.checkKeyUsage(KeyUsageBit.nonRepudiation));
    ServiceInfo serviceInfo = certificateToken.getAssociatedTSPS().iterator().next();
    //TODO test ServiceInfoStatus new methods
    ServiceInfoStatus serviceInfostatus = serviceInfo.getStatus().getLatest();
    assertEquals("http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/undersupervision", serviceInfostatus.getStatus());
    assertEquals("http://uri.etsi.org/TrstSvc/Svctype/CA/QC", serviceInfostatus.getType());
    assertNotNull(serviceInfostatus.getStartDate());
    Map<String, List<Condition>> qualifiersAndConditions = serviceInfostatus.getQualifiersAndConditions();
    assertTrue(qualifiersAndConditions.containsKey("http://uri.etsi.org/TrstSvc/TrustedList/SvcInfoExt/QCWithSSCD"));
  }

  @Test
  public void clearTSLLoadsFromConfiguration() {
    TSLCertificateSource tsl = configuration.getTSL();
    int numberOfTSLCertificates = tsl.getCertificates().size();
    configuration.setTSL(null);

    assertEquals(numberOfTSLCertificates, configuration.getTSL().getCertificates().size());
  }

  @Test
  public void setTSL() throws IOException, CertificateException {
    TSLCertificateSource trustedListsCertificateSource = new TSLCertificateSourceImpl();
    FileInputStream fileInputStream = new FileInputStream("src/test/resources/testFiles/certs/Juur-SK.pem.crt");
    X509Certificate certificate = DSSUtils.loadCertificate(fileInputStream).getCertificate();
    trustedListsCertificateSource.addTSLCertificate(certificate);

    configuration.setTSL(trustedListsCertificateSource);
    fileInputStream.close();

    assertEquals(1, configuration.getTSL().getCertificates().size());
  }

  @SuppressWarnings("ConstantConditions")
  @Ignore("Ignored till problem with file times are solved")
  @Test
  public void clearTSLCache() throws Exception {
    // TODO: find out why file times are equal; till then ignore
    Configuration myConfiguration = new Configuration(TEST);
    File fileCacheDirectory = TslLoader.fileCacheDirectory;
    if (fileCacheDirectory.exists()) {
      FileUtils.cleanDirectory(fileCacheDirectory);
    }

    TSLCertificateSource tslCertificateSource = myConfiguration.getTSL();
    tslCertificateSource.refresh();
    waitOneSecond();
    File oldCachedFile = fileCacheDirectory.listFiles()[0];
    FileTime oldCachedFileDate = (FileTime) Files.getAttribute(oldCachedFile.toPath(),
        "basic:creationTime");

    tslCertificateSource.invalidateCache();
    myConfiguration.setTSL(null);
    tslCertificateSource = myConfiguration.getTSL();
    tslCertificateSource.refresh();

    File newCachedFile = fileCacheDirectory.listFiles()[0];
    FileTime newCachedFileDate = (FileTime) Files.getAttribute(newCachedFile.toPath(), "basic:creationTime");

    assertTrue(newCachedFileDate.compareTo(oldCachedFileDate) > 0);
  }

  @Test
  public void getTsl_whenCacheIsNotExpired_shouldUseCachedTsl() throws Exception {
    deleteTSLCache();
    configuration.setTslCacheExpirationTime(10000L);
    TSLCertificateSource tsl = configuration.getTSL();
    tsl.refresh();
    long lastModified = getCacheModificationTime();
    waitOneSecond();
    TSLCertificateSource newTsl = configuration.getTSL();
    newTsl.refresh();
    long newModificationTime = getCacheModificationTime();
    assertEquals(lastModified, newModificationTime);
    assertSame(tsl, newTsl);
  }

  @Test
  public void getTsl_whenCacheIsExpired_shouldDownloadNewTsl() throws Exception {
    deleteTSLCache();
    configuration.setTslCacheExpirationTime(500L);
    TSLCertificateSource tsl = configuration.getTSL();
    tsl.refresh();
    long lastModified = getCacheModificationTime();
    waitOneSecond();
    TSLCertificateSource newTsl = configuration.getTSL();
    newTsl.refresh();
    long newModificationTime = getCacheModificationTime();
    assertTrue(lastModified < newModificationTime);
    assertSame(tsl, newTsl);
  }

  @Test
  public void lotlValidationFailsWithWrongCertsInKeystore() {
    Configuration myConfiguration = new Configuration(PROD);
    myConfiguration.setTslKeyStoreLocation("keystore/test-keystore.jks");
    try {
      myConfiguration.getTSL();
    } catch (TslCertificateSourceInitializationException e) {
      assertEquals("Not ETSI compliant signature. The signature is not valid.", e.getMessage());
    }
  }

  @Test
  public void addedTSLIsValid() throws IOException, CertificateException {
    addFromFileToTSLCertificate("src/test/resources/testFiles/certs/Juur-SK.pem.crt");
    addFromFileToTSLCertificate("src/test/resources/testFiles/certs/EE_Certification_Centre_Root_CA.pem.crt");
    addFromFileToTSLCertificate("src/test/resources/testFiles/certs/ESTEID-SK_2011.pem.crt");
    addFromFileToTSLCertificate("src/test/resources/testFiles/certs/SK_OCSP_RESPONDER_2011.pem.cer");
    addFromFileToTSLCertificate("src/test/resources/testFiles/certs/SK_TSA.pem.crt");
    // NB! With some reason using this.configuration breaks some future tests
    //Container container = ContainerOpener.open("src/test/resources/testFiles/valid-containers/test.asice", new Configuration(Mode.TEST));
    //ValidationResult verify = container.validate();
    //assertTrue(verify.isValid());
  }

  private void addFromFileToTSLCertificate(String fileName) throws IOException, CertificateException {
    TSLCertificateSource tsl = configuration.getTSL();
    addFromFileToTSLCertificate(fileName, tsl);
  }

  private void addFromFileToTSLCertificate(String fileName, TSLCertificateSource tsl) throws IOException {
    FileInputStream fileInputStream = new FileInputStream(fileName);
    X509Certificate certificate = DSSUtils.loadCertificate(fileInputStream).getCertificate();
    tsl.addTSLCertificate(certificate);
    fileInputStream.close();
  }

  @Test
  @Ignore("Ignored as this functionality is not used in DDS but this test is broken due to DDS forks custom revocation handling.")
  public void policyFileIsReadFromNonDefaultFileLocation() {
    configuration.setValidationPolicy("src/test/resources/testFiles/moved_constraint.xml");
    ContainerOpener.open("src/test/resources/testFiles/invalid-containers/asics_for_testing.bdoc", configuration);
  }

  @Test
  //@Ignore("RIA VPN")
  //This test works and succeeds only in RIA VPN; outside of RIA VPN it will not fail
  public void TSLIsLoadedAfterSettingNewTSLLocation() {
    Configuration configuration = new Configuration(TEST);
    configuration.setTslLocation("https://open-eid.github.io/test-TL/tl-mp-test-EE.xml");
    BDocContainer container = (BDocContainer) ContainerBuilder.
        aContainer(BDOC_CONTAINER_TYPE).
        withConfiguration(configuration).
        build();
    container.getConfiguration().getTSL();
    assertEquals(7, container.getConfiguration().getTSL().getCertificates().size());
    try {
      int tenSeconds = 10000;
      String tslHost = "10.0.25.57";
      if (InetAddress.getByName(tslHost).isReachable(tenSeconds)) {
        configuration.setTslLocation("http://" + tslHost + "/tsl/trusted-test-mp.xml");
        container = (BDocContainer) ContainerBuilder.
            aContainer(BDOC_CONTAINER_TYPE).
            withConfiguration(configuration).
            build();
        assertNotEquals(5, container.getConfiguration().getTSL().getCertificates().size());
      } else {
        System.out.println("Host " + tslHost + " is unreachable.");
      }
    } catch (Exception e) {
    }
  }

  @Test(expected = DigiDoc4JException.class)
  public void TSLFileNotFoundThrowsException() {
    Configuration configuration = new Configuration();
    configuration.setTslLocation("file:test-tsl/NotExisting.xml");
    BDocContainer container = (BDocContainer) ContainerBuilder.
        aContainer(BDOC_CONTAINER_TYPE).
        withConfiguration(configuration).
        build();
    container.getConfiguration().getTSL().refresh();
  }

  @Test(expected = DigiDoc4JException.class)
  public void TSLConnectionFailureThrowsException() {
    Configuration configuration = new Configuration();
    configuration.setTslLocation("http://127.0.0.1/tsl/incorrect.xml");
    BDocContainer container = (BDocContainer) ContainerBuilder.
        aContainer(BDOC_CONTAINER_TYPE).
        withConfiguration(configuration).
        build();
    container.getConfiguration().getTSL().refresh();
  }

  @Test
  public void testLoadConfiguration() throws Exception {
    BDocContainer container = (BDocContainer) ContainerBuilder.
        aContainer(BDOC_CONTAINER_TYPE).
        withConfiguration(new Configuration(Configuration.Mode.TEST)).
        build();
    assertFalse(container.getConfiguration().isBigFilesSupportEnabled());
    container.getConfiguration().loadConfiguration("src/test/resources/testFiles/yaml-configurations/digidoc_test_conf.yaml");
    assertTrue(container.getConfiguration().isBigFilesSupportEnabled());
    assertEquals(8192, container.getConfiguration().getMaxDataFileCachedInMB());
  }

  @Test
  public void whenTSLLocationIsMalformedURLNoErrorIsRaisedAndThisSameValueIsReturned() throws Exception {
    Configuration configuration = new Configuration();
    String tslLocation = "file://C:\\";
    configuration.setTslLocation(tslLocation);

    assertEquals(tslLocation, configuration.getTslLocation());
  }

  @Test
  public void getTSLLocationFileDoesNotExistReturnsUrlPath() {
    Configuration configuration = new Configuration();
    String tslLocation = ("file:conf/does-not-exist.xml");
    configuration.setTslLocation(tslLocation);

    assertEquals(configuration.getTslLocation(), tslLocation);
  }

  @Test
  public void setTslLocation() throws Exception {
    configuration.setTslLocation("tslLocation");
    assertEquals("tslLocation", configuration.getTslLocation());
  }

  @Test
  public void getTslLocationFromConfigurationFile() throws Exception {
    configuration.loadConfiguration("src/test/resources/testFiles/yaml-configurations/digidoc_test_conf.yaml");
    assertEquals("file:conf/test_TSLLocation", configuration.getTslLocation());
  }

  @Test
  public void setTslLocationOverwritesConfigurationFile() throws Exception {
    configuration.loadConfiguration("src/test/resources/testFiles/yaml-configurations/digidoc_test_conf.yaml");
    configuration.setTslLocation("tslLocation");
    assertEquals("tslLocation", configuration.getTslLocation());
  }

  @Test
  public void setTspSource() throws Exception {
    configuration.setTspSource("tspSource");
    assertEquals("tspSource", configuration.getTspSource());
  }

  @Test
  public void setValidationPolicy() throws Exception {
    configuration.setValidationPolicy("policy");
    assertEquals("policy", configuration.getValidationPolicy());
  }

  @Test
  public void setOcspSource() throws Exception {
    configuration.setOcspSource("ocsp_source");
    assertEquals("ocsp_source", configuration.getOcspSource());
  }

  @Test
  public void defaultOCSPAccessCertificateFile() {
    assertEquals("", configuration.getOCSPAccessCertificateFileName());
    assertEquals("", getJDigiDocConfValue(configuration, OCSP_PKCS12_CONTAINER));
  }

  @Test
  public void getOCSPAccessCertificateFileFromConfigurationFile() {
    configuration.loadConfiguration("src/test/resources/testFiles/yaml-configurations/digidoc_test_conf.yaml");
    assertEquals("conf/OCSP_access_certificate_test_file_name", configuration.getOCSPAccessCertificateFileName());
    assertEquals("conf/OCSP_access_certificate_test_file_name", getJDigiDocConfValue(configuration, OCSP_PKCS12_CONTAINER));
  }

  @Test
  public void getOCSPAccessCertificateFileFromStream() throws FileNotFoundException {
    FileInputStream stream = new FileInputStream("src/test/resources/testFiles/yaml-configurations/digidoc_test_conf.yaml");
    configuration.loadConfiguration(stream);
    assertEquals("conf/OCSP_access_certificate_test_file_name", configuration.getOCSPAccessCertificateFileName());
    assertEquals("conf/OCSP_access_certificate_test_file_name", getJDigiDocConfValue(configuration, OCSP_PKCS12_CONTAINER));
  }

  @Test
  public void setOCSPAccessCertificateFileNameOverwritesConfigurationFile() {
    configuration.loadConfiguration("src/test/resources/testFiles/yaml-configurations/digidoc_test_conf.yaml");
    configuration.setOCSPAccessCertificateFileName("New File");
    assertEquals("New File", configuration.getOCSPAccessCertificateFileName());
    assertEquals("New File", getJDigiDocConfValue(configuration, OCSP_PKCS12_CONTAINER));
  }

  @Test
  public void defaultOCSPAccessCertificatePassword() {
    assertEquals(0, configuration.getOCSPAccessCertificatePassword().length);
    assertNull(getJDigiDocConfValue(configuration, OCSP_PKCS_12_PASSWD));
  }

  @Test
  public void getOCSPAccessCertificatePasswordFromConfigurationFile() throws Exception {
    configuration.loadConfiguration("src/test/resources/testFiles/yaml-configurations/digidoc_test_conf.yaml");
    assertArrayEquals("OCSP_test_password".toCharArray(), configuration.getOCSPAccessCertificatePassword());
    assertEquals("OCSP_test_password", getJDigiDocConfValue(configuration, OCSP_PKCS_12_PASSWD));
  }

  @Test
  public void setOCSPAccessCertificatePasswordOverwritesConfigurationFile() {
    configuration.loadConfiguration("src/test/resources/testFiles/yaml-configurations/digidoc_test_conf.yaml");
    char[] newPassword = "New password".toCharArray();
    configuration.setOCSPAccessCertificatePassword(newPassword);
    assertArrayEquals(newPassword, configuration.getOCSPAccessCertificatePassword());
    assertEquals("New password", getJDigiDocConfValue(configuration, OCSP_PKCS_12_PASSWD));
  }

  @Test
  public void signingOcspRequest_ShouldBeDisabled_InProdByDefault() throws Exception {
    Configuration configuration = new Configuration(Mode.PROD);
    assertFalse(configuration.hasToBeOCSPRequestSigned());
    assertEquals("false", getJDigiDocConfValue(configuration, SIGN_OCSP_REQUESTS));
  }

  @Test
  public void signingOcspRequest_ShouldBeDisabled_InTestByDefault() throws Exception {
    Configuration configuration = new Configuration(Mode.TEST);
    assertFalse(configuration.hasToBeOCSPRequestSigned());
    assertEquals("false", getJDigiDocConfValue(configuration, SIGN_OCSP_REQUESTS));
  }

  @Test
  public void disableSigningOcspRequestsInProd() throws Exception {
    Configuration configuration = new Configuration(Mode.PROD);
    configuration.setSignOCSPRequests(false);
    assertFalse(configuration.hasToBeOCSPRequestSigned());
    assertEquals("false", getJDigiDocConfValue(configuration, SIGN_OCSP_REQUESTS));
  }

  @Test
  public void enableSigningOcspRequestsInTest() throws Exception {
    Configuration configuration = new Configuration(Mode.TEST);
    configuration.setSignOCSPRequests(true);
    assertTrue(configuration.hasToBeOCSPRequestSigned());
    assertEquals("true", getJDigiDocConfValue(configuration, SIGN_OCSP_REQUESTS));
  }

  @Test
  public void loadDisableSigningOcspRequestFromConfFileInProd() throws Exception {
    Configuration configuration = new Configuration(Mode.PROD);
    configuration.loadConfiguration("src/test/resources/testFiles/yaml-configurations/digidoc_test_all_optional_settings.yaml");
    assertFalse(configuration.hasToBeOCSPRequestSigned());
    assertEquals("false", getJDigiDocConfValue(configuration, SIGN_OCSP_REQUESTS));
  }

  @Test
  public void loadDisableSigningOcspRequestFromConfFile() throws Exception {
    File confFile = createConfFileWithParameter("SIGN_OCSP_REQUESTS: false");
    Configuration configuration = new Configuration();
    configuration.loadConfiguration(confFile.getPath());
    assertFalse(configuration.hasToBeOCSPRequestSigned());
    assertEquals("false", getJDigiDocConfValue(configuration, SIGN_OCSP_REQUESTS));
  }

  @Test
  public void loadEnableSigningOcspRequestFromConfFile() throws Exception {
    File confFile = createConfFileWithParameter("SIGN_OCSP_REQUESTS: true");
    Configuration configuration = new Configuration();
    configuration.loadConfiguration(confFile.getPath());
    assertTrue(configuration.hasToBeOCSPRequestSigned());
    assertEquals("true", getJDigiDocConfValue(configuration, SIGN_OCSP_REQUESTS));
  }

  @Test
  public void defaultOcspSource() throws Exception {
    assertEquals("http://demo.sk.ee/ocsp", configuration.getOcspSource());
  }

  @Test
  public void defaultProductionConfiguration() throws Exception {
    Configuration configuration = new Configuration(PROD);
    assertEquals("https://ec.europa.eu/information_society/policy/esignature/trusted-list/tl-mp.xml",
        configuration.getTslLocation());
  }

  @Test
  public void defaultConstructorWithSetSystemProperty() throws Exception {
    System.setProperty("digidoc4j.mode", "TEST");
    Configuration configuration = new Configuration();
    assertEquals("https://open-eid.github.io/test-TL/tl-mp-test-EE.xml", configuration.getTslLocation());
  }

  @Test
  public void setMaxDataFileCached() throws Exception {
    configuration = new Configuration();
    long maxDataFileCached = 12345;
    configuration.enableBigFilesSupport(maxDataFileCached);
    assertEquals(maxDataFileCached, configuration.getMaxDataFileCachedInMB());
    assertEquals(maxDataFileCached * Constant.ONE_MB_IN_BYTES, configuration.getMaxDataFileCachedInBytes());
  }

  @Test
  public void setMaxDataFileCachedToNoCaching() {
    configuration = new Configuration();
    long maxDataFileCached = Constant.CACHE_NO_DATA_FILES;
    configuration.enableBigFilesSupport(maxDataFileCached);
    assertEquals(Constant.CACHE_NO_DATA_FILES, configuration.getMaxDataFileCachedInMB());
    assertEquals(Constant.CACHE_NO_DATA_FILES, configuration.getMaxDataFileCachedInBytes());
  }

  @Test
  public void setMaxDataFileCachedToAllCaching() {
    configuration = new Configuration();
    long maxDataFileCached = Constant.CACHE_ALL_DATA_FILES;
    configuration.enableBigFilesSupport(maxDataFileCached);
    assertEquals(Constant.CACHE_ALL_DATA_FILES, configuration.getMaxDataFileCachedInMB());
    assertEquals(Constant.CACHE_ALL_DATA_FILES, configuration.getMaxDataFileCachedInBytes());
  }

  @Test
  public void maxDataFileCachedNotAllowedValue() {
    configuration = new Configuration();
    long oldValue = 4096;
    configuration.enableBigFilesSupport(oldValue);
    configuration.enableBigFilesSupport(-2);
    assertEquals(oldValue, configuration.getMaxDataFileCachedInMB());
  }

  @Test
  public void maxDataFileCachedNotAllowedValueFromFile() {
    configuration = new Configuration();
    String fileName = "src/test/resources/testFiles/yaml-configurations/digidoc_test_conf_max_datafile_cached_invalid.yaml";

    expectedException.expect(ConfigurationException.class);
    expectedException.expectMessage("Configuration parameter DIGIDOC_MAX_DATAFILE_CACHED should be greater or equal " +
        "-1 but the actual value is: -2.");

    configuration.loadConfiguration(fileName);
  }

  @Test
  public void defaultConstructorWithUnSetSystemProperty() throws Exception {
    Configuration configuration = new Configuration();
    assertEquals("https://ec.europa.eu/information_society/policy/esignature/trusted-list/tl-mp.xml",
        configuration.getTslLocation());
  }

  @Test
  public void generateJDigiDocConfig() throws Exception {
    Hashtable<String, String> jDigiDocConf = configuration.loadConfiguration("src/main/resources/digidoc4j.yaml");
    configuration.getJDigiDocConfiguration();

    assertEquals("jar://certs/ESTEID-SK.crt", jDigiDocConf.get("DIGIDOC_CA_1_CERT2"));
    assertEquals("jar://certs/KLASS3-SK OCSP 2006.crt", jDigiDocConf.get("DIGIDOC_CA_1_OCSP2_CERT_1"));
    assertEquals("jar://certs/EID-SK OCSP 2006.crt", jDigiDocConf.get("DIGIDOC_CA_1_OCSP13_CERT_1"));
    assertEquals("jar://certs/TEST Juur-SK.crt", jDigiDocConf.get("DIGIDOC_CA_1_CERT19"));
    assertEquals(Constant.JDigiDoc.SECURITY_PROVIDER, jDigiDocConf.get("DIGIDOC_SECURITY_PROVIDER"));
    assertEquals(Constant.JDigiDoc.SECURITY_PROVIDER_NAME, jDigiDocConf.get("DIGIDOC_SECURITY_PROVIDER_NAME"));
    assertEquals("false", jDigiDocConf.get("DATAFILE_HASHCODE_MODE"));
    assertEquals(Constant.JDigiDoc.CANONICALIZATION_FACTORY_IMPLEMENTATION, jDigiDocConf.get("CANONICALIZATION_FACTORY_IMPL"));
    assertEquals("-1", jDigiDocConf.get("DIGIDOC_MAX_DATAFILE_CACHED"));
    assertEquals("false", jDigiDocConf.get(SIGN_OCSP_REQUESTS));
    assertEquals("jar://certs/KLASS3-SK OCSP.crt", jDigiDocConf.get("DIGIDOC_CA_1_OCSP2_CERT"));
  }

  @Test
  public void loadsJDigiDocSecurityProviderFromFile() throws Exception {
    Hashtable<String, String> jDigiDocConf = configuration.loadConfiguration("src/test/resources/testFiles/yaml-configurations/digidoc_test_conf.yaml");
    assertEquals("org.bouncycastle.jce.provider.BouncyCastleProvider1", jDigiDocConf.get("DIGIDOC_SECURITY_PROVIDER"));
  }

  @Test
  public void loadsJDigiDocCacheDirectoryFromFile() throws Exception {
    Hashtable<String, String> jDigiDocConf = configuration.loadConfiguration("src/test/resources/testFiles/yaml-configurations/digidoc_test_conf.yaml");
    assertEquals("/test_cache_dir", jDigiDocConf.get("DIGIDOC_DF_CACHE_DIR"));
  }

  @Test
  public void defaultJDigiDocCacheDirectory() throws Exception {
    Hashtable<String, String> jDigiDocConf =
        configuration.loadConfiguration("src/test/resources/testFiles/yaml-configurations/digidoc_test_conf_without_cache_dir.yaml");
    assertNull(jDigiDocConf.get("DIGIDOC_DF_CACHE_DIR"));
  }

  @SuppressWarnings("NumericOverflow")
  @Test
  public void loadsMaxDataFileCachedFromFile() throws Exception {
    Hashtable<String, String> jDigiDocConf = configuration.loadConfiguration("src/test/resources/testFiles/yaml-configurations/digidoc_test_conf.yaml");

    assertEquals("8192", jDigiDocConf.get("DIGIDOC_MAX_DATAFILE_CACHED"));
    assertEquals(8192, configuration.getMaxDataFileCachedInMB());
    assertEquals(8192 * Constant.ONE_MB_IN_BYTES, configuration.getMaxDataFileCachedInBytes());
  }

  @Test
  public void settingNonExistingConfigurationFileThrowsError() throws Exception {
    expectedException.expect(ConfigurationException.class);
    expectedException.expectMessage("File src/test/resources/testFiles/not_exists.yaml not found in classpath.");

    configuration.loadConfiguration("src/test/resources/testFiles/not_exists.yaml");
  }

  @Test
  public void digiDocSecurityProviderDefaultValue() throws Exception {
    Hashtable<String, String> jDigiDocConf = configuration.loadConfiguration("src/main/resources/digidoc4j.yaml");
    assertEquals(Constant.JDigiDoc.SECURITY_PROVIDER, jDigiDocConf.get("DIGIDOC_SECURITY_PROVIDER"));
  }

  @Test
  public void digiDocSecurityProviderDefaultName() throws Exception {
    Hashtable<String, String> jDigiDocConf = configuration.loadConfiguration("src/main/resources/digidoc4j.yaml");
    assertEquals(Constant.JDigiDoc.SECURITY_PROVIDER_NAME, jDigiDocConf.get("DIGIDOC_SECURITY_PROVIDER_NAME"));
  }

  @Test
  public void asksValueOfNonExistingParameter() throws Exception {
    Hashtable<String, String> jDigiDocConf = configuration.loadConfiguration("src/main/resources/digidoc4j.yaml");
    assertNull(jDigiDocConf.get("DIGIDOC_PROXY_HOST"));
  }

  @Test
  public void digidocMaxDataFileCachedParameterIsNotANumber() throws Exception {
    String fileName = "src/test/resources/testFiles/yaml-configurations/digidoc_test_conf_invalid_max_data_file_cached.yaml";
    expectedException.expect(ConfigurationException.class);
    expectedException.expectMessage("Configuration parameter DIGIDOC_MAX_DATAFILE_CACHED" +
        " should have an integer value but the actual value is: 8192MB.");

    configuration.loadConfiguration(fileName);
  }

  @Test
  public void digidocSignOcspRequestIsNotABoolean() throws Exception {
    String fileName = "src/test/resources/testFiles/yaml-configurations/digidoc_test_conf_invalid_sign_ocsp_request.yaml";
    expectedException.expect(ConfigurationException.class);
    expectedException.expectMessage("Configuration parameter SIGN_OCSP_REQUESTS should be set to true or false" +
        " but the actual value is: NonBooleanValue.");

    configuration.loadConfiguration(fileName);
  }

  @Test
  public void digidocKeyUsageCheckIsNotABoolean() throws Exception {
    String fileName = "src/test/resources/testFiles/yaml-configurations/digidoc_test_conf_invalid_key_usage.yaml";
    expectedException.expect(ConfigurationException.class);
    expectedException.expectMessage("Configuration parameter KEY_USAGE_CHECK should be set to true or false" +
        " but the actual value is: NonBooleanValue.");

    configuration.loadConfiguration(fileName);
  }

  @Test
  public void digidocUseLocalTslIsNotABoolean() throws Exception {
    String fileName = "src/test/resources/testFiles/yaml-configurations/digidoc_test_conf_invalid_use_local_tsl.yaml";
    expectedException.expect(ConfigurationException.class);
    expectedException.expectMessage("Configuration parameter DIGIDOC_USE_LOCAL_TSL should be set to true or false" +
        " but the actual value is: NonBooleanValue.");

    configuration.loadConfiguration(fileName);
  }

  @Test
  public void digidocDataFileHashcodeModeIsNotABoolean() throws Exception {
    String fileName = "src/test/resources/testFiles/yaml-configurations/digidoc_test_conf_invalid_datafile_hashcode_mode.yaml";
    expectedException.expect(ConfigurationException.class);
    expectedException.expectMessage("Configuration parameter DATAFILE_HASHCODE_MODE should be set to true or false" +
        " but the actual value is: NonBooleanValue.");

    configuration.loadConfiguration(fileName);
  }

  @Test
  public void missingOCSPSEntryThrowsException() throws Exception {
    String fileName = "src/test/resources/testFiles/yaml-configurations/digidoc_test_conf_ocsps_no_entry.yaml";
    expectedException.expect(ConfigurationException.class);
    expectedException.expectMessage("No OCSPS entry found or OCSPS entry is empty. Configuration from: " + fileName);

    configuration.loadConfiguration(fileName);
    configuration.getJDigiDocConfiguration();
  }

  @Test
  public void emptyOCSPSEntryThrowsException() throws Exception {
    String fileName = "src/test/resources/testFiles/yaml-configurations/digidoc_test_conf_ocsps_empty.yaml";
    expectedException.expect(ConfigurationException.class);
    expectedException.expectMessage("No OCSPS entry found or OCSPS entry is empty. Configuration from: " + fileName);

    configuration.loadConfiguration(fileName);
    configuration.getJDigiDocConfiguration();
  }

  @Test
  public void OCSPWithoutCaCnValueThrowsException() throws Exception {
    String fileName = "src/test/resources/testFiles/yaml-configurations/digidoc_test_conf_ocsps_no_ca_cn.yaml";
    expectedException.expect(ConfigurationException.class);
    expectedException.expectMessage("Configuration from " + fileName + " contains error(s):\n" +
        "OCSPS list entry 2 does not have an entry for CA_CN or the entry is empty\n");

    configuration.loadConfiguration(fileName);
    configuration.getJDigiDocConfiguration();
  }

  @Test
  public void OCSPWithEmptySubEntriesThrowsException() throws Exception {
    String fileName = "src/test/resources/testFiles/yaml-configurations/digidoc_test_conf_ocsps_empty_sub_entries.yaml";
    String expectedErrorMessage = "Configuration from " + fileName + " contains error(s):\n" +
        "OCSPS list entry 3 does not have an entry for CA_CN or the entry is empty\n" +
        "OCSPS list entry 4 does not have an entry for CA_CERT or the entry is empty\n" +
        "OCSPS list entry 5 does not have an entry for CN or the entry is empty\n" +
        "OCSPS list entry 8 does not have an entry for URL or the entry is empty\n";
    expectedException.expect(ConfigurationException.class);
    expectedException.expectMessage(expectedErrorMessage);

    configuration.loadConfiguration(fileName);
    configuration.getJDigiDocConfiguration();
  }

  @Test
  public void OCSPWithMissingSubEntriesThrowsException() throws Exception {
    String fileName = "src/test/resources/testFiles/yaml-configurations/digidoc_test_conf_ocsps_missing_sub_entries.yaml";
    String expectedErrorMessage = "Configuration from " + fileName + " contains error(s):\n" +
        "OCSPS list entry 3 does not have an entry for CN or the entry is empty\n" +
        "OCSPS list entry 4 does not have an entry for URL or the entry is empty\n" +
        "OCSPS list entry 5 does not have an entry for CA_CERT or the entry is empty\n" +
        "OCSPS list entry 8 does not have an entry for CA_CN or the entry is empty\n";
    expectedException.expect(ConfigurationException.class);
    expectedException.expectMessage(expectedErrorMessage);

    configuration.loadConfiguration(fileName);
    configuration.getJDigiDocConfiguration();
  }

  @Test
  public void OCSPWithMissingOcspsCertsEntryThrowsException() throws Exception {
    String fileName = "src/test/resources/testFiles/yaml-configurations/digidoc_test_conf_ocsps_missing_certs_entry.yaml";
    String expectedErrorMessage = "Configuration from " + fileName + " contains error(s):\n" +
        "OCSPS list entry 3 does not have an entry for CERTS or the entry is empty\n";
    expectedException.expect(ConfigurationException.class);
    expectedException.expectMessage(expectedErrorMessage);

    configuration.loadConfiguration(fileName);
    configuration.getJDigiDocConfiguration();
  }

  @Test
  public void OCSPWithEmptyOcspsCertsEntryThrowsException() throws Exception {
    String fileName = "src/test/resources/testFiles/yaml-configurations/digidoc_test_conf_ocsps_empty_certs_entry.yaml";
    String expectedErrorMessage = "Configuration from " + fileName + " contains error(s):\n" +
        "OCSPS list entry 2 does not have an entry for CERTS or the entry is empty\n";
    expectedException.expect(ConfigurationException.class);
    expectedException.expectMessage(expectedErrorMessage);

    configuration.loadConfiguration(fileName);
    configuration.getJDigiDocConfiguration();
  }

  @Test
  public void configurationFileIsNotYamlFormatThrowsException() throws Exception {
    String fileName = "src/test/resources/testFiles/helper-files/test.txt";
    String expectedErrorMessage = "Configuration from " + fileName + " is not correctly formatted";
    expectedException.expect(ConfigurationException.class);
    expectedException.expectMessage(expectedErrorMessage);

    configuration.loadConfiguration(fileName);
  }

  @Test
  public void configurationStreamIsNotYamlFormatThrowsException() throws Exception {
    String fileName = "src/test/resources/testFiles/helper-files/test.txt";
    String expectedErrorMessage = "Configuration from stream is not correctly formatted";
    expectedException.expect(ConfigurationException.class);
    expectedException.expectMessage(expectedErrorMessage);

    FileInputStream stream = new FileInputStream(fileName);
    configuration.loadConfiguration(stream);
    stream.close();
  }

  @Test
  public void isOCSPSigningConfigurationAvailableWhenItIsNotAvailable() throws Exception {
    assertFalse(configuration.isOCSPSigningConfigurationAvailable());
  }

  @Test
  public void isOCSPSigningConfigurationAvailableWhenItIsAvailable() throws Exception {
    configuration.setOCSPAccessCertificateFileName("test.p12");
    configuration.setOCSPAccessCertificatePassword("aaa".toCharArray());

    assertTrue(configuration.isOCSPSigningConfigurationAvailable());
  }

  @Test
  public void isOCSPSigningConfigurationAvailableWhenFileIsAvailable() throws Exception {
    configuration.setOCSPAccessCertificateFileName("test.p12");
    assertFalse(configuration.isOCSPSigningConfigurationAvailable());
  }

  @Test
  public void isOCSPSigningConfigurationAvailableWhenPasswordIsAvailable() throws Exception {
    configuration.setOCSPAccessCertificatePassword("aaa".toCharArray());
    assertFalse(configuration.isOCSPSigningConfigurationAvailable());
  }

  @Test
  public void getTspSourceFromConfigurationFile() throws Exception {
    configuration.loadConfiguration("src/test/resources/testFiles/yaml-configurations/digidoc_test_conf.yaml");
    assertEquals("http://tsp.source.test/HttpTspServer", configuration.getTspSource());
  }

  @Test
  public void getValidationPolicyFromConfigurationFile() throws Exception {
    configuration.loadConfiguration("src/test/resources/testFiles/yaml-configurations/digidoc_test_conf.yaml");
    assertEquals("conf/test_validation_policy.xml", configuration.getValidationPolicy());
  }

  @Test
  public void getOcspSourceFromConfigurationFile() throws Exception {
    configuration.loadConfiguration("src/test/resources/testFiles/yaml-configurations/digidoc_test_conf.yaml");
    assertEquals("http://www.openxades.org/cgi-bin/test_ocsp_source.cgi", configuration.getOcspSource());
  }

  @Test
  public void getTslKeystoreLocationFromConfigurationFile() throws Exception {
    configuration.loadConfiguration("src/test/resources/testFiles/yaml-configurations/digidoc_test_conf.yaml");
    assertEquals("keystore", configuration.getTslKeyStoreLocation());
  }

  @Test(expected = TslKeyStoreNotFoundException.class)
  public void exceptionIsThrownWhenTslKeystoreIsNotFound() throws IOException {
    Configuration conf = new Configuration(PROD);
    conf.setTslKeyStoreLocation("not/existing/path");
    conf.getTSL().refresh();
  }

  @Test
  public void testDefaultTslKeystoreLocation() throws Exception {
    Configuration conf = new Configuration(PROD);
    assertEquals("keystore/keystore.jks", conf.getTslKeyStoreLocation());
  }

  @Test
  public void testDefaultTestTslKeystoreLocation() throws Exception {
    Configuration conf = new Configuration(TEST);
    assertEquals("keystore/test-keystore.jks", conf.getTslKeyStoreLocation());
  }

  @Test
  public void testDefaultTslKeystorePassword() throws Exception {
    Configuration conf = new Configuration(PROD);
    assertEquals("digidoc4j-password", conf.getTslKeyStorePassword());
  }

  @Test
  public void getTslKeystorePasswordFromConfigurationFile() throws Exception {
    configuration.loadConfiguration("src/test/resources/testFiles/yaml-configurations/digidoc_test_conf.yaml");
    assertEquals("password", configuration.getTslKeyStorePassword());
  }

  @Test
  public void setTslCacheExpirationTime() throws Exception {
    configuration.setTslCacheExpirationTime(1337);
    assertEquals(1337, configuration.getTslCacheExpirationTime());
  }

  @Test
  public void defaultTslCacheExpirationTime_shouldBeOneDay() throws Exception {
    long oneDayInMs = 1000 * 60 * 60 * 24;
    assertEquals(oneDayInMs, configuration.getTslCacheExpirationTime());
    assertEquals(oneDayInMs, new Configuration(Mode.PROD).getTslCacheExpirationTime());
  }

  @Test
  public void getTslCacheExpirationTimeFromConfigurationFile() throws Exception {
    configuration.loadConfiguration("src/test/resources/testFiles/yaml-configurations/digidoc_test_conf.yaml");
    assertEquals(1776, configuration.getTslCacheExpirationTime());
  }

  @Test
  public void defaultProxyConfiguration_shouldNotBeSet() throws Exception {
    assertFalse(configuration.isNetworkProxyEnabled());
    assertNull(configuration.getHttpProxyHost());
    assertNull(configuration.getHttpProxyPort());
    assertNull(configuration.getHttpProxyUser());
    assertNull(configuration.getHttpProxyPassword());
  }

  @Test
  public void getProxyConfigurationFromConfigurationFile() throws Exception {
    configuration.loadConfiguration("src/test/resources/testFiles/yaml-configurations/digidoc_test_conf.yaml");
    assertTrue(configuration.isNetworkProxyEnabled());
    assertEquals("cache.noile.ee", configuration.getHttpProxyHost());
    assertEquals(8080, configuration.getHttpProxyPort().longValue());
    assertEquals("proxyMan", configuration.getHttpProxyUser());
    assertEquals("proxyPass", configuration.getHttpProxyPassword());

  }

  @Test
  public void getInvalidProxyConfigurationFromConfigurationFile() throws Exception {
    expectedException.expect(ConfigurationException.class);
    expectedException.expectMessage("Configuration parameter HTTP_PROXY_PORT should have an integer value but the actual value is: notA_number.");
    configuration.loadConfiguration("src/test/resources/testFiles/yaml-configurations/digidoc_test_conf_invalid_key_usage.yaml");
  }

  @Test
  public void defaultSslConfiguration_shouldNotBeSet() throws Exception {
    assertFalse(configuration.isSslConfigurationEnabled());
    assertNull(configuration.getSslKeystorePath());
    assertNull(configuration.getSslKeystoreType());
    assertNull(configuration.getSslKeystorePassword());
    assertNull(configuration.getSslTruststorePath());
    assertNull(configuration.getSslTruststoreType());
    assertNull(configuration.getSslTruststorePassword());
  }

  @Test
  public void getSslConfigurationFromConfigurationFile() throws Exception {
    configuration.loadConfiguration("src/test/resources/testFiles/yaml-configurations/digidoc_test_all_optional_settings.yaml");
    assertTrue(configuration.isSslConfigurationEnabled());
    assertEquals("sslKeystorePath", configuration.getSslKeystorePath());
    assertEquals("sslKeystoreType", configuration.getSslKeystoreType());
    assertEquals("sslKeystorePassword", configuration.getSslKeystorePassword());
    assertEquals("sslTruststorePath", configuration.getSslTruststorePath());
    assertEquals("sslTruststoreType", configuration.getSslTruststoreType());
    assertEquals("sslTruststorePassword", configuration.getSslTruststorePassword());
  }

  @Test
  public void loadMultipleCAsFromConfigurationFile() throws Exception {
    Hashtable<String, String> jDigiDocConf = configuration.loadConfiguration("src/test/resources/testFiles/yaml-configurations/digidoc_test_conf_two_cas.yaml");
    configuration.getJDigiDocConfiguration();

    assertEquals("AS Sertifitseerimiskeskus", jDigiDocConf.get("DIGIDOC_CA_1_NAME"));
    assertEquals("jar://certs/ESTEID-SK.crt", jDigiDocConf.get("DIGIDOC_CA_1_CERT2"));
    assertEquals("Second CA", jDigiDocConf.get("DIGIDOC_CA_2_NAME"));
    assertEquals("jar://certs/CA_2_CERT_3.crt", jDigiDocConf.get("DIGIDOC_CA_2_CERT3"));
    assertEquals("jar://certs/CA_2_OCSP_1_SECOND_CERT", jDigiDocConf.get("DIGIDOC_CA_2_OCSP1_CERT_1"));
  }

  @Test
  public void missingCA_shouldNotThrowException() throws Exception {
    String fileName = "src/test/resources/testFiles/yaml-configurations/digidoc_test_conf_no_ca.yaml";
    configuration.loadConfiguration(fileName);
  }

  @Test
  public void missingCA_shouldThrowException_whenUsingDDoc() throws Exception {
    String fileName = "src/test/resources/testFiles/yaml-configurations/digidoc_test_conf_no_ca.yaml";
    String expectedErrorMessage = "Configuration from " + fileName + " contains error(s):\n" +
        "Empty or no DIGIDOC_CAS entry";
    expectedException.expect(ConfigurationException.class);
    expectedException.expectMessage(expectedErrorMessage);

    configuration.loadConfiguration(fileName);
    configuration.getJDigiDocConfiguration();
  }

  @Test
  public void emptyCAThrowsException() throws Exception {
    String fileName = "src/test/resources/testFiles/yaml-configurations/digidoc_test_conf_empty_ca.yaml";
    String expectedErrorMessage = "Configuration from " + fileName + " contains error(s):\n" +
        "Empty or no DIGIDOC_CA for entry 1";

    expectedException.expect(ConfigurationException.class);
    expectedException.expectMessage(expectedErrorMessage);

    configuration.loadConfiguration(fileName);
    configuration.getJDigiDocConfiguration();
  }

  @Test
  public void isTestMode() throws Exception {
    Configuration configuration = new Configuration(TEST);
    assertTrue(configuration.isTest());
  }

  @Test
  public void isNotTestMode() throws Exception {
    Configuration configuration = new Configuration(PROD);
    assertFalse(configuration.isTest());
  }

  @Test
  public void verifyAllOptionalConfigurationSettingsAreLoadedFromFile() throws Exception {
    configuration.setTslLocation("Set TSL location");
    configuration.setTspSource("Set TSP source");
    configuration.setOCSPAccessCertificateFileName("Set OCSP access certificate file name");
    configuration.setOCSPAccessCertificatePassword("Set password".toCharArray());
    configuration.setOcspSource("Set OCSP source");
    configuration.setValidationPolicy("Set validation policy");
    configuration.loadConfiguration("src/test/resources/testFiles/yaml-configurations/digidoc_test_all_optional_settings.yaml");
    assertEquals("123876", getJDigiDocConfValue(configuration, "DIGIDOC_MAX_DATAFILE_CACHED"));
    assertEquals("TEST_DIGIDOC_NOTARY_IMPL", getJDigiDocConfValue(configuration, "DIGIDOC_NOTARY_IMPL"));
    assertEquals("TEST_DIGIDOC_OCSP_SIGN_CERT_SERIAL", getJDigiDocConfValue(configuration, "DIGIDOC_OCSP_SIGN_CERT_SERIAL"));
    assertEquals("TEST_DIGIDOC_SECURITY_PROVIDER", getJDigiDocConfValue(configuration, "DIGIDOC_SECURITY_PROVIDER"));
    assertEquals("TEST_DIGIDOC_SECURITY_PROVIDER_NAME", getJDigiDocConfValue(configuration, "DIGIDOC_SECURITY_PROVIDER_NAME"));
    assertEquals("TEST_DIGIDOC_TSLFAC_IMPL", getJDigiDocConfValue(configuration, "DIGIDOC_TSLFAC_IMPL"));
    assertEquals("false", getJDigiDocConfValue(configuration, "DIGIDOC_USE_LOCAL_TSL"));
    assertEquals("false", getJDigiDocConfValue(configuration, "KEY_USAGE_CHECK"));
    assertEquals("false", getJDigiDocConfValue(configuration, SIGN_OCSP_REQUESTS));
    assertEquals("TEST_DIGIDOC_DF_CACHE_DIR", getJDigiDocConfValue(configuration, "DIGIDOC_DF_CACHE_DIR"));
    assertEquals("TEST_DIGIDOC_FACTORY_IMPL", getJDigiDocConfValue(configuration, "DIGIDOC_FACTORY_IMPL"));
    assertEquals("TEST_CANONICALIZATION_FACTORY_IMPL", getJDigiDocConfValue(configuration, "CANONICALIZATION_FACTORY_IMPL"));
    assertEquals("false", getJDigiDocConfValue(configuration, "DATAFILE_HASHCODE_MODE"));
    assertEquals("TEST_DIGIDOC_PKCS12_CONTAINER", configuration.getRegistry().get(ConfigurationParameter.OcspAccessCertificateFile));
    assertEquals("TEST_DIGIDOC_PKCS12_PASSWD", configuration.getRegistry().get(ConfigurationParameter.OcspAccessCertificatePassword));
    assertEquals("TEST_OCSP_SOURCE", configuration.getRegistry().get(ConfigurationParameter.OcspSource));
    assertEquals("TEST_TSP_SOURCE", configuration.getRegistry().get(ConfigurationParameter.TspSource));
    assertEquals("TEST_VALIDATION_POLICY", configuration.getRegistry().get(ConfigurationParameter.ValidationPolicy));
    assertEquals("TEST_TSL_LOCATION", configuration.getRegistry().get(ConfigurationParameter.TslLocation));
    configuration.setTslLocation("Set TSL location");
    configuration.setTspSource("Set TSP source");
    configuration.setOCSPAccessCertificateFileName("Set OCSP access certificate file name");
    configuration.setOCSPAccessCertificatePassword("Set password".toCharArray());
    configuration.setOcspSource("Set OCSP source");
    configuration.setValidationPolicy("Set validation policy");
    assertEquals("Set TSL location", configuration.getTslLocation());
    assertEquals("Set TSP source", configuration.getTspSource());
    assertEquals("Set OCSP access certificate file name", configuration.getOCSPAccessCertificateFileName());
    assertEquals("Set password", configuration.getRegistry().get(ConfigurationParameter.OcspAccessCertificatePassword));
    assertEquals("Set OCSP source", configuration.getOcspSource());
    assertEquals("Set validation policy", configuration.getValidationPolicy());
  }

  @Test
  public void getDefaultConnectionTimeout() throws Exception {
    assertEquals(1000, configuration.getConnectionTimeout());
    assertEquals(1000, configuration.getSocketTimeout());
  }

  @Test
  public void loadConnectionTimeoutFromFile() throws Exception {
    configuration.loadConfiguration("src/test/resources/testFiles/yaml-configurations/digidoc_test_conf_connection_timeout.yaml");
    assertEquals(4000, configuration.getConnectionTimeout());
    assertEquals(2000, configuration.getSocketTimeout());
  }

  @Test
  public void setConnectionTimeoutFromCode() throws Exception {
    configuration.loadConfiguration("src/test/resources/testFiles/yaml-configurations/digidoc_test_conf_connection_timeout.yaml");
    configuration.setConnectionTimeout(2000);
    configuration.setSocketTimeout(5000);
    assertEquals(2000, configuration.getConnectionTimeout());
    assertEquals(5000, configuration.getSocketTimeout());
  }

  @Test
  public void revocationAndTimestampDelta_shouldBeOneDay() throws Exception {
    int oneDayInMinutes = 24 * 60;
    assertEquals(oneDayInMinutes, configuration.getRevocationAndTimestampDeltaInMinutes());
  }

  @Test
  public void testSettingRevocationAndTimestampDelta() throws Exception {
    int twoDaysInMinutes = 48 * 60;
    configuration.setRevocationAndTimestampDeltaInMinutes(twoDaysInMinutes);
    assertEquals(twoDaysInMinutes, configuration.getRevocationAndTimestampDeltaInMinutes());
  }

  @Test
  public void testLoadingRevocationAndTimestampDeltaFromConf() throws Exception {
    configuration.loadConfiguration("src/test/resources/testFiles/yaml-configurations/digidoc_test_all_optional_settings.yaml");
    assertEquals(1337, configuration.getRevocationAndTimestampDeltaInMinutes());
  }

  @Test
  public void getTruestedTerritories_defaultTesting_shouldBeNull() throws Exception {
    assertEquals(new ArrayList<>(), configuration.getTrustedTerritories());
  }

  @Test
  public void getTrustedTerritories_defaultProd() throws Exception {
    Configuration configuration = new Configuration(PROD);
    List<String> trustedTerritories = configuration.getTrustedTerritories();
    assertNotNull(trustedTerritories);
    assertTrue(trustedTerritories.contains("EE"));
    assertTrue(trustedTerritories.contains("BE"));
    assertTrue(trustedTerritories.contains("NO"));
    assertFalse(trustedTerritories.contains("DE"));
    assertFalse(trustedTerritories.contains("HR"));
  }

  @Test
  public void setTrustedTerritories() throws Exception {
    configuration.setTrustedTerritories("AR", "US", "CA");
    List<String> trustedTerritories = configuration.getTrustedTerritories();
    assertEquals(3, trustedTerritories.size());
    assertEquals("AR", trustedTerritories.get(0));
    assertEquals("US", trustedTerritories.get(1));
    assertEquals("CA", trustedTerritories.get(2));
  }

  @Test
  public void loadTrustedTerritoriesFromConf() throws Exception {
    configuration.loadConfiguration("src/test/resources/testFiles/yaml-configurations/digidoc_test_all_optional_settings.yaml");
    List<String> trustedTerritories = configuration.getTrustedTerritories();
    assertEquals(3, trustedTerritories.size());
    assertEquals("NZ", trustedTerritories.get(0));
    assertEquals("AU", trustedTerritories.get(1));
    assertEquals("BR", trustedTerritories.get(2));
  }

  @Test
  public void testOpenBDocWithConfFromSetter() {
    Configuration configuration = new Configuration(Configuration.Mode.PROD);
    configuration.setOcspSource("http://demo.sk.ee/TEST");
    Container container = ContainerBuilder.
        aContainer().withConfiguration(configuration).
        fromExistingFile("src/test/resources/testFiles/valid-containers/test.asice").
        build();
    assertEquals("http://demo.sk.ee/TEST", configuration.getOcspSource());
  }

  @Test
  public void testOpenBDocWithConfFromYaml() {
    Configuration configuration = new Configuration(Configuration.Mode.PROD);
    configuration.loadConfiguration("src/test/resources/testFiles/yaml-configurations/digidoc_test_conf_parameters.yaml");
    Container container = ContainerBuilder.
        aContainer().withConfiguration(configuration).
        fromExistingFile("src/test/resources/testFiles/valid-containers/test.asice").
        build();
    assertEquals("test_source_from_yaml", configuration.getOcspSource());
  }

  @Test
  public void testOpenBDocWithConfFromSetterWhenYamlParamPresented() {
    Configuration configuration = new Configuration(Configuration.Mode.PROD);
    configuration.loadConfiguration("src/test/resources/testFiles/yaml-configurations/digidoc_test_conf_parameters.yaml");
    configuration.setOcspSource("http://demo.sk.ee/TEST");
    Container container = ContainerBuilder.
        aContainer().withConfiguration(configuration).
        fromExistingFile("src/test/resources/testFiles/valid-containers/test.asice").
        build();
    assertEquals("http://demo.sk.ee/TEST", configuration.getOcspSource());
  }

  @Test
  public void loadAllowedTimestampAndOCSPResponseDelta() throws Exception {
    Configuration configuration = new Configuration(TEST);
    assertEquals(15, configuration.getAllowedTimestampAndOCSPResponseDeltaInMinutes().longValue());
  }

  @Test
  public void loadAllowedTimestampAndOCSPResponseDeltaFromConf() throws Exception {
    configuration.loadConfiguration("src/test/resources/testFiles/yaml-configurations/digidoc_test_all_optional_settings.yaml");
    assertEquals(1, configuration.getAllowedTimestampAndOCSPResponseDeltaInMinutes().longValue());
  }

  @Test
  public void testLoadingSignatureProfile() throws Exception {
    Configuration configuration = new Configuration(TEST);
    assertEquals(SignatureProfile.LT, configuration.getSignatureProfile());
  }

  @Test
  public void testLoadingSignatureProfileFromConf() throws Exception {
    configuration.loadConfiguration("src/test/resources/testFiles/yaml-configurations/digidoc_test_all_optional_settings.yaml");
    assertEquals(SignatureProfile.LT_TM, configuration.getSignatureProfile());
  }

  @Test
  public void testLoadingSignatureDigestAlgorithm() throws Exception {
    Configuration configuration = new Configuration(TEST);
    assertEquals(DigestAlgorithm.SHA256, configuration.getSignatureDigestAlgorithm());
  }

  @Test
  public void testLoadingSignatureDigestAlgorithmFromConf() throws Exception {
    configuration.loadConfiguration("src/test/resources/testFiles/yaml-configurations/digidoc_test_all_optional_settings.yaml");
    assertEquals(DigestAlgorithm.SHA512, configuration.getSignatureDigestAlgorithm());
  }

  @Test
  public void testConfigurationHasChanged() throws Exception {
    Configuration configuration1 = new Configuration(Mode.TEST);
    Configuration configuration2 = new Configuration(Mode.PROD);
    String path = this.toPath("configuration1");
    Helper.serialize(configuration1, path);
    configuration1 = Helper.deserializer(path);
    Assert.assertTrue("No differences", this.isConfigurationsDifferent(configuration1, configuration2));
  }

  @Test
  public void testConfigurationHasNotChanged() throws Exception {
    System.setProperty("digidoc4j.mode", "test");
    Configuration configuration1 = new Configuration(Mode.TEST);
    Configuration configuration2 = new Configuration(Mode.TEST);
    String path = this.toPath("configuration1");
    Helper.serialize(configuration1, path);
    configuration1 = Helper.deserializer(path);
    Assert.assertFalse("Differences", this.isConfigurationsDifferent(configuration1, configuration2));
  }

  private boolean isConfigurationsDifferent(Configuration configuration1, Configuration configuration2) {
    if (StringUtils.isBlank(configuration1.getRegistry().getSealValue())) {
      return false;
    }
    return !configuration1.getRegistry().getSealValue().equals(configuration2.getRegistry().generateSealValue());
  }

  private String toPath(String fileName) throws IOException {
    return String.format("%s%s%s.ser", this.testFolder.newFolder().getAbsolutePath(), File.separator, fileName);
  }

  private File createConfFileWithParameter(String parameter) throws IOException {
    File confFile = testFolder.newFile();
    FileUtils.writeStringToFile(confFile, parameter);
    String defaultConfParameters = "\n" +
        "DIGIDOC_CAS:\n" +
        "- DIGIDOC_CA:\n" +
        "    NAME: AS Sertifitseerimiskeskus\n" +
        "    TRADENAME: SK\n" +
        "    CERTS:\n" +
        "      - jar://certs/EID-SK.crt\n" +
        "    OCSPS:\n" +
        "      - OCSP:\n" +
        "        CA_CN: ESTEID-SK\n" +
        "        CA_CERT: jar://certs/ESTEID-SK 2007.crt\n" +
        "        CN: ESTEID-SK 2007 OCSP RESPONDER\n" +
        "        CERTS:\n" +
        "         - jar://certs/ESTEID-SK 2007 OCSP.crt\n" +
        "        URL: http://ocsp.sk.ee";
    FileUtils.writeStringToFile(confFile, defaultConfParameters, true);
    return confFile;
  }

  private String getJDigiDocConfValue(Configuration configuration, String key) {
    return configuration.getJDigiDocConfiguration().get(key);
  }

  private void waitOneSecond() throws InterruptedException {
    Thread.sleep(1000L); //Waiting is necessary to check changes in the cached files modification time
  }

  private long getCacheModificationTime() {
    return TSLHelper.getCacheLastModificationTime();
  }

  private void deleteTSLCache() {
    TSLHelper.deleteTSLCache();
  }

}