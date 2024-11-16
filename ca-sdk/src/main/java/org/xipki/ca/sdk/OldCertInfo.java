// Copyright (c) 2013-2024 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.ca.sdk;

import org.xipki.util.Args;
import org.xipki.util.cbor.CborDecoder;
import org.xipki.util.cbor.CborEncoder;
import org.xipki.util.exception.DecodeException;
import org.xipki.util.exception.EncodeException;

import java.io.IOException;
import java.math.BigInteger;

/**
 *
 * @author Lijun Liao (xipki)
 * @since 6.0.0
 */

public class OldCertInfo extends SdkEncodable {

  private static final long TAG_ISN = 1;

  private static final long TAG_SUBJECT = 2;

  private static final long TAG_FSN = 3;

  /**
   * Whether to reu-use the public key in the old certificate for the new one.
   */
  private final boolean reusePublicKey;

  private ByIssuerAndSerial isn;

  private BySha1FpAndSerial fsn;

  private BySubject subject;

  public OldCertInfo(boolean reusePublicKey, ByIssuerAndSerial isn) {
    this.reusePublicKey = reusePublicKey;
    this.isn = Args.notNull(isn, "isn");
  }

  public OldCertInfo(boolean reusePublicKey, BySha1FpAndSerial fsn) {
    this.reusePublicKey = reusePublicKey;
    this.fsn = Args.notNull(fsn, "fsn");
  }

  public OldCertInfo(boolean reusePublicKey, BySubject subject) {
    this.reusePublicKey = reusePublicKey;
    this.subject = Args.notNull(subject, "subject");
  }

  public boolean isReusePublicKey() {
    return reusePublicKey;
  }

  public ByIssuerAndSerial getIsn() {
    return isn;
  }

  public BySubject getSubject() {
    return subject;
  }

  public BySha1FpAndSerial getFsn() {
    return fsn;
  }

  @Override
  protected void encode0(CborEncoder encoder) throws IOException, EncodeException {
    encoder.writeArrayStart(2);
    encoder.writeBoolean(isReusePublicKey());
    if (isn != null) {
      encoder.writeTag(TAG_ISN);
      encoder.writeObject(isn);
    } else if (subject != null) {
      encoder.writeTag(TAG_SUBJECT);
      encoder.writeObject(subject);
    } else {
      encoder.writeTag(TAG_FSN);
      encoder.writeObject(fsn);
    }
  }

  public static OldCertInfo decode(CborDecoder decoder) throws DecodeException {
    try {
      if (decoder.readNullOrArrayLength(2)) {
        return null;
      }

      boolean usePublicKey = decoder.readBoolean();
      long tag = decoder.readTag();
      if (tag == TAG_ISN) {
        ByIssuerAndSerial isn = ByIssuerAndSerial.decode(decoder);
        return new OldCertInfo(usePublicKey, isn);
      } else if (tag == TAG_SUBJECT) {
        BySubject subject = BySubject.decode(decoder);
        return new OldCertInfo(usePublicKey, subject);
      } else { // if (tag == TAG_FSN) {
        BySha1FpAndSerial fsn = BySha1FpAndSerial.decode(decoder);
        return new OldCertInfo(usePublicKey, fsn);
      }
    } catch (RuntimeException ex) {
      throw new DecodeException(buildDecodeErrMessage(ex, ByIssuerAndSerial.class), ex);
    }
  }

  public static class ByIssuerAndSerial extends SdkEncodable {

    private final X500NameType issuer;

    /**
     * Uppercase hex encoded serialNumber.
     */
    private final BigInteger serialNumber;

    public ByIssuerAndSerial(X500NameType issuer, BigInteger serialNumber) {
      this.issuer = issuer;
      this.serialNumber = serialNumber;
    }

    public X500NameType getIssuer() {
      return issuer;
    }

    public BigInteger getSerialNumber() {
      return serialNumber;
    }

    @Override
    protected void encode0(CborEncoder encoder) throws IOException, EncodeException {
      encoder.writeArrayStart(2);
      encoder.writeObject(issuer);
      encoder.writeBigInt(serialNumber);
    }

    public static ByIssuerAndSerial decode(CborDecoder decoder) throws DecodeException {
      try {
        if (decoder.readNullOrArrayLength(2)) {
          return null;
        }

        return new ByIssuerAndSerial(
            X500NameType.decode(decoder),
            decoder.readBigInt());
      } catch (RuntimeException ex) {
        throw new DecodeException(buildDecodeErrMessage(ex, ByIssuerAndSerial.class), ex);
      }
    }
  }

  public static class BySha1FpAndSerial extends SdkEncodable {

    private final byte[] caCertSha1;

    /**
     * Uppercase hex encoded serialNumber.
     */
    private final BigInteger serialNumber;

    public BySha1FpAndSerial(byte[] caCertSha1, BigInteger serialNumber) {
      this.caCertSha1 = caCertSha1;
      this.serialNumber = serialNumber;
    }

    public byte[] getCaCertSha1() {
      return caCertSha1;
    }

    public BigInteger getSerialNumber() {
      return serialNumber;
    }

    @Override
    protected void encode0(CborEncoder encoder) throws IOException, EncodeException {
      encoder.writeArrayStart(2);
      encoder.writeByteString(caCertSha1);
      encoder.writeBigInt(serialNumber);
    }

    public static BySha1FpAndSerial decode(CborDecoder decoder) throws DecodeException {
      try {
        if (decoder.readNullOrArrayLength(2)) {
          return null;
        }

        return new BySha1FpAndSerial(
            decoder.readByteString(),
            decoder.readBigInt());
      } catch (RuntimeException ex) {
        throw new DecodeException(buildDecodeErrMessage(ex, ByIssuerAndSerial.class), ex);
      }
    }
  }

  public static class BySubject extends SdkEncodable {

    private final byte[] subject;

    private final byte[] san;

    public BySubject(byte[] subject, byte[] san) {
      this.subject = subject;
      this.san = san;
    }

    public byte[] getSubject() {
      return subject;
    }

    public byte[] getSan() {
      return san;
    }

    @Override
    protected void encode0(CborEncoder encoder) throws IOException, EncodeException {
      encoder.writeArrayStart(2);
      encoder.writeByteString(subject);
      encoder.writeByteString(san);
    }

    public static BySubject decode(CborDecoder decoder) throws DecodeException {
      try {
        if (decoder.readNullOrArrayLength(2)) {
          return null;
        }

        return new BySubject(
            decoder.readByteString(),
            decoder.readByteString());
      } catch (RuntimeException ex) {
        throw new DecodeException(buildDecodeErrMessage(ex, BySubject.class), ex);
      }
    }

  }
}
