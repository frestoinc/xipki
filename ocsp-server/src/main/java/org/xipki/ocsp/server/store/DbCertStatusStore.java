// Copyright (c) 2013-2024 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.ocsp.server.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.datasource.DataAccessException;
import org.xipki.datasource.DataSourceWrapper;
import org.xipki.ocsp.api.CertStatusInfo;
import org.xipki.ocsp.api.CertStatusInfo.CertStatus;
import org.xipki.ocsp.api.CertStatusInfo.UnknownCertBehaviour;
import org.xipki.ocsp.api.OcspStore;
import org.xipki.ocsp.api.OcspStoreException;
import org.xipki.ocsp.api.RequestIssuer;
import org.xipki.ocsp.server.IssuerFilter;
import org.xipki.ocsp.server.OcspServerConf;
import org.xipki.security.CertRevocationInfo;
import org.xipki.security.CrlReason;
import org.xipki.security.HashAlgo;
import org.xipki.security.X509Cert;
import org.xipki.security.util.X509Util;
import org.xipki.util.Args;
import org.xipki.util.Base64;
import org.xipki.util.CollectionUtil;
import org.xipki.util.JSON;
import org.xipki.util.LogUtil;
import org.xipki.util.RandomUtil;
import org.xipki.util.StringUtil;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OcspStore for XiPKI OCSP database.
 *
 * @author Lijun Liao (xipki)
 * @since 2.0.0
 */

public class DbCertStatusStore extends OcspStore {

  private class StoreUpdateService implements Runnable {

    @Override
    public void run() {
      updateIssuerStore();
    }

  } // class StoreUpdateService

  protected DataSourceWrapper datasource;

  private static final Logger LOG = LoggerFactory.getLogger(DbCertStatusStore.class);

  private static final int SECONDS_PER_5MIN = 300;

  private final Object lock = new Object();

  private final AtomicBoolean storeUpdateInProcess = new AtomicBoolean(false);

  private final StoreUpdateService storeUpdateService = new StoreUpdateService();

  private String sqlCsNoRit;

  private String sqlCs;

  private String sqlCsNoRitWithCertHash;

  private String sqlCsWithCertHash;

  private IssuerFilter issuerFilter;

  private final IssuerStore issuerStore = new IssuerStore();

  private HashAlgo certHashAlgo;

  private boolean initialized;

  private ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;

  protected List<Runnable> getScheduledServices() {
    return Collections.singletonList(storeUpdateService);
  }

  protected IssuerStore getIssuerStore() {
    return issuerStore;
  }

  private synchronized void updateIssuerStore() {
    updateIssuerStore(false);
  }

  protected void updateIssuerStore(boolean force) {
    if (!force) {
      if (storeUpdateInProcess.get()) {
        return;
      }
    }

    synchronized (lock) {
      if (force) {
        while (storeUpdateInProcess.get()) {
          try {
            wait(1000);
          } catch (InterruptedException ex) {
            LOG.warn("interrupted, continue waiting");
          }
        }
      }

      storeUpdateInProcess.set(true);
      try {
        updateIssuers();
        updateCrls();
      } finally {
        initialized = true;
        storeUpdateInProcess.set(false);
      }
    } // end lock
  } // method updateIssuerStore

  private void updateIssuers() {
    try {
      if (initialized) {
        final String sql = "SELECT ID,REV_INFO,S1C FROM ISSUER";
        PreparedStatement ps = preparedStatement(sql);
        ResultSet rs = null;

        try {
          Map<Integer, SimpleIssuerEntry> newIssuers = new HashMap<>();

          rs = ps.executeQuery();
          while (rs.next()) {
            if (!issuerFilter.includeAll()) {
              String sha1Fp = rs.getString("S1C");
              if (!issuerFilter.includeIssuerWithSha1Fp(sha1Fp)) {
                continue;
              }
            }

            int id = rs.getInt("ID");
            Instant revTime = null;
            String str = rs.getString("REV_INFO");
            if (str != null) {
              revTime = CertRevocationInfo.fromEncoded(str).getRevocationTime();
            }
            newIssuers.put(id, new SimpleIssuerEntry(id, revTime));
          }

          // no change in the issuerStore
          Set<Integer> newIds = newIssuers.keySet();
          Set<Integer> ids = issuerStore.getIds();

          boolean issuersUnchanged = (ids.size() == newIds.size())
              && ids.containsAll(newIds) && newIds.containsAll(ids);

          if (issuersUnchanged) {
            for (Integer id : newIds) {
              if (!newIssuers.get(id).match(issuerStore.getIssuerForId(id))) {
                issuersUnchanged = false;
                break;
              }
            }
          }

          if (issuersUnchanged) {
            return;
          }
        } finally {
          releaseDbResources(ps, rs);
        }
      } // end if(initialized)

      final String sql = "SELECT ID,NBEFORE,REV_INFO,S1C,CERT,CRL_ID FROM ISSUER";
      PreparedStatement ps = preparedStatement(sql);

      ResultSet rs = null;
      try {
        rs = ps.executeQuery();
        List<IssuerEntry> caInfos = new LinkedList<>();
        while (rs.next()) {
          String sha1Fp = rs.getString("S1C");
          if (!issuerFilter.includeIssuerWithSha1Fp(sha1Fp)) {
            continue;
          }

          X509Cert cert = X509Util.parseCert(StringUtil.toUtf8Bytes(rs.getString("CERT")));
          IssuerEntry caInfoEntry = new IssuerEntry(rs.getInt("ID"), cert);
          RequestIssuer reqIssuer = new RequestIssuer(HashAlgo.SHA1, caInfoEntry.getEncodedHash(HashAlgo.SHA1));
          for (IssuerEntry existingIssuer : caInfos) {
            if (existingIssuer.matchHash(reqIssuer)) {
              throw new Exception("found at least two issuers with the same subject and key");
            }
          }

          String str = rs.getString("REV_INFO");
          if (str != null) {
            CertRevocationInfo revInfo = CertRevocationInfo.fromEncoded(str);
            caInfoEntry.setRevocationInfo(revInfo.getRevocationTime());
          }

          caInfoEntry.setCrlId(rs.getInt("CRL_ID"));

          caInfos.add(caInfoEntry);
        } // end while (rs.next())

        this.issuerStore.setIssuers(caInfos);
        if (LOG.isInfoEnabled()) {
          StringBuilder sb = new StringBuilder();
          for (IssuerEntry m : caInfos) {
            sb.append(overviewString(m.getCert()));
            sb.append("\n");
          }
          if (sb.length() > 1) {
            sb.deleteCharAt(sb.length() - 1);
          }
          LOG.info("Updated store {} with issuers {}", name, sb);
        }
      } finally {
        releaseDbResources(ps, rs);
      }
    } catch (Throwable th) {
      LogUtil.error(LOG, th, "error while executing updateIssuers()");
    }
  } // method updateIssuers

  private void updateCrls() {
    try {
      final String sql = "SELECT ID,INFO FROM CRL_INFO";
      PreparedStatement ps = preparedStatement(sql);
      ResultSet rs = null;

      try {
        Map<Integer, CrlInfo> crlInfos = new HashMap<>();

        rs = ps.executeQuery();
        while (rs.next()) {
          crlInfos.put(rs.getInt("ID"), new CrlInfo(rs.getString("INFO")));
        }

        issuerStore.setCrlInfos(crlInfos);

        LOG.info("Updated CRL_INFOs of store {}", name);
      } finally {
        releaseDbResources(ps, rs);
      }
    } catch (Throwable th) {
      LogUtil.error(LOG, th, "error while executing updateCrls()");
    }
  } // method updateCrls

  @Override
  protected CertStatusInfo getCertStatus0(Instant time, RequestIssuer reqIssuer, BigInteger serialNumber,
                                          boolean includeCertHash, boolean includeRit, boolean inheritCaRevocation)
      throws OcspStoreException {
    if (serialNumber.signum() != 1) { // non-positive serial number
      return CertStatusInfo.getUnknownCertStatusInfo(Instant.now(), null);
    }

    if (!initialized) {
      throw new OcspStoreException("initialization of CertStore is still in process");
    }

    String sql;

    try {
      IssuerEntry issuer = issuerStore.getIssuerForFp(reqIssuer);
      if (issuer == null) {
        return null;
      }

      CrlInfo crlInfo = null;
      if (issuer.getCrlId() != 0) {
        crlInfo = issuerStore.getCrlInfo(issuer.getCrlId());
        // check whether CRL is expired
        if (isIgnoreExpiredCrls()) {
          // CRL will expire in 5 minutes
          if (crlInfo.getNextUpdate().getEpochSecond() < time.getEpochSecond() + SECONDS_PER_5MIN) {
            return CertStatusInfo.getCrlExpiredStatusInfo();
          }
        }
      }

      if (includeCertHash) {
        sql = includeRit ? sqlCsWithCertHash : sqlCsNoRitWithCertHash;
      } else {
        sql = includeRit ? sqlCs : sqlCsNoRit;
      }

      ResultSet rs = null;
      CertStatusInfo certStatusInfo;

      boolean unknown = true;
      boolean ignore = false;
      String b64CertHash = null;
      boolean revoked = false;
      int reason = 0;
      long revTime = 0;
      long invalTime = 0;
      int crlId = 0;

      PreparedStatement ps = datasource.prepareStatement(sql);

      try {
        ps.setInt(1, issuer.getId());
        ps.setString(2, serialNumber.toString(16));
        rs = ps.executeQuery();

        if (rs.next()) {
          unknown = false;
          crlId = rs.getInt("CRL_ID");

          long timeInSec = time.getEpochSecond();
          if (ignoreNotYetValidCert) {
            long notBeforeInSec = rs.getLong("NBEFORE");
            if (notBeforeInSec != 0 && timeInSec < notBeforeInSec) {
              ignore = true;
            }
          }

          if (!ignore && ignoreExpiredCert) {
            long notAfterInSec = rs.getLong("NAFTER");
            if (notAfterInSec != 0 && timeInSec > notAfterInSec) {
              ignore = true;
            }
          }

          if (!ignore) {
            if (includeCertHash) {
              b64CertHash = rs.getString("HASH");
            }

            revoked = rs.getBoolean("REV");
            if (revoked) {
              reason = rs.getInt("RR");
              revTime = rs.getLong("RT");
              if (includeRit) {
                invalTime = rs.getLong("RIT");
              }
            }
          }
        } // end if (rs.next())
      } catch (SQLException ex) {
        throw datasource.translate(sql, ex);
      } finally {
        releaseDbResources(ps, rs);
      }

      if (crlId == 0) {
        crlId = issuer.getCrlId();
      }

      if (crlInfo == null && crlId != 0) {
        crlInfo = issuerStore.getCrlInfo(crlId);
      }

      Instant thisUpdate;
      Instant nextUpdate;
      if (crlInfo == null) {
        thisUpdate = Instant.now();
        nextUpdate = null;
      } else {
        thisUpdate = crlInfo.getThisUpdate();
        nextUpdate = crlInfo.getNextUpdate();

        if (isIgnoreExpiredCrls()) {
          // CRL will expire in 5 minutes
          if (crlInfo.getNextUpdate().getEpochSecond() < time.getEpochSecond() + SECONDS_PER_5MIN) {
            return CertStatusInfo.getCrlExpiredStatusInfo();
          }
        }
      }

      if (unknown) {
        certStatusInfo = CertStatusInfo.getUnknownCertStatusInfo(thisUpdate, nextUpdate);
      } else if (ignore) {
        certStatusInfo = CertStatusInfo.getIgnoreCertStatusInfo(thisUpdate, nextUpdate);
      } else {
        byte[] certHash = (b64CertHash == null) ? null : Base64.decodeFast(b64CertHash);
        if (revoked) {
          Instant invTime = (invalTime == 0 || invalTime == revTime) ? null : Instant.ofEpochSecond(invalTime);
          CertRevocationInfo revInfo = new CertRevocationInfo(reason, Instant.ofEpochSecond(revTime), invTime);
          certStatusInfo = CertStatusInfo.getRevokedCertStatusInfo(revInfo,
              certHashAlgo, certHash, thisUpdate, nextUpdate, null);
        } else {
          certStatusInfo = CertStatusInfo.getGoodCertStatusInfo(certHashAlgo, certHash, thisUpdate, nextUpdate, null);
        }
      }

      if (includeCrlId && crlInfo != null) {
        certStatusInfo.setCrlId(crlInfo.getCrlId());
      }

      if (includeArchiveCutoff) {
        if (retentionInterval != 0) {
          Instant date;

          if (retentionInterval < 0) {
            date = issuer.getNotBefore(); // expired certificate remains in status store forever
          } else {
            Instant t1 = Instant.now().minus(retentionInterval, ChronoUnit.DAYS);
            date = issuer.getNotBefore().isBefore(t1) ? issuer.getNotBefore() : t1;
          }

          certStatusInfo.setArchiveCutOff(date);
        }
      }

      if ((!inheritCaRevocation) || issuer.getRevocationInfo() == null) {
        return certStatusInfo;
      }

      CertRevocationInfo caRevInfo = issuer.getRevocationInfo();
      CertStatus certStatus = certStatusInfo.getCertStatus();
      boolean replaced = false;
      if (certStatus == CertStatus.GOOD) {
        replaced = true;
      } else if (certStatus == CertStatus.UNKNOWN || certStatus == CertStatus.IGNORE) {
        if (unknownCertBehaviour == UnknownCertBehaviour.good) {
          replaced = true;
        }
      } else if (certStatus == CertStatus.REVOKED) {
        if (certStatusInfo.getRevocationInfo().getRevocationTime().isAfter(caRevInfo.getRevocationTime())) {
          replaced = true;
        }
      }

      if (replaced) {
        CertRevocationInfo newRevInfo = (caRevInfo.getReason() == CrlReason.CA_COMPROMISE)
            ? caRevInfo
            : new CertRevocationInfo(CrlReason.CA_COMPROMISE, caRevInfo.getRevocationTime(),
                  caRevInfo.getInvalidityTime());

        certStatusInfo = CertStatusInfo.getRevokedCertStatusInfo(newRevInfo,
            certStatusInfo.getCertHashAlgo(), certStatusInfo.getCertHash(),
            certStatusInfo.getThisUpdate(), certStatusInfo.getNextUpdate(), certStatusInfo.getCertprofile());
      }
      return certStatusInfo;
    } catch (DataAccessException ex) {
      throw new OcspStoreException(ex.getMessage(), ex);
    }

  } // method getCertStatus0

  /**
   * Borrow PreparedStatement.
   * @return the next idle preparedStatement, {@code null} will be returned if no
   *     PreparedStatement can be created within 5 seconds.
   */
  private PreparedStatement preparedStatement(String sqlQuery) throws DataAccessException {
    return datasource.prepareStatement(sqlQuery);
  }

  @Override
  public boolean isHealthy() {
    if (!isInitialized()) {
      return false;
    }

    final String sql = "SELECT ID FROM ISSUER";

    try {
      PreparedStatement ps = preparedStatement(sql);
      ResultSet rs = null;
      try {
        rs = ps.executeQuery();
        return true;
      } finally {
        releaseDbResources(ps, rs);
      }
    } catch (Exception ex) {
      LogUtil.error(LOG, ex);
      return false;
    }
  } // method isHealthy

  private void releaseDbResources(PreparedStatement ps, ResultSet rs) {
    datasource.releaseResources(ps, rs);
  }

  /**
   * Initialize the store.
   *
   * @param sourceConf
   * the store source configuration. It contains following key-value pairs:
   * <ul>
   * <li>caCerts: optional
   *   <p>
   *   CA certificate files to be included / excluded.</li>
   *  </ul>
   * @param datasource DataSource.
   */
  @Override
  public void init(Map<String, ?> sourceConf, DataSourceWrapper datasource) throws OcspStoreException {
    OcspServerConf.CaCerts caCerts = null;
    if (sourceConf != null) {
      Object objValue = sourceConf.get("caCerts");
      if (objValue != null) {
        caCerts = JSON.parseConf(JSON.toJSONBytes(objValue), OcspServerConf.CaCerts.class);
      }
    }

    this.datasource = Args.notNull(datasource, "datasource");

    sqlCs = datasource.buildSelectFirstSql(1,
        "NBEFORE,NAFTER,REV,RR,RT,RIT,CRL_ID FROM CERT WHERE IID=? AND SN=?");
    sqlCsNoRit = datasource.buildSelectFirstSql(1,
        "NBEFORE,NAFTER,REV,RR,RT,CRL_ID FROM CERT WHERE IID=? AND SN=?");

    sqlCsWithCertHash = datasource.buildSelectFirstSql(1,
        "NBEFORE,NAFTER,REV,RR,RT,RIT,HASH,CRL_ID FROM CERT WHERE IID=? AND SN=?");
    sqlCsNoRitWithCertHash = datasource.buildSelectFirstSql(1,
        "NBEFORE,NAFTER,REV,RR,RT,HASH,CRL_ID FROM CERT WHERE IID=? AND SN=?");

    try {
      this.certHashAlgo = getCertHashAlgo(datasource);
    } catch (NoSuchAlgorithmException | DataAccessException ex) {
      throw new OcspStoreException("Could not retrieve the certhash's algorithm from the database", ex);
    }

    Set<X509Cert> includeIssuers = null;
    Set<X509Cert> excludeIssuers = null;

    if (caCerts != null) {
      if (CollectionUtil.isNotEmpty(caCerts.getIncludes())) {
        includeIssuers = parseCerts(caCerts.getIncludes());
      }

      if (CollectionUtil.isNotEmpty(caCerts.getExcludes())) {
        excludeIssuers = parseCerts(caCerts.getExcludes());
      }
    }

    this.issuerFilter = new IssuerFilter(includeIssuers, excludeIssuers);

    updateIssuerStore();

    if (this.scheduledThreadPoolExecutor != null) {
      this.scheduledThreadPoolExecutor.shutdownNow();
    }

    if (updateInterval != null) {
      List<Runnable> scheduledServices = getScheduledServices();
      int size = scheduledServices == null ? 0 : scheduledServices.size();
      if (size > 0) {
        this.scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(size);
        long intervalSeconds = updateInterval.approxMinutes() * 60;
        for (Runnable service : scheduledServices) {
          this.scheduledThreadPoolExecutor.scheduleAtFixedRate(service,
              intervalSeconds + RandomUtil.nextInt(60), intervalSeconds, TimeUnit.SECONDS);
        }
      }
    }
  } // method init

  @Override
  public void close() {
    if (scheduledThreadPoolExecutor != null) {
      scheduledThreadPoolExecutor.shutdown();
      scheduledThreadPoolExecutor = null;
    }

    if (datasource != null) {
      datasource.close();
    }
  }

  @Override
  public boolean knowsIssuer(RequestIssuer reqIssuer) {
    return null != issuerStore.getIssuerForFp(reqIssuer);
  }

  @Override
  public X509Cert getIssuerCert(RequestIssuer reqIssuer) {
    IssuerEntry issuer = issuerStore.getIssuerForFp(reqIssuer);
    return (issuer == null) ? null : issuer.getCert();
  }

  protected boolean isInitialized() {
    return initialized;
  }

  static Set<X509Cert> parseCerts(Collection<String> certFiles) throws OcspStoreException {
    Set<X509Cert> certs = new HashSet<>(certFiles.size());
    for (String certFile : certFiles) {
      try {
        certs.add(X509Util.parseCert(new File(certFile)));
      } catch (CertificateException | IOException ex) {
        throw new OcspStoreException("could not parse X.509 certificate from file "
            + certFile + ": " + ex.getMessage(), ex);
      }
    }
    return certs;
  }

  public static HashAlgo getCertHashAlgo(DataSourceWrapper datasource)
      throws DataAccessException, NoSuchAlgorithmException {
    // analyze the database
    String certHashAlgoStr = Optional.ofNullable(
        datasource.getFirstStringValue(null, "DBSCHEMA", "VALUE2", "NAME='CERTHASH_ALGO'"))
        .orElseThrow(() ->
            new DataAccessException("Column with NAME='CERTHASH_ALGO' is not defined in table DBSCHEMA"));

    return HashAlgo.getInstance(certHashAlgoStr);
  }

}
