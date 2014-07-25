package org.digidoc4j;

import ee.sk.digidoc.CertValue;
import ee.sk.digidoc.DigiDocException;
import ee.sk.digidoc.SignatureProductionPlace;
import ee.sk.digidoc.SignedDoc;
import ee.sk.digidoc.factory.DigiDocFactory;
import ee.sk.digidoc.factory.SAXDigiDocFactory;
import ee.sk.utils.ConfigManager;
import org.digidoc4j.api.*;
import org.digidoc4j.api.exceptions.DigiDoc4JException;
import org.digidoc4j.api.exceptions.NotYetImplementedException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static ee.sk.digidoc.DataFile.CONTENT_EMBEDDED_BASE64;

/**
 * Offers functionality for handling data files and signatures in a container.
 * <p>
 * A container can contain several files and all those files can be signed using signing certificates.
 * A container can only be signed if it contains data files.
 * </p><p>
 * Data files can be added and removed from a container only if the container is not signed.
 * To modify the data list of a signed container by adding or removing datafiles you must first
 * remove all the signatures.
 * </p>
 */
public class DDocContainer extends Container {

  private SignedDoc ddoc;
  private ArrayList<ee.sk.digidoc.DigiDocException> openContainerErrors =
      new ArrayList<ee.sk.digidoc.DigiDocException>();

  /**
   * Create a new container object of DDOC type Container.
   */
  public DDocContainer() {
    try {
      ddoc = new SignedDoc("DIGIDOC-XML", "1.3");
    } catch (DigiDocException e) {
      throw new DigiDoc4JException(e);
    }
  }

  private void intConfiguration() {
    ConfigManager.init("jdigidoc.cfg");
    ConfigManager.addProvider();
  }

  /**
   * Opens the container from a file.
   *
   * @param fileName container file name with path
   *                 ]
   */
  public DDocContainer(String fileName) {
    intConfiguration();
    DigiDocFactory digFac = new SAXDigiDocFactory();
    try {
      ddoc = digFac.readSignedDocOfType(fileName, false, openContainerErrors);
    } catch (DigiDocException e) {
      throw new DigiDoc4JException(e);
    }
  }

  DDocContainer(SignedDoc ddoc) {
    intConfiguration();
    this.ddoc = ddoc;
  }

  @Override
  public void addDataFile(String path, String mimeType) {
    try {
      ddoc.addDataFile(new File(path), mimeType, CONTENT_EMBEDDED_BASE64);
    } catch (DigiDocException e) {
      throw new DigiDoc4JException(e);
    }
  }

  @Override
  public void addDataFile(InputStream is, String fileName, String mimeType) {
    try {
      ee.sk.digidoc.DataFile dataFile = new ee.sk.digidoc.DataFile(ddoc.getNewDataFileId(),
          ee.sk.digidoc.DataFile.CONTENT_EMBEDDED_BASE64,
          fileName, mimeType, ddoc);
      dataFile.setBodyFromStream(is);
      ddoc.addDataFile(dataFile);
    } catch (DigiDocException e) {
      throw new DigiDoc4JException(e);
    }
  }

  @Override
  public void addRawSignature(byte[] signatureBytes) {
    addRawSignature(new ByteArrayInputStream(signatureBytes));
  }

  @Override
  public void addRawSignature(InputStream signatureStream) {
    try {
      ddoc.readSignature(signatureStream);
    } catch (DigiDocException e) {
      throw new DigiDoc4JException(e);
    }
  }

  @Override
  public List<DataFile> getDataFiles() {
    List<DataFile> dataFiles = new ArrayList<DataFile>();
    ArrayList ddocDataFiles = ddoc.getDataFiles();
    for (Object ddocDataFile : ddocDataFiles) {
      ee.sk.digidoc.DataFile dataFile = (ee.sk.digidoc.DataFile) ddocDataFile;
      try {
        if (dataFile.getBody() == null)
          dataFiles.add(new DataFile(dataFile.getFileName(), dataFile.getMimeType()));
        else
          dataFiles.add(new DataFile(dataFile.getBody(), dataFile.getFileName(), dataFile.getMimeType()));
      } catch (DigiDocException e) {
        throw new DigiDoc4JException(e);
      }
    }
    return dataFiles;
  }

  @Override
  public void removeDataFile(String fileName) {
    removeDataFile(new File(fileName));
  }

  private void removeDataFile(File file) {
    int index = -1;
    ArrayList ddocDataFiles = ddoc.getDataFiles();
    for (int i = 0; i < ddocDataFiles.size(); i++) {
      ee.sk.digidoc.DataFile dataFile = (ee.sk.digidoc.DataFile) ddocDataFiles.get(i);
      if (dataFile.getFileName().equalsIgnoreCase(file.getName())) index = i;
    }
    if (index == -1) throw new DigiDoc4JException("File not found");

    try {
      ddoc.removeDataFile(index);
    } catch (DigiDocException e) {
      throw new DigiDoc4JException(e);
    }
  }

  @Override
  public void removeSignature(int index) {
    try {
      ddoc.removeSignature(index);
    } catch (DigiDocException e) {
      throw new DigiDoc4JException(e);
    }
  }

  @Override
  public void save(String path) {
    try {
      ddoc.writeToFile(new File(path));
    } catch (DigiDocException e) {
      throw new DigiDoc4JException(e);
    }
  }

  @Override
  public Signature sign(Signer signer) {
    ee.sk.digidoc.Signature signature;
    try {
      List<String> signerRoles = signer.getSignerRoles();
      signature = ddoc.prepareSignature(signer.getCertificate().getX509Certificate(),
          signerRoles.toArray(new String[signerRoles.size()]),
          new SignatureProductionPlace(signer.getCity(), signer.getStateOrProvince(),
              signer.getCountry(), signer.getPostalCode()));

      signature.setSignatureValue(signer.sign(eu.europa.ec.markt.dss.DigestAlgorithm.SHA1.getXmlId(),
          signature.calculateSignedInfoXML()));

      signature.getConfirmation();
    } catch (DigiDocException e) {
      throw new DigiDoc4JException(e);
    }

    return new DDocSignature(signature);
  }

  @Override
  public void setConfiguration(Configuration conf) {
    throw new NotYetImplementedException();
  }

  @Override
  public List<Signature> getSignatures() {
    List<Signature> signatures = new ArrayList<Signature>();
    if (ddoc == null) {
      return null;
    }

    ArrayList dDocSignatures = ddoc.getSignatures();

    if (dDocSignatures == null) {
      return null;
    }

    for (Object signature : dDocSignatures) {
      Signature finalSignature = mapJDigiDocSignatureToDigiDoc4J((ee.sk.digidoc.Signature) signature);
      if (finalSignature != null) {
        signatures.add(finalSignature);
      }
    }

    return signatures;
  }

  private Signature mapJDigiDocSignatureToDigiDoc4J(ee.sk.digidoc.Signature signature) {
    Signature finalSignature = new DDocSignature(signature);
    CertValue lastCertValue = signature.getLastCertValue();
    if (lastCertValue == null) {
      return null;
    }

    finalSignature.setCertificate(new X509Cert(lastCertValue.getCert()));
    //TODO can be several certs
    //TODO check logic about one role versus several roles

    return finalSignature;
  }

  @Override
  public DocumentType getDocumentType() {
    return DocumentType.DDOC;
  }

  @Override
  public void setDigestAlgorithm(DigestAlgorithm algorithm) {
  }


  @Override
  public List<DigiDoc4JException> validate() {
    if (SignedDoc.hasFatalErrs(openContainerErrors)) {
      return convertToDigiDoc4JExceptions(openContainerErrors);
    }

    List exceptions = ddoc.verify(true, true);

    List<DigiDoc4JException> allExceptions;
    allExceptions = convertToDigiDoc4JExceptions(openContainerErrors);
    allExceptions.addAll(convertToDigiDoc4JExceptions(exceptions));
    return allExceptions;
  }

  private List<DigiDoc4JException> convertToDigiDoc4JExceptions(List errorsAndWarnings) {
    List<DigiDoc4JException> errors = new ArrayList<DigiDoc4JException>();
    for (Object errorsAndWarning : errorsAndWarnings) {
      errors.add(new DigiDoc4JException(errorsAndWarning.toString()));
    }
    return errors;
  }
}






