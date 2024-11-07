// Copyright (c) 2013-2024 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.qa.ca.extn;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1String;
import org.bouncycastle.asn1.DERBMPString;
import org.bouncycastle.asn1.DERPrintableString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERT61String;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.ca.api.profile.Certprofile.ExtKeyUsageControl;
import org.xipki.ca.api.profile.Certprofile.ExtensionControl;
import org.xipki.ca.api.profile.Certprofile.KeyUsageControl;
import org.xipki.ca.api.profile.CertprofileException;
import org.xipki.ca.certprofile.xijson.DirectoryStringType;
import org.xipki.ca.certprofile.xijson.XijsonCertprofile;
import org.xipki.ca.certprofile.xijson.conf.ExtensionType;
import org.xipki.ca.certprofile.xijson.conf.X509ProfileType;
import org.xipki.ca.certprofile.xijson.conf.extn.AdditionalInformation;
import org.xipki.ca.certprofile.xijson.conf.extn.CCCSimpleExtensionSchema;
import org.xipki.ca.certprofile.xijson.conf.extn.CertificatePolicies;
import org.xipki.ca.certprofile.xijson.conf.extn.InhibitAnyPolicy;
import org.xipki.ca.certprofile.xijson.conf.extn.NameConstraints;
import org.xipki.ca.certprofile.xijson.conf.extn.PolicyConstraints;
import org.xipki.ca.certprofile.xijson.conf.extn.PolicyMappings;
import org.xipki.ca.certprofile.xijson.conf.extn.QcStatements;
import org.xipki.ca.certprofile.xijson.conf.extn.Restriction;
import org.xipki.ca.certprofile.xijson.conf.extn.SmimeCapabilities;
import org.xipki.ca.certprofile.xijson.conf.extn.TlsFeature;
import org.xipki.qa.ValidationIssue;
import org.xipki.qa.ca.IssuerInfo;
import org.xipki.security.ObjectIdentifiers;
import org.xipki.security.ObjectIdentifiers.Extn;
import org.xipki.security.X509Cert;
import org.xipki.util.Args;
import org.xipki.util.CollectionUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static org.xipki.qa.ca.extn.CheckerUtil.addIfNotIn;
import static org.xipki.qa.ca.extn.CheckerUtil.addViolation;
import static org.xipki.qa.ca.extn.CheckerUtil.buildConstantExtesions;
import static org.xipki.qa.ca.extn.CheckerUtil.hex;
import static org.xipki.qa.ca.extn.CheckerUtil.readAsn1Encodable;

/**
 * Extensions checker.
 *
 * @author Lijun Liao (xipki)
 * @since 2.0.0
 */

public class ExtensionsChecker {

  private static final Logger LOG = LoggerFactory.getLogger(ExtensionsChecker.class);

  private CertificatePolicies certificatePolicies;

  private PolicyMappings policyMappings;

  private NameConstraints nameConstraints;

  private PolicyConstraints policyConstraints;

  private InhibitAnyPolicy inhibitAnyPolicy;

  private Restriction restriction;

  private AdditionalInformation additionalInformation;

  private ASN1ObjectIdentifier validityModelId;

  private QcStatements qcStatements;

  private TlsFeature tlsFeature;

  private QaExtensionValue smimeCapabilities;

  private ASN1ObjectIdentifier cccExtensionSchemaType;

  private byte[] cccExtensionSchemaValue;

  private final Map<ASN1ObjectIdentifier, QaExtensionValue> constantExtensions;

  private final XijsonCertprofile certprofile;

  private final A2gChecker a2gChecker;
  private final H2nChecker h2nChecker;
  private final O2tChecker o2tChecker;
  private final U2zChecker u2zChecker;

  public ExtensionsChecker(X509ProfileType conf, XijsonCertprofile certprofile)
      throws CertprofileException {
    this.certprofile = Args.notNull(certprofile, "certprofile");

    // Extensions
    Map<String, ExtensionType> extensions = Args.notNull(conf, "conf").buildExtensions();

    // Extension controls
    Map<ASN1ObjectIdentifier, ExtensionControl> extensionControls = certprofile.getExtensionControls();

    // Certificate Policies
    ASN1ObjectIdentifier type = Extension.certificatePolicies;
    if (extensionControls.containsKey(type)) {
      this.certificatePolicies = extensions.get(type.getId()).getCertificatePolicies();
    }

    // Policy Mappings
    type = Extension.policyMappings;
    if (extensionControls.containsKey(type)) {
      this.policyMappings = extensions.get(type.getId()).getPolicyMappings();
    }

    // Name Constraints
    type = Extension.nameConstraints;
    if (extensionControls.containsKey(type)) {
      this.nameConstraints = extensions.get(type.getId()).getNameConstraints();
    }

    // Policy Constraints
    type = Extension.policyConstraints;
    if (extensionControls.containsKey(type)) {
      this.policyConstraints = extensions.get(type.getId()).getPolicyConstraints();
    }

    // Inhibit anyPolicy
    type = Extension.inhibitAnyPolicy;
    if (extensionControls.containsKey(type)) {
      this.inhibitAnyPolicy = extensions.get(type.getId()).getInhibitAnyPolicy();
    }

    // restriction
    type = Extn.id_extension_restriction;
    if (extensionControls.containsKey(type)) {
      this.restriction = extensions.get(type.getId()).getRestriction();
    }

    // additionalInformation
    type = Extn.id_extension_additionalInformation;
    if (extensionControls.containsKey(type)) {
      this.additionalInformation = extensions.get(type.getId()).getAdditionalInformation();
    }

    // validityModel
    type = Extn.id_extension_validityModel;
    if (extensionControls.containsKey(type)) {
      this.validityModelId = extensions.get(type.getId()).getValidityModel().getModelId().toXiOid();
    }

    // QCStatements
    type = Extension.qCStatements;
    if (extensionControls.containsKey(type)) {
      this.qcStatements = extensions.get(type.getId()).getQcStatements();
    }

    // tlsFeature
    type = Extn.id_pe_tlsfeature;
    if (extensionControls.containsKey(type)) {
      this.tlsFeature = extensions.get(type.getId()).getTlsFeature();
    }

    // SMIMECapabilities
    type = Extn.id_smimeCapabilities;
    if (extensionControls.containsKey(type)) {
      List<SmimeCapabilities.SmimeCapability> list =
          extensions.get(type.getId()).getSmimeCapabilities().getCapabilities();

      ASN1EncodableVector vec = new ASN1EncodableVector();
      for (SmimeCapabilities.SmimeCapability m : list) {
        ASN1ObjectIdentifier oid = m.getCapabilityId().toXiOid();
        ASN1Encodable params = null;
        SmimeCapabilities.SmimeCapabilityParameter capParam = m.getParameter();
        if (capParam != null) {
          if (capParam.getInteger() != null) {
            params = new ASN1Integer(capParam.getInteger());
          } else if (capParam.getBinary() != null) {
            params = readAsn1Encodable(capParam.getBinary().getValue());
          }
        }
        org.bouncycastle.asn1.smime.SMIMECapability cap = new org.bouncycastle.asn1.smime.SMIMECapability(oid, params);
        vec.add(cap);
      }

      DERSequence extValue = new DERSequence(vec);
      try {
        smimeCapabilities = new QaExtensionValue(extensionControls.get(type).isCritical(), extValue.getEncoded());
      } catch (IOException ex) {
        throw new CertprofileException("Cannot encode SMIMECapabilities: " + ex.getMessage());
      }
    }

    // CCC
    initCCCExtensionSchemas(extensions);

    // constant extensions
    this.constantExtensions = buildConstantExtesions(extensions);

    this.a2gChecker = new A2gChecker(this);
    this.h2nChecker = new H2nChecker(this);
    this.o2tChecker = new O2tChecker(this);
    this.u2zChecker = new U2zChecker(this);
  } // constructor

  private void initCCCExtensionSchemas(Map<String, ExtensionType> extensions)
      throws CertprofileException {
    Set<String> extnIds = extensions.keySet();
    ASN1ObjectIdentifier type = null;
    for (String m : extnIds) {
      ASN1ObjectIdentifier mOid = new ASN1ObjectIdentifier(m);
      if (mOid.on(Extn.id_ccc_extn)) {
        if (type != null) {
          throw new CertprofileException("Maximal one CCC Extension is allowed, but configured at least 2.");
        }
        type = mOid;
      }
    }

    if (type == null) {
      return;
    }

    ExtensionType ex = extensions.get(type.getId());
    if (!ex.critical()) {
      throw new CertprofileException("CCC Extension must be set to critical, but configured non-critical.");
    }

    List<ASN1ObjectIdentifier> simpleSchemaTypes = Arrays.asList(
        Extn.id_ccc_Vehicle_Cert_K,
        Extn.id_ccc_External_CA_Cert_F,
        Extn.id_ccc_VehicleOEM_Enc_Cert,
        Extn.id_ccc_VehicleOEM_Sig_Cert,
        Extn.id_ccc_Device_Enc_Cert,
        Extn.id_ccc_Vehicle_Intermediate_Cert,
        Extn.id_ccc_VehicleOEM_CA_Cert_J,
        Extn.id_ccc_VehicleOEM_CA_Cert_M);

    if (!simpleSchemaTypes.contains(type)) {
      return;
    }

    CCCSimpleExtensionSchema schema = ex.getCccExtensionSchema();
    if (schema == null) {
      throw new CertprofileException("ccExtensionSchema is not set for " + type);
    }

    ASN1Sequence seq = new DERSequence(new ASN1Integer(schema.getVersion()));
    this.cccExtensionSchemaType = type;
    try {
      this.cccExtensionSchemaValue = seq.getEncoded();
    } catch (IOException e) {
      throw new CertprofileException("error encoding CCC extensionSchemaValue");
    }
  }

  CertificatePolicies getCertificatePolicies() {
    return certificatePolicies;
  }

  PolicyMappings getPolicyMappings() {
    return policyMappings;
  }

  NameConstraints getNameConstraints() {
    return nameConstraints;
  }

  PolicyConstraints getPolicyConstraints() {
    return policyConstraints;
  }

  InhibitAnyPolicy getInhibitAnyPolicy() {
    return inhibitAnyPolicy;
  }

  Restriction getRestriction() {
    return restriction;
  }

  AdditionalInformation getAdditionalInformation() {
    return additionalInformation;
  }

  ASN1ObjectIdentifier getValidityModelId() {
    return validityModelId;
  }

  QcStatements getQcStatements() {
    return qcStatements;
  }

  TlsFeature getTlsFeature() {
    return tlsFeature;
  }

  QaExtensionValue getSmimeCapabilities() {
    return smimeCapabilities;
  }

  Map<ASN1ObjectIdentifier, QaExtensionValue> getConstantExtensions() {
    return constantExtensions;
  }

  XijsonCertprofile getCertprofile() {
    return certprofile;
  }

  public List<ValidationIssue> checkExtensions(
      Certificate cert, IssuerInfo issuerInfo, Extensions requestedExtns, X500Name requestedSubject) {
    Args.notNull(issuerInfo, "issuerInfo");

    X509Cert jceCert = new X509Cert(Args.notNull(cert, "cert"));
    List<ValidationIssue> result = new LinkedList<>();

    // detect the list of extension types in certificate
    Set<ASN1ObjectIdentifier> presentExtensionTypes = getExtensionTypes(cert, issuerInfo, requestedExtns);

    Extensions extensions = cert.getTBSCertificate().getExtensions();
    ASN1ObjectIdentifier[] oids = extensions.getExtensionOIDs();

    if (oids == null) {
      ValidationIssue issue = new ValidationIssue("X509.EXT.GEN", "extension general");
      result.add(issue);
      issue.setFailureMessage("no extension is present");
      return result;
    }

    List<ASN1ObjectIdentifier> certExtTypes = Arrays.asList(oids);

    for (ASN1ObjectIdentifier extType : presentExtensionTypes) {
      if (!certExtTypes.contains(extType)) {
        ValidationIssue issue = createExtensionIssue(extType);
        result.add(issue);
        issue.setFailureMessage("extension is absent but is required");
      }
    }

    Map<ASN1ObjectIdentifier, ExtensionControl> extnControls = certprofile.getExtensionControls();
    for (ASN1ObjectIdentifier oid : certExtTypes) {
      ValidationIssue issue = createExtensionIssue(oid);
      result.add(issue);
      if (!presentExtensionTypes.contains(oid)) {
        issue.setFailureMessage("extension is present but is not permitted");
        continue;
      }

      Extension ext = extensions.getExtension(oid);
      StringBuilder failureMsg = new StringBuilder();
      ExtensionControl extnControl = extnControls.get(oid);

      if (extnControl.isCritical() != ext.isCritical()) {
        addViolation(failureMsg, "critical", ext.isCritical(), extnControl.isCritical());
      }

      byte[] extnValue = ext.getExtnValue().getOctets();
      try {
        if (Extension.authorityKeyIdentifier.equals(oid)) {
          a2gChecker.checkExtnAuthorityKeyId(failureMsg, extnValue, issuerInfo);
        } else if (Extension.subjectKeyIdentifier.equals(oid)) {
          // SubjectKeyIdentifier
          o2tChecker.checkExtnSubjectKeyIdentifier(failureMsg, extnValue, cert.getSubjectPublicKeyInfo());
        } else if (Extension.keyUsage.equals(oid)) {
          h2nChecker.checkExtnKeyUsage(failureMsg, jceCert.getKeyUsage(), requestedExtns, extnControl);
        } else if (Extension.certificatePolicies.equals(oid)) {
          a2gChecker.checkExtnCertificatePolicies(failureMsg, extnValue, requestedExtns, extnControl);
        } else if (Extension.policyMappings.equals(oid)) {
          o2tChecker.checkExtnPolicyMappings(failureMsg, extnValue, requestedExtns, extnControl);
        } else if (Extension.subjectAlternativeName.equals(oid)) {
          o2tChecker.checkExtnSubjectAltNames(failureMsg, extnValue, requestedExtns, extnControl, requestedSubject);
        } else if (Extension.subjectDirectoryAttributes.equals(oid)) {
          o2tChecker.checkExtnSubjectDirAttrs(failureMsg, extnValue, requestedExtns, extnControl);
        } else if (Extension.issuerAlternativeName.equals(oid)) {
          h2nChecker.checkExtnIssuerAltNames(failureMsg, extnValue, issuerInfo);
        } else if (Extension.basicConstraints.equals(oid)) {
          a2gChecker.checkExtnBasicConstraints(failureMsg, extnValue);
        } else if (Extension.nameConstraints.equals(oid)) {
          h2nChecker.checkExtnNameConstraints(failureMsg, extnValue, requestedExtns, extnControl);
        } else if (Extension.policyConstraints.equals(oid)) {
          o2tChecker.checkExtnPolicyConstraints(failureMsg, extnValue, requestedExtns, extnControl);
        } else if (Extension.extendedKeyUsage.equals(oid)) {
          a2gChecker.checkExtnExtendedKeyUsage(failureMsg, extnValue, requestedExtns, extnControl);
        } else if (Extension.cRLDistributionPoints.equals(oid)) {
          a2gChecker.checkExtnCrlDistributionPoints(failureMsg, extnValue, issuerInfo);
        } else if (Extension.inhibitAnyPolicy.equals(oid)) {
          h2nChecker.checkExtnInhibitAnyPolicy(failureMsg, extnValue, extensions, extnControl);
        } else if (Extension.freshestCRL.equals(oid)) {
          a2gChecker.checkExtnDeltaCrlDistributionPoints(failureMsg, extnValue, issuerInfo);
        } else if (Extension.authorityInfoAccess.equals(oid)) {
          a2gChecker.checkExtnAuthorityInfoAccess(failureMsg, extnValue, issuerInfo);
        } else if (Extension.subjectInfoAccess.equals(oid)) {
          o2tChecker.checkExtnSubjectInfoAccess(failureMsg, extnValue, requestedExtns, extnControl);
        } else if (Extn.id_extension_admission.equals(oid)) {
          a2gChecker.checkExtnAdmission(failureMsg, extnValue, requestedExtns, requestedSubject, extnControl);
        } else if (Extn.id_extension_pkix_ocsp_nocheck.equals(oid)) {
          o2tChecker.checkExtnOcspNocheck(failureMsg, extnValue);
        } else if (Extn.id_extension_restriction.equals(oid)) {
          o2tChecker.checkExtnRestriction(failureMsg, extnValue, requestedExtns, extnControl);
        } else if (Extn.id_extension_additionalInformation.equals(oid)) {
          a2gChecker.checkExtnAdditionalInformation(failureMsg, extnValue, requestedExtns, extnControl);
        } else if (Extn.id_extension_validityModel.equals(oid)) {
          u2zChecker.checkExtnValidityModel(failureMsg, extnValue, requestedExtns, extnControl);
        } else if (Extension.privateKeyUsagePeriod.equals(oid)) {
          o2tChecker.checkExtnPrivateKeyUsagePeriod(
              failureMsg, extnValue, jceCert.getNotBefore(), jceCert.getNotAfter());
        } else if (Extension.qCStatements.equals(oid)) {
          o2tChecker.checkExtnQcStatements(failureMsg, extnValue, requestedExtns, extnControl);
        } else if (Extension.biometricInfo.equals(oid)) {
          a2gChecker.checkExtnBiometricInfo(failureMsg, extnValue, requestedExtns);
        } else if (Extn.id_pe_tlsfeature.equals(oid)) {
          o2tChecker.checkExtnTlsFeature(failureMsg, extnValue, requestedExtns, extnControl);
        } else if (Extn.id_smimeCapabilities.equals(oid)) {
          o2tChecker.checkSmimeCapabilities(failureMsg, extnValue, extnControl);
        } else if (Extn.id_SCTs.equals(oid)) {
          o2tChecker.checkScts(failureMsg, extnValue, extnControl);
        } else if (Extn.id_GMT_0015_ICRegistrationNumber.equals(oid)
            || Extn.id_GMT_0015_InsuranceNumber.equals(oid)
            || Extn.id_GMT_0015_OrganizationCode.equals(oid)
            || Extn.id_GMT_0015_TaxationNumber.equals(oid)
            || Extn.id_GMT_0015_IdentityCode.equals(oid)) {
          a2gChecker.checkExtnGmt0015(failureMsg, extnValue, requestedExtns, extnControl, oid, requestedSubject);
        } else if (oid.equals(cccExtensionSchemaType)) {
          byte[] expected = cccExtensionSchemaValue;
          if (!Arrays.equals(cccExtensionSchemaValue, extnValue)) {
            addViolation(failureMsg, "extension value", hex(extnValue),
                (expected == null) ? "not present" : hex(expected));
          }
        } else {
          byte[] expected = getExpectedExtValue(oid, requestedExtns, extnControl);
          if (!Arrays.equals(expected, extnValue)) {
            addViolation(failureMsg, "extension value", hex(extnValue),
                (expected == null) ? "not present" : hex(expected));
          }
        }

        if (failureMsg.length() > 0) {
          issue.setFailureMessage(failureMsg.toString());
        }

      } catch (IllegalArgumentException | ClassCastException | IOException | ArrayIndexOutOfBoundsException ex) {
        LOG.debug("extension value does not have correct syntax", ex);
        issue.setFailureMessage("extension value does not have correct syntax");
      }
    }

    return result;
  } // method checkExtensions

  private byte[] getExpectedExtValue(
      ASN1ObjectIdentifier type, Extensions requestedExtns, ExtensionControl extControl) {
    if (constantExtensions != null && constantExtensions.containsKey(type)) {
      return constantExtensions.get(type).getValue();
    } else if (requestedExtns != null && extControl.isPermittedInRequest()) {
      Extension reqExt = requestedExtns.getExtension(type);
      if (reqExt != null) {
        return reqExt.getExtnValue().getOctets();
      }
    }

    return null;
  } // getExpectedExtValue

  private Set<ASN1ObjectIdentifier> getExtensionTypes(
      Certificate cert, IssuerInfo issuerInfo, Extensions requestedExtns) {
    Set<ASN1ObjectIdentifier> types = new HashSet<>();
    // profile required extension types
    Map<ASN1ObjectIdentifier, ExtensionControl> extensionControls = certprofile.getExtensionControls();

    for (Entry<ASN1ObjectIdentifier, ExtensionControl> entry : extensionControls.entrySet()) {
      ASN1ObjectIdentifier oid = entry.getKey();
      if (entry.getValue().isRequired()) {
        types.add(oid);
      } else if ((requestedExtns != null && requestedExtns.getExtension(oid) != null)) {
        types.add(oid);
      }
    }

    // Authority key identifier
    ASN1ObjectIdentifier type = Extension.authorityKeyIdentifier;
    if (extensionControls.containsKey(type)) {
      addIfNotIn(types, type);
    }

    // Subject key identifier, Subject Ke
    type = Extension.subjectKeyIdentifier;
    if (extensionControls.containsKey(type)) {
      addIfNotIn(types, type);
    }

    // KeyUsage
    type = Extension.keyUsage;
    if (extensionControls.containsKey(type)) {
      boolean required = requestedExtns != null && requestedExtns.getExtension(type) != null;

      if (!required) {
        Set<KeyUsageControl> requiredKeyusage = h2nChecker.getKeyusage(true);
        if (CollectionUtil.isNotEmpty(requiredKeyusage)) {
          required = true;
        }
      }

      if (required) {
        addIfNotIn(types, type);
      }
    }

    // CertificatePolicies
    type = Extension.certificatePolicies;
    if (extensionControls.containsKey(type)) {
      if (certificatePolicies != null) {
        addIfNotIn(types, type);
      }
    }

    // Policy Mappings
    type = Extension.policyMappings;
    if (extensionControls.containsKey(type)) {
      if (policyMappings != null) {
        addIfNotIn(types, type);
      }
    }

    // SubjectAltNames
    type = Extension.subjectAlternativeName;
    if (extensionControls.containsKey(type)) {
      if (requestedExtns != null && requestedExtns.getExtension(type) != null) {
        addIfNotIn(types, type);
      }
    }

    // IssuerAltName
    type = Extension.issuerAlternativeName;
    if (extensionControls.containsKey(type)) {
      if (cert.getTBSCertificate().getExtensions().getExtension(Extension.subjectAlternativeName) != null) {
        addIfNotIn(types, type);
      }
    }

    // BasicConstraints
    type = Extension.basicConstraints;
    if (extensionControls.containsKey(type)) {
      addIfNotIn(types, type);
    }

    // Name Constraints
    type = Extension.nameConstraints;
    if (extensionControls.containsKey(type)) {
      if (nameConstraints != null) {
        addIfNotIn(types, type);
      }
    }

    // PolicyConstraints
    type = Extension.policyConstraints;
    if (extensionControls.containsKey(type)) {
      if (policyConstraints != null) {
        addIfNotIn(types, type);
      }
    }

    // ExtendedKeyUsage
    type = Extension.extendedKeyUsage;
    if (extensionControls.containsKey(type)) {
      boolean required = requestedExtns != null && requestedExtns.getExtension(type) != null;

      if (!required) {
        Set<ExtKeyUsageControl> requiredExtKeyusage = getExtKeyusage(true);
        if (CollectionUtil.isNotEmpty(requiredExtKeyusage)) {
          required = true;
        }
      }

      if (required) {
        addIfNotIn(types, type);
      }
    }

    // CRLDistributionPoints
    type = Extension.cRLDistributionPoints;
    if (extensionControls.containsKey(type)) {
      if (issuerInfo.getCrlUrls() != null) {
        addIfNotIn(types, type);
      }
    }

    // Inhibit anyPolicy
    type = Extension.inhibitAnyPolicy;
    if (extensionControls.containsKey(type)) {
      if (inhibitAnyPolicy != null) {
        addIfNotIn(types, type);
      }
    }

    // FreshestCRL
    type = Extension.freshestCRL;
    if (extensionControls.containsKey(type)) {
      if (issuerInfo.getDeltaCrlUrls() != null) {
        addIfNotIn(types, type);
      }
    }

    // AuthorityInfoAccess
    type = Extension.authorityInfoAccess;
    if (extensionControls.containsKey(type)) {
      if (issuerInfo.getOcspUrls() != null) {
        addIfNotIn(types, type);
      }
    }

    // SubjectInfoAccess
    type = Extension.subjectInfoAccess;
    if (extensionControls.containsKey(type)) {
      if (requestedExtns != null && requestedExtns.getExtension(type) != null) {
        addIfNotIn(types, type);
      }
    }

    // Admission
    type = Extn.id_extension_admission;
    if (extensionControls.containsKey(type)) {
      if (certprofile.extensions().getAdmission() != null) {
        addIfNotIn(types, type);
      }
    }

    // ocsp-nocheck
    type = Extn.id_extension_pkix_ocsp_nocheck;
    if (extensionControls.containsKey(type)) {
      addIfNotIn(types, type);
    }

    if (requestedExtns != null) {
      ASN1ObjectIdentifier[] extOids = requestedExtns.getExtensionOIDs();
      for (ASN1ObjectIdentifier oid : extOids) {
        if (extensionControls.containsKey(oid)) {
          addIfNotIn(types, oid);
        }
      }
    }

    return types;
  } // method getExensionTypes

  private ValidationIssue createExtensionIssue(ASN1ObjectIdentifier extId) {
    String extName = ObjectIdentifiers.getName(extId);
    if (extName == null) {
      extName = extId.getId().replace('.', '_');
      return new ValidationIssue("X509.EXT." + extName, "extension " + extId.getId());
    } else {
      return new ValidationIssue("X509.EXT." + extName, "extension " + extName
          + " (" + extId.getId() + ")");
    }
  } // method createExtensionIssue

  void checkDirectoryString(
      ASN1ObjectIdentifier extnType, DirectoryStringType type, String text, StringBuilder failureMsg,
      byte[] extensionValue, Extensions requestedExtns, ExtensionControl extControl) {
    if (type == null) {
      checkConstantExtnValue(extnType, failureMsg, extensionValue, requestedExtns, extControl);
      return;
    }

    ASN1Primitive asn1;
    try {
      asn1 = ASN1Primitive.fromByteArray(extensionValue);
    } catch (IOException ex) {
      failureMsg.append("invalid syntax of extension value; ");
      return;
    }

    boolean correctStringType;

    switch (type) {
      case bmpString:
        correctStringType = (asn1 instanceof DERBMPString);
        break;
      case printableString:
        correctStringType = (asn1 instanceof DERPrintableString);
        break;
      case teletexString:
        correctStringType = (asn1 instanceof DERT61String);
        break;
      case utf8String:
        correctStringType = (asn1 instanceof DERUTF8String);
        break;
      default:
        throw new IllegalStateException("should not reach here, unknown DirectoryStringType " + type);
    } // end switch

    if (!correctStringType) {
      failureMsg.append("extension value is not of type DirectoryString.").append(text).append("; ");
      return;
    }

    String extTextValue = ((ASN1String) asn1).getString();
    if (!text.equals(extTextValue)) {
      addViolation(failureMsg, "content", extTextValue, text);
    }
  } // method checkDirectoryString

  Set<ExtKeyUsageControl> getExtKeyusage(boolean required) {
    Set<ExtKeyUsageControl> ret = new HashSet<>();

    Set<ExtKeyUsageControl> controls = certprofile.extensions().getExtendedKeyusages();
    if (controls != null) {
      for (ExtKeyUsageControl control : controls) {
        if (control.isRequired() == required) {
          ret.add(control);
        }
      }
    }
    return ret;
  } // method getExtKeyusage

  byte[] getConstantExtensionValue(ASN1ObjectIdentifier type) {
    return (constantExtensions == null) ? null : constantExtensions.get(type).getValue();
  }

  void checkConstantExtnValue(
      ASN1ObjectIdentifier extnType, StringBuilder failureMsg, byte[] extensionValue,
      Extensions requestedExtns, ExtensionControl extControl) {
    byte[] expected = getExpectedExtValue(extnType, requestedExtns, extControl);
    if (!Arrays.equals(expected, extensionValue)) {
      addViolation(failureMsg, "extension values", hex(extensionValue),
          (expected == null) ? "not present" : hex(expected));
    }
  } // method checkConstantExtnValue

}
