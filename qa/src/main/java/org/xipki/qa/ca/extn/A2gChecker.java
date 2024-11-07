// Copyright (c) 2013-2024 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.qa.ca.extn;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1IA5String;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1PrintableString;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1String;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.ASN1UTF8String;
import org.bouncycastle.asn1.DERPrintableString;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.CRLDistPoint;
import org.bouncycastle.asn1.x509.DistributionPoint;
import org.bouncycastle.asn1.x509.DistributionPointName;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.PolicyInformation;
import org.bouncycastle.asn1.x509.PolicyQualifierId;
import org.bouncycastle.asn1.x509.PolicyQualifierInfo;
import org.bouncycastle.asn1.x509.UserNotice;
import org.bouncycastle.asn1.x509.X509ObjectIdentifiers;
import org.bouncycastle.asn1.x509.qualified.BiometricData;
import org.bouncycastle.asn1.x509.qualified.TypeOfBiometricData;
import org.xipki.ca.api.profile.Certprofile.AuthorityInfoAccessControl;
import org.xipki.ca.api.profile.Certprofile.CertLevel;
import org.xipki.ca.api.profile.Certprofile.ExtKeyUsageControl;
import org.xipki.ca.api.profile.Certprofile.ExtensionControl;
import org.xipki.ca.certprofile.xijson.AdmissionExtension;
import org.xipki.ca.certprofile.xijson.BiometricInfoOption;
import org.xipki.ca.certprofile.xijson.XijsonCertprofile;
import org.xipki.ca.certprofile.xijson.conf.extn.AdditionalInformation;
import org.xipki.ca.certprofile.xijson.conf.extn.CertificatePolicies;
import org.xipki.pki.BadCertTemplateException;
import org.xipki.qa.ca.IssuerInfo;
import org.xipki.security.HashAlgo;
import org.xipki.security.ObjectIdentifiers.Extn;
import org.xipki.security.util.X509Util;
import org.xipki.util.CollectionUtil;
import org.xipki.util.CompareUtil;
import org.xipki.util.ConfPairs;
import org.xipki.util.Hex;
import org.xipki.util.LogUtil;
import org.xipki.util.StringUtil;
import org.xipki.util.TripleState;

import java.io.IOException;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.xipki.qa.ca.extn.CheckerUtil.addViolation;
import static org.xipki.qa.ca.extn.CheckerUtil.checkAia;
import static org.xipki.qa.ca.extn.CheckerUtil.getExtKeyUsage;
import static org.xipki.qa.ca.extn.CheckerUtil.hex;
import static org.xipki.qa.ca.extn.CheckerUtil.strInBnotInA;

/**
 * Checker for extensions whose name is from A to G.
 * @author Lijun Liao (xipki)
 */

class A2gChecker extends ExtensionChecker {

  A2gChecker(ExtensionsChecker parent) {
    super(parent);
  }

  void checkExtnAdditionalInformation(
      StringBuilder failureMsg, byte[] extnValue, Extensions requestedExtns, ExtensionControl extControl) {
    AdditionalInformation additionalInformation = caller.getAdditionalInformation();
    caller.checkDirectoryString(Extn.id_extension_additionalInformation,
        additionalInformation.getType(), additionalInformation.getText(),
        failureMsg, extnValue, requestedExtns, extControl);
  }

  void checkExtnAdmission(
      StringBuilder failureMsg, byte[] extnValue, Extensions requestedExtns, X500Name requestedSubject,
      ExtensionControl extnControl) {
    AdmissionExtension.AdmissionSyntaxOption conf = getCertprofile().extensions().getAdmission();

    ASN1ObjectIdentifier type = Extn.id_extension_admission;
    if (conf == null) {
      caller.checkConstantExtnValue(type, failureMsg, extnValue, requestedExtns, extnControl);
      return;
    }

    List<List<String>> reqRegNumsList = null;
    if (requestedSubject != null && conf.isInputFromRequestRequired()) {

      RDN[] admissionRdns = requestedSubject.getRDNs(type);
      if (admissionRdns == null || admissionRdns.length == 0) {
        failureMsg.append("no subject RDN Admission is contained in the request;");
        return;
      }

      reqRegNumsList = new LinkedList<>();
      for (RDN m : admissionRdns) {
        String str = X509Util.rdnValueToString(m.getFirst().getValue());
        ConfPairs pairs = new ConfPairs(str);
        for (String name : pairs.names()) {
          if ("registrationNumber".equalsIgnoreCase(name)) {
            reqRegNumsList.add(StringUtil.split(pairs.value(name), " ,;:"));
          }
        }
      }
    }

    try {
      byte[] expected = conf.getExtensionValue(reqRegNumsList).getValue().toASN1Primitive().getEncoded();
      if (!Arrays.equals(expected, extnValue)) {
        addViolation(failureMsg, "extension valus", hex(extnValue), hex(expected));
      }
    } catch (IOException ex) {
      LogUtil.error(log, ex);
      failureMsg.append("IOException while computing the expected extension value;");
    } catch (BadCertTemplateException ex) {
      LogUtil.error(log, ex);
      failureMsg.append("BadCertTemplateException while computing the expected extension value;");
    }
  } // method checkExtnAdmission

  void checkExtnAuthorityInfoAccess(StringBuilder failureMsg, byte[] extnValue, IssuerInfo issuerInfo) {
    AuthorityInfoAccessControl aiaControl = getCertprofile().getAiaControl();
    Set<String> expCaIssuerUris = (aiaControl == null || aiaControl.isIncludesCaIssuers())
        ? issuerInfo.getCaIssuerUrls() : Collections.emptySet();

    Set<String> expOcspUris = (aiaControl == null || aiaControl.isIncludesOcsp())
        ? issuerInfo.getOcspUrls() : Collections.emptySet();

    if (CollectionUtil.isEmpty(expCaIssuerUris) && CollectionUtil.isEmpty(expOcspUris)) {
      failureMsg.append("AIA is present but expected is 'none'; ");
      return;
    }

    AuthorityInformationAccess isAia = AuthorityInformationAccess.getInstance(extnValue);
    checkAia(failureMsg, isAia, X509ObjectIdentifiers.id_ad_caIssuers, expCaIssuerUris);
    checkAia(failureMsg, isAia, X509ObjectIdentifiers.id_ad_ocsp, expOcspUris);
  } // method checkExtnAuthorityInfoAccess

  void checkExtnAuthorityKeyId(StringBuilder failureMsg, byte[] extnValue, IssuerInfo issuerInfo) {
    AuthorityKeyIdentifier asn1 = AuthorityKeyIdentifier.getInstance(extnValue);
    byte[] keyIdentifier = asn1.getKeyIdentifier();
    BigInteger authorityCertSerialNumber = asn1.getAuthorityCertSerialNumber();
    GeneralNames authorityCertIssuer = asn1.getAuthorityCertIssuer();

    if (keyIdentifier == null) {
      failureMsg.append("keyIdentifier is 'absent', but expected 'present'; ");
    } else {
      if (!Arrays.equals(issuerInfo.getSubjectKeyIdentifier(), keyIdentifier)) {
        addViolation(failureMsg, "keyIdentifier", hex(keyIdentifier), hex(issuerInfo.getSubjectKeyIdentifier()));
      }
    }

    if (getCertprofile().useIssuerAndSerialInAki()) {
      if (authorityCertIssuer == null) {
        failureMsg.append("authorityCertIssuer is 'absent', but expected 'present'; ");
      } else {
        GeneralName[] genNames = authorityCertIssuer.getNames();
        X500Name x500GenName = null;
        for (GeneralName genName : genNames) {
          if (genName.getTagNo() != GeneralName.directoryName) {
            continue;
          }

          if (x500GenName != null) {
            failureMsg.append("authorityCertIssuer contains at least two directoryName but expected one; ");
            break;
          } else {
            x500GenName = (X500Name) genName.getName();
          }
        }

        if (x500GenName == null) {
          failureMsg.append("authorityCertIssuer does not contain directoryName but expected one; ");
        } else {
          X500Name caIssuer = issuerInfo.getCert().getIssuer();
          if (!caIssuer.equals(x500GenName)) {
            addViolation(failureMsg, "authorityCertIssuer", x500GenName, caIssuer);
          }
        }
      }

      if (authorityCertSerialNumber == null) {
        failureMsg.append("authorityCertSerialNumber is 'absent', but expected 'present'; ");
      } else {
        BigInteger issuerSn = issuerInfo.getCert().getSerialNumber();
        if (!issuerSn.equals(authorityCertSerialNumber)) {
          addViolation(failureMsg, "authorityCertSerialNumber", authorityCertSerialNumber, issuerSn);
        }
      }
    }
  } // method checkExtnAuthorityKeyId

  void checkExtnBasicConstraints(StringBuilder failureMsg, byte[] extnValue) {
    XijsonCertprofile certprofile = getCertprofile();
    BasicConstraints bc = BasicConstraints.getInstance(extnValue);
    CertLevel certLevel = certprofile.getCertLevel();
    boolean ca = CertLevel.EndEntity != certLevel;
    if (ca != bc.isCA()) {
      addViolation(failureMsg, "ca", bc.isCA(), ca);
    }

    if (!bc.isCA()) {
      return;
    }

    BigInteger tmpPathLen = bc.getPathLenConstraint();
    Integer pathLen = certprofile.extensions().getPathLen();
    if (pathLen == null) {
      if (tmpPathLen != null) {
        addViolation(failureMsg, "pathLen", tmpPathLen, "absent");
      }
    } else {
      if (tmpPathLen == null) {
        addViolation(failureMsg, "pathLen", "null", pathLen);
      } else if (!BigInteger.valueOf(pathLen).equals(tmpPathLen)) {
        addViolation(failureMsg, "pathLen", tmpPathLen, pathLen);
      }
    }
  } // method checkExtnBasicConstraints

  void checkExtnBiometricInfo(StringBuilder failureMsg, byte[] extnValue, Extensions requestedExtns) {
    BiometricInfoOption conf = getCertprofile().extensions().getBiometricInfo();

    if (conf == null) {
      failureMsg.append("extension is present but not expected; ");
      return;
    }

    ASN1Encodable extInRequest = null;
    if (requestedExtns != null) {
      extInRequest = requestedExtns.getExtensionParsedValue(Extension.biometricInfo);
    }

    if (extInRequest == null) {
      failureMsg.append("extension is present but not expected; ");
      return;
    }

    ASN1Sequence extValueInReq = ASN1Sequence.getInstance(extInRequest);
    final int expSize = extValueInReq.size();

    ASN1Sequence extValue = ASN1Sequence.getInstance(extnValue);
    final int isSize = extValue.size();
    if (isSize != expSize) {
      addViolation(failureMsg, "number of biometricData", isSize, expSize);
      return;
    }

    for (int i = 0; i < expSize; i++) {
      BiometricData isData = BiometricData.getInstance(extValue.getObjectAt(i));
      BiometricData expData = BiometricData.getInstance(extValueInReq.getObjectAt(i));

      TypeOfBiometricData isType = isData.getTypeOfBiometricData();
      TypeOfBiometricData expType = expData.getTypeOfBiometricData();
      if (!isType.equals(expType)) {
        String isStr = isType.isPredefined()
            ? Integer.toString(isType.getPredefinedBiometricType())
            : isType.getBiometricDataOid().getId();
        String expStr = expType.isPredefined()
            ? Integer.toString(expType.getPredefinedBiometricType())
            : expType.getBiometricDataOid().getId();

        addViolation(failureMsg, "biometricData[" + i + "].typeOfBiometricData", isStr, expStr);
      }

      HashAlgo hashAlgo;
      try {
        hashAlgo = HashAlgo.getInstance(expData.getHashAlgorithm());
      } catch (NoSuchAlgorithmException e) {
        hashAlgo = null;
        failureMsg.append("biometricData[").append(i)
            .append("].biometricDataHash of the request has incorrect syntax; ");
      }

      if (hashAlgo != null) {
        if(!hashAlgo.getAlgorithmIdentifier().equals(isData.getHashAlgorithm())) {
          try {
            addViolation(failureMsg, "biometricData[" + i + "].hashAlgorithm",
                    Hex.encode(isData.getHashAlgorithm().getEncoded()),
                    Hex.encode(hashAlgo.getAlgorithmIdentifier().getEncoded()));
          } catch (Exception ex) {
            failureMsg.append("biometricData[").append(i).append("].biometricDataHash: could not encode; ");
          }
        }
      }

      byte[] isBytes = isData.getBiometricDataHash().getOctets();
      byte[] expBytes = expData.getBiometricDataHash().getOctets();
      if (!Arrays.equals(isBytes, expBytes)) {
        addViolation(failureMsg, "biometricData[" + i + "].biometricDataHash", hex(isBytes), hex(expBytes));
      }

      ASN1IA5String str = isData.getSourceDataUriIA5();
      String isSourceDataUri = (str == null) ? null : str.getString();

      String expSourceDataUri = null;
      if (conf.getSourceDataUriOccurrence() != TripleState.forbidden) {
        str = expData.getSourceDataUriIA5();
        expSourceDataUri = (str == null) ? null : str.getString();
      }

      if (expSourceDataUri == null) {
        if (isSourceDataUri != null) {
          addViolation(failureMsg, "biometricData[" + i + "].sourceDataUri", "present", "absent");
        }
      } else {
        if (isSourceDataUri == null) {
          failureMsg.append("biometricData[").append(i).append("].sourceDataUri is 'absent'");
          failureMsg.append(" but expected 'present'; ");
        } else if (!isSourceDataUri.equals(expSourceDataUri)) {
          addViolation(failureMsg, "biometricData[" + i + "].sourceDataUri", isSourceDataUri, expSourceDataUri);
        }
      }
    }
  } // method checkExtnBiometricInfo

  void checkExtnCertificatePolicies(
      StringBuilder failureMsg, byte[] extnValue, Extensions requestedExtns, ExtensionControl extnControl) {
    CertificatePolicies certificatePolicies = caller.getCertificatePolicies();
    if (certificatePolicies == null) {
      caller.checkConstantExtnValue(Extension.certificatePolicies, failureMsg, extnValue, requestedExtns, extnControl);
      return;
    }

    Map<String, CertificatePolicies.CertificatePolicyInformationType> expPoliciesMap = new HashMap<>();
    for (CertificatePolicies.CertificatePolicyInformationType cp
        : caller.getCertificatePolicies().getCertificatePolicyInformations()) {
      expPoliciesMap.put(cp.getPolicyIdentifier().getOid(), cp);
    }
    Set<String> expPolicyIds = new HashSet<>(expPoliciesMap.keySet());

    org.bouncycastle.asn1.x509.CertificatePolicies asn1 =
        org.bouncycastle.asn1.x509.CertificatePolicies.getInstance(extnValue);
    PolicyInformation[] isPolicyInformations = asn1.getPolicyInformation();

    for (PolicyInformation isPolicyInformation : isPolicyInformations) {
      ASN1ObjectIdentifier isPolicyId = isPolicyInformation.getPolicyIdentifier();
      expPolicyIds.remove(isPolicyId.getId());
      CertificatePolicies.CertificatePolicyInformationType expCp = expPoliciesMap.get(isPolicyId.getId());
      if (expCp == null) {
        failureMsg.append("certificate policy '").append(isPolicyId).append("' is not expected; ");
        continue;
      }

      List<CertificatePolicies.PolicyQualifier> expCpPq = expCp.getPolicyQualifiers();
      if (CollectionUtil.isEmpty(expCpPq)) {
        continue;
      }

      ASN1Sequence isPolicyQualifiers = isPolicyInformation.getPolicyQualifiers();
      List<String> isCpsUris = new LinkedList<>();
      List<String> isUserNotices = new LinkedList<>();

      int size = isPolicyQualifiers.size();
      for (int i = 0; i < size; i++) {
        PolicyQualifierInfo isPolicyQualifierInfo = PolicyQualifierInfo.getInstance(isPolicyQualifiers.getObjectAt(i));
        ASN1ObjectIdentifier isPolicyQualifierId = isPolicyQualifierInfo.getPolicyQualifierId();
        ASN1Encodable isQualifier = isPolicyQualifierInfo.getQualifier();
        if (PolicyQualifierId.id_qt_cps.equals(isPolicyQualifierId)) {
          String isCpsUri = ASN1IA5String.getInstance(isQualifier).getString();
          isCpsUris.add(isCpsUri);
        } else if (PolicyQualifierId.id_qt_unotice.equals(isPolicyQualifierId)) {
          UserNotice isUserNotice = UserNotice.getInstance(isQualifier);
          if (isUserNotice.getExplicitText() != null) {
            isUserNotices.add(isUserNotice.getExplicitText().getString());
          }
        }
      }

      for (CertificatePolicies.PolicyQualifier qualifierInfo : expCpPq) {
        String value = qualifierInfo.getValue();
        switch (qualifierInfo.getType()) {
          case cpsUri:
            if (!isCpsUris.contains(value)) {
              failureMsg.append("CPSUri '").append(value).append("' is absent but is required; ");
            }
            continue;
          case userNotice:
            if (!isUserNotices.contains(value)) {
              failureMsg.append("userNotice '").append(value).append("' is absent but is required; ");
            }
            continue;
          default:
            throw new IllegalStateException("should not reach here");
        }
      }
    }

    for (String policyId : expPolicyIds) {
      failureMsg.append("certificate policy '").append(policyId).append("' is absent but is required; ");
    }
  } // method checkExtnCertificatePolicies

  void checkExtnDeltaCrlDistributionPoints(StringBuilder failureMsg, byte[] extnValue, IssuerInfo issuerInfo) {
    checkExtnCrlDistributionPoints(true, failureMsg, extnValue, issuerInfo);
  }

  void checkExtnCrlDistributionPoints(StringBuilder failureMsg, byte[] extnValue, IssuerInfo issuerInfo) {
    checkExtnCrlDistributionPoints(false, failureMsg, extnValue, issuerInfo);
  }

  private void checkExtnCrlDistributionPoints(
      boolean deltaCrl, StringBuilder failureMsg, byte[] extnValue, IssuerInfo issuerInfo) {
    CRLDistPoint isCrlDistPoints = CRLDistPoint.getInstance(extnValue);
    DistributionPoint[] isDistributionPoints = isCrlDistPoints.getDistributionPoints();

    String type = deltaCrl ? "deltaCRL" : "CRL";

    if (isDistributionPoints == null) {
      addViolation(failureMsg, "size of DistributionPoints of " + type, 0, 1);
      return;
    } else {
      int len = isDistributionPoints.length;
      if (len != 1) {
        addViolation(failureMsg, "size of DistributionPoints of " + type, len, 1);
        return;
      }
    }

    Set<String> isCrlUrls = new HashSet<>();
    for (DistributionPoint entry : isDistributionPoints) {
      int asn1Type = entry.getDistributionPoint().getType();
      if (asn1Type != DistributionPointName.FULL_NAME) {
        addViolation(failureMsg, "tag of DistributionPointName of CRLDistributionPoints of " + type,
            asn1Type, DistributionPointName.FULL_NAME);
        continue;
      }

      GeneralNames isDistributionPointNames = GeneralNames.getInstance(entry.getDistributionPoint().getName());
      GeneralName[] names = isDistributionPointNames.getNames();

      for (GeneralName name : names) {
        if (name.getTagNo() != GeneralName.uniformResourceIdentifier) {
          addViolation(failureMsg, "tag of URL of " + type, name.getTagNo(),
              GeneralName.uniformResourceIdentifier);
        } else {
          String uri = ((ASN1String) name.getName()).getString();
          isCrlUrls.add(uri);
        }
      }

      Set<String> expCrlUrls = deltaCrl ? issuerInfo.getDeltaCrlUrls() : issuerInfo.getCrlUrls();
      Set<String> diffs = strInBnotInA(expCrlUrls, isCrlUrls);
      if (CollectionUtil.isNotEmpty(diffs)) {
        failureMsg.append("URLs of ").append(type).append(" ").append(diffs).append(" are present but not expected; ");
      }

      diffs = strInBnotInA(isCrlUrls, expCrlUrls);
      if (CollectionUtil.isNotEmpty(diffs)) {
        failureMsg.append("URLs of ").append(type).append(" ").append(diffs).append(" are absent but are required; ");
      }
    }
  } // method checkExtnCrlDistributionPoints

  void checkExtnExtendedKeyUsage(
      StringBuilder failureMsg, byte[] extnValue, Extensions requestedExtns, ExtensionControl extnControl) {
    Set<String> isUsages = new HashSet<>();
    org.bouncycastle.asn1.x509.ExtendedKeyUsage keyusage =
        org.bouncycastle.asn1.x509.ExtendedKeyUsage.getInstance(extnValue);
    KeyPurposeId[] usages = keyusage.getUsages();
    if (usages != null) {
      for (KeyPurposeId usage : usages) {
        isUsages.add(usage.getId());
      }
    }

    Set<String> expectedUsages = new HashSet<>();
    Set<ExtKeyUsageControl> requiredExtKeyusage = caller.getExtKeyusage(true);
    if (requiredExtKeyusage != null) {
      for (ExtKeyUsageControl usage : requiredExtKeyusage) {
        expectedUsages.add(usage.getExtKeyUsage().getId());
      }
    }

    Set<ExtKeyUsageControl> optionalExtKeyusage = caller.getExtKeyusage(false);
    if (requestedExtns != null
        && extnControl.isPermittedInRequest()
        && CollectionUtil.isNotEmpty(optionalExtKeyusage)) {
      Extension extension = requestedExtns.getExtension(Extension.extendedKeyUsage);
      if (extension != null) {
        org.bouncycastle.asn1.x509.ExtendedKeyUsage reqKeyUsage =
            org.bouncycastle.asn1.x509.ExtendedKeyUsage.getInstance(extension.getParsedValue());
        for (ExtKeyUsageControl k : optionalExtKeyusage) {
          if (reqKeyUsage.hasKeyPurposeId(KeyPurposeId.getInstance(k.getExtKeyUsage()))) {
            expectedUsages.add(k.getExtKeyUsage().getId());
          }
        }
      }
    }

    if (CollectionUtil.isEmpty(expectedUsages)) {
      byte[] constantExtValue = caller.getConstantExtensionValue(Extension.extendedKeyUsage);
      if (constantExtValue != null) {
        expectedUsages = getExtKeyUsage(constantExtValue);
      }
    }

    Set<String> diffs = strInBnotInA(expectedUsages, isUsages);
    if (CollectionUtil.isNotEmpty(diffs)) {
      failureMsg.append("usages ").append(diffs).append(" are present but not expected; ");
    }

    diffs = strInBnotInA(isUsages, expectedUsages);
    if (CollectionUtil.isNotEmpty(diffs)) {
      failureMsg.append("usages ").append(diffs).append(" are absent but are required; ");
    }
  } // method checkExtnExtendedKeyUsage

  void checkExtnGmt0015(
      StringBuilder failureMsg, byte[] extnValue, Extensions requestedExtns,
      ExtensionControl extnControl, ASN1ObjectIdentifier oid, X500Name requestedSubject)
      throws IOException {
    if (Extn.id_GMT_0015_ICRegistrationNumber.equals(oid)
        || Extn.id_GMT_0015_InsuranceNumber.equals(oid)
        || Extn.id_GMT_0015_OrganizationCode.equals(oid)
        || Extn.id_GMT_0015_TaxationNumber.equals(oid)) {
      String expStr = null;
      Extension extension = requestedExtns == null ? null : requestedExtns.getExtension(oid);
      if (extension != null) {
        // extract from the extension
        expStr = ((ASN1String) extension.getParsedValue()).getString();
      } else {
        // extract from the subject
        RDN[] rdns = requestedSubject.getRDNs(oid);
        if (rdns != null && rdns.length > 0) {
          expStr = X509Util.rdnValueToString(rdns[0].getFirst().getValue());
        }
      }

      String isStr = null;
      try {
        isStr = ASN1PrintableString.getInstance(extnValue).getString();
      } catch (Exception ex) {
        failureMsg.append("exension value is not of type PrintableString; ");
      }

      if (isStr != null) {
        if (!CompareUtil.equalsObject(expStr, isStr)) {
          addViolation(failureMsg, "extension value", isStr, expStr);
        }
      }
    } else if (Extn.id_GMT_0015_IdentityCode.equals(oid)) {
      int tag = -1;
      String extnStr = null;

      Extension extension = requestedExtns == null ? null : requestedExtns.getExtension(oid);

      if (extension != null) {
        // extract from extension
        ASN1Encodable reqExtnValue = extension.getParsedValue();
        if (reqExtnValue instanceof ASN1TaggedObject) {
          ASN1TaggedObject tagged = (ASN1TaggedObject) reqExtnValue;
          tag = tagged.getTagNo();
          // we allow the EXPLICIT in request
          if (tagged.isExplicit()) {
            extnStr = ((ASN1String) tagged.getExplicitBaseObject()).getString();
          } else {
            // we also allow the IMPLICIT in request
            if (tag == 0 || tag == 2) {
              extnStr = ASN1PrintableString.getInstance(tagged, false).getString();
            } else if (tag == 1) {
              extnStr = ASN1UTF8String.getInstance(tagged, false).getString();
            }
          }
        }
      } else {
        String str;
        // extract from the subject
        RDN[] rdns = requestedSubject.getRDNs(oid);
        if (rdns != null && rdns.length > 0) {
          str = X509Util.rdnValueToString(rdns[0].getFirst().getValue());
        } else {
          str = "";
        }

        // [tag]value where tag is only one digit 0, 1 or 2
        if (str.length() > 3 && str.charAt(0) == '[' && str.charAt(2) == ']') {
          tag = Integer.parseInt(str.substring(1, 2));
          extnStr = str.substring(3);
        }
      }

      byte[] expected = null;
      if (StringUtil.isNotBlank(extnStr)) {
        final boolean explicit = true;
        if (tag == 0 || tag == 2) {
          expected = new DERTaggedObject(explicit, tag, new DERPrintableString(extnStr)).getEncoded();
        } else if (tag == 1) {
          expected = new DERTaggedObject(explicit, tag, new DERUTF8String(extnStr)).getEncoded();
        }
      }

      if (!Arrays.equals(expected, extnValue)) {
        addViolation(failureMsg, "extension value", hex(extnValue),
            (expected == null) ? "not present" : hex(expected));
      }
    } else {
      throw new IllegalArgumentException("unknown extension type " + oid.getId());
    }
  }
}
