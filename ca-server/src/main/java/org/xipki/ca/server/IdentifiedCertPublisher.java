/*
 *
 * Copyright (c) 2013 - 2019 Lijun Liao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.xipki.ca.server;

import java.io.Closeable;
import java.security.cert.X509CRL;
import java.util.Map;

import org.xipki.ca.api.CertWithDbId;
import org.xipki.ca.api.CertificateInfo;
import org.xipki.ca.api.NameId;
import org.xipki.ca.api.mgmt.MgmtEntry;
import org.xipki.ca.api.publisher.CertPublisher;
import org.xipki.ca.api.publisher.CertPublisherException;
import org.xipki.password.PasswordResolver;
import org.xipki.security.CertRevocationInfo;
import org.xipki.security.X509Cert;
import org.xipki.util.Args;
import org.xipki.util.FileOrValue;

/**
 * TODO.
 * @author Lijun Liao
 * @since 2.0.0
 */

class IdentifiedCertPublisher implements Closeable {

  private final MgmtEntry.Publisher entry;

  private final CertPublisher certPublisher;

  IdentifiedCertPublisher(MgmtEntry.Publisher entry, CertPublisher certPublisher) {
    this.entry = Args.notNull(entry, "entry");
    this.certPublisher = Args.notNull(certPublisher, "certPublisher");
  }

  public void initialize(PasswordResolver passwordResolver,
      Map<String, FileOrValue> datasourceConfs)
      throws CertPublisherException {
    certPublisher.initialize(entry.getConf(), passwordResolver, datasourceConfs);
  }

  public boolean caAdded(X509Cert caCert) {
    return certPublisher.caAdded(caCert);
  }

  public boolean certificateAdded(CertificateInfo certInfo) {
    return certPublisher.certificateAdded(certInfo);
  }

  public boolean certificateRevoked(X509Cert caCert, CertWithDbId cert, String certprofile,
      CertRevocationInfo revInfo) {
    return certPublisher.certificateRevoked(caCert, cert, certprofile, revInfo);
  }

  public boolean crlAdded(X509Cert caCert, X509CRL crl) {
    return certPublisher.crlAdded(caCert, crl);
  }

  public MgmtEntry.Publisher getDbEntry() {
    return entry;
  }

  public NameId getIdent() {
    return entry.getIdent();
  }

  public boolean isHealthy() {
    return certPublisher.isHealthy();
  }

  public boolean caRevoked(X509Cert caCert, CertRevocationInfo revocationInfo) {
    return certPublisher.caRevoked(caCert, revocationInfo);
  }

  public boolean caUnrevoked(X509Cert caCert) {
    return certPublisher.caUnrevoked(caCert);
  }

  public boolean certificateUnrevoked(X509Cert caCert, CertWithDbId cert) {
    return certPublisher.certificateUnrevoked(caCert, cert);
  }

  public boolean certificateRemoved(X509Cert caCert, CertWithDbId cert) {
    return certPublisher.certificateRemoved(caCert, cert);
  }

  public boolean isAsyn() {
    return certPublisher.isAsyn();
  }

  @Override
  public void close() {
    certPublisher.close();
  }

  public boolean publishsGoodCert() {
    return certPublisher.publishsGoodCert();
  }

}
