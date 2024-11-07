// Copyright (c) 2013-2024 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.ca.certprofile.xijson.conf.extn;

import org.xipki.ca.certprofile.xijson.conf.Describable.DescribableOid;
import org.xipki.util.ValidableConf;
import org.xipki.util.exception.InvalidConfException;

/**
 * Extension ValidityModel.
 *
 * @author Lijun Liao (xipki)
 */

public class ValidityModel extends ValidableConf {

  private DescribableOid modelId;

  public DescribableOid getModelId() {
    return modelId;
  }

  public void setModelId(DescribableOid modelId) {
    this.modelId = modelId;
  }

  @Override
  public void validate() throws InvalidConfException {
    notNull(modelId, "modelId");
    validate(modelId);
  }

} // class ValidityModel
