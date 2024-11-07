// Copyright (c) 2013-2024 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.ca.certprofile.xijson.conf.extn;

import org.xipki.ca.certprofile.xijson.DirectoryStringType;
import org.xipki.util.ValidableConf;
import org.xipki.util.exception.InvalidConfException;

/**
 * Extension Additional Information.
 *
 * @author Lijun Liao (xipki)
 */

public class AdditionalInformation extends ValidableConf {

  private DirectoryStringType type;

  private String text;

  public DirectoryStringType getType() {
    return type;
  }

  public void setType(DirectoryStringType type) {
    this.type = type;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  @Override
  public void validate() throws InvalidConfException {
    notBlank(text, "text");
    notNull(type, "type");
  }

} // class AdditionalInformation
