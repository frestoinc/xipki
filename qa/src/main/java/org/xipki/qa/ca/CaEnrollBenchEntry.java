// Copyright (c) 2013-2024 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.qa.ca;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.xipki.security.ObjectIdentifiers;
import org.xipki.security.util.X509Util;
import org.xipki.util.Args;

/**
 * Entry for benchmark enrollment test.
 *
 * @author Lijun Liao (xipki)
 * @since 2.0.0
 */

public class CaEnrollBenchEntry {

  public enum RandomDn {

    GIVENNAME,
    SURNAME,
    STREET,
    POSTALCODE,
    O,
    OU,
    CN;

    public static RandomDn getInstance(String text) {
      Args.notNull(text, "text");
      for (RandomDn value : values()) {
        if (value.name().equalsIgnoreCase(text)) {
          return value;
        }
      }
      return null;
    }

  } // class RandomDN

  private static class IncreasableSubject {

    private final X500Name subjectTemplate;

    private final ASN1ObjectIdentifier subjectRdnForIncrement;

    private IncreasableSubject(String subjectTemplate, RandomDn randomDn) {
      Args.notNull(randomDn, "randomDn");

      this.subjectTemplate = new X500Name(Args.notBlank(subjectTemplate, "subjectTemplate"));

      switch (randomDn) {
        case GIVENNAME:
          this.subjectRdnForIncrement = ObjectIdentifiers.DN.givenName;
          break;
        case SURNAME:
          this.subjectRdnForIncrement = ObjectIdentifiers.DN.surname;
          break;
        case STREET:
          this.subjectRdnForIncrement = ObjectIdentifiers.DN.street;
          break;
        case POSTALCODE:
          this.subjectRdnForIncrement = ObjectIdentifiers.DN.postalCode;
          break;
        case O:
          this.subjectRdnForIncrement = ObjectIdentifiers.DN.O;
          break;
        case OU:
          this.subjectRdnForIncrement = ObjectIdentifiers.DN.OU;
          break;
        case CN:
          this.subjectRdnForIncrement = ObjectIdentifiers.DN.CN;
          break;
        default:
          throw new IllegalStateException("should not reach here, unknown randomDn " + randomDn);
      }

      if (this.subjectTemplate.getRDNs(this.subjectRdnForIncrement).length == 0) {
        throw new IllegalArgumentException("subjectTemplate does not contain DN field "
            + ObjectIdentifiers.oidToDisplayName(this.subjectRdnForIncrement));
      }
    } // constructor

    private X500Name getX500Name(long index) {
      RDN[] baseRdns = subjectTemplate.getRDNs();

      final int n = baseRdns.length;
      RDN[] newRdns = new RDN[n];

      boolean incremented = false;
      for (int i = 0; i < n; i++) {
        RDN rdn = baseRdns[i];
        if (!incremented) {
          if (rdn.getFirst().getType().equals(subjectRdnForIncrement)) {
            String text = X509Util.rdnValueToString(rdn.getFirst().getValue());
            rdn = new RDN(subjectRdnForIncrement, new DERUTF8String(text + index));
            incremented = true;
          }
        }

        newRdns[i] = rdn;
      }
      return new X500Name(newRdns);
    } // method getX500Name

  } // class IncreasableSubject

  private final String certprofile;

  private final CaEnrollBenchKeyEntry keyEntry;

  private final IncreasableSubject subject;

  public CaEnrollBenchEntry(
      String certprofile, CaEnrollBenchKeyEntry keyEntry, String subjectTemplate, RandomDn randomDn) {
    this.certprofile = Args.notBlank(certprofile, "certprofile");
    this.keyEntry = keyEntry;
    this.subject = new IncreasableSubject(subjectTemplate, randomDn);
  }

  public SubjectPublicKeyInfo getSubjectPublicKeyInfo() throws Exception {
    return keyEntry == null ? null : keyEntry.getSubjectPublicKeyInfo();
  }

  public X500Name getX500Name(long index) {
    return subject.getX500Name(index);
  }

  public String getCertprofile() {
    return certprofile;
  }

}
