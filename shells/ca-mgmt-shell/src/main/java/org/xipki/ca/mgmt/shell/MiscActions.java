// Copyright (c) 2013-2024 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.ca.mgmt.shell;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.completers.FileCompleter;
import org.xipki.ca.api.mgmt.CaConfs;
import org.xipki.ca.api.mgmt.CaMgmtException;
import org.xipki.ca.api.mgmt.CaSystemStatus;
import org.xipki.ca.mgmt.shell.CaActions.CaAction;
import org.xipki.shell.CmdFailure;
import org.xipki.util.IoUtil;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Misc actions to manage CA.
 *
 * @author Lijun Liao (xipki)
 *
 */
public class MiscActions {

  @Command(scope = "ca", name = "export-conf", description = "export configuration to zip file")
  @Service
  public static class ExportConf extends CaAction {

    @Option(name = "--conf-file", required = true, description = "zip file that saves the exported configuration")
    @Completion(FileCompleter.class)
    private String confFile;

    @Option(name = "--ca", multiValued = true,
        description = "CAs whose configuration should be exported. Empty list means all CAs")
    @Completion(CaCompleters.CaNameCompleter.class)
    private List<String> caNames;

    @Override
    protected Object execute0() throws Exception {
      String msg = "configuration to file " + confFile;
      try {
        save(new File(confFile), IoUtil.readAllBytesAndClose(caManager.exportConf(caNames)));
        println("exported " + msg);
        return null;
      } catch (CaMgmtException ex) {
        throw new CmdFailure("could not export " + msg + ", error: " + ex.getMessage(), ex);
      }
    } // method execute0

  } // class ExportConf

  @Command(scope = "ca", name = "load-conf", description = "load configuration")
  @Service
  public static class LoadConf extends CaAction {

    @Argument(name = "conf-file", required = true, description = "CA system configuration file (JSON or zip file)")
    @Completion(FileCompleter.class)
    private String confFile;

    @Override
    protected Object execute0() throws Exception {
      String msg = "configuration " + confFile;
      try {
        InputStream confStream = confFile.endsWith(".json")
            ? CaConfs.convertFileConfToZip(confFile) : Files.newInputStream(Paths.get(confFile));
        caManager.loadConfAndClose(confStream);
        println("loaded " + msg);
      } catch (CaMgmtException ex) {
        throw new CmdFailure("could not load " + msg + ", error: " + ex.getMessage(), ex);
      }
      return null;
    }

  } // class LoadConf

  @Command(scope = "ca", name = "notify-change", description = "notify the change of CA system")
  @Service
  public static class NotifyChange extends CaAction {

    @Override
    protected Object execute0() throws Exception {
      String msg = "the change of CA system";
      try {
        caManager.notifyCaChange();
        println("notified " + msg);
        return null;
      } catch (CaMgmtException ex) {
        throw new CmdFailure("could not notify " + msg + ", error: " + ex.getMessage(), ex);
      }
    } // method execute0

  } // class NotifyChange

  @Command(scope = "ca", name = "republish", description = "republish certificates")
  @Service
  public static class Republish extends CaAction {

    @Option(name = "--thread", description = "number of threads")
    private Integer numThreads = 5;

    @Option(name = "--ca", required = true, description = "CA name")
    @Completion(CaCompleters.CaNameCompleter.class)
    private String caName;

    @Option(name = "--publisher", required = true, multiValued = true,
        description = "publisher name or 'all' for all publishers")
    @Completion(CaCompleters.PublisherNamePlusAllCompleter.class)
    private List<String> publisherNames;

    @Override
    protected Object execute0() throws Exception {
      if (publisherNames == null) {
        throw new IllegalStateException("should not reach here");
      }
      boolean allPublishers = false;
      for (String publisherName : publisherNames) {
        if ("all".equalsIgnoreCase(publisherName)) {
          allPublishers = true;
          break;
        }
      }

      if (allPublishers) {
        publisherNames = null;
      }

      if ("all".equalsIgnoreCase(caName)) {
        caName = null;
      }

      String msg = "certificates";
      try {
        caManager.republishCertificates(caName, publisherNames, numThreads);
        println("republished " + msg);
        return null;
      } catch (CaMgmtException ex) {
        throw new CmdFailure("could not republish " + msg + ", error: " + ex.getMessage(), ex);
      }
    } // method execute0

  } // class Republish

  @Command(scope = "ca", name = "restart-ca", description = "restart CA")
  @Service
  public static class RestartCa extends CaAction {

    @Argument(index = 0, name = "name", required = true, description = "CA name")
    @Completion(CaCompleters.CaNameCompleter.class)
    private String name;

    @Override
    protected Object execute0() throws Exception {
      try {
        caManager.restartCa(name);
        System.out.println("restarted CA " + name);
      } catch (CaMgmtException ex) {
        throw new CmdFailure("could not restart CA " + name + ", error: " + ex.getMessage(), ex);
      }
      return null;
    } // method execute0

  } // class RestartCa

  @Command(scope = "ca", name = "restart", description = "restart CA system")
  @Service
  public static class Restart extends CaAction {

    @Override
    protected Object execute0() throws Exception {
      try {
        caManager.restartCaSystem();
      } catch (CaMgmtException ex) {
        throw new CmdFailure("could not restart CA system, error: " + ex.getMessage(), ex);
      }

      StringBuilder sb = new StringBuilder("restarted CA system\n");

      sb.append("  successful CAs:\n");
      String prefix = "    ";
      printCaNames(sb, caManager.getSuccessfulCaNames(), prefix);

      sb.append("  failed CAs:\n");
      printCaNames(sb, caManager.getFailedCaNames(), prefix);

      sb.append("  inactive CAs:\n");
      printCaNames(sb, caManager.getInactiveCaNames(), prefix);

      print(sb.toString());
      return null;
    } // method execute0

  } // class Restart

  @Command(scope = "ca", name = "system-status", description = "show CA system status")
  @Service
  public static class SystemStatus extends CaAction {

    @Override
    protected Object execute0() throws Exception {
      CaSystemStatus status = caManager.getCaSystemStatus();
      if (status != null) {
        println(status.toString());
      } else {
        throw new CmdFailure("status is null");
      }
      return null;
    } // method execute0

  } // class SystemStatus

  @Command(scope = "ca", name = "unlock", description = "unlock CA system")
  @Service
  public static class Unlock extends CaAction {

    @Override
    protected Object execute0() throws Exception {
      try {
        caManager.unlockCa();
        println("unlocked CA system, calling ca:restart to restart CA system");
        return null;
      } catch (CaMgmtException ex) {
        throw new CmdFailure("could not unlock CA system, error: " + ex.getMessage(), ex);
      }
    } // method execute0

  } // class Unlock

  @Command(scope = "ca", name = "ca-token-info-p11", description = "list objects in PKCS#11 device of the CA")
  @Service
  public static class CaTokenInfoP11 extends CaAction {

    @Option(name = "--verbose", aliases = "-v", description = "show object information verbosely")
    private Boolean verbose = Boolean.FALSE;

    @Option(name = "--module", description = "name of the PKCS#11 module.")
    private String moduleName = "default";

    @Option(name = "--slot", description = "slot index")
    private Integer slotIndex;

    @Override
    protected Object execute0() throws Exception {
      try {
        println(caManager.getTokenInfoP11(moduleName, slotIndex, verbose));
        return null;
      } catch (CaMgmtException ex) {
        throw new CmdFailure("could not get token-info-p11, error: " + ex.getMessage(), ex);
      }
    } // method execute0

  }

}
