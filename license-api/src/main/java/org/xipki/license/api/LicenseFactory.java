// Copyright (c) 2013-2024 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.license.api;

/**
 * Unlimited license factory.
 * @author Lijun Liao (xipki)
 *
 */
public interface LicenseFactory {

  CmLicense createCmLicense();

  OcspLicense createOcspLicense();

  void close();

}
