// Copyright (c) 2013-2024 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.ca.server;

import org.bouncycastle.asn1.ASN1BitString;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.pkcs.RSAPrivateKey;
import org.bouncycastle.asn1.pkcs.RSAPublicKey;
import org.bouncycastle.asn1.sec.ECPrivateKey;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.ca.api.NameId;
import org.xipki.ca.api.kpgen.KeypairGenerator;
import org.xipki.ca.api.mgmt.ValidityMode;
import org.xipki.ca.api.profile.Certprofile;
import org.xipki.ca.api.profile.CertprofileException;
import org.xipki.ca.api.profile.KeypairGenControl;
import org.xipki.ca.api.profile.NotAfterMode;
import org.xipki.ca.server.X509Ca.GrantedCertTemplate;
import org.xipki.pki.BadCertTemplateException;
import org.xipki.pki.OperationException;
import org.xipki.security.ConcurrentContentSigner;
import org.xipki.security.XiSecurityException;
import org.xipki.security.util.RSABrokenKey;
import org.xipki.security.util.X509Util;
import org.xipki.util.LogUtil;
import org.xipki.util.Validity;

import java.io.IOException;
import java.math.BigInteger;
import java.security.spec.InvalidKeySpecException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.xipki.pki.ErrorCode.ALREADY_ISSUED;
import static org.xipki.pki.ErrorCode.BAD_CERT_TEMPLATE;
import static org.xipki.pki.ErrorCode.NOT_PERMITTED;
import static org.xipki.pki.ErrorCode.SYSTEM_FAILURE;
import static org.xipki.pki.ErrorCode.UNKNOWN_CERT_PROFILE;

/**
 * X509CA GrandCertTemplate builder.
 *
 * @author Lijun Liao (xipki)
 */

class GrandCertTemplateBuilder {

  private static final Logger LOG = LoggerFactory.getLogger(GrandCertTemplateBuilder.class);

  private static final Instant MAX_CERT_TIME = ZonedDateTime.of(9999, 12, 31,
      23, 59, 59, 0, ZoneOffset.UTC).toInstant(); //9999-12-31T23:59:59.000

  private final ASN1ObjectIdentifier keyAlgOidByImplicitCA;
  private final String keyspecByImplicitCA;

  private final CaInfo caInfo;

  GrandCertTemplateBuilder(CaInfo caInfo) {
    this.caInfo = caInfo;

    this.keyspecByImplicitCA = caInfo.getCaKeyspec();
    this.keyAlgOidByImplicitCA = caInfo.getCaKeyAlgId().getAlgorithm();
  }

  GrantedCertTemplate create(
      boolean batch, IdentifiedCertprofile certprofile, CertTemplateData certTemplate,
      List<KeypairGenerator> keypairGenerators)
      throws OperationException {
    if (caInfo.getRevocationInfo() != null) {
      throw new OperationException(NOT_PERMITTED, "CA is revoked");
    }

    if (certprofile == null) {
      throw new OperationException(UNKNOWN_CERT_PROFILE, "unknown cert profile " + certTemplate.getCertprofileName());
    }

    ConcurrentContentSigner signer = Optional.ofNullable(caInfo.getSigner(certprofile.getSignatureAlgorithms()))
        .orElseThrow(() -> new OperationException(SYSTEM_FAILURE,
                              "CA does not support any signature algorithm restricted by the cert profile"));

    final NameId certprofileIdent = certprofile.getIdent();
    if (certprofile.getVersion() != Certprofile.X509CertVersion.v3) {
      throw new OperationException(SYSTEM_FAILURE, "unknown cert version " + certprofile.getVersion());
    }

    switch (certprofile.getCertLevel()) {
      case RootCA:
        throw new OperationException(NOT_PERMITTED, "CA is not allowed to generate Root CA certificate");
      case SubCA:
      case CROSS:
        Integer reqPathlen = certprofile.getPathLenBasicConstraint();
        int caPathLen = caInfo.getPathLenConstraint();
        boolean allowed = (reqPathlen == null && caPathLen == Integer.MAX_VALUE)
                            || (reqPathlen != null && reqPathlen < caPathLen);
        if (!allowed) {
          throw new OperationException(NOT_PERMITTED, "invalid BasicConstraint.pathLenConstraint");
        }
        break;
      default:
    }

    boolean forCrossCert = certTemplate.isForCrossCert();
    X500Name requestedSubject = forCrossCert ? certTemplate.getSubject()
        : CaUtil.removeEmptyRdns(certTemplate.getSubject());

    Instant reqNotBefore = certTemplate.getNotBefore();

    Instant grantedNotBefore = certprofile.getNotBefore(reqNotBefore);
    // notBefore in the past is not permitted (due to the fact that some clients may not have
    // accurate time, we allow max. 5 minutes in the past)
    Instant _10MinBefore = Instant.now().minus(10, ChronoUnit.MINUTES);
    if (grantedNotBefore.isBefore(_10MinBefore)) {
      grantedNotBefore = _10MinBefore;
    }

    if (grantedNotBefore.isAfter(caInfo.getNoNewCertificateAfter())) {
      throw new OperationException(NOT_PERMITTED,
          "CA is not permitted to issue certificate after " + caInfo.getNoNewCertificateAfter());
    }

    if (grantedNotBefore.isBefore(caInfo.getNotBefore())) {
      // notBefore may not be before CA's notBefore
      grantedNotBefore = caInfo.getNotBefore();
    }

    PrivateKeyInfo privateKey = null;
    SubjectPublicKeyInfo grantedPublicKeyInfo = certTemplate.getPublicKeyInfo();

    if (grantedPublicKeyInfo != null) {
      try {
        grantedPublicKeyInfo = X509Util.toRfc3279Style(certTemplate.getPublicKeyInfo());
      } catch (InvalidKeySpecException ex) {
        LogUtil.warn(LOG, ex, "invalid SubjectPublicKeyInfo");
        throw new OperationException(BAD_CERT_TEMPLATE, "invalid SubjectPublicKeyInfo");
      }

      // CHECK weak public key, like RSA key (ROCA)
      if (grantedPublicKeyInfo.getAlgorithm().getAlgorithm().equals(
          PKCSObjectIdentifiers.rsaEncryption)) {
        try {
          ASN1Sequence seq = ASN1Sequence.getInstance(grantedPublicKeyInfo.getPublicKeyData().getOctets());
          if (seq.size() != 2) {
            throw new OperationException(BAD_CERT_TEMPLATE, "invalid format of RSA public key");
          }

          BigInteger modulus = ASN1Integer.getInstance(seq.getObjectAt(0)).getPositiveValue();
          if (RSABrokenKey.isAffected(modulus)) {
            throw new OperationException(BAD_CERT_TEMPLATE, "RSA public key is too weak");
          }
        } catch (IllegalArgumentException ex) {
          throw new OperationException(BAD_CERT_TEMPLATE, "invalid format of RSA public key");
        }
      }
    } else if (certTemplate.isServerkeygen()) {
      KeypairGenControl kg = certprofile.getKeypairGenControl();

      ASN1ObjectIdentifier keyAlgOid;
      String keyspec;

      if (kg == null || kg instanceof KeypairGenControl.ForbiddenKeypairGenControl) {
        throw new OperationException(BAD_CERT_TEMPLATE, "no public key is specified");
      }

      if (kg instanceof KeypairGenControl.InheritCAKeypairGenControl) {
        keyspec = keyspecByImplicitCA;
        keyAlgOid = keyAlgOidByImplicitCA;
      } else {
        keyspec = kg.getKeyspec();
        keyAlgOid = kg.getKeyAlgorithmOid();
      }

      KeypairGenerator keypairGenerator = null;
      if (keypairGenerators != null) {
        for (KeypairGenerator m : keypairGenerators) {
          if (m.supports(keyspec)) {
            keypairGenerator = m;
            break;
          }
        }
      }

      if (keypairGenerator == null) {
        throw new OperationException(SYSTEM_FAILURE, "found no keypair generator for keyspec " + keyspec);
      }

      String name = keypairGenerator.getName();
      try {
        privateKey = keypairGenerator.generateKeypair(keyspec);
        LOG.info("generated keypair {} with generator {}", keyspec, name);
      } catch (XiSecurityException ex) {
        String msg = "error generating keypair " + keyspec + " using generator " + name;
        LogUtil.error(LOG, ex, msg);
        throw new OperationException(SYSTEM_FAILURE, msg);
      }

      // adapt the algorithm identifier in private key and public key
      if (!privateKey.getPrivateKeyAlgorithm().getAlgorithm().equals(keyAlgOid)) {
        ASN1BitString asn1PublicKeyData = privateKey.getPublicKeyData();
        try {
          privateKey = new PrivateKeyInfo(
              new AlgorithmIdentifier(keyAlgOid, privateKey.getPrivateKeyAlgorithm().getParameters()),
              privateKey.getPrivateKey().toASN1Primitive(),
              privateKey.getAttributes(),
              asn1PublicKeyData == null ? null : asn1PublicKeyData.getOctets());
        } catch (IOException ex) {
          throw new OperationException(SYSTEM_FAILURE, ex);
        }
      }

      // construct SubjectPublicKeyInfo
      String keyType = keyspec.split("/")[0].toUpperCase(Locale.ROOT);
      byte[] publicKeyData;
      switch (keyType) {
        case "RSA": {
          RSAPrivateKey sk = RSAPrivateKey.getInstance(privateKey.getPrivateKey().getOctets());
          try {
            publicKeyData = new RSAPublicKey(sk.getModulus(), sk.getPublicExponent()).getEncoded();
          } catch (IOException ex) {
            throw new OperationException(SYSTEM_FAILURE, ex);
          }
          break;
        }
        case "EC": {
          ECPrivateKey sk = ECPrivateKey.getInstance(privateKey.getPrivateKey().getOctets());
          publicKeyData = sk.getPublicKey().getBytes();
          break;
        }
        case "DSA":
        case "ED25519":
        case "ED448":
        case "X25519":
        case "X448": {
          publicKeyData = privateKey.getPublicKeyData().getBytes();
          break;
        }
        default:
          throw new IllegalStateException("unknown key type " + keyType);
      }

      grantedPublicKeyInfo = new SubjectPublicKeyInfo(privateKey.getPrivateKeyAlgorithm(), publicKeyData);
      try {
        grantedPublicKeyInfo = X509Util.toRfc3279Style(grantedPublicKeyInfo);
      } catch (InvalidKeySpecException ex) {
        throw new OperationException(SYSTEM_FAILURE, ex);
      }
    } else {
      // show not reach here
      throw new OperationException(BAD_CERT_TEMPLATE, "no public key is specified");
    }

    // public key
    try {
      grantedPublicKeyInfo = certprofile.checkPublicKey(grantedPublicKeyInfo);
    } catch (CertprofileException ex) {
      throw new OperationException(SYSTEM_FAILURE, "exception in cert profile " + certprofileIdent);
    } catch (BadCertTemplateException ex) {
      throw new OperationException(BAD_CERT_TEMPLATE, ex);
    }

    StringBuilder msgBuilder = new StringBuilder();

    Certprofile.SubjectInfo subjectInfo;
    try {
      subjectInfo = certprofile.getSubject(requestedSubject, grantedPublicKeyInfo);
    } catch (CertprofileException ex) {
      throw new OperationException(SYSTEM_FAILURE, "exception in cert profile " + certprofileIdent);
    } catch (BadCertTemplateException ex) {
      throw new OperationException(BAD_CERT_TEMPLATE, ex);
    }

    // subject
    X500Name grantedSubject;
    if (forCrossCert) {
      // For cross certificate, the original requested certificate must be used.
      grantedSubject = requestedSubject;
    } else {
      grantedSubject = subjectInfo.getGrantedSubject();

      if (subjectInfo.getWarning() != null) {
        msgBuilder.append(", ").append(subjectInfo.getWarning());
      }
    }

    // make sure that the grantedSubject does not equal the CA's subject
    if (X509Util.canonicalizeName(grantedSubject).equals(
        caInfo.getPublicCaInfo().getC14nSubject())) {
      throw new OperationException(ALREADY_ISSUED, "certificate with the same subject as CA is not allowed");
    }

    Instant grantedNotAfter;

    if (certprofile.hasNoWellDefinedExpirationDate()) {
      grantedNotAfter = MAX_CERT_TIME;
    } else {
      Validity validity = certprofile.getValidity();

      if (validity == null) {
        validity = caInfo.getMaxValidity();
      } else if (validity.compareTo(caInfo.getMaxValidity()) > 0) {
        validity = caInfo.getMaxValidity();
      }

      Instant maxNotAfter = validity.add(grantedNotBefore);
      // maxNotAfter not after 99991231-235959.000
      if (maxNotAfter.isAfter(MAX_CERT_TIME)) {
        maxNotAfter = MAX_CERT_TIME;
      }

      grantedNotAfter = certTemplate.getNotAfter();

      if (grantedNotAfter != null) {
        if (grantedNotAfter.isAfter(maxNotAfter)) {
          grantedNotAfter = maxNotAfter;
          msgBuilder.append(", notAfter modified");
        }
      } else {
        grantedNotAfter = maxNotAfter;
      }

      if (grantedNotAfter.isAfter(caInfo.getNotAfter())) {
        ValidityMode caMode = caInfo.getValidityMode();
        NotAfterMode profileMode = certprofile.getNotAfterMode();
        if (profileMode == null) {
          profileMode = NotAfterMode.BY_CA;
        }

        if (profileMode == NotAfterMode.STRICT) {
          throw new OperationException(NOT_PERMITTED,
              "notAfter outside of CA's validity is not permitted by the CertProfile");
        }

        if (caMode == ValidityMode.strict) {
          throw new OperationException(NOT_PERMITTED,
              "notAfter outside of CA's validity is not permitted by the CA");
        }

        if (caMode == ValidityMode.cutoff) {
          grantedNotAfter = caInfo.getNotAfter();
        } else if (caMode == ValidityMode.lax) {
          if (profileMode == NotAfterMode.CUTOFF) {
            grantedNotAfter = caInfo.getNotAfter();
          }
        } else {
          throw new IllegalStateException("should not reach here, CA ValidityMode " + caMode
              + " CertProfile NotAfterMode " + profileMode);
        } // end if (caMode)
      } // end if (grantedNotAfter)
    }

    String warning = null;
    if (msgBuilder.length() > 2) {
      warning = msgBuilder.substring(2);
    }
    GrantedCertTemplate gct = new GrantedCertTemplate(batch,
        certTemplate.getCertReqId(), certTemplate.getExtensions(), certprofile, grantedNotBefore, grantedNotAfter,
        requestedSubject, grantedPublicKeyInfo, privateKey, signer, warning);
    gct.setGrantedSubject(grantedSubject);
    return gct;

  } // method createGrantedCertTemplate

}
