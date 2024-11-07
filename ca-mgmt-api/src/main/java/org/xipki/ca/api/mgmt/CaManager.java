// Copyright (c) 2013-2024 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.ca.api.mgmt;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CRLHolder;
import org.xipki.ca.api.mgmt.entry.CaEntry;
import org.xipki.ca.api.mgmt.entry.CaHasRequestorEntry;
import org.xipki.ca.api.mgmt.entry.CertprofileEntry;
import org.xipki.ca.api.mgmt.entry.ChangeCaEntry;
import org.xipki.ca.api.mgmt.entry.KeypairGenEntry;
import org.xipki.ca.api.mgmt.entry.PublisherEntry;
import org.xipki.ca.api.mgmt.entry.RequestorEntry;
import org.xipki.ca.api.mgmt.entry.SignerEntry;
import org.xipki.security.CertRevocationInfo;
import org.xipki.security.CrlReason;
import org.xipki.security.KeyCertBytesPair;
import org.xipki.security.X509Cert;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interface to manage the CA system.
 *
 * @author Lijun Liao (xipki)
 * @since 2.0.0
 */

public interface CaManager {

  String NULL = "null";

  /**
   * Although it is possible to configure it to 1, it is recommended to set it to at least 9.
   * At least 64 bit entropy is required. Since the highest bit is set to 0, at least
   * 9 bytes is recommended.
   */
  int MIN_SERIALNUMBER_SIZE = 1;

  /**
   * Since serial number should be positive and maximal 20 bytes.
   */
  int MAX_SERIALNUMBER_SIZE = 20;

  CaSystemStatus getCaSystemStatus() throws CaMgmtException;

  void unlockCa() throws CaMgmtException;

  void notifyCaChange() throws CaMgmtException;

  void addDbSchema(String name, String value) throws CaMgmtException;

  void changeDbSchema(String name, String value) throws CaMgmtException;

  void removeDbSchema(String name) throws CaMgmtException;

  Map<String, String> getDbSchemas() throws CaMgmtException;

  /**
   * Republishes certificates of the CA {@code caName} to the publishers {@code publisherNames}.
   *
   * @param caName
   *          CA name. Could be {@code null}.
   * @param publisherNames
   *          Publisher names. Could be {@code null}.
   * @param numThreads
   *          Number of threads
   * @throws CaMgmtException
   *          if error occurs.
   *
   */
  void republishCertificates(String caName, List<String> publisherNames, int numThreads) throws CaMgmtException;

  /**
   * Removes the CA {@code caName} from the system.
   *
   * @param caName
   *          CA name. Must not be {@code null}.
   * @throws CaMgmtException
   *          if error occurs.
   */
  void removeCa(String caName) throws CaMgmtException;

  /**
   * Restart the given CA.
   * @param caName
   *          CA name. Must not be {@code null}.
   * @throws CaMgmtException
   *           if error occurs.
   */
  void restartCa(String caName) throws CaMgmtException;

  /**
   * Restart the whole CA system.
   * @throws CaMgmtException
   *           if error occurs.
   */
  void restartCaSystem() throws CaMgmtException;

  /**
   * Adds the alias {@code aliasName} to the given CA {@code caName}.
   *
   * @param aliasName
   *          CA alias name. Must not be {@code null}.
   * @param caName
   *          CA name. Must not be {@code null}.
   * @throws CaMgmtException
   *          if error occurs.
   */
  void addCaAlias(String aliasName, String caName) throws CaMgmtException;

  /**
   * Remove the alias {@code aliasName}.
   *
   * @param aliasName
   *          Alias name. Must not be {@code null}.
   * @throws CaMgmtException
   *          if error occurs.
   */
  void removeCaAlias(String aliasName) throws CaMgmtException;

  /**
   * Gets the aliases of the given CA {@code caName}.
   *
   * @param caName
   *          CA name. Must not be {@code null}.
   * @return the aliases of the given CA.
   * @throws CaMgmtException
   *          if error occurs.
   */
  Set<String> getAliasesForCa(String caName) throws CaMgmtException;

  /**
   * Gets the CA name for the alias {@code aliasName}.
   *
   * @param aliasName
   *          CA alias name. Must not be {@code null}.
   * @return the aliases of the given CA.
   * @throws CaMgmtException
   *          if error occurs.
   */
  String getCaNameForAlias(String aliasName) throws CaMgmtException;

  Set<String> getCaAliasNames() throws CaMgmtException;

  Set<String> getCertprofileNames() throws CaMgmtException;

  Set<String> getKeypairGenNames() throws CaMgmtException;

  Set<String> getPublisherNames() throws CaMgmtException;

  Set<String> getRequestorNames() throws CaMgmtException;

  Set<String> getSignerNames() throws CaMgmtException;

  Set<String> getCaNames() throws CaMgmtException;

  Set<String> getSuccessfulCaNames() throws CaMgmtException;

  Set<String> getFailedCaNames() throws CaMgmtException;

  Set<String> getInactiveCaNames() throws CaMgmtException;

  /**
   * Adds a CA.
   * @param caEntry
   *          CA to be added. Must not be {@code null}.
   * @throws CaMgmtException
   *          if error occurs.
   */
  void addCa(CaEntry caEntry) throws CaMgmtException;

  /**
   * Gets the CA named {@code caName}.
   * @param caName
   *          CA name. Must not be {@code null}.
   * @return the CaEntry
   * @throws CaMgmtException
   *          if error occurs.
   */
  CaEntry getCa(String caName) throws CaMgmtException;

  /**
   * Changes a CA.
   *
   * @param changeCaEntry
   *          ChangeCA entry. Must not be {@code null}.
   * @throws CaMgmtException
   *          if error occurs.
   */
  void changeCa(ChangeCaEntry changeCaEntry) throws CaMgmtException;

  /**
   * Removes the support of the certprofile {@code profileName} from the CA {@code caName}.
   *
   * @param profileName
   *          Profile name. Must not be {@code null}.
   * @param caName
   *          CA name. Must not be {@code null}.
   * @throws CaMgmtException
   *          if error occurs.
   */
  void removeCertprofileFromCa(String profileName, String caName) throws CaMgmtException;

  /**
   * Add the certificate profile {@code profileName} the CA {@code caName}.
   * @param profileNameAliases
   *          Profile name and aliases. Must not be {@code null}.
   * @param caName
   *          CA name. Must not be {@code null}.
   * @throws CaMgmtException
   *          if error occurs.
   */
  void addCertprofileToCa(String profileNameAliases, String caName) throws CaMgmtException;

  /**
   * Removes publisher {@code publisherName} from the CA {@code caName}.
   * @param publisherName
   *          Publisher name. Must not be {@code null}.
   * @param caName
   *          CA name. Must not be {@code null}.
   * @throws CaMgmtException
   *          if error occurs.
   */
  void removePublisherFromCa(String publisherName, String caName) throws CaMgmtException;

  /**
   * Adds publisher {@code publisherName} to CA {@code caName}.
   * @param publisherName
   *          Publisher name. Must not be {@code null}.
   * @param caName
   *          CA name. Must not be {@code null}.
   * @throws CaMgmtException
   *          if error occurs.
   */
  void addPublisherToCa(String publisherName, String caName) throws CaMgmtException;

  /**
   * Returns the Certprofile names supported by the CA {@code caName}.
   *
   * @param caName CA name. Must not be {@code null}.
   * @return set of certprofiles name and their associated aliases.
   * @throws CaMgmtException if error occurs.
   */
  Set<CaProfileEntry> getCertprofilesForCa(String caName) throws CaMgmtException;

  /**
   * Returns the Requests supported by the CA {@code caName}.
   * @param caName
   *          CA name. Must not be {@code null}.
   * @return the requestors.
   * @throws CaMgmtException
   *          if error occurs.
   */
  Set<CaHasRequestorEntry> getRequestorsForCa(String caName) throws CaMgmtException;

  /**
   * Returns the requestor named {@code name}.
   * @param name
   *          Requestor name. Must not be {@code null}.
   * @return the requestor.
   * @throws CaMgmtException
   *          if error occurs.
   */
  RequestorEntry getRequestor(String name) throws CaMgmtException;

  /**
   * Adds requstor.
   * @param requestorEntry
   *          Requestor entry. Must not be {@code null}.
   * @throws CaMgmtException
   *          if error occurs.
   */
  void addRequestor(RequestorEntry requestorEntry) throws CaMgmtException;

  /**
   * Removes requestor named {@code requestorName}.
   * @param requestorName
   *          Requestor name. Must not be {@code null}.
   * @throws CaMgmtException
   *          if error occurs.
   */
  void removeRequestor(String requestorName) throws CaMgmtException;

  /**
   * Changes the requestor {@code name} of type CERTIFCATE.
   * @param name
   *          name of the certificate profile to be changed. Must not be {@code null}.
   * @param type
   *          Type to be changed. {@code null} indicates no change.
   * @param conf
   *          Configuration to be changed. {@code null} indicates no change.
   * @throws CaMgmtException
   *          if error occurs.
   */
  void changeRequestor(String name, String type, String conf) throws CaMgmtException;

  /**
   * Removes the requestor {@code requestorName} from the CA {@code caName}.
   * @param requestorName
   *          Requestor name. Must not be {@code null}.
   * @param caName
   *          CA name. Must not be {@code null}.
   * @throws CaMgmtException
   *          if error occurs.
   */
  void removeRequestorFromCa(String requestorName, String caName) throws CaMgmtException;

  /**
   * Adds the requestor {@code requestorName} to the CA {@code caName}.
   * @param requestor
   *          Requestor name. Must not be {@code null}.
   * @param caName
   *          CA name. Must not be {@code null}.
   * @throws CaMgmtException
   *          if error occurs.
   */
  void addRequestorToCa(CaHasRequestorEntry requestor, String caName) throws CaMgmtException;

  /**
   * Returns the certificate profile named {@code profileName}.
   * @param profileName
   *          certificate profile name. Must not be {@code null}.
   * @return the profile
   * @throws CaMgmtException
   *          if error occurs.
   */
  CertprofileEntry getCertprofile(String profileName) throws CaMgmtException;

  /**
   * Removes the certificate profile {@code profileName}.
   * @param profileName
   *          certificate profile name. Must not be {@code null}.
   * @throws CaMgmtException
   *          if error occurs.
   */
  void removeCertprofile(String profileName) throws CaMgmtException;

  /**
   * Changes the certificate profile {@code name}.
   * @param name
   *          name of the certificate profile to be changed. Must not be {@code null}.
   * @param type
   *          Type to be changed. {@code null} indicates no change.
   * @param conf
   *          Configuration to be changed. {@code null} indicates no change.
   * @throws CaMgmtException
   *          if error occurs.
   */
  void changeCertprofile(String name, String type, String conf) throws CaMgmtException;

  /**
   * Adds a certificate profile.
   * @param certprofileEntry
   *          Certificate profile entry. Must not be {@code null}.
   * @throws CaMgmtException
   *          if error occurs.
   */
  void addCertprofile(CertprofileEntry certprofileEntry) throws CaMgmtException;

  /**
   * Returns the keypair generation entry named {@code profileName}.
   * @param name
   *          keypair generation name. Must not be {@code null}.
   * @return the keypair generation entry
   * @throws CaMgmtException
   *          if error occurs.
   */
  KeypairGenEntry getKeypairGen(String name) throws CaMgmtException;

  /**
   * Removes the keypair generation entry {@code name}.
   * @param name
   *          keypair generation name. Must not be {@code null}.
   * @throws CaMgmtException
   *          if error occurs.
   */
  void removeKeypairGen(String name) throws CaMgmtException;

  /**
   * Changes the keypair generation entry {@code name}.
   * @param name
   *          name of the keypair generation entry to be changed. Must not be {@code null}.
   * @param type
   *          Type to be changed. {@code null} indicates no change.
   * @param conf
   *          Configuration to be changed. {@code null} indicates no change.
   * @throws CaMgmtException
   *          if error occurs.
   */
  void changeKeypairGen(String name, String type, String conf) throws CaMgmtException;

  /**
   * Adds a keypair generation entry.
   * @param keypairGenEntry
   *          Keypair generation entry. Must not be {@code null}.
   * @throws CaMgmtException
   *          if error occurs.
   */
  void addKeypairGen(KeypairGenEntry keypairGenEntry) throws CaMgmtException;

  /**
   * Adds a signer.
   * @param signerEntry
   *          Signer entry. Must not be {@code null}.
   * @throws CaMgmtException
   *          if error occurs.
   */
  void addSigner(SignerEntry signerEntry) throws CaMgmtException;

  /**
   * Removes the signer named {@code name}.
   * @param name
   *          Signer name. Must not be {@code null}.
   * @throws CaMgmtException
   *          if error occurs.
   */
  void removeSigner(String name) throws CaMgmtException;

  /**
   * Returns the signer named {@code name}.
   * @param name
   *          Signer name. Must not be {@code null}.
   * @return the signer.
   * @throws CaMgmtException
   *          if error occurs.
   */
  SignerEntry getSigner(String name) throws CaMgmtException;

  /**
   * Changes the signer {@code name}.
   * @param name
   *          name of the signer to be changed. Must not be {@code null}.
   * @param type
   *          Type to be changed. {@code null} indicates no change.
   * @param conf
   *          Configuration to be changed. {@code null} indicates no change.
   * @param base64Cert
   *          Base64 encoded certificate of the signer. {@code null} indicates no change.
   * @throws CaMgmtException
   *          if error occurs.
   */
  void changeSigner(String name, String type, String conf, String base64Cert) throws CaMgmtException;

  /**
   * Adds a publisher.
   * @param entry
   *          Publisher entry.
   * @throws CaMgmtException
   *          if error occurs.
   */
  void addPublisher(PublisherEntry entry) throws CaMgmtException;

  /**
   * Returns names of publishers for the CA {@code caName}.
   * @param caName
   *          CA name. Must not be {@code null}.
   * @return publisher names for the given CA.
   * @throws CaMgmtException
   *          if error occurs.
   */
  Set<String> getPublisherNamesForCa(String caName) throws CaMgmtException;

  /**
   * Returns publishers for the CA {@code caName}.
   * @param caName
   *          CA name. Must not be {@code null}.
   * @return publishers for the given CA.
   * @throws CaMgmtException
   *          if error occurs.
   * @deprecated since v6.5.2
   */
  @Deprecated
  List<PublisherEntry> getPublishersForCa(String caName) throws CaMgmtException;

  /**
   * Returns the publisher.
   * @param publisherName
   *          Publisher name. Must not be {@code null}.
   * @return the publisher.
   * @throws CaMgmtException
   *          if error occurs.
   */
  PublisherEntry getPublisher(String publisherName) throws CaMgmtException;

  /**
   * Removes the publisher {@code publisherName}.
   * @param publisherName
   *          Publisher name. Must not be {@code null}.
   * @throws CaMgmtException
   *          if error occurs.
   */
  void removePublisher(String publisherName) throws CaMgmtException;

  /**
   * Changes the publisher {@code name}.
   * @param name
   *          name of the publisher to be changed. Must not be {@code null}.
   * @param type
   *          Type to be changed. {@code null} indicates no change.
   * @param conf
   *          Configuration to be changed. {@code null} indicates no change.
   * @throws CaMgmtException
   *          if error occurs.
   */
  void changePublisher(String name, String type, String conf) throws CaMgmtException;

  /**
   * Revokes the CA {@code caName}.
   * @param caName
   *          CA name. Must not be {@code null}.
   * @param revocationInfo
   *          Revocation information. Must not be {@code null}.
   * @throws CaMgmtException
   *          if error occurs.
   */
  void revokeCa(String caName, CertRevocationInfo revocationInfo) throws CaMgmtException;

  /**
   * Unrevokes the CA {@code caName}.
   * @param caName
   *          CA name. Must not be {@code null}.
   * @throws CaMgmtException
   *          if error occurs.
   */
  void unrevokeCa(String caName) throws CaMgmtException;

  /**
   * Revokes a certificate with the serial number {@code serialNumber}, and
   * issued by the CA {@code caName}.
   * @param caName
   *          CA name. Must not be {@code null}.
   * @param serialNumber
   *          Serial number. Must not be {@code null}.
   * @param reason
   *          Revocation reason. Must not be {@code null}.
   * @param invalidityTime
   *          Invalidity time. Could be {@code null}.
   * @throws CaMgmtException
   *          if error occurs.
   */
  void revokeCertificate(String caName, BigInteger serialNumber, CrlReason reason, Instant invalidityTime)
      throws CaMgmtException;

  /**
   * Unrevokes a certificate with the serial number {@code serialNumber}, and
   * issued by the CA {@code caName}.
   *
   * @param caName
   *          CA name. Must not be {@code null}.
   * @param serialNumber
   *          Serial number. Must not be {@code null}.
   * @throws CaMgmtException
   *          if error occurs.
   */
  void unsuspendCertificate(String caName, BigInteger serialNumber) throws CaMgmtException;

  /**
   * Removes a certificate with the serial number {@code serialNumber}, and
   * issued by the CA {@code caName}.
   *
   * @param caName
   *          CA name. Must not be {@code null}.
   * @param serialNumber
   *          Serial number. Must not be {@code null}.
   * @throws CaMgmtException
   *          if error occurs.
   */
  void removeCertificate(String caName, BigInteger serialNumber) throws CaMgmtException;

  /**
   * CA {@code caName} issues a new certificate.
   *
   * @param caName
   *          CA name. Must not be {@code null}.
   * @param profileName
   *          Name of the certificate profile. Must not be {@code null}.
   * @param encodedCsr
   *          CSR. Must not be {@code null}.
   * @param notBefore
   *          NotBefore. Could be {@code null}.
   * @param notAfter
   *          NotAfter. Could be {@code null}.
   * @return the issued certificate
   * @throws CaMgmtException
   *          if error occurs.
   */
  X509Cert generateCertificate(String caName, String profileName, byte[] encodedCsr,
                               Instant notBefore, Instant notAfter) throws CaMgmtException;

  /**
   * CA {@code caName} issues a new certificate.
   *
   * @param caName
   *          CA name. Must not be {@code null}.
   * @param profileName
   *          Name of the certificate profile. Must not be {@code null}.
   * @param subject
   *          Subject. Must not be {@code null}.
   * @param notBefore
   *          NotBefore. Could be {@code null}.
   * @param notAfter
   *          NotAfter. Could be {@code null}.
   * @return the generated key and issued certificate
   * @throws CaMgmtException
   *          if error occurs.
   */
  KeyCertBytesPair generateKeyCert(
      String caName, String profileName, String subject, Instant notBefore, Instant notAfter) throws CaMgmtException;

  /**
   * CA {@code caName} issues a new certificate.
   *
   * @param caName
   *          CA name. Must not be {@code null}.
   * @param profileName
   *          Name of the certificate profile. Must not be {@code null}.
   * @param encodedCsr
   *          CSR. Must not be {@code null}.
   * @param encodedTargetCert
   *          certificate file, for which the cross certificate will be
   *          generated. There shall be not different in subject and public
   *          key between #encodedCsr and #encodedCert.
   * @param notBefore
   *          NotBefore.
   * @param notAfter
   *          NotAfter
   * @return the issued certificate
   * @throws CaMgmtException
   *          if error occurs.
   */
  X509Cert generateCrossCertificate(String caName, String profileName, byte[] encodedCsr, byte[] encodedTargetCert,
                                    Instant notBefore, Instant notAfter) throws CaMgmtException;

  /**
   * Generates a self-signed CA certificate.
   * @param caEntry
   *          CA entry. Must not be {@code null}.
   * @param certprofileName
   *          Profile name of the root CA certificate. Must not be {@code null}.
   * @param subject
   *          Subject. Must not be {@code null}.
   * @param serialNumber
   *          Serial number. {@code null}: lets CA choose the serial number;
   *          fixed serialnumber: decimal or heximal (beginning with 0x) number;
   *          RANDOM:&lt;size in bytes&gt; random number with given length.
   * @param notBefore
   *          NotBefore.
   * @param notAfter
   *          NotAfter
   * @return the generated certificate
   * @throws CaMgmtException
   *          if error occurs.
   */
  X509Cert generateRootCa(CaEntry caEntry, String certprofileName, String subject, String serialNumber,
                          Instant notBefore, Instant notAfter) throws CaMgmtException;

  /**
   * Generates a new CRL for CA {@code caName}.
   * @param caName
   *          CA name. Must not be {@code null}.
   * @return the generated CRL.
   * @throws CaMgmtException
   *          if error occurs.
   */
  X509CRLHolder generateCrlOnDemand(String caName) throws CaMgmtException;

  /**
   * Returns the CRL of CA {@code caName} with the CRL number {@code  crlNumber}.
   * @param caName
   *          CA name. Must not be {@code null}.
   * @param crlNumber
   *          CRL number. Must not be {@code null}.
   * @return the CRL.
   * @throws CaMgmtException
   *          if error occurs.
   */
  X509CRLHolder getCrl(String caName, BigInteger crlNumber) throws CaMgmtException;

  /**
   * Returns the latest CRL of CA {@code caName}.
   * @param caName
   *          CA name. Must not be {@code null}.
   * @return the CRL.
   * @throws CaMgmtException
   *          if error occurs.
   */
  X509CRLHolder getCurrentCrl(String caName) throws CaMgmtException;

  /**
   * Returns CA certificate chain, starting with the CA's certificate,  for the CA {@code caName}.
   * @param caName
   *          CA name. Must not be {@code null}.
   * @return the certificate chain.
   * @throws CaMgmtException
   *          if error occurs.
   */
  List<X509Cert> getCaCerts(String caName) throws CaMgmtException;

  /**
   * Returns certificate with status information for the CA {@code caName}
   * and with serial number {@code serialNumber}.
   * @param caName
   *          CA name. Must not be {@code null}.
   * @param serialNumber
   *          Serial number. Must not be {@code null}.
   * @return the certificate with status information.
   * @throws CaMgmtException
   *          if error occurs.
   */
  CertWithRevocationInfo getCert(String caName, BigInteger serialNumber) throws CaMgmtException;

  /**
   * Returns certificate with revocation information for the {@code issuer}
   * and with serial number {@code serialNumber}.
   * @param issuer
   *          Issuer of the certificate. Must not be {@code null}.
   * @param serialNumber
   *          Serial number. Must not be {@code null}.
   * @return the certificate with status information.
   * @throws CaMgmtException
   *          if error occurs.
   */
  CertWithRevocationInfo getCert(X500Name issuer, BigInteger serialNumber) throws CaMgmtException;

  /**
   * Loads the CA system configuration.
   * The specified stream is closed after this method call.
   * @param zippedConfBytes
   *          Byte array of the zipped Configuration the CA system. Must not be {@code null}.
   * @throws IOException
   *          If read the ZIP stream fails.
   * @throws CaMgmtException
   *          if other error occurs.
   */
  void loadConf(byte[] zippedConfBytes) throws CaMgmtException, IOException;

  /**
   * Loads the CA system configuration.
   * The specified stream is closeds after this method call.
   * @param zippedConfStream
   *          Inputstream of the zipped Configuration the CA system. Must not be {@code null}.
   * @throws IOException
   *          If read the ZIP stream fails.
   * @throws CaMgmtException
   *          if other error occurs.
   */
  void loadConfAndClose(InputStream zippedConfStream) throws CaMgmtException, IOException;

  /**
   * Exports the CA system configuration to a zip-stream.
   * @param caNames
   *          List of the names of CAs to be exported. {@code null} to export all CAs.
   * @return ZIP stream of the CA system configuration.
   * @throws IOException
   *          If read the ZIP file fails.
   * @throws CaMgmtException
   *          if non-IO error occurs.
   */
  InputStream exportConf(List<String> caNames) throws CaMgmtException, IOException;

  /**
   * Returns a sorted list of certificate meta information.
   * @param caName
   *          CA name. Must not be {@code null}.
   * @param subjectPattern
   *          Subject pattern. Could be {@code null}.
   * @param validFrom
   *          Valid from. Could be {@code null}.
   * @param validTo
   *          Valid to. Could be {@code null}.
   * @param orderBy
   *          How the result is ordered. Could be {@code null}.
   * @param numEntries
   *          Maximal number of entries in the returned list.
   * @return a sorted list of certificate meta information.
   * @throws CaMgmtException
   *          if error occurs.
   */
  List<CertListInfo> listCertificates(String caName, X500Name subjectPattern, Instant validFrom,
                                      Instant validTo, CertListOrderBy orderBy, int numEntries)
      throws CaMgmtException;

  /**
   * Retrieves the types of supported signers.
   * @return lower-case types of supported signers, never {@code null}.
   * @throws CaMgmtException
   *          if error occurs.
   */
  Set<String> getSupportedSignerTypes() throws CaMgmtException;

  /**
   * Retrieves the types of supported certificate profiles.
   * @return types of supported certificate profiles, never {@code null}.
   * @throws CaMgmtException
   *          if error occurs.
   */
  Set<String> getSupportedCertprofileTypes() throws CaMgmtException;

  /**
   * Retrieves the types of supported publishers.
   * @return lower-case types of supported publishers, never {@code null}.
   * @throws CaMgmtException
   *          if error occurs.
   */
  Set<String> getSupportedPublisherTypes() throws CaMgmtException;

  String getTokenInfoP11(String moduleName, Integer slotIndex, boolean verbose) throws CaMgmtException;

}
