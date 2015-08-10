/*
 *
 * This file is part of the XiPKI project.
 * Copyright (c) 2014 - 2015 Lijun Liao
 * Author: Lijun Liao
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY
 * THE AUTHOR LIJUN LIAO. LIJUN LIAO DISCLAIMS THE WARRANTY OF NON INFRINGEMENT
 * OF THIRD PARTY RIGHTS.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Public License.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the XiPKI software without
 * disclosing the source code of your own applications.
 *
 * For more information, please contact Lijun Liao at this
 * address: lijun.liao@gmail.com
 */

package org.xipki.ca.scep.client.shell;

import java.io.File;
import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.util.List;

import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.bouncycastle.asn1.x500.X500Name;
import org.xipki.console.karaf.CmdFailure;
import org.xipki.scep4j.client.ScepClient;

/**
 * @author Lijun Liao
 */

@Command(scope = "scep", name = "getcert", description="download certificate")
public class GetCertCommand extends ClientCommand
{
    @Option(name = "--serial", aliases = "-s",
            required = true,
            description = "serial number\n"
                    + "(required)")
    private String serialNumber;

    @Option(name = "--out", aliases = "-o",
            required = true,
            description = "where to save the certificate\n"
                    + "(required)")
    private String outputFile;

    @Override
    protected Object _doExecute()
    throws Exception
    {
        ScepClient client = getScepClient();
        BigInteger serial = toBigInt(serialNumber);
        X509Certificate caCert = client.getAuthorityCertStore().getCACert();
        X500Name caSubject = X500Name.getInstance(caCert.getSubjectX500Principal().getEncoded());
        List<X509Certificate> certs = client.scepGetCert(getIdentityKey(), getIdentityCert(),
                caSubject, serial);
        if(certs == null || certs.isEmpty())
        {
            throw new CmdFailure("received no certficate from server");
        }

        saveVerbose("saved certificate to file", new File(outputFile), certs.get(0).getEncoded());
        return null;
    }
}