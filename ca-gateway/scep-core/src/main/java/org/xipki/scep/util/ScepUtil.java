// Copyright (c) 2013-2024 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.scep.util;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Set;
import org.bouncycastle.asn1.BERTags;
import org.bouncycastle.asn1.DERGeneralizedTime;
import org.bouncycastle.asn1.DERUTCTime;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.SignedData;
import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.asn1.x509.CertificateList;
import org.bouncycastle.asn1.x509.Time;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.xipki.security.HashAlgo;
import org.xipki.security.SignAlgo;
import org.xipki.security.X509Cert;
import org.xipki.util.Args;

import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CRLException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.text.ParseException;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * SCEP utility class.
 *
 * @author Lijun Liao (xipki)
 */

public class ScepUtil {

  private ScepUtil() {
  }

  /*
   * The first one is a non-CA certificate if there exists one non-CA certificate.
   */
  public static List<X509Cert> getCertsFromSignedData(SignedData signedData) throws CertificateException {
    Args.notNull(signedData, "signedData");
    ASN1Set set = signedData.getCertificates();
    if (set == null) {
      return Collections.emptyList();
    }

    final int n = set.size();
    if (n == 0) {
      return Collections.emptyList();
    }

    List<X509Cert> certs = new LinkedList<>();

    X509Cert eeCert = null;
    for (int i = 0; i < n; i++) {
      X509Cert cert;
      try {
        cert = new X509Cert(Certificate.getInstance(set.getObjectAt(i)));
      } catch (IllegalArgumentException ex) {
        throw new CertificateException(ex);
      }

      if (eeCert == null && cert.getBasicConstraints() == -1) {
        eeCert = cert;
      } else {
        certs.add(cert);
      }
    }

    if (eeCert != null) {
      certs.add(0, eeCert);
    }

    return certs;
  } // method getCertsFromSignedData

  public static X509CRLHolder getCrlFromPkiMessage(SignedData signedData) throws CRLException {
    ASN1Set set = Args.notNull(signedData, "signedData").getCRLs();
    if (set == null || set.size() == 0) {
      return null;
    }

    try {
      return new X509CRLHolder(CertificateList.getInstance(set.getObjectAt(0)));
    } catch (IllegalArgumentException ex) {
      throw new CRLException(ex);
    }
  } // method getCrlFromPkiMessage

  public static String getSignatureAlgName(Key key, HashAlgo hashAlgo) throws NoSuchAlgorithmException {
    return SignAlgo.getInstance(key, hashAlgo, null).getJceName();
  }

  public static ASN1Encodable getFirstAttrValue(AttributeTable attrs, ASN1ObjectIdentifier type) {
    Args.notNull(attrs, "attrs");
    Args.notNull(type, "type");
    Attribute attr = attrs.get(type);
    if (attr == null) {
      return null;
    }
    ASN1Set set = attr.getAttrValues();
    return (set.size() == 0) ? null : set.getObjectAt(0);
  }

  public static void addCmsCertSet(CMSSignedDataGenerator generator, X509Cert[] cmsCertSet)
      throws CertificateEncodingException, CMSException {
    if (cmsCertSet == null || cmsCertSet.length == 0) {
      return;
    }
    Args.notNull(generator, "generator");
    Collection<X509CertificateHolder> certColl = new LinkedList<>();
    for (X509Cert m : cmsCertSet) {
      certColl.add(m.toBcCert());
    }

    generator.addCertificates(new JcaCertStore(certColl));
  } // method addCmsCertSet

  public static Instant getTime(Object obj) {
    if (obj instanceof byte[]) {
      byte[] encoded = (byte[]) obj;
      int tag = encoded[0] & 0xFF;
      try {
        if (tag == BERTags.UTC_TIME) {
          return DERUTCTime.getInstance(encoded).getDate().toInstant();
        } else if (tag == BERTags.GENERALIZED_TIME) {
          return DERGeneralizedTime.getInstance(encoded).getDate().toInstant();
        } else {
          throw new IllegalArgumentException("invalid tag " + tag);
        }
      } catch (ParseException ex) {
        throw new IllegalArgumentException("error parsing time", ex);
      }
    } else if (obj instanceof Time) {
      return ((Time) obj).getDate().toInstant();
    } else if (obj instanceof org.bouncycastle.asn1.cms.Time) {
      return ((org.bouncycastle.asn1.cms.Time) obj).getDate().toInstant();
    } else {
      return Time.getInstance(obj).getDate().toInstant();
    }
  } // method getTime

}
