// Copyright (c) 2013-2024 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.ocsp.server.store.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.datasource.DataSourceWrapper;
import org.xipki.ocsp.api.CertStatusInfo;
import org.xipki.ocsp.api.OcspStore;
import org.xipki.ocsp.api.OcspStoreException;
import org.xipki.ocsp.api.RequestIssuer;
import org.xipki.security.CertRevocationInfo;
import org.xipki.security.CrlReason;
import org.xipki.security.X509Cert;
import org.xipki.security.util.X509Util;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.cert.CertificateException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

/**
 * This is just an example that demonstrates how to use the custom OcspStore.
 *
 * <p>To use this store, configure the store in the ocsp-responder.json file as follows
 * <pre>
 *      "source":{
 *           "type":"java:org.xipki.ocsp.server.store.example.DummyStore",
 *           "conf":{
 *               "caCert":"path/to/CA-certificate-file"
 *           }
 *       },
 * </pre>
 * Where the CA-certificate-file is either the CA certificate in DER or PEM format.
 *
 * <p>This dummy store returns the following certificate status
 * <ul>
 * <li>GOOD if serial-number % 3 == 0</li>
 * <li>REVOKED if serial-number % 3 == 1</li>
 * <li>UNKNOWN if serial-number % 3 == 2</li>
 * </ul>
 * @author Lijun Liao (xipki)
 * @since 5.0.2
 */
public class DummyStore extends OcspStore {

  public static class DummySourceConf {

    private String caCert;

    public String getCaCert() {
      return caCert;
    }

    public void setCaCert(String caCert) {
      this.caCert = caCert;
    }

  } // class DummySourceConf

  private static final Logger LOG = LoggerFactory.getLogger(DummyStore.class);

  private static final BigInteger BN_3 = BigInteger.valueOf(3);

  private IssuerEntry issuerEntry;

  public DummyStore() {
    LOG.error("\n--------------------------------------------------------\n"
        + " ONLY FOR DEMO, DO NOT USE IT IN PRODUCTION ENVIRONMENT\n"
        + "--------------------------------------------------------");
  }

  @Override
  public void close() throws IOException {
  }

  @Override
  public boolean knowsIssuer(RequestIssuer reqIssuer) {
    return issuerEntry.matchHash(reqIssuer);
  }

  @Override
  public X509Cert getIssuerCert(RequestIssuer reqIssuer) {
    return issuerEntry.matchHash(reqIssuer) ? issuerEntry.getCert() : null;
  }

  @Override
  protected CertStatusInfo getCertStatus0(
      Instant time, RequestIssuer reqIssuer, BigInteger serialNumber,
      boolean includeCertHash, boolean includeRit, boolean inheritCaRevocation)
      throws OcspStoreException {
    if (!knowsIssuer(reqIssuer)) {
      return null;
    }

    final int rest = serialNumber.mod(BN_3).intValue();
    Instant thisUpdate = Instant.now();
    Instant nextUpdate = thisUpdate.plus(12, ChronoUnit.HOURS); // 12 hours

    if (rest == 0) {
      return CertStatusInfo.getGoodCertStatusInfo(thisUpdate, nextUpdate);
    } else if (rest == 1) {
      CertRevocationInfo revInfo = new CertRevocationInfo(CrlReason.KEY_COMPROMISE);
      return CertStatusInfo.getRevokedCertStatusInfo(revInfo, thisUpdate, nextUpdate);
    } else {
      return CertStatusInfo.getUnknownCertStatusInfo(thisUpdate, nextUpdate);
    }
  } // method getCertStatus0

  /**
   * Initialize the store.
   *
   * @param sourceConf
   * the store source configuration. It contains following key-value pairs:
   * <ul>
   * <li>caCert: optional
   *   <p>
   *   CA cert file.</li>
   * </ul>
   * @param datasource DataSource.
   */
  @Override
  public void init(Map<String, ?> sourceConf, DataSourceWrapper datasource) throws OcspStoreException {
    Object objVal = Optional.ofNullable(sourceConf.get("caCert")).orElseThrow(
        () -> new IllegalArgumentException("mandatory caCert is not specified in sourceConf"));

    if (!(objVal instanceof String)) {
      throw new IllegalArgumentException(
          "content of caCert is not String, but " + objVal.getClass().getName());
    }

    String caCertFile = (String) objVal;
    X509Cert cert;
    IssuerEntry issuserEntry;
    try {
      cert = X509Util.parseCert(new File(caCertFile));
      issuserEntry = new IssuerEntry(cert);
    } catch (CertificateException | IOException ex) {
      throw new OcspStoreException("cannot parse the cacert " + caCertFile, ex);
    }

    this.issuerEntry = issuserEntry;
    LOG.info("use caCert {}", caCertFile);
  }

  @Override
  public boolean isHealthy() {
    return true;
  }

}
