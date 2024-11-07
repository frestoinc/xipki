// Copyright (c) 2013-2024 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.ca.certprofile.demo;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.xipki.ca.api.PublicCaInfo;
import org.xipki.ca.api.profile.CertprofileException;
import org.xipki.ca.api.profile.ExtensionValue;
import org.xipki.ca.api.profile.ExtensionValues;
import org.xipki.ca.certprofile.xijson.XijsonCertprofile;
import org.xipki.ca.certprofile.xijson.conf.ExtensionType;
import org.xipki.pki.BadCertTemplateException;
import org.xipki.util.ConfPairs;
import org.xipki.util.JSON;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Example Certprofile.
 *
 * @author Lijun Liao (xipki)
 */

public class DemoCertprofile extends XijsonCertprofile {

  public static class ExtnDemoWithConf {
    private List<String> texts;

    public List<String> getTexts() {
      return texts;
    }

    public void setTexts(List<String> texts) {
      this.texts = texts;
    }

  }

  public static final ASN1ObjectIdentifier id_demo_without_conf = new ASN1ObjectIdentifier("1.2.3.4.1");

  public static final ASN1ObjectIdentifier id_demo_with_conf = new ASN1ObjectIdentifier("1.2.3.4.2");

  private boolean addExtraWithoutConf;

  private boolean addExtraWithConf;

  private ASN1Sequence sequence;

  @Override
  protected void extraReset() {
    addExtraWithoutConf = false;
    addExtraWithConf = false;
    sequence = null;
  }

  @Override
  protected boolean initExtraExtension(ExtensionType extn) throws CertprofileException {
    ASN1ObjectIdentifier extnId = extn.getType().toXiOid();
    if (id_demo_without_conf.equals(extnId)) {
      this.addExtraWithoutConf = true;
      return true;
    } else if (id_demo_with_conf.equals(extnId)) {
      Object customObj = Optional.ofNullable(extn.getCustom()).orElseThrow(() ->
        new CertprofileException("ExtensionType.custom is not specified"));

      // we need to first serialize the configuration
      byte[] serializedConf = JSON.toJSONBytes(customObj);
      ExtnDemoWithConf conf = JSON.parseObject(serializedConf, ExtnDemoWithConf.class);

      List<String> list = conf.getTexts();
      DERUTF8String[] texts = new DERUTF8String[list.size()];
      for (int i = 0; i < list.size(); i++) {
        texts[i] = new DERUTF8String(list.get(i));
      }

      this.sequence = new DERSequence(texts);

      this.addExtraWithConf = true;
      return true;
    } else {
      return false;
    }
  } // method initExtraExtension

  @Override
  public ExtensionValues getExtraExtensions(
      Map<ASN1ObjectIdentifier, ExtensionControl> extensionOccurrences, X500Name requestedSubject,
      X500Name grantedSubject, Map<ASN1ObjectIdentifier, Extension> requestedExtensions,
      Instant notBefore, Instant notAfter, PublicCaInfo caInfo)
      throws CertprofileException, BadCertTemplateException {
    ExtensionValues extnValues = new ExtensionValues();

    if (addExtraWithoutConf) {
      ASN1ObjectIdentifier type = id_demo_without_conf;
      ExtensionControl extnControl = extensionOccurrences.get(type);
      if (extnControl != null) {
        ConfPairs caExtraControl = caInfo.getExtraControl();
        String name = "name-a";
        String value = null;
        if (caExtraControl != null) {
          value = caExtraControl.value(name);
        }

        if (value == null) {
          value = "UNDEF";
        }

        ExtensionValue extnValue = new ExtensionValue(extnControl.isCritical(), new DERUTF8String(name + ": " + value));
        extnValues.addExtension(type, extnValue);
      }
    }

    if (addExtraWithConf) {
      ASN1ObjectIdentifier type = id_demo_with_conf;
      ExtensionControl extnControl = extensionOccurrences.get(type);
      if (extnControl != null) {
        if (sequence == null) {
          throw new IllegalStateException("Certprofile is not initialized");
        }

        ExtensionValue extnValue = new ExtensionValue(extnControl.isCritical(), sequence);
        extnValues.addExtension(type, extnValue);
      }
    }

    return extnValues.size() == 0 ? null : extnValues;
  } // method getExtraExtensions

}
